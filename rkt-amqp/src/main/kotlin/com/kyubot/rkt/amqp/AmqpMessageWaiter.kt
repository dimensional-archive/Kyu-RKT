package com.kyubot.rkt.amqp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

open class AmqpMessageWaiter<T : Any>(val klass: KClass<T>, val deferred: CompletableDeferred<T>) {
    fun complete(body: ByteArray, broker: Amqp) {
        val data = broker.resources.format.decodeFromByteArray(klass.serializer(), body)
        deferred.complete(data)
    }
}
