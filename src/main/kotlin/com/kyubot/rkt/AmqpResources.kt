package com.kyubot.rkt

import com.rabbitmq.client.ConnectionFactory
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.protobuf.ProtoBuf

data class AmqpResources(
    val format: BinaryFormat,
    val group: String,
    val subGroup: String? = null,
    val factory: ConnectionFactory? = null,
    val exchangeType: ExchangeType
) {
    val connectionFactory by lazy {
        factory ?: ConnectionFactory().also { it.useNio() }
    }

    class Builder {

        /**
         * The exchange type to use, defaults to [ExchangeType.Direct]
         */
        val exchangeType: ExchangeType = ExchangeType.Direct

        /**
         * [BinaryFormat] used for (de)serialization any published or received messages.
         * Defaults to [ProtoBuf]
         */
        var format: BinaryFormat? = null

        /**
         * The "group" this broker is bound to, defaults to "default".
         */
        var group: String = "default"

        /**
         * The "sub group" this broker is bound to.
         */
        var subGroup: String? = null

        /**
         * [ConnectionFactory] used for creating new connections to the rabbitmq server.
         */
        var connectionFactory: ConnectionFactory? = null

        /**
         * Configures the [group] name of this broker.
         */
        fun group(name: String) {
            group = name
        }

        /**
         * Configures the [group] and [subGroup] of this broker.
         */
        fun group(pair: Pair<String, String>) {
            group(pair.first)
            subGroup = pair.second
        }

        /**
         *
         */
        operator fun String.rangeTo(other: String): Pair<String, String> {
            return this to other
        }

        /**
         * Creates an instance of [AmqpResources] with the configured values.
         */
        fun create(): AmqpResources = AmqpResources(
            format = format ?: ProtoBuf { },
            group = group,
            subGroup = subGroup,
            factory = connectionFactory,
            exchangeType = exchangeType
        )

    }
}
