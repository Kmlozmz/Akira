package com.akira.app.core.design.backdrop.effects

import com.akira.app.core.design.backdrop.BackdropEffectScope
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import com.akira.app.core.design.backdrop.isRuntimeShaderSupported

/** Adjust only source pixels that conflict with the foreground ink, preserving refraction and source alpha. */
internal fun BackdropEffectScope.glassReadability(lightInk: Boolean, presence: Float) {
    if (!isRuntimeShaderSupported()) return
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
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    if (lightInk > 0.5) {
        float need = smoothstep(0.44, 0.80, luma) * presence;
        rgb *= mix(1.0, min(1.0, 0.42 / max(luma, 0.001)), need);
    } else {
        float need = (1.0 - smoothstep(0.06, 0.42, luma)) * presence;
        rgb = mix(rgb, max(rgb, float3(0.68)), need);
    }
    return half4(half3(rgb * alpha), sample.a);
}
"""
