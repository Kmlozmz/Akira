package com.akira.app.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bus en memoria para el estado y control del temporizador de apagado entre
 * [MusicLibraryService] y la UI de Compose.
 */
object SleepTimerBus {
    const val END_OF_SONG = -1L
    const val END_OF_QUEUE = -2L

    // El prefijo sigue al applicationId (com.akira.app), no al paquete
    // Kotlin, que sigue siendo com.akira.app. Son cadenas de identidad
    // para MediaSession: si se quedaran en el paquete viejo, los comandos
    // enviados desde la notificación se confundirian con los de otra app.
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

    /**
     * Traduce el comando de sesión al handler local.
     *
     * Los acciones llegan como texto desde el controlador (notificación,
     * reloj de centro de comandos) y aquí se enrutan al temporizador. Se
     * enruta por la constante y no comparando el paquete del intent, para que
     * un cambio de applicationId no rompa el enlazado.
     */
    fun dispatch(action: String, durationMs: Long) {
        when (action) {
            COMMAND_START_TIMER -> start(durationMs)
            COMMAND_CANCEL_TIMER -> cancel()
        }
    }
}
