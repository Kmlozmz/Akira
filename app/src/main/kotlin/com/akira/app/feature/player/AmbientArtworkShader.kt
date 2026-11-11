package com.akira.app.feature.player

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.sin
import kotlin.math.cos

/** One pass blends the moving color fields instead of painting five full-screen layers. */
@RequiresApi(33)
internal class AmbientArtworkShader(source:String) {
    private val shader=RuntimeShader(source)
    val brush=ShaderBrush(shader)
    private val palette=FloatArray(20)
    private val fields=FloatArray(20)
    private val previousPalette=LongArray(5){Long.MIN_VALUE}
    private var previousBase=Long.MIN_VALUE
    private var previousSize=Size.Zero
    fun update(size:Size,phase:Float,base:Color,colors:List<State<Color>>) {
        if(size!=previousSize) {
            shader.setFloatUniform("viewport",size.width.coerceAtLeast(1f),size.height.coerceAtLeast(1f))
            previousSize=size
        }
        if(base.value.toLong()!=previousBase) {
            shader.setFloatUniform("baseColor",base.red,base.green,base.blue,1f)
            previousBase=base.value.toLong()
        }
        var colorsChanged=false
        for(i in 0..4) {
            val c=colors[i].value;val offset=i*4
            if(c.value.toLong()!=previousPalette[i]) {
                palette[offset]=c.red;palette[offset+1]=c.green;palette[offset+2]=c.blue;palette[offset+3]=1f
                previousPalette[i]=c.value.toLong();colorsChanged=true
            }
            val angle=phase*(.65f+i*.13f)+i*1.27f
            fields[offset]=.5f+.64f*cos(angle)
            fields[offset+1]=.5f+.64f*sin(angle*.91f+i)
            fields[offset+2]=.13f+.035f*sin(angle*.71f+i)
            fields[offset+3]=0f
        }
        if(colorsChanged)shader.setFloatUniform("palette",palette)
        shader.setFloatUniform("fields",fields)
    }
}
