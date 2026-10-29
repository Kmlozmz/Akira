/* Derived from ClearPDF, Copyright 2026 Chethan616, MIT License.
 * Akira adaptations: namespace, integration and accessibility. See assets/licenses/glass-controls/NOTICE. */
package com.akira.app.core.design.controls.components

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Draw-time springs scale with these values; layout and dismissal remain critically damped. */
@Immutable
data class MotionStyle(
    val bounce: Float = 0.5f,
    val speed: Float = 1f,
    val press: Float = 1f,
    val wobble: Float = 1f,
    val stretch: Float = 1f,
    val bloom: Float = 1f
) {
    companion object {
        val Normal = MotionStyle()
        val Performance = MotionStyle(bounce = 0.2f, speed = 1.45f, press = 0.6f, wobble = 0f, stretch = 0.5f, bloom = 0.5f)
        val Bouncy = MotionStyle(bounce = 0.75f, speed = 0.92f, press = 1.3f, wobble = 1.6f, stretch = 1.2f, bloom = 1.2f)
        val SuperSpringy = MotionStyle(bounce = 0.95f, speed = 0.82f, press = 1.6f, wobble = 2.2f, stretch = 1.5f, bloom = 1.5f)
        val Presets = listOf(Normal, Performance, Bouncy, SuperSpringy)
        /** What the app runs until the user picks a profile, and what Reset returns to. */
        val Default = Bouncy
    }
}

object MotionSettings {
    private const val PREFS = "akira_glass"

    var style: MotionStyle by mutableStateOf(MotionStyle.Default)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = MotionStyle.Default
        style = MotionStyle(
            bounce = p.getFloat("motion_bounce", d.bounce),
            speed = p.getFloat("motion_speed", d.speed),
            press = p.getFloat("motion_press", d.press),
            wobble = p.getFloat("motion_wobble", d.wobble),
            stretch = p.getFloat("motion_stretch", d.stretch),
            bloom = p.getFloat("motion_bloom", d.bloom)
        )
    }

    fun preview(s: MotionStyle) { style = s }

    fun update(context: Context, s: MotionStyle) {
        style = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("motion_bounce", s.bounce)
            .putFloat("motion_speed", s.speed)
            .putFloat("motion_press", s.press)
            .putFloat("motion_wobble", s.wobble)
            .putFloat("motion_stretch", s.stretch)
            .putFloat("motion_bloom", s.bloom)
            .apply()
    }

    /** Bounce changes damping; the lower bound prevents indefinite ringing. */
    fun damping(designed: Float): Float {
        if (GlassSettings.reducedMotion) return 1f
        val f = 1f + (0.5f - style.bounce) * 1.2f
        return (designed * f).coerceIn(0.12f, 0.98f)
    }

    fun stiffness(designed: Float): Float = designed * style.speed.coerceIn(0.4f, 2.5f)
}
