/*
 * Refraction and dispersion adapted from liquid_glass_easy 5.0.0,
 * Copyright (c) 2025 Ahmed Gamil and Copyright (c) 2026 zarzet (MIT).
 * Source attribution: THIRD_PARTY_NOTICES.md.
 * AGSL/Compose integration and bounded rounded-rectangle geometry: Akira.
 * Full licenses are shipped in assets/licenses.
 */
package com.akira.app.core.design.backdrop.effects

import com.akira.app.core.design.backdrop.BackdropEffectScope
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import com.akira.app.core.design.backdrop.isRuntimeShaderSupported
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import com.akira.app.core.design.motion.ElasticGlassShape
import com.akira.app.core.design.motion.ElasticGlassMapShader
import kotlin.math.ceil

/** A clear centre and a Snell-refraction edge band; three reads only in that band.
 * Coordinates, blur and displacement all use the same reduced backdrop space. */
fun BackdropEffectScope.refractionLens(pixelPerDp: Float, deformation: Float = 0f, presence: Float = 1f) {
    if (!isRuntimeShaderSupported()) return
    val corners = shape as? CornerBasedShape ?: return
    val scale=pixelPerDp/density
    val radius = (corners.topStart.toPx(size/scale, this)*scale).coerceAtMost(size.minDimension / 2f)
    val shader = obtainRuntimeShader("AkiraElasticSnellLens", RefractionLensShader)
    val strain=(shape as? ElasticGlassShape)?.strainFor(size) ?: Offset.Zero
    shader.setFloatUniform("strain",strain.x,strain.y)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("inset", padding, padding)
    shader.setFloatUniform("captureSize", ceil(size.width + padding * 2f), ceil(size.height + padding * 2f))
    shader.setFloatUniform("radius", radius)
    shader.setFloatUniform("dp", pixelPerDp.coerceAtLeast(.01f))
    shader.setFloatUniform("presence",presence.coerceIn(0f,1f))
    shader.setFloatUniform("deformation", deformation.coerceIn(0f, 1f))
    effect(RuntimeShaderEffect(shader, "content"))
}

private const val RefractionLensShader = """
uniform shader content;
uniform float2 size;
uniform float2 inset;
uniform float2 captureSize;
uniform float radius;
uniform float dp;
uniform float deformation;
uniform float presence;
uniform float2 strain;
""" + ElasticGlassMapShader + """

// Sample pixel centres inside this surface's finite capture, including blur
// padding. The page outside it is not a valid input to this RenderEffect.
half4 sampleScene(float2 at) {
    return content.eval(clamp(at, float2(0.5), max(captureSize - 0.5, float2(0.5))));
}

half4 main(float2 coord) {
    if(presence<0.001)return sampleScene(coord);
    float2 local = elasticInverse((coord-inset)/max(size,float2(1.0)),strain)*size;
    float2 p = local - size * 0.5;
    float2 q = abs(p) - size * 0.5 + radius;
    float2 outside = max(q, 0.0);
    float sd = length(outside) + min(max(q.x, q.y), 0.0) - radius;
    float band = (20.0 + 3.0 * deformation) * dp;
    float2 normal2D;
    if (length(outside) > 0.0001) normal2D = sign(p) * normalize(outside);
    else normal2D = sign(p) * (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    // Inverse-transpose of the same outline map. The rim and its lighting
    // follow the curved neck, rather than refracting as a hidden rectangle.
    float2 uv=local/max(size,float2(1.0));
    float2 weights=clamp(4.0*uv*(1.0-uv),0.0,1.0);
    float a=1.0-0.25*abs(strain.x)*weights.y;
    float b=1.0-0.25*abs(strain.y)*weights.x;
    float xy=(-0.25*abs(strain.x)*(uv.x-0.5)+0.11*strain.x)*4.0*(1.0-2.0*uv.y)*size.x/max(size.y,1.0);
    float yx=(-0.25*abs(strain.y)*(uv.y-0.5)+0.11*strain.y)*4.0*(1.0-2.0*uv.x)*size.y/max(size.x,1.0);
    float2 warpedNormal=float2(b*normal2D.x-yx*normal2D.y,a*normal2D.y-xy*normal2D.x)/max(a*b-xy*yx,0.01);
    sd/=max(length(warpedNormal),0.01);
    normal2D=warpedNormal/max(length(warpedNormal),0.0001);
    if (-sd >= band) return sampleScene(coord);
    float h = clamp(-sd / band, 0.0, 1.0);
    float zone = 1.0 - h;
    float3 normal3D = normalize(float3(normal2D * sqrt(max(1.0-h*h, 0.0)), h));
    // Refractive index calibrated for a depth of .30 and a 20 dp edge band.
    float3 ray = refract(float3(0.0, 0.0, -1.0), normal3D, 0.93794);
    float2 at = coord + ray.xy * ((70.0 + 20.0 * deformation) * dp * zone * presence);
    half4 base = sampleScene(at);
    float luma = dot(float3(base.rgb), float3(0.2126, 0.7152, 0.0722));
    // Same brightness-dependent diagonal split and visibility threshold as v5.
    if (1.32 * zone < 0.66) return base;
    float split = 1.32 * dp * zone * luma * presence;
    half4 red = sampleScene(at + float2(split));
    half4 blue = sampleScene(at - float2(split));
    // Neighbouring samples can have different alpha at a clipped row edge.
    // Keep the dispersed channels premultiplied by the OUTPUT alpha too.
    base.r = clamp(red.r / max(red.a, half(0.0001)), half(0.0), half(1.0)) * base.a;
    base.b = clamp(blue.b / max(blue.a, half(0.0001)), half(0.0), half(1.0)) * base.a;
    return base;
}
"""
