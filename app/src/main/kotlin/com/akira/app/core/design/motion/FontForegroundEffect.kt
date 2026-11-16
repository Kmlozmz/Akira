package com.akira.app.core.design.motion

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Uses the font selector's foreground lens, independently of the backdrop material. */
@RequiresApi(33)
internal class FontForegroundEffect(context:Context) {
    private val shader=com.akira.app.core.design.rendering.PreparedRuntimeShaders.acquire(
        context.assets.open("visual-material/foreground-morph.agsl").bufferedReader().use{it.readText()})
    private val points=FloatArray(32)
    private val radii=FloatArray(8)
    fun bind(frame:SharedMorphFrame,dp:Float):androidx.compose.ui.graphics.RenderEffect? {
        val warp=frame.warp ?: return null
        if(kotlin.math.abs(warp[0])<.125f && kotlin.math.abs(warp[1])<.125f)return null
        points.fill(0f);radii.fill(0f)
        frame.localElements().take(8).forEachIndexed{i,e->
            points[i*4]=e.center.x/dp;points[i*4+1]=e.center.y/dp
            points[i*4+2]=e.half.width/dp;points[i*4+3]=e.half.height/dp;radii[i]=e.radius/dp
        }
        val body=frame.rawBody ?: return null
        val center=(body.center-frame.bounds.topLeft)/dp
        shader.setFloatUniform("uSize",kotlin.math.ceil(frame.bounds.width),kotlin.math.ceil(frame.bounds.height))
        shader.setFloatUniform("uFrag",0f,0f,1f/dp,0f)
        shader.setFloatUniform("uP",points);shader.setFloatUniform("uRad",radii)
        shader.setFloatUniform("uShape",frame.elements.size.toFloat(),frame.union/dp,1f,1f/dp)
        shader.setFloatUniform("uWarp",warp)
        shader.setFloatUniform("uA",center.x,center.y,body.width/2/dp,body.height/2/dp)
        shader.setFloatUniform("uDeep",0f,0f,0f,0f)
        return RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
    }
}
