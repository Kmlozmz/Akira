package com.akira.app.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/** Own audio focus once for both decks. Disable per-deck focus handling to avoid abandon/request cycles during crossfade. Let Android handle ducking through setWillPauseWhenDucked(false). */
internal class PlaybackFocus(
    context: Context,
    /** Focus lost; willResume indicates a transient interruption that permits automatic resume. */
    private val onLost: (willResume: Boolean) -> Unit,
    /** Focus restored after a transient interruption. */
    private val onGained: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null
    private var held = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.d(TAG, "focus change=$change")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true
                onGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                onLost(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                held = false
                onLost(true)
            }
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: lo gestiona el sistema.
        }
    }

    /** Request focus when not already held; return false if Android denies it. */
    fun acquire(): Boolean {
        if (held) return true
        val req = request ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener)
            .setWillPauseWhenDucked(false)
            .build()
            .also { request = it }
        held = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "acquire -> granted=$held")
        return held
    }

    fun release() {
        request?.let { audioManager.abandonAudioFocusRequest(it) }
        held = false
    }

    private companion object {
        const val TAG = "AkiraPlayback"
    }
}
