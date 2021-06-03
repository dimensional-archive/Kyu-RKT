package com.kyubot.rkt

import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

data class Consumer<T : Any>(val klass: KClass<T>, val broker: Amqp, val flow: MutableSharedFlow<Delivery>) {

  /**
   *
   */
  val callback: DeliverCallback = DeliverCallback { _, delivery ->
    flow.tryEmit(delivery)
  }

  /**
   *
   */
  fun take(scope: CoroutineScope, block: suspend Message<T>.() -> Unit): Job {
    return flow
      .onEach {
        broker.launch {
          it
            .runCatching { Message(this, broker, klass).block() }
            .onFailure { }
        }
      }
      .launchIn(scope)
  }

}
