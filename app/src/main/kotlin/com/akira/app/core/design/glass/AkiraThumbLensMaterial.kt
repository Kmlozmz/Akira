package com.akira.app.core.design.glass

import android.content.Context
import android.os.Build
import com.akira.app.core.design.backdrop.BackdropEffectScope
import com.akira.app.core.design.backdrop.RuntimeShader
import com.akira.app.core.design.backdrop.effects.effect
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap
import kotlin.math.ceil

/** The same held-thumb shader and recipe used by Appearance's switches. */
internal class AkiraThumbLensMaterial private constructor(
    private val source:String, private val fields:Map<String,FloatArray>
) {
    private val bindings=WeakHashMap<RuntimeShader,FloatArray>()
    var failed=false
        private set
    fun apply(scope:BackdropEffectScope,presence:Float) {
        if(Build.VERSION.SDK_INT<33 || presence<=.001f || failed)return
        try {
            val dp=scope.density
            scope.padding=24*dp
            val shader=scope.obtainRuntimeShader("AkiraSharedThumbLens",source)
            val points=bindings[shader] ?: fields.getValue("uP").copyOf().also {
                fields.forEach { (key,value)->shader.setFloatUniform(key,value) }
                bindings[shader]=it
            }
            val w=scope.size.width/dp;val h=scope.size.height/dp
            val cw=ceil(scope.size.width+2*scope.padding);val ch=ceil(scope.size.height+2*scope.padding)
            points[0]=w/2;points[1]=h/2;points[2]=w/2;points[3]=h/2
            shader.setFloatUniform("uP",points)
            val radii=FloatArray(8);radii[0]=-h/2
            shader.setFloatUniform("uRad",radii)
            shader.setFloatUniform("uShape",1f,0f,presence.coerceIn(0f,1f),1f/dp)
            shader.setFloatUniform("uFrag",0f,0f,1f,dp)
            shader.setFloatUniform("uBd",scope.padding/dp,scope.padding/dp,dp/cw,dp/ch)
            shader.setFloatUniform("uTap",-scope.padding/dp,-scope.padding/dp,cw/dp,ch/dp)
            shader.setFloatUniform("captureInfo",cw,ch,dp,scope.padding)
            with(scope){ effect(RuntimeShaderEffect(shader,"content")) }
        } catch(error:IllegalArgumentException) {
            failed=true
            android.util.Log.w("AkiraVisualMaterial","Switch material unavailable; using its solid thumb",error)
        }
    }
    companion object {
        fun load(context:Context,kind:String="switch"):AkiraThumbLensMaterial {
            val source=context.assets.open("visual-material/switch.agsl").bufferedReader().use{it.readText()}
            val json=JSONObject(context.assets.open("visual-material/$kind.json").bufferedReader().use{it.readText()})
            val fields=json.keys().asSequence().associateWith { key ->
                val values=ArrayList<Float>()
                fun flatten(array:JSONArray) {
                    for(i in 0 until array.length()) {
                        val v=array.get(i)
                        if(v is JSONArray)flatten(v) else values.add((v as Number).toFloat())
                    }
                }
                flatten(json.getJSONArray(key));values.toFloatArray()
            }
            return AkiraThumbLensMaterial(source,fields)
        }
    }
}
