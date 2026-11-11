package com.akira.app.core.design.glass

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import com.akira.app.core.design.system.AkiraContactSpec
import org.json.JSONArray

/** A contact filter for the complete control, including its icon and material. */
@Composable
internal fun rememberAkiraContactLighting(amount:()->Float,point:()->Offset):Modifier {
    val material=LocalSharedGlassMaterial.current
    val context=LocalContext.current.applicationContext
    if(!LocalLiquidGlassEnabled.current || Build.VERSION.SDK_INT<33 || material?.contactSource==null)return Modifier
    val filter=remember(context,material){
        val data=JSONArray(context.assets.open("visual-material/press-glow.json").bufferedReader().use{it.readText()})
        runCatching { AkiraContactFilter(material.contactSource,FloatArray(data.length()){data.getDouble(it).toFloat()}) }.getOrNull()
    } ?: return Modifier
    return Modifier.graphicsLayer {
        renderEffect=filter.frame(size,point(),amount())?.asComposeRenderEffect()
    }
}

@androidx.annotation.RequiresApi(33)
internal class AkiraContactFilter(source:String,profile:FloatArray) {
    private val shader=android.graphics.RuntimeShader(source).apply { setFloatUniform("contactProfile",profile) }
    fun frame(size:androidx.compose.ui.geometry.Size,point:Offset,amount:Float):android.graphics.RenderEffect? {
        val p=amount.coerceIn(0f,1f)
        if(p<.001f || size.width<=0f || size.height<=0f)return null
        shader.setFloatUniform("uSize",size.width,size.height)
        shader.setFloatUniform("uCenter",(point.x+1)*size.width/2,(point.y+1)*size.height/2)
        shader.setFloatUniform("uReach",AkiraContactSpec.glowReach*minOf(size.width,size.height)*(1+3*(1-p)))
        shader.setFloatUniform("uBig",p*AkiraContactSpec.glowAmount)
        shader.setFloatUniform("uLittle",p*AkiraContactSpec.glowAmount)
        shader.setFloatUniform("uDodge",AkiraContactSpec.glowDodge)
        // RenderEffect snapshots uniforms: build it only after this frame is completely bound.
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content")
    }
}
