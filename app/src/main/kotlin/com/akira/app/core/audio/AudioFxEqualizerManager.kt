package com.akira.app.core.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

internal data class EqualizerReadback(val enabled: Boolean, val controlled: Boolean, val bandLevels: List<Int>)

/** Owns the effects for one deck. Configuration is retained across bypass and session changes. */
class AudioFxEqualizerManager(private val targetFrequencies: IntArray) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var sessionId = 0
    private var eqAttempted = false
    private var bassAttempted = false
    private var spaceAttempted = false
    private var centers = intArrayOf()
    private var minimum = -1500
    private var maximum = 1500
    private var enabled = false
    private var configuration = EqualizerConfiguration()
    private var lastApplied: Pair<Boolean, EqualizerConfiguration>? = null
    private var lastApplySucceeded = true

    @Synchronized
    fun apply(audioSessionId: Int, enabled: Boolean, bandGainsDb: FloatArray,
        bassLevel: Float, virtualizerLevel: Float) {
        val next = EqualizerConfiguration(bandGainsDb.toList(), bassLevel, virtualizerLevel).normalized()
        val turningOn = enabled && !this.enabled
        this.enabled = enabled
        configuration = next
        if (sessionId != audioSessionId) {
            releaseEffects()
            sessionId = audioSessionId
        }
        if (sessionId <= 0) return
        if (!enabled) {
            if (equalizer != null || bassBoost != null || virtualizer != null) {
                pushValues()
                releaseEffects()
            }
            return
        }
        if (turningOn) {
            if (equalizer == null) eqAttempted = false
            if (bassBoost == null) bassAttempted = false
            if (virtualizer == null) spaceAttempted = false
        }
        if (enabled) acquireEffects()
        if (lastApplied != (enabled to next)) pushValues()
    }

    private fun acquireEffects() {
        if (!eqAttempted) {
            eqAttempted = true
            equalizer = create("Equalizer", { Equalizer(0, sessionId) }) { effect ->
                    val range = effect.bandLevelRange
                    minimum = range.first().toInt(); maximum = range.last().toInt()
                    centers = IntArray(effect.numberOfBands.toInt()) { effect.getCenterFreq(it.toShort()) }
                    check(minimum <= maximum && centers.isNotEmpty())
                    listen(effect)
            }
            lastApplied = null
        }
        if (configuration.bass > 0f && !bassAttempted) {
            bassAttempted = true
            bassBoost = create("BassBoost", { BassBoost(0, sessionId) }, ::listen)
            lastApplied = null
        }
        if (configuration.space > 0f && !spaceAttempted) {
            spaceAttempted = true
            virtualizer = create("Virtualizer", { Virtualizer(0, sessionId) }, ::listen)
            lastApplied = null
        }
    }

    private fun listen(effect: AudioEffect) {
        effect.setEnableStatusListener { changed, _ ->
            synchronized(this) {
                if (changed === equalizer || changed === bassBoost || changed === virtualizer) publishSupport()
            }
        }
        effect.setControlStatusListener { changed, granted ->
            synchronized(this) {
                if (changed !== equalizer && changed !== bassBoost && changed !== virtualizer) return@synchronized
                lastApplied = null
                if (granted) pushValues() else publishSupport()
            }
        }
    }

    private fun pushValues() {
        lastApplySucceeded = true
        equalizer?.let { eq -> if (attempt("Equalizer settings") {
            if (eq.hasControl()) {
                if (enabled) {
                    val levels = EqualizerBands.levels(targetFrequencies, configuration.bands.toFloatArray(), centers, minimum, maximum)
                    levels.forEachIndexed { band, level -> eq.setBandLevel(band.toShort(), level.toShort()) }
                }
                setEnabled(eq, enabled)
            }
        } == null) lastApplySucceeded = false }
        bassBoost?.let { effect -> if (attempt("Bass settings") {
            if (effect.hasControl()) {
                val use = enabled && configuration.bass > 0f && effect.strengthSupported
                if (use) effect.setStrength((configuration.bass * 1000).toInt().toShort())
                setEnabled(effect, use)
            }
        } == null) lastApplySucceeded = false }
        virtualizer?.let { effect -> if (attempt("Spatial settings") {
            if (effect.hasControl()) {
                val use = enabled && configuration.space > 0f && effect.strengthSupported
                if (use) effect.setStrength((configuration.space * 1000).toInt().toShort())
                setEnabled(effect, use)
            }
        } == null) lastApplySucceeded = false }
        lastApplied = enabled to configuration
        publishSupport()
    }

    private fun setEnabled(effect: AudioEffect, value: Boolean) {
        if (effect.enabled != value) check(effect.setEnabled(value) == AudioEffect.SUCCESS)
    }

    private fun publishSupport() {
        val effects = listOfNotNull(equalizer, bassBoost, virtualizer)
        val controlled = effects.takeIf { it.isNotEmpty() }?.all { effect ->
            runCatching { effect.hasControl() }.getOrDefault(false)
        }
        val applied = equalizer?.let { eq ->
            lastApplySucceeded && runCatching {
                eq.enabled == enabled &&
                    (bassBoost?.enabled ?: false) == (enabled && configuration.bass > 0f && bassBoost?.strengthSupported == true) &&
                    (virtualizer?.enabled ?: false) == (enabled && configuration.space > 0f && virtualizer?.strengthSupported == true)
            }.getOrDefault(false)
        }
        AudioFxAvailability.update(sessionId, AudioFxSupport(
            equalizer = equalizer?.let { true } ?: if (eqAttempted) false else null,
            bass = bassBoost?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: if (bassAttempted) false else null,
            space = virtualizer?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: if (spaceAttempted) false else null,
            controlled = controlled,
            applied = applied,
        ))
    }

    private fun <T> attempt(operation: String, block: () -> T): T? = runCatching(block)
        .onFailure { Log.w("AkiraAudioFx", "$operation unavailable: ${it.javaClass.simpleName}") }.getOrNull()

    private fun <T : AudioEffect> create(operation: String, factory: () -> T, configure: (T) -> Unit): T? {
        val effect = attempt(operation, factory) ?: return null
        return try { configure(effect); effect } catch (error: Exception) {
            runCatching { effect.release() }
            Log.w("AkiraAudioFx", "$operation unavailable: ${error.javaClass.simpleName}")
            null
        }
    }

    @Synchronized fun deviceGainRange(): Pair<Float, Float> = minimum / 100f to maximum / 100f

    @Synchronized internal fun readback(): EqualizerReadback? = equalizer?.let { effect ->
        attempt("Equalizer readback") {
            EqualizerReadback(effect.enabled, effect.hasControl(),
                List(centers.size) { effect.getBandLevel(it.toShort()).toInt() })
        }
    }

    @Synchronized fun releaseEffects() {
        listOfNotNull(equalizer, bassBoost, virtualizer).forEach { effect ->
            runCatching { effect.setControlStatusListener(null); effect.setEnableStatusListener(null); effect.release() }
        }
        equalizer = null; bassBoost = null; virtualizer = null
        eqAttempted = false; bassAttempted = false; spaceAttempted = false
        centers = intArrayOf(); lastApplied = null
        AudioFxAvailability.clear(sessionId)
    }
}
