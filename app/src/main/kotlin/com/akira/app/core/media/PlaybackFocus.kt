package com.akira.app.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Único dueño del foco de audio de la app.
 *
 * Antes cada ExoPlayer gestionaba su propio foco y en cada handover del crossfade se
 * activaba en un deck y se desactivaba en el otro (setAudioAttributes(..., true/false))
 * mientras sonaban los dos. Eso fuerza una cadena abandonar -> pedir foco justo en el
 * instante más delicado, y si una petición no se concede ExoPlayer pausa el deck
 * entrante: es una de las causas probables del "pausa/play y se queda pausado".
 *
 * Ahora los dos decks tienen handleAudioFocus = false y este objeto pide el foco UNA
 * vez al empezar a sonar y lo suelta al pausar, sin importar qué deck esté activo.
 *
 * Ducking: se deja al sistema (setWillPauseWhenDucked(false)), que baja el volumen solo.
 */
internal class PlaybackFocus(
    context: Context,
    /** Foco perdido. willResume = pérdida transitoria (llamada, otra app): hay que reanudar al volver. */
    private val onLost: (willResume: Boolean) -> Unit,
    /** Foco recuperado tras una pérdida transitoria. */
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

    /** Pide el foco si no lo tiene. Devuelve false si el sistema lo deniega. */
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
