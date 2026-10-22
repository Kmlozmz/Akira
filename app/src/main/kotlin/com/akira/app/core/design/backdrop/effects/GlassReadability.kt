package com.akira.app.core.design.backdrop.effects

import com.akira.app.core.design.backdrop.BackdropEffectScope
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import com.akira.app.core.design.backdrop.isRuntimeShaderSupported
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter

/** Adjust only source pixels that conflict with the foreground ink, preserving refraction and source alpha. */
internal fun BackdropEffectScope.glassReadability(lightInk: Boolean, presence: Float) {
    if (!isRuntimeShaderSupported()) {
        val amount = presence.coerceIn(0f, 1f)
        val scale = 1f - amount * if (lightInk) .73f else .74f
        val offset = if (lightInk) 0f else 255f * .74f * amount
        colorFilter(ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ))))
        return
    }
    val shader = obtainRuntimeShader("AkiraGlassReadability", ReadabilityShader)
    shader.setFloatUniform("lightInk", if (lightInk) 1f else 0f)
    shader.setFloatUniform("presence", presence.coerceIn(0f, 1f))
    effect(RuntimeShaderEffect(shader, "content"))
}

private const val ReadabilityShader = """
uniform shader content;
uniform float lightInk;
uniform float presence;
half4 main(float2 p) {
    half4 sample = content.eval(p);
    float alpha = float(sample.a);
    float3 rgb = clamp(float3(sample.rgb) / max(alpha, 0.0001), 0.0, 1.0);
    // Keep secondary ink at alpha 0.62 readable without replacing the sampled texture.
    if (lightInk > 0.5) {
        float peak = max(rgb.r, max(rgb.g, rgb.b));
        rgb *= mix(1.0, min(1.0, 0.27 / max(peak, 0.001)), presence);
    } else {
        float lowest = min(rgb.r, min(rgb.g, rgb.b));
        float lift = max(0.0, (0.74 - lowest) / max(1.0 - lowest, 0.001));
        rgb = mix(rgb, float3(1.0), lift * presence);
    }
    return half4(half3(rgb * alpha), sample.a);
}
"""
