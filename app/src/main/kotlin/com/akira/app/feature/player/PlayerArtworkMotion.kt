package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.*
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.motion.ElasticGlassShape
import com.akira.app.core.model.Song
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ArtworkRectConverter = TwoWayConverter<Rect, AnimationVector4D>(
    { AnimationVector4D(it.left,it.top,it.right,it.bottom) },
    { Rect(it.v1,it.v2,it.v3,it.v4) })

internal data class ArtworkTarget(val bounds: Rect, val radiusPx: Float, val mask: Float)

/** One owner survives PLAYER/LYRICS/QUEUE changes. Anchors only measure targets;
 * none of them paints a newly composed full-sized cover before matching starts.
 */
@Stable
internal class PlayerArtworkMotion {
    var target by mutableStateOf<ArtworkTarget?>(null)
    var ready by mutableStateOf(false)
    val bounds = Animatable(Rect.Zero,ArtworkRectConverter)
    val radius = Animatable(0f)
    val mask = Animatable(0f)
}

@Composable
internal fun rememberPlayerArtworkMotion(expansion: Animatable<Float,*>,
    onBounds: ((Rect,androidx.compose.ui.unit.Dp,Float)->Unit)?): PlayerArtworkMotion {
    val config=androidx.compose.ui.platform.LocalConfiguration.current
    val density=LocalDensity.current
    val motion=remember(config.screenWidthDp,config.screenHeightDp) { PlayerArtworkMotion() }
    val target=motion.target
    val reduced=LocalContentMotionReduced.current
    val settled by remember(expansion) { derivedStateOf { expansion.value>=.999f } }
    val latestBounds by rememberUpdatedState(onBounds)
    LaunchedEffect(motion,target,settled,reduced) {
        val end=target ?: return@LaunchedEffect
        if(motion.ready)latestBounds?.invoke(motion.bounds.value,with(density){motion.radius.value.toDp()},motion.mask.value)
        if(!motion.ready) {
            motion.bounds.snapTo(end.bounds);motion.radius.snapTo(end.radiusPx);motion.mask.snapTo(end.mask)
            motion.ready=true
        } else if(!settled)return@LaunchedEffect
        else if(reduced) {
            motion.bounds.snapTo(end.bounds);motion.radius.snapTo(end.radiusPx);motion.mask.snapTo(end.mask)
        } else coroutineScope {
            // A cover changes presentation; it is not a menu materializing from
            // a button. All edges settle directly, with no elastic overshoot.
            launch { motion.bounds.animateTo(end.bounds,spring(1f,850f,Rect(.5f,.5f,.5f,.5f))) }
            launch { motion.radius.animateTo(end.radiusPx,spring(1f,850f,.1f)) }
            launch { motion.mask.animateTo(end.mask,spring(1f,850f,.001f)) }
        }
    }
    LaunchedEffect(motion,density) {
        snapshotFlow { if(motion.ready)Triple(motion.bounds.value,motion.radius.value,motion.mask.value) else null }
            .collect { frame -> frame?.let { (bounds,radius,mask) ->
                latestBounds?.invoke(bounds,with(density){radius.toDp()},mask)
            } }
    }
    return motion
}

@Composable
internal fun PlayerArtworkSurface(motion: PlayerArtworkMotion, song: Song,
    expansion: Animatable<Float,*>, fullWidthPx: Float, motionArtwork: MotionArtwork?=null,
    isPlaying: Boolean=false) {
    val density=LocalDensity.current
    val outline=remember(motion,density) {
        ElasticGlassShape({motion.radius.value},{0f},{androidx.compose.ui.geometry.Offset.Zero},density)
    }
    val needsMask by remember(motion) { derivedStateOf { motion.mask.value>.001f || motion.target?.mask==1f } }
    Box(Modifier.graphicsLayer {
        alpha=if(motion.ready)playerArtworkSettledAlpha(expansion.value) else 0f
        translationX=motion.bounds.value.left;translationY=motion.bounds.value.top
        shape=outline
        shadowElevation=24.dp.toPx()*(1f-motion.mask.value).coerceIn(0f,1f)*
            ((motion.bounds.value.width/fullWidthPx-.15f)/.7f).coerceIn(0f,1f)
    }.layout { measurable,_ ->
        val rect=motion.bounds.value
        val width=rect.width.roundToInt().coerceAtLeast(1)
        val height=rect.height.roundToInt().coerceAtLeast(1)
        val child=measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width,height))
        layout(width,height) { child.place(0,0) }
    }.clip(outline)
        .then(if(needsMask)Modifier.playerArtworkFade(fullWidthPx){motion.mask.value} else Modifier)
        .background(Color.Transparent),contentAlignment=Alignment.Center) {
        if(!motion.ready) {
            Spacer(Modifier.fillMaxSize())
        } else if(!song.albumArtUri.isNullOrBlank()) {
            com.akira.app.core.widget.WidgetTransitionArtwork(song.albumArtUri,
                Modifier.fillMaxSize(),imageRequest=rememberPlayerArtworkRequest(song.albumArtUri))
        } else {
            val activity=LocalActivity.current as? com.akira.app.MainActivity
            LaunchedEffect(activity) { activity?.widgetArtworkReady=true;activity?.widgetLaunchMotion?.contentReady=true }
            Icon(Icons.Rounded.MusicNote,null,Modifier.size(76.dp).onGloballyPositioned {
                activity?.widgetLaunchMotion?.takeIf { motion -> !motion.finished }?.artworkTarget=it.boundsInWindow()
            },tint=Color.White)
        }
        if(motionArtwork!=null && motion.ready) {
            MotionArtworkVideo(motionArtwork,isPlaying,Modifier.fillMaxSize())
        }
    }
}
