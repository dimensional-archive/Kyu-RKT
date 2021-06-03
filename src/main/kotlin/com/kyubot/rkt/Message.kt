package com.kyubot.rkt

import com.rabbitmq.client.Delivery
import com.rabbitmq.client.MessageProperties
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

data class Message<T : Any>(val delivery: Delivery, val broker: Amqp, val klass: KClass<T>) {

  val data: T by lazy {
    broker.resources.format.decodeFromByteArray(klass.serializer(), delivery.body)
  }

  /**
   * Acknowledges this message
   *
   * @param allUpTo Whether all messages up to this one should be acknowledged.
   */
  fun ack(allUpTo: Boolean = false) =
    broker.channel.basicAck(delivery.envelope.deliveryTag, allUpTo)

  /**
   * Reject multiple messages
   *
   * @param allUpTo Whether to reject all messages up to this one
   * @param requeue Whether the publisher should requeue
   */
  fun nack(allUpTo: Boolean = false, requeue: Boolean = false) =
    broker.channel.basicNack(delivery.envelope.deliveryTag, allUpTo, requeue)

  /**
   * Rejects this message
   *
   * @param requeue Whether the publisher should requeue
   */
  fun reject(requeue: Boolean = false) =
    broker.channel.basicReject(delivery.envelope.deliveryTag, requeue)

  inline fun <reified T : Any> reply(data: T) {
    reply(T::class.serializer(), data)
  }

  fun <T : Any> reply(serializer: SerializationStrategy<T>, data: T) {
    val bytes = broker.resources.format.encodeToByteArray(serializer, data)

    broker.channel.basicPublish(
      "",
      delivery.properties.replyTo,
      MessageProperties.BASIC
        .builder()
        .correlationId(delivery.properties.correlationId)
        .build(),
      bytes
    )
  }

}
