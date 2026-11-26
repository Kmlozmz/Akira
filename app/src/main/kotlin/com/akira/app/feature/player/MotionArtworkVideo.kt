package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/** A video-only decoder. It owns neither the music session nor audio focus.
 * TextureView participates in the same clipped artwork/mask as the sleeve.
 * Player subpages retain this owner; disposal releases its decoder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun MotionArtworkVideo(artwork: MotionArtwork,isPlaying: Boolean,modifier: Modifier=Modifier,
    onPlayer: ((ExoPlayer)->Unit)?=null,
    onBlend: ((Float) -> Unit)? = null,
    onLiveSample: ((LiveArtworkSample?) -> Unit)? = null,
    presented: Boolean = true,
    retainPresentedFrame: Boolean = false,
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var source by remember(artwork) {mutableStateOf(artwork.url)}
    var ready by remember(artwork) {mutableStateOf(false)}
    var failed by remember(artwork) {mutableStateOf(false)}
    val playing by rememberUpdatedState(isPlaying)
    val multiWindow by rememberUpdatedState(com.akira.app.core.design.components.LocalMultiWindowMode.current)
    fun visiblePlaybackAllowed() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
        (multiWindow && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    val texture=remember(context,artwork) {TextureView(context).apply {isOpaque=false;isClickable=false;isFocusable=false;importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO}}
    val player=remember(context,artwork) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            trackSelectionParameters=trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).setMaxVideoSize(2560,2560)
                .setMaxVideoBitrate(16_000_000).setForceHighestSupportedBitrate(true).build()
            volume=0f;repeatMode=Player.REPEAT_MODE_ONE
            setHandleAudioBecomingNoisy(false)
            setVideoTextureView(texture)
        }
    }
    DisposableEffect(player,lifecycle) {
        var video=VideoSize.UNKNOWN
        val matrix = Matrix()
        var cropWidth = 0
        var cropHeight = 0
        var cropRatio = 0f
        fun crop() {
            val w=texture.width.toFloat();val h=texture.height.toFloat()
            if(w<=0 || h<=0 || video.width<=0 || video.height<=0)return
            val ratio=video.width*video.pixelWidthHeightRatio/video.height
            if (texture.width == cropWidth && texture.height == cropHeight && ratio == cropRatio) return
            cropWidth = texture.width
            cropHeight = texture.height
            cropRatio = ratio
            val scaleX=maxOf(1f,ratio/(w/h));val scaleY=maxOf(1f,(w/h)/ratio)
            matrix.setScale(scaleX,scaleY,w/2,h/2)
            texture.setTransform(matrix)
        }
        val layout=android.view.View.OnLayoutChangeListener {_,_,_,_,_,_,_,_,_ ->crop()}
        texture.addOnLayoutChangeListener(layout)
        val listener=object: Player.Listener {
            override fun onRenderedFirstFrame() {
                ready=true
                if(com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraHomeArtwork", "motion video first frame rendered")
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {video=videoSize;crop()}
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                ready=false
                if(source!=artwork.fallback && !artwork.fallback.isNullOrBlank())source=artwork.fallback
                else {failed=true;player.stop()}
            }
        }
        val observer=LifecycleEventObserver {_,event ->
            val visible=visiblePlaybackAllowed()
            if(visible && !failed && player.playbackState==Player.STATE_IDLE && player.mediaItemCount>0)player.prepare()
            player.playWhenReady=playing && !failed && visible
            // A transient focus/lifecycle loss pauses the same prepared decoder.
            // Re-entering the player must not refetch or rewind the editorial loop.
            if(event==Lifecycle.Event.ON_STOP)player.pause()
        }
        player.addListener(listener);lifecycle.addObserver(observer);onPlayer?.invoke(player)
        onDispose {
            lifecycle.removeObserver(observer);texture.removeOnLayoutChangeListener(layout)
            player.removeListener(listener);player.clearVideoTextureView(texture);player.release()
        }
    }
    LaunchedEffect(player,source) {
        ready=false;failed=false
        player.setMediaItem(MediaItem.fromUri(source))
        if(visiblePlaybackAllowed())player.prepare()
    }
    SideEffect {
        val visible = visiblePlaybackAllowed()
        if(visible && !failed && player.playbackState==Player.STATE_IDLE && player.mediaItemCount>0)player.prepare()
        player.playWhenReady=isPlaying && !failed && visible
    }
    var hasPresented by remember(artwork, source) { mutableStateOf(false) }
    SideEffect {
        if (retainPresentedFrame && ready && !failed && presented) hasPresented = true
    }
    val showFrame = ready && !failed && (presented || hasPresented)
    val alpha = animateFloatAsState(if (showFrame) 1f else 0f,
        if (showFrame) tween(260) else androidx.compose.animation.core.snap(), label = "motionSleeve")
    val currentPresentation by rememberUpdatedState(showFrame)
    if (onBlend != null) {
        val report by rememberUpdatedState(onBlend)
        LaunchedEffect(alpha, source) {
            snapshotFlow { if (currentPresentation) alpha.value else 0f }.collect { report(it) }
        }
    }
    if (onLiveSample != null) {
        val reportSample by rememberUpdatedState(onLiveSample)
        val sampler = remember(artwork) { LiveArtworkSampler(12) }
        DisposableEffect(artwork) {
            onDispose { reportSample(null) }
        }
        LaunchedEffect(texture, showFrame, isPlaying, multiWindow) {
            if (!showFrame || !isPlaying) {
                sampler.reset()
                reportSample(null)
                return@LaunchedEffect
            }
            val sampleBitmap = android.graphics.Bitmap.createBitmap(12, 12, android.graphics.Bitmap.Config.ARGB_8888)
            val pixels = IntArray(12 * 12)
            try {
                while (true) {
                    if (visiblePlaybackAllowed() && texture.isAvailable && texture.width > 0 && texture.height > 0) {
                        val captured = runCatching { texture.getBitmap(sampleBitmap) }.getOrNull()
                        if (captured != null) {
                            captured.getPixels(pixels, 0, 12, 0, 0, 12, 12)
                            reportSample(sampler.analyze(pixels))
                        }
                    }
                    kotlinx.coroutines.delay(95L)
                }
            } finally {
                sampleBitmap.recycle()
            }
        }
    }
    key(texture) {
        AndroidView(factory = { texture }, modifier = modifier.graphicsLayer {
            this.alpha = if (currentPresentation) alpha.value else 0f
        })
    }
}
