package com.akira.app.feature.setup

import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Survives rotation with the ViewModel; completion waits for every preference write. */
internal class SetupPreferenceWrites(private val scope: CoroutineScope) {
    private val serial = Mutex()
    private var sequence = 0L
    private val latest = mutableMapOf<String, Long>()
    private val failed = linkedMapOf<String, suspend () -> Unit>()
    private val mutablePending = MutableStateFlow(0)
    private val mutableFailures = MutableStateFlow<Set<String>>(emptySet())
    val pending = mutablePending.asStateFlow()
    val failures = mutableFailures.asStateFlow()

    @MainThread
    fun submit(key: String, action: suspend () -> Unit): Job {
        val request = ++sequence
        latest[key] = request
        mutablePending.value++
        return scope.launch {
            try {
                serial.withLock { action() }
                if (latest[key] == request) failed.remove(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (latest[key] == request) failed[key] = action
            } finally {
                mutablePending.value--
                mutableFailures.value = failed.keys.toSet()
            }
        }
    }

    @MainThread
    fun retry() = failed.toMap().forEach { (key, action) -> submit(key, action) }
}
