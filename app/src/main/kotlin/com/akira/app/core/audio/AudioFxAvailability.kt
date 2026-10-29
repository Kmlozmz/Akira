package com.akira.app.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioFxSupport(val equalizer: Boolean? = null, val bass: Boolean? = null,
    val space: Boolean? = null, val controlled: Boolean? = null, val applied: Boolean? = null)

internal class AudioFxSessionRegistry {
    private val sessions = mutableMapOf<Int, AudioFxSupport>()
    private var active = 0
    val current get() = sessions[active] ?: AudioFxSupport()
    fun select(session: Int) { active = session }
    fun update(session: Int, support: AudioFxSupport) { if (session > 0) sessions[session] = support }
    fun clear(session: Int) { sessions.remove(session) }
}

object AudioFxAvailability {
    private val sessions = AudioFxSessionRegistry()
    private val state = MutableStateFlow(AudioFxSupport())
    val current = state.asStateFlow()
    @Synchronized internal fun select(session: Int) { sessions.select(session); state.value = sessions.current }
    @Synchronized internal fun update(session: Int, support: AudioFxSupport) {
        sessions.update(session, support); state.value = sessions.current
    }
    @Synchronized internal fun clear(session: Int) { sessions.clear(session); state.value = sessions.current }
}
