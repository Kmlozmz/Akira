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
)

/** One lifecycle-owned scan. A cancelled scan can never publish a late result. */
internal class SetupController(
    private val scope: CoroutineScope,
    private val scan: suspend (suspend (Int, String) -> Unit) -> List<Song>,
    private val complete: suspend () -> Unit,
) {
    private val mutable = MutableStateFlow(SetupState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (mutable.value.saving || mutable.value.completed) return
        job?.cancel()
        mutable.value = SetupState(status = SetupStatus.SCANNING)
        job = scope.launch {
            try {
                val songs = scan { count, title ->
                    currentCoroutineContext().ensureActive()
                    mutable.value = mutable.value.copy(count = count, title = title)
                }
                currentCoroutineContext().ensureActive()
                mutable.value = SetupState(status = SetupStatus.READY, count = songs.size, songs = songs)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutable.value = SetupState(failure = if (error is SecurityException) SetupFailure.PERMISSION else SetupFailure.SCAN)
            }
        }
    }

    fun reset() {
        if (mutable.value.saving || mutable.value.completed) return
        job?.cancel()
        mutable.value = SetupState()
    }

    fun finish() {
        if (mutable.value.status != SetupStatus.READY || mutable.value.saving || mutable.value.completed) return
        mutable.value = mutable.value.copy(saving = true, failure = null)
        job = scope.launch {
            try {
                complete()
                mutable.value = mutable.value.copy(saving = false, completed = true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutable.value = if (error is SecurityException) SetupState(failure = SetupFailure.PERMISSION)
                    else mutable.value.copy(saving = false, failure = SetupFailure.SAVE)
            }
        }
    }
}
