package com.akira.app.core.design.backdrop.effects

import com.akira.app.core.design.backdrop.BackdropEffectScope
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import com.akira.app.core.design.backdrop.isRuntimeShaderSupported
import com.akira.app.core.design.glass.GlassContrast
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter

/** Adjust only source pixels that conflict with the foreground ink, preserving refraction and source alpha. */
internal fun BackdropEffectScope.glassReadability(lightInk: Boolean, presence: Float,
    readableContent: Boolean = false, surfaceFloor: Float = 0f) {
    if (!isRuntimeShaderSupported()) {
        if (readableContent) {
            val limit = GlassContrast.background(lightInk).red
            val amount = presence.coerceIn(0f, 1f)
            val scale = 1f - amount * if (lightInk) 1f - limit else limit
            val offset = if (lightInk) 0f else 255f * limit * amount
            colorFilter(ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ))))
        } else if (surfaceFloor > 0f) {
            val scale = 1f - surfaceFloor
            val offset = surfaceFloor * 255f
            colorFilter(ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f))))
        }
        return
    }
    val shader = obtainRuntimeShader("AkiraGlassReadability", ReadabilityShader)
    shader.setFloatUniform("lightInk", if (lightInk) 1f else 0f)
    shader.setFloatUniform("presence", presence.coerceIn(0f, 1f))
    shader.setFloatUniform("readableContent", if (readableContent) 1f else 0f)
    shader.setFloatUniform("contrastLimits", GlassContrast.DarkLuminance, GlassContrast.LightLuminance)
    shader.setFloatUniform("surfaceFloor", surfaceFloor.coerceIn(0f, .1f))
    effect(RuntimeShaderEffect(shader, "content"))
}

private const val ReadabilityShader = """
uniform shader content;
uniform float lightInk;
uniform float presence;
uniform float readableContent;
uniform float2 contrastLimits;
uniform float surfaceFloor;
float3 linearRgb(float3 color) {
    return mix(color / 12.92, pow((color + 0.055) / 1.055, float3(2.4)), step(0.04045, color));
}
float3 encodedRgb(float3 color) {
    return mix(color * 12.92, 1.055 * pow(max(color, float3(0.0)), float3(1.0 / 2.4)) - 0.055,
        step(0.0031308, color));
}
half4 main(float2 p) {
    half4 sample = content.eval(p);
    float alpha = float(sample.a);
    float3 rgb = clamp(float3(sample.rgb) / max(alpha, 0.0001), 0.0, 1.0);
    rgb = max(rgb, float3(surfaceFloor));
    if (readableContent > 0.5) {
        float3 linear = linearRgb(rgb);
        float luminance = dot(linear, float3(0.2126, 0.7152, 0.0722));
        float3 protectedRgb;
        if (lightInk > 0.5) {
            protectedRgb = linear * min(1.0, contrastLimits.x / max(luminance, 0.0001));
        } else {
            float amount = clamp((contrastLimits.y - luminance) / max(1.0 - luminance, 0.0001), 0.0, 1.0);
            protectedRgb = mix(linear, float3(1.0), amount);
        }
        rgb = encodedRgb(mix(linear, protectedRgb, presence));
        return half4(half3(rgb * alpha), sample.a);
    }
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
