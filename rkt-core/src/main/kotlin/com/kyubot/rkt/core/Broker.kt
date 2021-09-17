package com.kyubot.rkt.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlin.reflect.KClass
import kotlin.time.Duration

interface Broker : CoroutineScope {
    /**
     * The current state of this broker.
     */
    val state: BrokerState

    /**
     * The resources for this broker.
     */
    val resources: BrokerResources

    /**
     * Creates a new connection to the broker.
     */
    suspend fun connect()

    /**
     * Connects this broker to the provided URL.
     * @param url The url to connect to.
     */
    suspend fun connect(url: String)

    /**
     *
     */
    suspend fun <T> on(description: QueueDescriptor<T>, block: suspend Message<T>.() -> Unit): Job

    /**
     * Subscribes to a queue with the provided [QueueDescriptor]
     *
     * @param descriptor The queue descriptor.
     *
     * @return true, if the queue was subscribed to.
     */
    suspend fun <T> subscribe(descriptor: QueueDescriptor<T>): Boolean

    /**
     * Publishes a new [message]
     *
     * @param descriptor The queue descriptor.
     * @param message Message to publish.
     */
    suspend fun <T> publish(descriptor: QueueDescriptor<T>, message: T)

    /**
     *
     */
    suspend fun <R : Any, S : Callable<R>> call(kClass: Pair<KClass<S>, KClass<R>>, message: S): Deferred<R> {
        return call(kClass, message, null)
    }

    /**
     *
     */
    suspend fun <R : Any, S : Callable<R>> call(kClass: Pair<KClass<S>, KClass<R>>, message: S, timeout: Duration?): Deferred<R>
}
