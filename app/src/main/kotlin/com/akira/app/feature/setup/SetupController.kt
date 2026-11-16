package com.akira.app.feature.setup

import com.akira.app.core.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SetupStatus { IDLE, SCANNING, READY }
internal enum class SetupFailure { SCAN, PERMISSION, SAVE }
internal data class SetupState(
    val status: SetupStatus = SetupStatus.IDLE,
    val count: Int = 0,
    val title: String = "",
    val songs: List<Song> = emptyList(),
    val failure: SetupFailure? = null,
    val saving: Boolean = false,
    val completed: Boolean = false,
    val librarySkipped: Boolean = false,
)

/** One lifecycle-owned scan. A cancelled scan can never publish a late result. */
internal class SetupController(
    private val scope: CoroutineScope,
    private val scan: suspend (suspend (Int, String) -> Unit) -> List<Song>,
    private val complete: suspend (SetupState) -> Unit,
) {
    private val mutable = MutableStateFlow(SetupState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    /** Reconcile a restored completion request with the operation that still exists. */
    fun resumeCompletion() {
        val current = mutable.value
        if (current.failure != null || current.saving || current.completed) return
        when (current.status) {
            SetupStatus.IDLE -> start()
            SetupStatus.SCANNING -> Unit
            SetupStatus.READY -> finish()
        }
    }

    fun start() {
        if (mutable.value.saving || mutable.value.completed) return
        job?.cancel()
        val operation = ++generation
        mutable.value = SetupState(status = SetupStatus.SCANNING)
        job = scope.launch {
            try {
                val songs = scan progress@ { count, title ->
                    currentCoroutineContext().ensureActive()
                    if (operation != generation) return@progress
                    mutable.value = mutable.value.copy(count = count, title = title)
                }
                currentCoroutineContext().ensureActive()
                if (operation != generation) return@launch
                mutable.value = SetupState(status = SetupStatus.READY, count = songs.size, songs = songs)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (operation != generation) return@launch
                mutable.value = SetupState(failure = if (error is SecurityException) SetupFailure.PERMISSION else SetupFailure.SCAN)
            }
        }
    }

    fun reset() {
        if (mutable.value.saving || mutable.value.completed) return
        generation++
        job?.cancel()
        mutable.value = SetupState()
    }

    /** An explicit choice, never the result of a failed or cancelled scan. */
    fun skipLibrary() {
        if (mutable.value.saving || mutable.value.completed) return
        generation++
        job?.cancel()
        mutable.value = SetupState(status = SetupStatus.READY, librarySkipped = true)
    }

    fun finish() {
        if (mutable.value.status != SetupStatus.READY || mutable.value.saving || mutable.value.completed) return
        val result = mutable.value
        mutable.value = mutable.value.copy(saving = true, failure = null)
        job = scope.launch {
            try {
                complete(result)
                mutable.value = mutable.value.copy(saving = false, completed = true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutable.value = if (error is SecurityException) SetupState(failure = SetupFailure.PERMISSION)
                    else mutable.value.copy(saving = false, failure = SetupFailure.SAVE)
            }
        }
    }
}
