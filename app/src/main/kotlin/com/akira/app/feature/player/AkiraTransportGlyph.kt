package com.akira.app.feature.player

import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.akira.app.core.design.components.LocalContentMotionReduced

/** Replacing triangles communicate the direction, without moving the touch target. */
@Composable
internal fun AkiraTransportGlyph(direction: Int, trigger: Int, label: String, modifier: Modifier,
    tint: Color = Color.White) {
    val reduced=LocalContentMotionReduced.current
    val phase=remember { Animatable(trigger.toFloat()) }
    LaunchedEffect(trigger,reduced) {
        if(trigger>0 && !reduced) {
            // One conveyor step at most. A burst retargets the unfinished step
            // instead of accumulating an arbitrarily fast multi-cycle spring.
            phase.animateTo(kotlin.math.floor(phase.value)+1f,tween(220,easing=LinearOutSlowInEasing))
        } else phase.snapTo(trigger.toFloat())
    }
    Canvas(modifier.clipToBounds().semantics { contentDescription=label }) {
        // Tangent quadratic corners, shared by the mini and expanded player.
        // The first tip enters the second triangle by 6% of the pair width.
        val step=size.width*.47f
        val width=size.width*.53f
        val height=size.height*.5625f
        val cut=size.width*.072f
        val edge=kotlin.math.hypot(width,height/2f)
        val dx=width/edge*cut;val dy=height/2f/edge*cut
        fun triangle(left: Float)=Path().apply {
            moveTo(left,cut)
            quadraticTo(left,0f,left+dx,dy)
            lineTo(left+width-dx,height/2f-dy)
            quadraticTo(left+width,height/2f,left+width-dx,height/2f+dy)
            lineTo(left+dx,height-dy)
            quadraticTo(left,height,left,height-cut)
            close()
        }
        // Tight quadratic extrema, rather than Path's control-point bounds.
        val top=cut*dy/(cut+dy)
        val optical=androidx.compose.ui.geometry.Rect(0f,top,step+width-dx*.5f,height-top)
        val sx=size.width*.98f/optical.width
        val sy=size.height*.5625f/optical.height
        scale(if(direction<0)-1f else 1f,1f) {
            clipRect(left=0f,right=size.width) {
                scale(sx,sy,pivot=androidx.compose.ui.geometry.Offset.Zero) {
                    translate(size.width*.01f/sx-optical.left,(size.height*.21875f)/sy-optical.top) {
                        val cycle=phase.value-kotlin.math.floor(phase.value)
                        for(i in -1..1) {
                            val alpha=when(i) {-1 -> cycle;1 -> 1f-cycle;else -> 1f}
                            if(alpha>0f)drawPath(triangle((i+cycle)*step),tint.copy(alpha=tint.alpha*alpha))
                        }
                    }
                }
            }
        }
    }
}
