package com.akira.app.feature.player

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.cos
import kotlin.math.sin

/** One pass blends fluid warped color fields reactive to audio and artwork motion. */
@RequiresApi(33)
internal class AmbientArtworkShader(source: String) {
    private val shader = RuntimeShader(source)
    val brush = ShaderBrush(shader)
    private val palette = FloatArray(20)
    private val fields = FloatArray(20)
    private val previousPalette = LongArray(5) { Long.MIN_VALUE }
    private var previousBase = Long.MIN_VALUE
    private var previousSize = Size.Zero

    fun update(
        size: Size,
        phase: Float,
        base: Color,
        colors: List<State<Color>>,
        beat: PlaybackBeatSnapshot = PlaybackBeatSnapshot.Zero,
        artMotion: Float = 0f,
        artShiftX: Float = 0f,
        artShiftY: Float = 0f,
        fadeStartY: Float = 0f,
        fadeEndY: Float = 0f,
        intensity: Float = 1f,
        overlayAlpha: Float = 1f,
    ) {
        if (size != previousSize) {
            shader.setFloatUniform("viewport", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            previousSize = size
        }
        if (base.value.toLong() != previousBase) {
            shader.setFloatUniform("baseColor", base.red, base.green, base.blue, 1f)
            previousBase = base.value.toLong()
        }
        var colorsChanged = false
        val boost = intensity.coerceIn(0.2f, 1.4f)
        for (i in 0..4) {
            val c = colors[i].value
            val offset = i * 4
            if (c.value.toLong() != previousPalette[i]) {
                palette[offset] = c.red
                palette[offset + 1] = c.green
                palette[offset + 2] = c.blue
                palette[offset + 3] = 1f
                previousPalette[i] = c.value.toLong()
                colorsChanged = true
            }
            val angle = phase * (.65f + i * .13f) + i * 1.27f
            val driftScale = 0.56f + 0.10f * beat.bass * boost + 0.08f * artMotion
            val shiftBiasX = if (i % 2 == 0) artShiftX * 0.14f else -artShiftX * 0.08f
            val shiftBiasY = if (i < 3) artShiftY * 0.10f else 0f
            fields[offset] = (.5f + driftScale * cos(angle) + shiftBiasX).coerceIn(-0.25f, 1.25f)
            fields[offset + 1] = (.54f + driftScale * sin(angle * .91f + i) + shiftBiasY).coerceIn(-0.25f, 1.25f)
            val bandEnergy = if (i % 2 == 0) beat.bass + beat.kick * 0.7f else beat.highs + beat.accent * 0.7f
            fields[offset + 2] = .14f + .035f * sin(angle * .71f + i) + .085f * bandEnergy * boost
            fields[offset + 3] = 1f + 0.55f * bandEnergy * boost
        }
        if (colorsChanged) shader.setFloatUniform("palette", palette)
        shader.setFloatUniform("fields", fields)
        shader.setFloatUniform("beat", beat.bass, beat.highs, beat.kick, beat.accent)
        shader.setFloatUniform("motion", phase, artMotion, artShiftX, artShiftY)
        shader.setFloatUniform("framing", fadeStartY, fadeEndY, boost, overlayAlpha.coerceIn(0f, 1f))
    }
}
