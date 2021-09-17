package com.kyubot.rkt.core

import kotlinx.serialization.SerializationStrategy

interface Message<T> {
    /**
     * The broker that received this message.
     */
    val broker: Broker

    /**
     * The data of this message.
     */
    val data: T

    /**
     * Replies to this message.
     * @param serializationStrategy The serialization strategy used for the reply [message]
     * @param message The reply message to send
     */
    suspend fun <T> reply(serializationStrategy: SerializationStrategy<T>, message: T)
}
