package com.akira.app.core.design.motion

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.ContinuousMenuShape
import kotlin.math.abs

/** Bounded elastic map shared by the outline, foreground and refractive rim.
 * Positive strain draws the waist towards the source on that axis. The map
 * stays inside its measured bounds, so animated clipping never cuts its lobe.
 */
internal fun elasticGlassPoint(point: Offset, size: Size, strain: Offset): Offset {
    val u = point.x / size.width.coerceAtLeast(1f)
    val v = point.y / size.height.coerceAtLeast(1f)
    val wx = (4f*u*(1f-u)).coerceIn(0f,1f)
    val wy = (4f*v*(1f-v)).coerceIn(0f,1f)
    val x = strain.x.coerceIn(-1f,1f)
    val y = strain.y.coerceIn(-1f,1f)
    return Offset(size.width*(.5f+(u-.5f)*(1f-.25f*abs(x)*wy)+.11f*x*wy),
        size.height*(.5f+(v-.5f)*(1f-.25f*abs(y)*wx)+.11f*y*wx))
}

private class LiveCorner(private val radius: () -> Float): CornerSize {
    override fun toPx(shapeSize: Size, density: Density) = radius().coerceIn(0f,shapeSize.minDimension/2f)
}

internal class ElasticGlassShape(
    private val radius: () -> Float,
    private val smoothing: () -> Float,
    val strain: () -> Offset,
    private val density: Density,
    private val preserveCapsuleEnds: Boolean = false,
): CornerBasedShape(LiveCorner(radius),LiveCorner(radius),LiveCorner(radius),LiveCorner(radius)) {
    fun strainFor(size: Size): Offset {
        val value=strain()
        return if(preserveCapsuleEnds) Offset(value.x*size.minDimension/size.width.coerceAtLeast(1f),
            value.y*size.minDimension/size.height.coerceAtLeast(1f)) else value
    }
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float,
        bottomStart: Float, layoutDirection: LayoutDirection): Outline {
        val base = ContinuousMenuShape(with(density){radius().coerceIn(0f,size.minDimension/2f).toDp()},smoothing().coerceIn(0f,1f))
            .createOutline(size,layoutDirection,density)
        val deformation = strainFor(size)
        if (abs(deformation.x)+abs(deformation.y)<.001f) return base
        val source = when(base) {
            is Outline.Generic -> base.path
            is Outline.Rounded -> Path().apply { addRoundRect(base.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(base.rect) }
        }
        val measure = PathMeasure().apply { setPath(source,false) }
        val path = Path()
        val subdivisions=kotlin.math.ceil(measure.length/(size.minDimension/32f).coerceAtLeast(1f)).toInt().coerceIn(128,1024)
        for (i in 0..subdivisions) {
            val p = elasticGlassPoint(measure.getPosition(measure.length*i/subdivisions),size,deformation)
            if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
        }
        path.close()
        return Outline.Generic(path)
    }
    override fun copy(topStart: CornerSize,topEnd: CornerSize,bottomEnd: CornerSize,bottomStart: CornerSize): CornerBasedShape = this
}

/** Shader is compiled once per presentation, never once per frame. */
internal class ElasticContentWarp {
    private val shader = if(Build.VERSION.SDK_INT>=33) android.graphics.RuntimeShader(ElasticContentShader) else null
    fun effect(size: Size, strain: Offset): RenderEffect? {
        if(Build.VERSION.SDK_INT<33 || shader==null || abs(strain.x)+abs(strain.y)<.001f)return null
        shader.setFloatUniform("size",size.width.coerceAtLeast(1f),size.height.coerceAtLeast(1f))
        shader.setFloatUniform("strain",strain.x,strain.y)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
    }
}

internal const val ElasticGlassMapShader = """
float2 elasticPoint(float2 p, float2 strain) {
    float2 weights = clamp(4.0*p*(1.0-p), 0.0, 1.0);
    return 0.5 + (p-0.5)*(1.0-0.25*abs(strain)*weights.yx) + 0.11*strain*weights.yx;
}
float2 elasticInverse(float2 p, float2 strain) {
    float2 q=p;
    for(int i=0;i<4;i++) q += p-elasticPoint(q,strain);
    return q;
}
"""

private const val ElasticContentShader = """
uniform shader content;
uniform float2 size;
uniform float2 strain;
""" + ElasticGlassMapShader + """
half4 main(float2 coord) {
    float2 p=elasticInverse(coord/size,strain)*size;
    return content.eval(p);
}
"""
