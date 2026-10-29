package com.akira.app.core.design.controls.components

/** Optical multipliers of the recovered controls; page material remains independently configured. */
internal object GlassSettings {
    val style = ControlGlassStyle()
    val reducedMotion: Boolean get() = com.akira.app.core.design.motion.InteractionPreferences.reducedMotion
}

internal data class ControlGlassStyle(
    val blur: Float = 1f,
    val refractionHeight: Float = 1f,
    val refractionAmount: Float = 1f,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
)
