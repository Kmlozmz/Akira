package com.akira.app.core.online

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Apply an asynchronous result only to the session that submitted it. */
internal fun sessionOperationIsCurrent(started: Long, current: Long, enabled: Boolean): Boolean =
    enabled && started == current

/** Invalidate pending work immediately; notify observers once credentials and
 * account state are consistent, including reconnecting the same account. */
internal class OnlineSessionEpoch {
    private val generation = AtomicLong()
    private val committed = MutableStateFlow(0L)
    val changes = committed.asStateFlow()
    fun get(): Long = generation.get()
    fun incrementAndGet(): Long = generation.incrementAndGet()
    fun commit() { committed.value = generation.get() }
}
