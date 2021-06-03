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
     *
     */
    val exchangeType: ExchangeType = ExchangeType.Direct

    /**
     *
     */
    var format: BinaryFormat? = null

    /**
     *
     */
    var group: String? = null

    /**
     *
     */
    var subGroup: String? = null

    /**
     *
     */
    fun group(name: String) {
      group = name
    }

    /**
     *
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

    fun create(): AmqpResources {
      require(group != null) {
        "Group mustn't be null."
      }

      return AmqpResources(
        format = format ?: ProtoBuf { },
        group = group!!,
        subGroup = subGroup,
        factory = null,
        exchangeType = exchangeType
      )
    }
  }

}
