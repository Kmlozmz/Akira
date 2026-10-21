package com.akira.app.core.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.asStateFlow

data class AudioFxSupport(val equalizer: Boolean? = null, val bass: Boolean? = null, val space: Boolean? = null)
object AudioFxAvailability {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(AudioFxSupport())
    val current = state.asStateFlow()
    internal fun update(support: AudioFxSupport) { state.value = support }
}

/**
 * Gestor del ecualizador basado en `android.media.audiofx`.
 *
 * Se usa audiofx en lugar de un AudioProcessor propio porque los ExoPlayer
 * ya se encargan del resampling y del resto de la cadena de audio; meter un
 * processor custom obligaba a construir el AudioSink a mano y se corría el
 * riesgo de perder los processors internos (sonic, silence-skipper,
 * resampling), con el riesgo de romper la reproducción.
 *
 * Limitación conocida: la API de audiofx sólo expone el número de banda, la
 * frecuencia y el rango de ganancia que publica cada dispositivo. Los presets
 * se interpolan en una escala de frecuencia logarítmica sobre cada banda
 * física. AudioFX publica frecuencias en milihertz y niveles en millibels.
 */
class AudioFxEqualizerManager(
    private val targetFrequencies: IntArray
) {

    companion object {
        private const val TAG = "AudioFxEqualizerManager"

        /** Prioridad del efecto de audio. 0 =FxEngine, más bajo = más prioridad. */
        private const val EFFECT_PRIORITY = 0
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var sessionId: Int = 0
    private var attached = false

    /** Ganancias lógicas en dB, entre min y max del dispositivo. */
    private var gains = FloatArray(targetFrequencies.size)

    private var deviceMinGainMb = -1500
    private var deviceMaxGainMb = 1500

    /**
     * Aplica el estado a un sessionId concreto. Se llama en cada cambio de
     * ajustes y también para el deck entrante del crossfade.
     *
     * Reconstruye los efectos si cambia el sessionId, porque audiofx ata los
     * efectos a una sesión concreta y no los reutiliza entre sesiones.
     */
    @Synchronized
    fun apply(
        audioSessionId: Int,
        enabled: Boolean,
        bandGainsDb: FloatArray,
        bassLevel: Float,
        virtualizerLevel: Float
    ) {
        gains = bandGainsDb.copyOf()
        this.enabled = enabled
        this.bassLevel = bassLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        this.virtualizerLevel = virtualizerLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

        if (sessionId != audioSessionId) {
            releaseEffects()
            sessionId = audioSessionId
            attached = false
        }
        if (!attached) {
            if (!createEffects()) return
        }
        pushValues()
    }

    private var enabled = false
    private var bassLevel = 0.5f
    private var virtualizerLevel = 0.5f

    /**
     * Crea los tres efectos **de forma independiente**.
     *
     * Antes iban los tres dentro de un único try y, si uno fallaba, se llamaba
     * a releaseEffects(), que además tiraba los que sí se habían creado. Se veía
     * en el dispositivo: `Virtualizer.<init>` falla con
     * "Cannot initialize effect engine ... Error: -3" cuando el motor de
     * efectos ya está saturado (la app usa dos decks, así que pueden llegar a
     * pedirse seis efectos), y con eso se perdían también el Equalizer y el
     * BassBoost, dejando el EQ muerto sin explicación.
     *
     * Ahora cada uno va en su propio try: se conserva lo que funcione y sólo
     * se avisa de lo que no.
     */
    private fun createEffects(): Boolean {
        if (sessionId == 0) return false
        // El sessionId va en el constructor: attach() no hace falta (ni está
        // expuesto al SDK público) porque el efecto ya nace atado.
        equalizer = runCatching { Equalizer(EFFECT_PRIORITY, sessionId) }
            .onFailure { Log.w(TAG, "Equalizer no disponible en esta sesión", it) }
            .getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }
            .onFailure { Log.w(TAG, "BassBoost no disponible en esta sesión", it) }
            .getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }
            .onFailure { Log.w(TAG, "Virtualizer no disponible en esta sesión", it) }
            .getOrNull()

        val range = runCatching { equalizer?.bandLevelRange }.getOrNull()
        deviceMinGainMb = range?.firstOrNull()?.toInt() ?: -1500
        deviceMaxGainMb = range?.lastOrNull()?.toInt() ?: 1500

        // Basta con que el ecualizador de 5 bandas exista; los otros dos son
        // complementos opcionales.
        attached = equalizer != null
        AudioFxAvailability.update(AudioFxSupport(
            equalizer = attached && runCatching { equalizer?.hasControl() == true }.getOrDefault(false),
            bass = attached && runCatching { bassBoost?.strengthSupported == true && bassBoost?.hasControl() == true }.getOrDefault(false),
            space = attached && runCatching { virtualizer?.strengthSupported == true && virtualizer?.hasControl() == true }.getOrDefault(false)))
        if (!attached) {
            Log.w(TAG, "ningún efecto de audiofx pudo crearse")
            releaseEffects()
        }
        return attached
    }

    private fun pushValues() {
        val eq = equalizer ?: return
        try {
            eq.enabled = enabled
            // El número de bandas varía entre 2 y 8 según el dispositivo.
            val centers = IntArray(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) }
            val levels = EqualizerBands.levels(targetFrequencies, gains, centers, deviceMinGainMb, deviceMaxGainMb)
            for (band in levels.indices) {
                eq.setBandLevel(band.toShort(), levels[band].toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "no se pudieron aplicar las bandas", e)
        }
        runCatching { bassBoost?.let {
            if (it.hasControl()) {
                it.enabled = enabled && it.strengthSupported && bassLevel > 0f
                if (it.strengthSupported) it.setStrength((bassLevel * 1000).toInt().toShort())
            }
        } }
        runCatching { virtualizer?.let {
            if (it.hasControl()) {
                it.enabled = enabled && it.strengthSupported && virtualizerLevel > 0f
                if (it.strengthSupported) it.setStrength((virtualizerLevel * 1000).toInt().toShort())
            }
        } }
    }

    /** Rango real de ganancia en dB del dispositivo, para pintar la UI. */
    @Synchronized
    fun deviceGainRange(): Pair<Float, Float> = deviceMinGainMb / 100f to deviceMaxGainMb / 100f

    @Synchronized
    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        attached = false
    }
}
