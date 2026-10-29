package com.akira.app.core.media

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Service-owned sleep timer survives UI closure. Expose its countdown as a flow; expiry fades the external volume multiplier over ten seconds and pauses playback. End-of-track timers suppress crossfade before prebuffering. */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val crossfadeEngine: CrossfadeEngine,
    private val getActiveDeck: () -> DeckPlayer,
    private val onEndItemModeChanged: (Boolean) -> Unit = {},
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    companion object {
        private const val TAG = "AkiraSleepTimer"
        const val FADE_OUT_DURATION_MS = 10_000L
        const val END_OF_SONG = -1L
        const val END_OF_QUEUE = -2L
    }

    private var timerJob: Job? = null
    private var fadeJob: Job? = null

    // Milisegundos restantes; 0 = inactivo.
    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    // Active timer type for the UI.
    private val _activeMode = MutableStateFlow(0L) // 0 = off, minutes or END_OF_SONG/END_OF_QUEUE
    val activeMode: StateFlow<Long> = _activeMode.asStateFlow()

    /**
     * Inicia el temporizador.
     * @param durationMs milisegundos totales, [END_OF_SONG] o [END_OF_QUEUE].
     */
    fun start(durationMs: Long) {
        if (durationMs <= 0 && durationMs != END_OF_SONG && durationMs != END_OF_QUEUE) return
        cancel()
        onEndItemModeChanged(durationMs == END_OF_SONG)
        _activeMode.value = durationMs
        SleepTimerBus.setActiveMode(durationMs)
        Log.d(TAG, "Timer started: mode=$durationMs")

        when (durationMs) {
            END_OF_SONG -> {
                crossfadeEngine.cancelCrossfade()
                // Suppress crossfade so the engine pauses at the current track's end.
                crossfadeEngine.pauseAtEndOfItem = true
                _remainingMs.value = END_OF_SONG
                SleepTimerBus.setRemainingMs(END_OF_SONG)
            }
            END_OF_QUEUE -> {
                crossfadeEngine.pauseAtEndOfQueue = true
                _remainingMs.value = END_OF_QUEUE
                SleepTimerBus.setRemainingMs(END_OF_QUEUE)
            }
            else -> {
                _remainingMs.value = durationMs
                SleepTimerBus.setRemainingMs(durationMs)
                timerJob = scope.launch(dispatcher) {
                    val startTime = clock()
                    while (isActive) {
                        val elapsed = (clock() - startTime).coerceAtLeast(0L)
                        val left = (durationMs - elapsed).coerceAtLeast(0L)
                        _remainingMs.value = left
                        SleepTimerBus.setRemainingMs(left)
                        if (left <= 0L) {
                            startFadeOut()
                            break
                        }
                        delay(1000L)
                    }
                }
            }
        }
    }

    fun cancel() {
        onEndItemModeChanged(false)
        timerJob?.cancel()
        timerJob = null
        fadeJob?.cancel()
        fadeJob = null
        _remainingMs.value = 0L
        _activeMode.value = 0L
        SleepTimerBus.setRemainingMs(0L)
        SleepTimerBus.setActiveMode(0L)
        crossfadeEngine.pauseAtEndOfItem = false
        crossfadeEngine.pauseAtEndOfQueue = false
        // Restaurar volumen externo
        crossfadeEngine.externalVolumeMultiplier = 1.0f
        Log.d(TAG, "Timer cancelled")
    }

    val isActive: Boolean get() = _activeMode.value != 0L

    /** Called when a track ends naturally after crossfade has been suppressed. */
    fun onSongEndedNaturally() {
        if (_activeMode.value == END_OF_SONG) {
            Log.d(TAG, "End-of-song mode: pausing playback")
            getActiveDeck().pause()
            cancel()
        }
    }

    /** Called when the queue is exhausted. */
    fun onQueueEnded() {
        if (_activeMode.value == END_OF_QUEUE) {
            Log.d(TAG, "End-of-queue mode: pausing playback")
            getActiveDeck().pause()
            cancel()
        }
    }

    private fun startFadeOut() {
        fadeJob?.cancel()
        if (!getActiveDeck().playWhenReady) {
            // Playback is already paused.
            cancel()
            return
        }
        fadeJob = scope.launch(dispatcher) {
            Log.d(TAG, "Starting 10s fade-out")
            val startedAt = clock()
            while (isActive) {
                val progress = ((clock() - startedAt).toFloat() / FADE_OUT_DURATION_MS).coerceIn(0f, 1f)
                crossfadeEngine.externalVolumeMultiplier = (1f - progress).coerceIn(0f, 1f)
                if (progress >= 1f) break
                delay(50L)
            }
            crossfadeEngine.externalVolumeMultiplier = 0f
            // Crossfade may have swapped decks during these ten seconds.
            getActiveDeck().pause()
            crossfadeEngine.cancelCrossfade()
            Log.d(TAG, "Fade-out complete, playback paused")
            cancel()
        }
    }
}
