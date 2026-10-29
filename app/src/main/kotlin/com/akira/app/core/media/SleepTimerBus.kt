package com.akira.app.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Share sleep-timer state and commands between MusicLibraryService and Compose in the same process. */
object SleepTimerBus {
    const val END_OF_SONG = -1L
    const val END_OF_QUEUE = -2L

    // Derive MediaSession command identifiers from applicationId so notification commands remain scoped to this app.
    const val COMMAND_START_TIMER = "com.akira.app.START_SLEEP_TIMER"
    const val COMMAND_CANCEL_TIMER = "com.akira.app.CANCEL_SLEEP_TIMER"
    const val EXTRA_DURATION_MS = "extra_duration_ms"

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _activeMode = MutableStateFlow(0L)
    val activeMode: StateFlow<Long> = _activeMode.asStateFlow()

    private var onStartAction: ((Long) -> Unit)? = null
    private var onCancelAction: (() -> Unit)? = null

    fun setRemainingMs(ms: Long) {
        _remainingMs.value = ms
    }

    fun setActiveMode(mode: Long) {
        _activeMode.value = mode
    }

    fun registerHandlers(onStart: (Long) -> Unit, onCancel: () -> Unit) {
        onStartAction = onStart
        onCancelAction = onCancel
    }

    fun unregisterHandlers() {
        onStartAction = null
        onCancelAction = null
        _remainingMs.value = 0L
        _activeMode.value = 0L
    }

    fun start(durationMs: Long) {
        onStartAction?.invoke(durationMs)
    }

    fun cancel() {
        onCancelAction?.invoke()
    }

    /** Route session command constants to local timer handlers, independently of the intent package. */
    fun dispatch(action: String, durationMs: Long) {
        when (action) {
            COMMAND_START_TIMER -> start(durationMs)
            COMMAND_CANCEL_TIMER -> cancel()
        }
    }
}
