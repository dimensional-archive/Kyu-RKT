package com.kyubot.rkt.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

data class QueueDescriptor<T>(val serializer: KSerializer<T>, val name: String)

fun <T : Any> KClass<T>.queueDescriptor(): QueueDescriptor<T>? {
    val queue = findAnnotation<Queue>()
        ?: return null

    return QueueDescriptor(serializer(), queue.name)
}
