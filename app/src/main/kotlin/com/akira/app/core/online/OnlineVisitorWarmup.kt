package com.akira.app.core.online

import kotlinx.coroutines.*

/** Optional visitor metadata must never be a prerequisite for the user's first request. */
internal class OnlineVisitorWarmup(private val scope: CoroutineScope, private val now: () -> Long,
    private val epoch: () -> Long, private val needed: () -> Boolean,
    private val load: suspend () -> String?, private val publish: suspend (String, Long) -> Unit) {
    private val monitor = Any()
    private var pending: Job? = null
    private var attemptedEpoch: Long? = null
    private var attemptedAt = 0L

    fun request() {
        val generation = epoch()
        val job = synchronized(monitor) {
            if (!needed() || (attemptedEpoch == generation && (pending?.isActive == true || now() - attemptedAt < 60_000))) return
            pending?.cancel()
            attemptedEpoch = generation; attemptedAt = now()
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = withTimeoutOrNull(3_000) { load() }
                    if (!result.isNullOrBlank() && epoch() == generation && needed()) publish(result, generation)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* Browsing and playback keep their normal request/error handling. */ }
            }.also { pending = it }
        }
        job.invokeOnCompletion { synchronized(monitor) { if (pending === job) pending = null } }
        job.start()
    }

    fun cancel() = synchronized(monitor) {
        pending?.cancel(); pending = null; attemptedEpoch = null
    }
}
