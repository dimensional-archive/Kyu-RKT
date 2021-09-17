package com.kyubot.rkt.amqp

import com.kyubot.rkt.core.QueueDescriptor
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

open class AmqpMessageConsumer<T>(val description: QueueDescriptor<T>, val broker: Amqp, val flow: MutableSharedFlow<Delivery>) : CoroutineScope {
    val callback: DeliverCallback = DeliverCallback { _, delivery ->
        flow.tryEmit(delivery)
    }

    override val coroutineContext: CoroutineContext
        get() = broker.resources.dispatcher + SupervisorJob()

    fun take(scope: CoroutineScope = this, block: suspend AmqpMessage<T>.() -> Unit): Job {
        return flow
            .onEach {
                broker.launch {
                    it
                        .runCatching { AmqpMessage(broker, this, description.serializer).block() }
                        .onFailure { }
                }
            }
            .launchIn(scope)
    }
}
