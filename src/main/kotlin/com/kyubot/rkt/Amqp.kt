package com.kyubot.rkt

import com.rabbitmq.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class Amqp(val resources: AmqpResources) : CoroutineScope {

  companion object {
    val log: Logger = LoggerFactory.getLogger(Amqp::class.java)

    /**
     *
     */
    operator fun invoke(builder: AmqpResources.Builder.() -> Unit): Amqp {
      return Amqp(
        AmqpResources.Builder()
          .also(builder)
          .create()
      )
    }
  }

  /**
   * The connection to the AMQP Broker
   */
  private lateinit var connection: Connection

  /**
   * The amqp channel.
   */
  private lateinit var _channel: Channel

  /**
   *
   */
  private val _state = MutableStateFlow(BrokerState.Idle)

  /**
   * The callback queue
   */
  private var callbackQueue: String? = null

  /**
   *
   */
  private val consumers = mutableMapOf<String, Consumer<*>>()

  /**
   *
   */
  private val waiters: MutableMap<String, Waiter<*>> = mutableMapOf()

  /**
   *
   */
  val state: StateFlow<BrokerState>
    get() = _state

  /**
   *
   */
  val channel: Channel
    get() = _channel

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + Job()

  /**
   *
   */
  fun connect(conn: Connection) {
    require(conn.isOpen) {
      "Provided connection isn't open"
    }

    connection = conn
  }

  /**
   *
   */
  fun connect(url: String = "amqp://localhost"): Connection {
    /* check for */
    if (::connection.isInitialized && connection.isOpen) {
      connection.removeShutdownListener(::onShutdown)
      connection.close()
    }

    /* create a new connection */
    connection = resources.connectionFactory.newConnection(url)

    /* add shutdown listener */
    connection.addShutdownListener(::onShutdown)

    _channel = connection.createChannel()
    _state.tryEmit(BrokerState.Connected)
    log.info("Connected to RabbitMQ.")

    /* setup rpc callback queue */
    setupCallback()

    /* make sure to create the exchange */
    _channel.exchangeDeclare(resources.group, resources.exchangeType.asString, true)

    return connection
  }

  /**
   *
   */
  inline fun <reified T : Any> on(scope: CoroutineScope = this, noinline block: suspend Message<T>.() -> Unit) {
    on(T::class, scope, block)
  }

  /**
   *
   */
  fun <T : Any> on(klass: KClass<T>, scope: CoroutineScope = this, block: suspend Message<T>.() -> Unit): Job {
    val queue = klass.findAnnotation<Queue>()?.name?.lowercase()
      ?: throw IllegalArgumentException("Provided class must be annotated with @Queue")

    /* consumer */
    @Suppress("UNCHECKED_CAST")
    val consumer = consumers[queue] as? Consumer<T> ?: subscribe(queue, klass)

    /* receive the job */
    return consumer.take(scope, block)
  }

  /**
   * Subscribes to the supplied queue name
   *
   * @param name Queue name
   * @param klass The class to deserialize data into
   */
  fun <T : Any> subscribe(name: String, klass: KClass<T>): Consumer<T> {
    val consumer = Consumer(klass, this, MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE))
    with(createQueue(name)) {
      val cancellation = CancelCallback {
        consumers.remove(this)
      }

      _channel.basicConsume(this, consumer.callback, cancellation)
    }

    return consumer
  }

  /**
   *
   */
  inline fun <reified T : Any> publish(data: T) {
    publish(T::class, data)
  }

  /**
   *
   */
  fun <T : Any> publish(klass: KClass<T>, data: T) {
    val queue = klass.findAnnotation<Queue>()?.name?.lowercase()
      ?: throw IllegalArgumentException("Provided class must be annotated with @Queue")

    val bytes = resources.format.encodeToByteArray(klass.serializer(), data)
    _channel.basicPublish(resources.group, queue, MessageProperties.BASIC, bytes)
  }

  /**
   *
   */
  inline fun <reified R : Any, reified S : Callable<R>> call(data: S): CompletableDeferred<R> {
    return call(S::class to R::class, data)
  }

  /**
   *
   */
  fun <R : Any, S : Any> call(klass: Pair<KClass<S>, KClass<R>>, data: S): CompletableDeferred<R> {
    val queue = klass.first.findAnnotation<Queue>()?.name?.lowercase()
      ?: throw IllegalArgumentException("Receiving class must be annotated with @Queue")


    /* get the properties */
    val correlation = UUID.randomUUID()
    val properties = MessageProperties.BASIC.builder()
      .correlationId(correlation.toString())
      .replyTo(callbackQueue)
      .build()

    /* encode to byte array */
    val bytes = resources.format.encodeToByteArray(klass.first.serializer(), data)

    /* get the waiter */
    val waiter = Waiter(klass.second, CompletableDeferred()).also {
      waiters[correlation.toString()] = it
      _channel.basicPublish(resources.group, queue, properties, bytes)
    }

    return waiter.deferred
  }

  /**
   *
   */
  private fun createQueue(name: String): String {
    val queue = "${resources.group}${resources.subGroup?.let { ".$it" } ?: ""}:$name"
    _channel.queueDeclare(queue, true, false, false, emptyMap())
    _channel.queueBind(queue, resources.group, name)

    return queue
  }

  /**
   *
   */
  private fun onShutdown(exception: ShutdownSignalException) {
    _state.tryEmit(BrokerState.Disconnected)
    log.info("Disconnected from RabbitMQ")
    if (exception.isHardError) {
      return
    }

    log.info("Reconnecting...")
    runBlocking {
      connect()
    }
  }

  /**
   *
   */
  private fun setupCallback() {
    callbackQueue = _channel.queueDeclare().queue.also { queue ->
      val callback = DeliverCallback { _, msg ->
        val waiter = msg.properties.correlationId
          .let { waiters.remove(it) }

        waiter?.complete(msg.body, this)
      }

      _channel.basicConsume(queue, false, callback, CancelCallback { })
    }
  }

  /**
   *
   */
  private fun <T> ByteArray.decode(deserializer: DeserializationStrategy<T>): T {
    return resources.format.decodeFromByteArray(deserializer, this)
  }

}
