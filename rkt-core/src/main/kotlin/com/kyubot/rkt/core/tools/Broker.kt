package com.kyubot.rkt.core.tools

import com.kyubot.rkt.core.Broker
import com.kyubot.rkt.core.Callable
import com.kyubot.rkt.core.Message
import com.kyubot.rkt.core.queueDescriptor
import kotlinx.coroutines.Job
import kotlin.reflect.KClass

suspend inline fun <reified T : Any> Broker.subscribe(): Boolean {
    val descriptor = T::class.queueDescriptor()
        ?: throw IllegalStateException("Class ${T::class.qualifiedName} is not annotated with @Queue")

    return subscribe(descriptor)
}

suspend inline fun <reified T : Any> Broker.publish(message: T) =
    publish(T::class.queueDescriptor()!!, message)

suspend inline fun <reified R : Any, reified S : Callable<R>> Broker.call(message: S) =
    call(S::class to R::class, message,)

suspend inline fun <reified R : Any, reified S : Callable<R>> Broker.callAsync(message: S): R =
    call(S::class to R::class, message,).await()

suspend fun <R : Any, S : Callable<R>> Broker.callAsync(kClass: Pair<KClass<S>, KClass<R>>, message: S): R =
    call(kClass, message,).await()

suspend inline fun <reified T : Any> Broker.on(noinline block: suspend Message<T>.() -> Unit): Job {
    val descriptor = T::class.queueDescriptor()
        ?: throw IllegalStateException("Class ${T::class.qualifiedName} is not annotated with @Queue")

    return on(descriptor, block)
}
