package com.akira.app.core.design.motion

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.layout.*
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.akira.app.core.design.components.rememberContentMotionReduced
import kotlinx.coroutines.*
import kotlin.math.*

/** Adapter between Compose's clamped scroll state and Flutter's out-of-range offset. */
class AkiraBouncingEffect(private val pinTopContent: Boolean = false,
    private val allowTopPull: Boolean = false):OverscrollEffect {
    private var translation by mutableFloatStateOf(0f)
    private var height=1
    private var rebound:Job?=null
    private var dispatching=false
    private val viewport get()=height.toDouble()
    val topPull get()=translation.coerceAtLeast(0f)
    override val isInProgress get()=translation!=0f
    // Compose bypasses OverscrollEffect when neither direction can scroll.

    // drag through nested scroll, AFTER touch slop, without intercepting taps.
    private val shortContent = object:NestedScrollConnection {
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset =
            if(!dispatching && source==NestedScrollSource.UserInput && available!=Offset.Zero)
                applyToScroll(available,source){Offset.Zero} else Offset.Zero
        override suspend fun onPostFling(consumed:Velocity,available:Velocity):Velocity {
            if(available==Velocity.Zero && !isInProgress)return Velocity.Zero
            applyToFling(available){Velocity.Zero}
            return Velocity(0f,available.y)
        }
    }
    override val node:DelegatableNode=object:DelegatingNode(),LayoutModifierNode {
        init { delegate(nestedScrollModifierNode(shortContent, dispatcher=null)) }
        override fun MeasureScope.measure(measurable:Measurable,constraints:Constraints):MeasureResult {
            val placeable=measurable.measure(constraints)
            height=placeable.height.coerceAtLeast(1)
            return layout(placeable.width,placeable.height) {
                placeable.placeWithLayer(0,0) {
                    translationX=0f
                    // Home's artwork lives inside a clipped LazyColumn item.
                    // Keep that viewport filled while the header consumes topPull
                    // as zoom; other lists translate over a separate backdrop.
                    translationY=if(pinTopContent && translation>0f)0f else translation
                }
            }
        }
        override fun onDetach(){rebound?.cancel();translation=0f}
    }
    private fun axis(o:Offset)=o.y
    private fun vector(value:Float)=Offset(0f,value)
    private fun dispatch(delta:Offset,performScroll:(Offset)->Offset):Offset {
        dispatching=true
        return try{performScroll(delta)}finally{dispatching=false}
    }
    override fun applyToScroll(delta:Offset,source:NestedScrollSource,performScroll:(Offset)->Offset):Offset {
        // Programmatic scrolling and ballistic frames must not load a drag spring.
        // A fling transfers its remaining VELOCITY at the edge in applyToFling.
        if(source!=NestedScrollSource.UserInput || delta.y==0f)return performScroll(delta)
        rebound?.cancel()
        if(translation!=0f) {
            val physical=BouncingScrollPhysics.userOffset(translation.toDouble(),viewport,axis(delta).toDouble()).toFloat()
            val proposed=translation+physical
            if(proposed*translation>0f) { translation=proposed;return vector(axis(delta)) }
            // Cross the boundary in the SAME event, with no sticky extra drag.
            translation=0f
            dispatch(vector(proposed),performScroll)
            return vector(axis(delta))
        }
        val consumed=dispatch(delta,performScroll)
        translation=axis(delta-consumed).let { if(!allowTopPull && it>0f)0f else it }
        return consumed+vector(translation)
    }
    override suspend fun applyToFling(velocity:Velocity,performFling:suspend(Velocity)->Velocity) {
        rebound?.cancel()
        val rest=if(translation!=0f)velocity else velocity-performFling(velocity)
        val v=rest.y
        if(!allowTopPull && translation==0f && v>0f)return
        if(translation==0f && abs(v)<20f)return
        coroutineScope {
            val spring=BouncingScrollPhysics.ReturnSpring(translation.toDouble(),v.toDouble())
            val job=launch {
                var start=-1L
                do {
                    val t=withFrameNanos { now->if(start<0)start=now;(now-start)/1_000_000_000.0 }
                    translation=spring.position(t).toFloat()
                } while(!spring.done(t))
                translation=0f
            }
            rebound=job
            try { job.join() } finally {
                if(!currentCoroutineContext().isActive) {job.cancel();translation=0f}
            }
        }
    }
}

private class BouncingFactory:OverscrollFactory {
    override fun createOverscrollEffect()=AkiraBouncingEffect()
    override fun equals(other:Any?)=other is BouncingFactory
    override fun hashCode()=BouncingFactory::class.hashCode()
}
@Composable fun rememberAkiraBouncingFactory():OverscrollFactory? {
    val reduced=rememberContentMotionReduced()
    return remember(reduced){if(reduced)null else BouncingFactory()}
}
@Composable fun rememberAkiraBouncingEffect(pinTopContent: Boolean = false, allowTopPull: Boolean = false):AkiraBouncingEffect? {
    val reduced=rememberContentMotionReduced()
    return remember(reduced,pinTopContent,allowTopPull){if(reduced)null else AkiraBouncingEffect(pinTopContent,allowTopPull)}
}
val LocalAkiraMediaPull=staticCompositionLocalOf<()->Float>{{0f}}
/** FlexibleSpaceBar StretchMode.zoomBackground: artwork fills the extra height. */
fun Modifier.akiraMediaStretch(pull:()->Float,counterTranslate:Boolean=false)=graphicsLayer {
    val extra=pull().coerceAtLeast(0f)
    val zoom=(size.height+extra)/size.height.coerceAtLeast(1f)
    scaleX=zoom;scaleY=zoom;transformOrigin=TransformOrigin(.5f,0f)
    if(counterTranslate)translationY=-extra
}

private class BouncingFling(private val density:Float):FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity:Float):Float {
        if(abs(initialVelocity)<100f*density)return 0f // Flutter minFlingVelocity * 2
        val v=initialVelocity.coerceIn(-8000f*density,8000f*density).toDouble()
        var start=-1L
        var last=0.0
        while(true) {
            val t=withFrameNanos { now->if(start<0)start=now;(now-start)/1_000_000_000.0 }
            val next=BouncingScrollPhysics.frictionPosition(v,t)
            val delta=(next-last).toFloat()
            val consumed=scrollBy(delta)
            val speed=BouncingScrollPhysics.frictionVelocity(v,t).toFloat()
            if(abs(delta-consumed)>.5f)return speed
            if(abs(speed)<20f)return 0f
            last=next
        }
    }
}
@Composable fun rememberAkiraBouncingFling():FlingBehavior {
    val density=LocalDensity.current.density
    return remember(density){BouncingFling(density)}
}
