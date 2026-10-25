package com.akira.app.core.widget

import android.graphics.Rect as AndroidRect
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akira.app.MainActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class WidgetLaunchState(
    val origin: AndroidRect,
    val variant: String,
    val widgetId: Int = -1,
    val initialPositionMs: Long = 0L,
    initialSnapshot: Bitmap? = null,
    initialSnapshotSongId: String? = null
) {
    val progress = Animatable(0f)
    var snapshot by mutableStateOf(initialSnapshot)
    var snapshotSongId: String? = initialSnapshotSongId
    var closing by mutableStateOf(false)
    var artworkTarget by mutableStateOf<Rect?>(null)
    var contentReady by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var windowReady by mutableStateOf(false)
}

internal fun widgetPlayerAlpha(state: WidgetLaunchState): Float = widgetContentAlpha(state.progress.value)

internal fun widgetSurfaceRect(origin: Rect, width: Float, height: Float, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(origin.left * (1f - p), origin.top * (1f - p),
        origin.right + (width - origin.right) * p, origin.bottom + (height - origin.bottom) * p)
}

internal data class WidgetContentFrame(val surface: Rect, val scale: Float, val viewportHeight: Float)

internal fun widgetContentFrame(origin: Rect, width: Float, height: Float, progress: Float): WidgetContentFrame {
    val surface = widgetSurfaceRect(origin, width, height, progress)
    val scale = (surface.width / width.coerceAtLeast(1f)).coerceAtLeast(.001f)
    return WidgetContentFrame(surface, scale, surface.height / scale)
}

internal fun widgetContentFrame(origin: AndroidRect, width: Float, height: Float, progress: Float): WidgetContentFrame =
    widgetContentFrame(Rect(origin.left.toFloat(), origin.top.toFloat(), origin.right.toFloat(), origin.bottom.toFloat()),
        width, height, progress)

internal fun widgetContentAlpha(progress: Float): Float {
    // Hold the exact RemoteViews snapshot until the card is already moving, then
    // replace it quickly. This keeps differently wrapped player text from flashing
    // at widget size on both the opening and closing paths.
    val t = ((progress - .22f) / (.42f - .22f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun widgetContentShape(state: WidgetLaunchState, radiusPx: Float): Shape = GenericShape { size, _ ->
    val frame = widgetContentFrame(state.origin, size.width, size.height, state.progress.value)
    addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(0f, 0f, size.width, frame.viewportHeight),
        androidx.compose.ui.geometry.CornerRadius(radiusPx * (1f - state.progress.value.coerceIn(0f, 1f)) / frame.scale)))
}

/** A single card expands. The real player scales uniformly inside it, with an exact launcher snapshot at its origin. */
@Composable
internal fun WidgetOpeningOverlay(state: WidgetLaunchState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val song = remember(state) { AkiraWidgets.currentSong(context) }
    LaunchedEffect(state.closing) {
        if (state.closing) {
            state.progress.animateTo(0f, tween(360, easing = CubicBezierEasing(.2f, 0f, .35f, 1f)))
            val activity = context as? MainActivity
            activity?.window?.let { window -> window.attributes = window.attributes.apply { alpha = 0f } }
            withFrameNanos { }
            activity?.finishWidgetReturn()
        }
    }
    LaunchedEffect(state, state.closing) {
        if (state.closing) return@LaunchedEffect
        if (song == null) { state.finished = true; return@LaunchedEffect }
        if (state.snapshot == null) { state.finished = true; return@LaunchedEffect }
        withFrameNanos { }
        state.progress.animateTo(1f, tween(360, easing = CubicBezierEasing(.2f, 0f, .35f, 1f)))
        state.finished = true
    }
    if (state.finished) return
    val image = state.snapshot ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = widgetContentFrame(state.origin, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), state.progress.value)
        Image(image.asImageBitmap(), null, contentScale = ContentScale.Crop,
            modifier = Modifier.offset { IntOffset(frame.surface.left.roundToInt(), frame.surface.top.roundToInt()) }
                .size(with(density) { frame.surface.width.toDp() }, with(density) { frame.surface.height.toDp() })
                .clip(RoundedCornerShape((24f * (1f - state.progress.value.coerceIn(0f, 1f))).dp))
                .graphicsLayer { alpha = 1f - widgetPlayerAlpha(state) })
    }
}

/** System backdrop blur avoids taking screenshots of other apps or the launcher. */
@Composable
internal fun WidgetLaunchBackdrop(state: WidgetLaunchState) {
    if (state.finished || Build.VERSION.SDK_INT < 31) return
    val activity = androidx.activity.compose.LocalActivity.current as? MainActivity ?: return
    val manager = activity.getSystemService(WindowManager::class.java)
    val density = LocalDensity.current.density
    DisposableEffect(state) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.progress.value }.map { (it.coerceIn(0f, 1f) * 20f * density).roundToInt() }
            .distinctUntilChanged().collect { radius ->
                activity.window.attributes = activity.window.attributes.apply {
                    blurBehindRadius = if (manager.isCrossWindowBlurEnabled) radius else 0
                    dimAmount = (radius / (20f * density)).coerceIn(0f, 1f) * .12f
                }
            }
    }
}

@Composable
internal fun WidgetTransitionArtwork(uri: String?, modifier: Modifier = Modifier, decodeSize: Int? = null, imageRequest: coil.request.ImageRequest? = null) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val model = imageRequest ?: remember(uri,decodeSize,context) {
        if(decodeSize == null) uri else coil.request.ImageRequest.Builder(context)
            .data(uri).size(decodeSize.coerceAtLeast(1)).crossfade(false).build()
    }
    LaunchedEffect(uri) { if (uri.isNullOrBlank()) activity?.widgetArtworkReady = true }
    val cached = WidgetArtwork.peek(uri)?.asImageBitmap()?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) }
    AsyncImage(model, null, placeholder = cached, contentScale = ContentScale.Crop,
        onSuccess = { activity?.widgetArtworkReady = true; activity?.widgetLaunchMotion?.contentReady = true },
        onError = {
            if (com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraArtwork", "expanded decode failed", it.result.throwable)
            activity?.widgetArtworkReady = true; activity?.widgetLaunchMotion?.contentReady = true
        },
        modifier = modifier.onGloballyPositioned { coordinates ->
            activity?.widgetLaunchMotion?.takeIf { !it.finished }?.artworkTarget = coordinates.boundsInWindow()
        })
}
