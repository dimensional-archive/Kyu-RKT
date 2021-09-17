package com.kyubot.rkt.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.protobuf.ProtoBuf

data class BrokerResources(
    val format: BinaryFormat,
    val group: String,
    val subGroup: String? = null,
    val dispatcher: CoroutineDispatcher
) {
    class Builder {
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
         * The "subgroup" this broker is bound to.
         */
        var subGroup: String? = null

        /**
         * The [CoroutineDispatcher] to use.
         */
        var dispatcher: CoroutineDispatcher = Dispatchers.Default

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
         * Creates an instance of [BrokerResources] with the configured values.
         */
        fun create(): BrokerResources = BrokerResources(
            format = format ?: ProtoBuf { },
            group = group,
            subGroup = subGroup,
            dispatcher = dispatcher
        )
    }
}
