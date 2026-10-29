package com.akira.app.core.media

import kotlin.math.cos
import kotlin.math.sin

/** Direction of a track's volume change during crossfade. */
enum class CrossfadeDirection {
    OUTGOING, // Pista saliente: volumen 1.0 -> 0.0
    INCOMING  // Pista entrante: volumen 0.0 -> 1.0
}

/** Available crossfade attenuation and amplification curves. */
enum class CrossfadeShape {
    CURRENT,          // Legacy constant-power formula.
    LINEAR,           // Linear attenuation and gain.
    SMOOTH,           // SUAVE: curva sigmoidal / smoothstep tipo S (3u^2 - 2u^3)
    CONSTANT_POWER,   // Use cos(u*pi/2) and sin(u*pi/2) for constant acoustic power.
    FAST_START,       // Fast outgoing decay and incoming rise: ease out.
    SLOW_START        // Slow outgoing decay and incoming rise: ease in.
}

/** Volume curve specification: shape; curvature from linear (0) to full shape (1); startRange in 0..0.9; endRange in 0.1..1. */
data class CurveSpec(
    val shape: CrossfadeShape = CrossfadeShape.CURRENT,
    val curvature: Float = 1.0f,
    val startRange: Float = 0.0f,
    val endRange: Float = 1.0f
) {
    init {
        require(curvature in 0f..1f) { "curvature must be in [0, 1]" }
        require(startRange in 0f..0.9f) { "startRange must be in [0, 0.9]" }
        require(endRange in 0.1f..1.0f) { "endRange must be in [0.1, 1.0]" }
        require(endRange >= startRange + 0.1f) { "endRange must be at least startRange + 0.1f" }
    }
}

/** Paired crossfade curve presets. */
data class CrossfadeCurvePreset(
    val id: String,
    val outgoingSpec: CurveSpec,
    val incomingSpec: CurveSpec
)

/** Framework-independent gain-curve evaluation for JVM tests. */
object CrossfadeCurve {

    const val PRESET_ACTUAL = "ACTUAL"
    const val PRESET_LINEAR = "LINEAR"
    const val PRESET_CONSTANT_POWER = "CONSTANT_POWER"
    const val PRESET_SMOOTH = "SMOOTH"
    const val PRESET_FAST_START_SLOW_END = "FAST_START_SLOW_END"
    const val PRESET_CUSTOM = "CUSTOM"

    val DEFAULT_OUTGOING_SPEC = CurveSpec(
        shape = CrossfadeShape.CURRENT,
        curvature = 1.0f,
        startRange = 0.0f,
        endRange = 1.0f
    )

    val DEFAULT_INCOMING_SPEC = CurveSpec(
        shape = CrossfadeShape.CURRENT,
        curvature = 1.0f,
        startRange = 0.0f,
        endRange = 1.0f
    )

    val PRESETS: Map<String, CrossfadeCurvePreset> = mapOf(
        PRESET_LINEAR to CrossfadeCurvePreset(
            id = PRESET_LINEAR,
            outgoingSpec = CurveSpec(CrossfadeShape.LINEAR, 0.0f, 0.0f, 1.0f),
            incomingSpec = CurveSpec(CrossfadeShape.LINEAR, 0.0f, 0.0f, 1.0f)
        ),
        PRESET_CONSTANT_POWER to CrossfadeCurvePreset(
            id = PRESET_CONSTANT_POWER,
            outgoingSpec = CurveSpec(CrossfadeShape.CONSTANT_POWER, 1.0f, 0.0f, 1.0f),
            incomingSpec = CurveSpec(CrossfadeShape.CONSTANT_POWER, 1.0f, 0.0f, 1.0f)
        ),
        PRESET_SMOOTH to CrossfadeCurvePreset(
            id = PRESET_SMOOTH,
            outgoingSpec = CurveSpec(CrossfadeShape.SMOOTH, 1.0f, 0.0f, 1.0f),
            incomingSpec = CurveSpec(CrossfadeShape.SMOOTH, 1.0f, 0.0f, 1.0f)
        ),
        PRESET_FAST_START_SLOW_END to CrossfadeCurvePreset(
            id = PRESET_FAST_START_SLOW_END,
            outgoingSpec = CurveSpec(CrossfadeShape.FAST_START, 1.0f, 0.0f, 1.0f),
            incomingSpec = CurveSpec(CrossfadeShape.SLOW_START, 1.0f, 0.0f, 1.0f)
        )
    ).let { presets ->
        // Keep saved ACTUAL values readable while exposing a single equivalent curve in the UI.
        presets + (PRESET_ACTUAL to presets.getValue(PRESET_CONSTANT_POWER).copy(id = PRESET_ACTUAL))
    }

    fun canonicalPresetId(id: String): String = when (id) {
        PRESET_ACTUAL -> PRESET_CONSTANT_POWER
        else -> id
    }

    /** Evaluate normalized gain at t in 0..1 using the direction and curve specification. */
    fun gain(direction: CrossfadeDirection, spec: CurveSpec, t: Float): Float {
        val clampedT = t.coerceIn(0f, 1f)

        // Check range boundaries.
        if (clampedT <= spec.startRange) {
            return if (direction == CrossfadeDirection.OUTGOING) 1.0f else 0.0f
        }
        if (clampedT >= spec.endRange) {
            return if (direction == CrossfadeDirection.OUTGOING) 0.0f else 1.0f
        }

        // Project time into the normalized startRange..endRange segment.
        val span = (spec.endRange - spec.startRange).coerceAtLeast(0.001f)
        val u = ((clampedT - spec.startRange) / span).coerceIn(0f, 1f)

        val linearGain = if (direction == CrossfadeDirection.OUTGOING) (1.0f - u) else u

        val pureGain = when (spec.shape) {
            CrossfadeShape.CURRENT, CrossfadeShape.CONSTANT_POWER -> {
                val angle = u * (Math.PI.toFloat() / 2f)
                if (direction == CrossfadeDirection.OUTGOING) cos(angle) else sin(angle)
            }
            CrossfadeShape.LINEAR -> linearGain
            CrossfadeShape.SMOOTH -> {
                val s = u * u * (3.0f - 2.0f * u)
                if (direction == CrossfadeDirection.OUTGOING) (1.0f - s) else s
            }
            CrossfadeShape.FAST_START -> {
                if (direction == CrossfadeDirection.OUTGOING) {
                    (1.0f - u) * (1.0f - u)
                } else {
                    1.0f - (1.0f - u) * (1.0f - u)
                }
            }
            CrossfadeShape.SLOW_START -> {
                if (direction == CrossfadeDirection.OUTGOING) {
                    1.0f - u * u
                } else {
                    u * u
                }
            }
        }

        val curvature = spec.curvature.coerceIn(0f, 1f)
        val blended = linearGain + curvature * (pureGain - linearGain)
        return blended.coerceIn(0f, 1f)
    }

    /**
     * Calcula la potencia total estimada en un instante `t`:
     * Potencia = volumen_saliente² + volumen_entrante²
     */
    fun totalPower(outgoingSpec: CurveSpec, incomingSpec: CurveSpec, t: Float): Float {
        val outGain = gain(CrossfadeDirection.OUTGOING, outgoingSpec, t)
        val inGain = gain(CrossfadeDirection.INCOMING, incomingSpec, t)
        return (outGain * outGain) + (inGain * inGain)
    }

    /** Detect combined acoustic power below 0.70 or above 1.30 during the mix. */
    fun hasPowerAnomaly(outgoingSpec: CurveSpec, incomingSpec: CurveSpec, samples: Int = 100): Boolean {
        for (i in 0..samples) {
            val t = i.toFloat() / samples.toFloat()
            val power = totalPower(outgoingSpec, incomingSpec, t)
            if (power < 0.70f || power > 1.30f) {
                return true
            }
        }
        return false
    }

    /** Match the curve specifications against standard presets. */
    fun detectPreset(outgoingSpec: CurveSpec, incomingSpec: CurveSpec): String {
        for ((id, preset) in PRESETS) {
            if (preset.outgoingSpec == outgoingSpec && preset.incomingSpec == incomingSpec) {
                return id
            }
        }
        return PRESET_CUSTOM
    }
}
