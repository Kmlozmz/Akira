package com.akira.app.core.design.motion

import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.PI
import kotlin.math.sin

/** Transient rim distortion; the resting text has no foreground filter. */
internal class PresentationContentOptics {
    private val shader=if(Build.VERSION.SDK_INT>=33)android.graphics.RuntimeShader(ContentShader) else null

    fun effect(viewport: Size, pose: PresentationContentTransform, radius: Float,
        progress: Float, density: Float): RenderEffect? {
        val amount=sin(PI.toFloat()*progress.coerceIn(0f,1f)).coerceAtLeast(0f)
        if(Build.VERSION.SDK_INT<33 || shader==null || amount<.01f)return null
        shader.setFloatUniform("viewport",viewport.width.coerceAtLeast(1f),viewport.height.coerceAtLeast(1f))
        shader.setFloatUniform("offset",pose.offset.x,pose.offset.y)
        shader.setFloatUniform("scale",pose.scale)
        shader.setFloatUniform("radius",radius.coerceAtLeast(0f))
        shader.setFloatUniform("amount",amount)
        shader.setFloatUniform("dp",density)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
    }
}

private const val ContentShader="""
uniform shader content;
uniform float2 viewport;
uniform float2 offset;
uniform float scale;
uniform float radius;
uniform float amount;
uniform float dp;
half4 main(float2 coord) {
    float2 surface=coord*scale+offset;
    float2 local=surface-viewport*0.5;
    float r=min(radius,min(viewport.x,viewport.y)*0.5);
    float2 q=abs(local)-viewport*0.5+r;
    float distance=length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
    float rim=1.0-smoothstep(0.0,24.0*dp,-distance);
    float2 at=coord+local*(0.065*rim*amount)/scale;
    float softness=dp*amount*(0.18+1.25*rim)/scale;
    return content.eval(at)*0.5+
        (content.eval(at+float2(softness,0.0))+content.eval(at-float2(softness,0.0))+
         content.eval(at+float2(0.0,softness))+content.eval(at-float2(0.0,softness)))*0.125;
}
"""
