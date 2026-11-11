package com.akira.app.core.design.glass

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CornerBasedShape
import com.akira.app.core.design.backdrop.BackdropEffectScope
import com.akira.app.core.design.backdrop.RuntimeShader
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.backdrop.effects.effect
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import com.akira.app.core.design.motion.ElasticGlassShape
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap
import kotlin.math.ceil

internal enum class SharedGlassNature { Control, Lens, Menu, Panel }
internal val LocalSharedGlassNature = staticCompositionLocalOf { SharedGlassNature.Control }

internal val LocalSharedGlassMaterial = staticCompositionLocalOf<SharedGlassMaterial?> { null }

@Composable
internal fun rememberSharedGlassMaterial(): SharedGlassMaterial? {
    val context = LocalContext.current.applicationContext
    return remember(context) { SharedGlassMaterial.load(context) }
}

/** The evaluation and Flutter surfaces share optical source and light/dark recipes. */
internal class SharedGlassMaterial private constructor(
    private val source: String,
    private val darkFields: Map<String, FloatArray>,
    private val lightFields: Map<String, FloatArray>,
    private val controlDark: Map<String, FloatArray>,
    private val controlLight: Map<String, FloatArray>,
    private val menuDark: Map<String, FloatArray>,
    val contactSource:String?,
    val switchMaterial: AkiraThumbLensMaterial?,
    val sliderMaterial: AkiraThumbLensMaterial?,
    val presentationMotion: com.akira.app.core.design.motion.SharedPresentationMotion?
) {
    private class Binding(val dark: Boolean, val nature: SharedGlassNature) {
        val points = FloatArray(32)
        val radii = FloatArray(8)
        val tint = FloatArray(4)
        val blur = FloatArray(4)
        val light = FloatArray(4)
        val ring = FloatArray(4)
        val shadow = FloatArray(4)
        val transform = FloatArray(4)
        val mixed = mutableMapOf<String,FloatArray>()
        var lastBlend=0f
    }
    private val bindings = WeakHashMap<RuntimeShader, Binding>()
    var failed: Boolean = false
        private set

    fun apply(scope: BackdropEffectScope, pixelsPerDp: Float, dark: Boolean,
        opacity: Float, diffusion: Float, presence: Float = 1f, readable: Boolean = false,
        nature: SharedGlassNature = SharedGlassNature.Control, interaction:Float=0f) {
        if (Build.VERSION.SDK_INT < 33 || presence <= .001f || failed) return
        try {
            val shader = scope.obtainRuntimeShader("AkiraSharedLeanMaterial", source)
            val baseFields = when (nature) {
                SharedGlassNature.Control -> if (dark) controlDark else controlLight
                SharedGlassNature.Lens -> controlLight
                SharedGlassNature.Menu -> if (dark) menuDark else lightFields
                SharedGlassNature.Panel -> if (dark) darkFields else lightFields
            }
            val binding = bindings[shader]?.takeIf { it.dark == dark && it.nature == nature } ?: Binding(dark, nature).also { state ->
                baseFields.forEach { (name, values) -> shader.setFloatUniform(name, values) }
                baseFields.getValue("uTintO").copyInto(state.tint)
                baseFields.getValue("uBlurP").copyInto(state.blur)
                bindings[shader] = state
            }
            val morph = (scope.shape as? com.akira.app.core.design.motion.SharedPresentationShape)?.currentFrame()
            val sourceBlend=(morph?.sourceFraction ?: 0f).coerceIn(0f,1f)
            val targetFields=if(dark)controlDark else controlLight
            if(binding.lastBlend!=sourceBlend) {
                baseFields.forEach { (name,base) ->
                    val target=targetFields[name] ?: base
                    val values=binding.mixed.getOrPut(name){FloatArray(base.size)}
                    for(i in base.indices)values[i]=base[i]+((target.getOrNull(i) ?: base[i])-base[i])*sourceBlend
                    if(name !in dynamicUniforms)shader.setFloatUniform(name,values)
                }
                binding.lastBlend=sourceBlend
            }
            val fields=if(sourceBlend>0f)binding.mixed else baseFields
            val dp = pixelsPerDp.coerceAtLeast(.01f)
            val width = scope.size.width / dp
            val height = scope.size.height / dp
            val radius = (scope.shape as? CornerBasedShape)?.topStart?.toPx(scope.size, scope)?.div(dp)
                ?.coerceIn(0f, minOf(width, height) / 2f) ?: minOf(width, height) / 2f
            scope.padding = 24f * dp
            val captureWidth = ceil(scope.size.width + 2 * scope.padding)
            val captureHeight = ceil(scope.size.height + 2 * scope.padding)
            binding.points[0] = width / 2; binding.points[1] = height / 2
            binding.points[2] = width / 2; binding.points[3] = height / 2
            binding.radii[0] = -radius
            if (morph != null) {
                binding.points.fill(0f); binding.radii.fill(0f)
                morph.localMaterialElements().take(8).forEachIndexed { i, element ->
                    binding.points[i*4] = element.center.x / dp
                    binding.points[i*4+1] = element.center.y / dp
                    binding.points[i*4+2] = element.half.width / dp
                    binding.points[i*4+3] = element.half.height / dp
                    binding.radii[i] = element.radius / dp
                }
            }
            shader.setFloatUniform("uP", binding.points)
            shader.setFloatUniform("uRad", binding.radii)
            shader.setFloatUniform("uFrag", 0f, 0f, 1f, 1f)
            shader.setFloatUniform("uShape", morph?.let { (it.materialElements ?: it.elements).size.toFloat() } ?: 1f,
                (morph?.union ?: 0f) / dp, (presence*(morph?.alpha ?: 1f)).coerceIn(0f, 1f), 1f / dp)
            val baseLight=fields.getValue("uLight")
            for(i in binding.light.indices) {
                val dynamic=morph?.light?.get(i) ?: baseLight[i]
                binding.light[i]=dynamic+(baseLight[i]-dynamic)*sourceBlend
            }
            fields.getValue("uRing").copyInto(binding.ring)
            binding.light[0]+=.26f*interaction
            binding.light[2]+=.2f*interaction
            binding.ring[0]+=.1f*interaction
            shader.setFloatUniform("uLight",binding.light)
            val baseShadow=fields.getValue("uShadow")
            for(i in binding.shadow.indices) {
                val dynamic=morph?.shadow?.get(i) ?: baseShadow[i]
                binding.shadow[i]=dynamic+(baseShadow[i]-dynamic)*sourceBlend
            }
            shader.setFloatUniform("uShadow",binding.shadow)
            fields.getValue("uTr").copyInto(binding.transform)
            morph?.style?.let{binding.transform[3]=it+(binding.transform[3]-it)*sourceBlend}
            shader.setFloatUniform("uTr",binding.transform)
            shader.setFloatUniform("uRing",binding.ring)
            shader.setFloatUniform("lensZoom",if(nature==SharedGlassNature.Lens)com.akira.app.core.design.system.AkiraDockSpec.lensZoom else 1f)
            shader.setFloatUniform("edgeDispersion", if (nature == SharedGlassNature.Lens) com.akira.app.core.design.system.AkiraDockSpec.dispersion else .0f)
            shader.setFloatUniform("uBd", scope.padding / dp, scope.padding / dp, dp / captureWidth, dp / captureHeight)
            shader.setFloatUniform("uTap", .5f / captureWidth, .5f / captureHeight,
                0f, fields.getValue("uTap")[3])
            shader.setFloatUniform("captureInfo", captureWidth, captureHeight, dp, scope.padding)
            shader.setFloatUniform("materialSize", width, height)
            val strain = (scope.shape as? ElasticGlassShape)?.strainFor(scope.size) ?: Offset.Zero
            shader.setFloatUniform("materialStrain", strain.x, strain.y)
            val blur = (diffusion*(1f-.72f*sourceBlend)).coerceIn(0f, 24f)
            fields.getValue("uBlurP").copyInto(binding.blur)
            binding.blur[0] = fields.getValue("uBlurP")[0] * blur / 14.64f
            shader.setFloatUniform("uBlurP", binding.blur)
            val tint = maxOf(opacity.coerceIn(0f, 1f), if (readable) .12f*(1f-sourceBlend) else 0f)
            val base = fields.getValue("uTintO")[3]
            fields.getValue("uTintO").copyInto(binding.tint)
            binding.tint[3] = base + (1 - base) * tint
            shader.setFloatUniform("uTintO", binding.tint)
            with(scope) {
                blur(blur * dp)
                effect(RuntimeShaderEffect(shader, "content"))
            }
        } catch (error: IllegalArgumentException) {
            failed = true
            android.util.Log.w("AkiraVisualMaterial", "Shared material unavailable; using a solid accessible surface", error)
        }
    }

    companion object {
        private val dynamicUniforms=setOf("uP","uRad","uShape","uFrag","uBd","uTap","uLight","uTr","uShadow","uRing","uBlurP","uTintO")
        fun load(context: Context): SharedGlassMaterial? {
            val source = try {
                context.assets.open("visual-material/glass.agsl").bufferedReader().use { it.readText() }
            } catch (_: java.io.FileNotFoundException) { return null }
            return runCatching {
                val recipes = JSONObject(context.assets.open("visual-material/material.json").bufferedReader().use { it.readText() })
                fun fields(name: String): Map<String, FloatArray> {
                    val data = recipes.getJSONObject(name)
                    return data.keys().asSequence().associateWith { key ->
                        val flat = ArrayList<Float>()
                        fun flatten(array: JSONArray) {
                            for (i in 0 until array.length()) {
                                val value = array.get(i)
                                if (value is JSONArray) flatten(value) else flat.add((value as Number).toFloat())
                            }
                        }
                        flatten(data.getJSONArray(key)); flat.toFloatArray()
                    }
                }
                SharedGlassMaterial(source, fields("dark"), fields("light"), fields("controlDark"), fields("controlLight"),
                    fields("menuDark"), context.assets.open("visual-material/press-glow.agsl").bufferedReader().use{it.readText()}, AkiraThumbLensMaterial.load(context), AkiraThumbLensMaterial.load(context,"slider"), com.akira.app.core.design.motion.SharedPresentationMotion.load(context))
            }.getOrElse {
                android.util.Log.w("AkiraVisualMaterial", "Invalid shared material recipes; using a solid accessible surface")
                SharedGlassMaterial(source, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), null, null, null, null).also { it.failed = true }
            }
        }
    }
}
