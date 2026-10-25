package com.akira.app.core.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

internal class StreamRequestCache<T>(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val ttlMillis: Long = 240_000,
    private val capacity: Int = 64,
) {
    private data class Cached<T>(val expiresAt: Long, val value: T)
    private val lock = Any()
    private val values = linkedMapOf<String, Cached<T>>()
    private val requests = mutableMapOf<String, Deferred<T>>()
    private var generation = 0L

    init { require(ttlMillis > 0 && capacity > 0) }

    suspend fun get(key: String, refresh: Boolean = false, load: suspend () -> T): T {
        val request = synchronized(lock) {
            values.entries.removeAll { it.value.expiresAt <= now() }
            if (!refresh) values.remove(key)?.let { cached ->
                values[key] = cached
                return cached.value
            }
            if (refresh) {
                values.remove(key)
                requests.remove(key)?.cancel()
            }
            requests[key] ?: run {
                val startedGeneration = generation
                scope.async(start = CoroutineStart.LAZY) {
                    val value = load()
                    val context = currentCoroutineContext()
                    context.ensureActive()
                    synchronized(lock) {
                        if (generation != startedGeneration || requests[key] !== context.job)
                            throw CancellationException("Stream request superseded")
                        values[key] = Cached(now() + ttlMillis, value)
                        while (values.size > capacity) values.remove(values.keys.first())
                    }
                    value
                }.also { requests[key] = it }
            }
        }
        request.invokeOnCompletion {
            synchronized(lock) { if (requests[key] === request) requests.remove(key) }
        }
        return request.await()
    }

    fun clear() {
        val pending = synchronized(lock) {
            generation++
            values.clear()
            requests.values.toList().also { requests.clear() }
        }
        pending.forEach { it.cancel() }
    }
}
