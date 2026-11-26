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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.motion.ElasticGlassShape
import com.akira.app.core.design.rendering.DownscaledBlur
import com.akira.app.core.model.Song
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlin.math.roundToInt

internal data class ArtworkTarget(val bounds: Rect, val radiusPx: Float, val mask: Float)

/** One owner survives PLAYER/LYRICS/QUEUE changes. Anchors only measure targets;
 * none of them paints a newly composed full-sized cover before matching starts.
 */
@Stable
internal class PlayerArtworkMotion {
    var target by mutableStateOf<ArtworkTarget?>(null)
    var frame by mutableStateOf<ArtworkTarget?>(null)
    val ready get() = frame != null
    val bounds get() = frame?.bounds ?: Rect.Zero
    val radius get() = frame?.radiusPx ?: 0f
    val mask get() = frame?.mask ?: 0f
    val isAtFullscreenCover: Boolean
        get() = target?.mask == 1f && frame == target
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
        val start = motion.frame
        if (start != null && !settled) return@LaunchedEffect
        if (start != null && start != end && !reduced) {
            // Publish one frame so the fade can never lag behind the resized cover.
            animate(0f, 1f, animationSpec = tween(270, easing = FastOutSlowInEasing)) { fraction, _ ->
                motion.frame = ArtworkTarget(
                    lerp(start.bounds, end.bounds, fraction),
                    androidx.compose.ui.util.lerp(start.radiusPx, end.radiusPx, fraction),
                    androidx.compose.ui.util.lerp(start.mask, end.mask, fraction),
                )
            }
        }
        motion.frame = end
    }
    LaunchedEffect(motion,density) {
        snapshotFlow { motion.frame }
            .collect { frame -> frame?.let { (bounds,radius,mask) ->
                latestBounds?.invoke(bounds,with(density){radius.toDp()},mask)
            } }
    }
    return motion
}

@Composable
internal fun PlayerArtworkSurface(motion: PlayerArtworkMotion, song: Song,
    expansion: Animatable<Float,*>, fullWidthPx: Float, motionArtwork: MotionArtwork?=null,
    showMotionArtwork: Boolean,
    playMotionArtwork: Boolean,
    fullBleed: Boolean,
    expandedCoverBounds: () -> Rect?,
    mediaLayer: GraphicsLayer,
    onVideoBlend: (Float) -> Unit,
    onLiveSample: ((LiveArtworkSample?) -> Unit)? = null,
    onArtworkReady: () -> Unit,
) {
    val density=LocalDensity.current
    val outline=remember(motion,density) {
        ElasticGlassShape({motion.radius},{0f},{androidx.compose.ui.geometry.Offset.Zero},density)
    }
    val softEdge = fullBleed && android.os.Build.VERSION.SDK_INT >= 31
    val recordEdge by remember(motion, softEdge) {
        derivedStateOf { softEdge && motion.mask > .001f }
    }
    val showVideo by remember(motion, showMotionArtwork) {
        derivedStateOf { showMotionArtwork && motion.isAtFullscreenCover }
    }
    val blurMask = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            .30f to Color.Black.copy(alpha = .18f),
            .72f to Color.Black.copy(alpha = .90f),
            1f to Color.Black,
        )
    }
    val blurRadius = with(density) { 29.dp.toPx() }
    Box(Modifier.graphicsLayer {
        alpha=if(motion.ready)playerArtworkSettledAlpha(expansion.value) else 0f
        translationX=motion.bounds.left;translationY=motion.bounds.top
        shape=outline
        shadowElevation=24.dp.toPx()*(1f-motion.mask).coerceIn(0f,1f)*
            ((motion.bounds.width/fullWidthPx-.15f)/.7f).coerceIn(0f,1f)
    }.layout { measurable,_ ->
        val rect=motion.bounds
        val width=rect.width.roundToInt().coerceAtLeast(1)
        val height=rect.height.roundToInt().coerceAtLeast(1)
        val child=measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width,height))
        layout(width,height) { child.place(0,0) }
    }.clip(outline)
        .then(if(fullBleed)Modifier.playerArtworkFade(fullWidthPx, completeDuringTravel = true){motion.mask} else Modifier)
        .background(Color.Transparent),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxSize().then(if (softEdge) Modifier.drawWithContent {
            if (recordEdge) {
                mediaLayer.record { this@drawWithContent.drawContent() }
                drawLayer(mediaLayer)
            } else drawContent()
        } else Modifier), contentAlignment = Alignment.Center) {
            if(!motion.ready) {
                Spacer(Modifier.fillMaxSize())
            } else if(!song.albumArtUri.isNullOrBlank()) {
                com.akira.app.core.widget.WidgetTransitionArtwork(song.albumArtUri,
                    Modifier.fillMaxSize(),imageRequest=rememberPlayerArtworkRequest(song.albumArtUri),
                    onArtworkReady = onArtworkReady)
            } else {
                val activity=LocalActivity.current as? com.akira.app.MainActivity
                LaunchedEffect(activity) { activity?.widgetArtworkReady=true;activity?.widgetLaunchMotion?.contentReady=true }
                Icon(Icons.Rounded.MusicNote,null,Modifier.size(76.dp).onGloballyPositioned {
                    activity?.widgetLaunchMotion?.takeIf { motion -> !motion.finished }?.artworkTarget=it.boundsInWindow()
                },tint=Color.White)
            }
            if (motionArtwork != null && motion.ready) {
                // Keep the same paused editorial frame through both directions of the transition.
                // The crop follows the moving width; using the height as a second scale can zoom it.
                MotionArtworkVideo(motionArtwork, isPlaying = showVideo && playMotionArtwork, presented = showVideo,
                    retainPresentedFrame = true,
                    modifier = Modifier.fillMaxSize().layout { measurable, constraints ->
                        val full = expandedCoverBounds()
                        val width = full?.width?.roundToInt()?.coerceAtLeast(1) ?: constraints.maxWidth
                        val height = full?.height?.roundToInt()?.coerceAtLeast(1) ?: constraints.maxHeight
                        val texture = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                        layout(constraints.maxWidth, constraints.maxHeight) { texture.place(0, 0) }
                    }.graphicsLayer {
                        val viewport = motion.bounds
                        val scale = (viewport.width / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = (viewport.width - size.width * scale) / 2f
                        translationY = (viewport.height - size.height * scale) / 2f
                    }, onBlend = onVideoBlend, onLiveSample = onLiveSample)
            }
        }
        if (recordEdge) {
            DownscaledBlur(
                radius = { blurRadius },
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                mask = blurMask,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.55f)
                    .graphicsLayer { alpha = motion.mask.coerceIn(0f, 1f) },
            ) { strip ->
                translate(top = strip.height - mediaLayer.size.height) { drawLayer(mediaLayer) }
            }
        }
    }
}
