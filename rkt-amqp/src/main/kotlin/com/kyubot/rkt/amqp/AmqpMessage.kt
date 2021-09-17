package com.kyubot.rkt.amqp

import com.kyubot.rkt.core.Message
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.MessageProperties
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

open class AmqpMessage<T>(override val broker: Amqp, val delivery: Delivery, val deserializer: DeserializationStrategy<T>) :
    Message<T> {
    override val data: T by lazy {
        broker.resources.format.decodeFromByteArray(deserializer, delivery.body)
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

    override suspend fun <T> reply(serializationStrategy: SerializationStrategy<T>, message: T) {
        val bytes = broker.resources.format.encodeToByteArray(serializationStrategy, message)

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
