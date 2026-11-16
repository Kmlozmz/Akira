/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.akira.app.core.design.backdrop

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import org.intellij.lang.annotations.Language

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RuntimeShader(@Language("AGSL") shaderString: String): RuntimeShader {
    val shader = com.akira.app.core.design.rendering.PreparedRuntimeShaders.acquire(shaderString)
    return AndroidRuntimeShader(shader)
}

fun RuntimeShader.asComposeShader(): Shader {
    return this.asAndroidRuntimeShader()
}

fun RuntimeShader.asAndroidRuntimeShader(): android.graphics.RuntimeShader {
    return (this as AndroidRuntimeShader).shader
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidRuntimeShader(val shader: android.graphics.RuntimeShader) : RuntimeShader {

    // Dynamic material lambdas also run for stationary geometry. Avoid identical JNI uploads;
    // arrays are copied because callers reuse their uniform buffers between frames.
    private val floats = mutableMapOf<String, FloatArray>()
    private fun changed(name: String, count: Int, a: Float, b: Float = 0f, c: Float = 0f, d: Float = 0f): Boolean {
        val old = floats[name]
        if (old != null && old.size == count && old[0].toRawBits() == a.toRawBits() &&
            (count < 2 || old[1].toRawBits() == b.toRawBits()) && (count < 3 || old[2].toRawBits() == c.toRawBits()) &&
            (count < 4 || old[3].toRawBits() == d.toRawBits())) return false
        val values = old?.takeIf { it.size == count } ?: FloatArray(count).also { floats[name] = it }
        values[0] = a
        if (count > 1) values[1] = b
        if (count > 2) values[2] = c
        if (count > 3) values[3] = d
        return true
    }

    override fun setFloatUniform(name: String, value: Float) {
        if (changed(name, 1, value)) shader.setFloatUniform(name, value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        if (changed(name, 2, value1, value2)) shader.setFloatUniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        if (changed(name, 3, value1, value2, value3)) shader.setFloatUniform(name, value1, value2, value3)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        if (changed(name, 4, value1, value2, value3, value4)) shader.setFloatUniform(name, value1, value2, value3, value4)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        val old = floats[name]
        if (old != null && old.contentEquals(values)) return
        shader.setFloatUniform(name, values)
        if (old != null && old.size == values.size) values.copyInto(old) else floats[name] = values.copyOf()
    }

    override fun setIntUniform(name: String, value: Int) {
        shader.setIntUniform(name, value)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int) {
        shader.setIntUniform(name, value1, value2)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {
        shader.setIntUniform(name, value1, value2, value3)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        shader.setIntUniform(name, value1, value2, value3, value4)
    }

    override fun setIntUniform(name: String, values: IntArray) {
        shader.setIntUniform(name, values)
    }

    override fun setColorUniform(name: String, color: Color) {
        shader.setColorUniform(name, color.toArgb())
    }
}

interface RuntimeShader {

    fun setFloatUniform(name: String, value: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
    fun setFloatUniform(name: String, values: FloatArray)

    fun setIntUniform(name: String, value: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)
    fun setIntUniform(name: String, values: IntArray)

    fun setColorUniform(name: String, color: Color)
}
