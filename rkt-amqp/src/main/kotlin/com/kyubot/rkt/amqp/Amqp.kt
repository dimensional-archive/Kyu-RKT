package com.kyubot.rkt.amqp

import com.kyubot.rkt.core.*
import com.rabbitmq.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.serializer
import mu.KotlinLogging
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.time.Duration

open class Amqp(
    override val resources: BrokerResources,
    private val exchangeType: ExchangeType,
    connectionFactory: ConnectionFactory? = null
) : Broker {
    companion object {
        private val executor = Executors.newSingleThreadScheduledExecutor()
        private val log = KotlinLogging.logger { }

        var SUB_GROUP_SEPARATOR: Char = '.'
        var QUEUE_SEPARATOR: Char = ':'

        /*
         * Convenience method for creating an instance of [AmqpBroker]
         *
         * @param block Used for configuration a [BrokerResources] instance.
         */
        operator fun invoke(
            exchangeType: ExchangeType = ExchangeType.Direct,
            block: BrokerResources.Builder.() -> Unit = {}
        ): Amqp {
            val resources = BrokerResources.Builder()
                .also(block)
                .create()

            return Amqp(resources, exchangeType)
        }
    }

    protected lateinit var mutableConnection: Connection
    protected lateinit var mutableChannel: Channel
    protected val mutableState = MutableStateFlow(BrokerState.Idle)
    protected var callbackQueue: String? = null
    protected val consumers = mutableMapOf<String, AmqpMessageConsumer<*>>()
    protected val waiters: MutableMap<String, AmqpMessageWaiter<*>> = mutableMapOf()
    protected val connectionFactory = connectionFactory ?: ConnectionFactory().also { it.useNio() }

    /**
     * The current connection.
     */
    val connection: Connection
        get() = mutableConnection

    /**
     * The current active channel.
     */
    val channel: Channel
        get() = mutableChannel

    /**
     * The prefix used for queue names.
     */
    val queuePrefix: String
        get() = "${resources.group}${resources.subGroup?.let { "$SUB_GROUP_SEPARATOR$it" } ?: ""}"

    override val state: BrokerState
        get() = mutableState.value

    override val coroutineContext: CoroutineContext
        get() = resources.dispatcher + SupervisorJob() + CoroutineName("Amqp Broker [$queuePrefix}]")

    /**
     * Uses [conn] as the rabbitmq connection for this broker.
     *
     * @param conn The [Connection] to use, must be open.
     */
    fun use(conn: Connection) {
        require(conn.isOpen) {
            "Provided connection isn't open"
        }

        mutableConnection = conn
        setup()
    }

    override suspend fun connect() =
        connect("amqp://localhost")

    override suspend fun connect(url: String) {
        /* check for */
        if (::mutableConnection.isInitialized && connection.isOpen) {
            connection.removeShutdownListener(::onShutdown)
            connection.close()
        }

        /* create a new connection */
        mutableConnection = withContext(Dispatchers.IO) {
            connectionFactory.newConnection(url)
        }

        /* setup this broker */
        setup()
    }

    override suspend fun <T> on(description: QueueDescriptor<T>, block: suspend Message<T>.() -> Unit): Job {
        subscribe(description)
        val consumer = consumers[description.name] as AmqpMessageConsumer<T>

        /* receive the job */
        return consumer.take(block = block)
    }

    override suspend fun <T> subscribe(descriptor: QueueDescriptor<T>): Boolean {
        if (consumers.containsKey(descriptor.name)) {
            return false
        }

        val consumer = AmqpMessageConsumer(descriptor, this, MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE))
        with(createQueue(descriptor.name)) {
            val cancellation = CancelCallback {
                consumers.remove(this)
            }

            withContext(Dispatchers.IO) {
                channel.basicConsume(this@with, consumer.callback, cancellation)
            }
        }

        consumers[descriptor.name] = consumer
        return true
    }

    override suspend fun <T> publish(descriptor: QueueDescriptor<T>, message: T) {
        val bytes = resources.format.encodeToByteArray(descriptor.serializer, message)
        withContext(Dispatchers.IO) {
            channel.basicPublish(resources.group, descriptor.name, MessageProperties.BASIC, bytes)
        }
    }

    override suspend fun <R : Any, S : Callable<R>> call(
        kClass: Pair<KClass<S>, KClass<R>>,
        message: S,
        timeout: Duration?
    ): Deferred<R> {
        val queue = kClass.first.findAnnotation<Queue>()?.name
            ?: throw IllegalArgumentException("Receiving class must be annotated with @Queue")

        /* get the properties */
        val correlation = UUID.randomUUID()
        val properties = MessageProperties.BASIC.builder()
            .correlationId(correlation.toString())
            .replyTo(callbackQueue)
            .build()

        /* encode to byte array */
        val bytes = resources.format.encodeToByteArray(kClass.first.serializer(), message)

        /* get the waiter */
        val waiter = AmqpMessageWaiter(kClass.second, CompletableDeferred()).also {
            waiters[correlation.toString()] = it
            withContext(Dispatchers.IO) {
                channel.basicPublish(resources.group, queue, properties, bytes)
            }
        }

        if (timeout != null) {
            val timeoutFuture = executor.schedule({
                val exception = CancellationException("Didn't receive message in time.", null)
                waiter.deferred.completeExceptionally(exception)
            }, timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

            waiter.deferred.invokeOnCompletion {
                if (it != null) timeoutFuture.cancel(false)
            }
        }

        return waiter.deferred
    }

    protected fun createQueue(name: String): String {
        val queue = "$queuePrefix$QUEUE_SEPARATOR$name"
        channel.queueDeclare(queue, true, false, false, emptyMap())
        channel.queueBind(queue, resources.group, name)

        return queue
    }

    protected fun onShutdown(exception: ShutdownSignalException) {
        mutableState.value = BrokerState.Disconnected
        log.info { "Disconnected from RabbitMQ" }
        if (exception.isHardError) {
            return
        }

        log.info { "Reconnecting..." }
        runBlocking {
            connect()
        }
    }

    protected fun setupCallback() {
        callbackQueue = mutableChannel.queueDeclare().queue.also { queue ->
            val callback = DeliverCallback { _, msg ->
                val waiter = msg.properties.correlationId
                    .let { waiters.remove(it) }

                waiter?.complete(msg.body, this)
            }

            mutableChannel.basicConsume(queue, false, callback, CancelCallback { })
        }
    }

    protected fun setup() {
        require(::mutableConnection.isInitialized && connection.isOpen) {
            "An existing connection must be present."
        }

        // add shutdown listener
        connection.addShutdownListener(::onShutdown)

        // initialize the channel if we haven't already.
        if (!::mutableChannel.isInitialized || !channel.isOpen) {
            mutableChannel = mutableConnection.createChannel()
            mutableState.value = BrokerState.Connected
            log.info("Connected to RabbitMQ.")
        }

        // setup rpc callback queue
        setupCallback()

        // make sure to create the exchange
        channel.exchangeDeclare(resources.group, exchangeType.name.lowercase(), true)
    }

}
