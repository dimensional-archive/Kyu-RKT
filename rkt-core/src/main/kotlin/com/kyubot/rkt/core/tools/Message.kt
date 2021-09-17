package com.kyubot.rkt.core.tools

import com.kyubot.rkt.core.Message
import kotlinx.serialization.serializer

/**
 * Replies to this message with the provided [message]
 *
 * @param message Message to reply with.
 */
suspend inline fun <reified T : Any> Message<*>.reply(message: T) =
    reply(T::class.serializer(), message)
