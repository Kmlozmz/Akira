package com.akira.app.feature.player

import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithCache
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
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
    val phase=remember { Animatable(0f) }
    val latestTrigger=rememberUpdatedState(trigger)
    LaunchedEffect(reduced) {
        phase.snapTo(0f)
        if(!reduced) snapshotFlow { latestTrigger.value }.drop(1).conflate().collect {
            // Finish the visible step before accepting the latest queued tap.
            // Zero and one draw the same pair, so the reset has no visual seam.
            phase.snapTo(0f)
            phase.animateTo(1f,tween(280,easing=FastOutSlowInEasing))
        }
    }
    Box(modifier.clipToBounds().semantics { contentDescription=label }.drawWithCache {
        // Tangent quadratic corners, shared by the mini and expanded player.
        // The first tip enters the second triangle by 6% of the pair width.
        val step=size.width*.47f
        val width=size.width*.53f
        val height=size.height*.5625f
        val cut=size.width*.072f
        val edge=kotlin.math.hypot(width,height/2f)
        val dx=width/edge*cut;val dy=height/2f/edge*cut
        val triangle=Path().apply {
            val left=0f
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
        onDrawBehind {
        scale(if(direction<0)-1f else 1f,1f) {
            clipRect(left=0f,right=size.width) {
                scale(sx,sy,pivot=androidx.compose.ui.geometry.Offset.Zero) {
                    translate(size.width*.01f/sx-optical.left,(size.height*.21875f)/sy-optical.top) {
                        val cycle=phase.value.coerceIn(0f,1f)
                        for(i in -1..1) {
                            val alpha=when(i) {-1 -> cycle;1 -> 1f-cycle;else -> 1f}
                            if(alpha>0f)translate((i+cycle)*step,0f) {
                                drawPath(triangle,tint.copy(alpha=tint.alpha*alpha))
                            }
                        }
                    }
                }
            }
        }
        }
    })
}
