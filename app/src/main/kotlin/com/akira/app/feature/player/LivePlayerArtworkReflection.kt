package com.akira.app.feature.player

import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.rendering.DownscaledBlur

/** Reads the same recorded media layer as the crisp artwork. The blurred
 * reflection can therefore follow editorial lighting with one decoder. */
@Composable
internal fun LivePlayerArtworkReflection(
    mediaLayer: GraphicsLayer,
    artwork: () -> Rect?,
    opacity: () -> Float,
    modifier: Modifier,
) {
    val mask = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            .22f to Color.Black.copy(alpha = .12f),
            .48f to Color.Black.copy(alpha = .62f),
            .72f to Color.Black.copy(alpha = .37f),
            1f to Color.Transparent,
        )
    }
    val radius = with(LocalDensity.current) { 37.dp.toPx() }
    DownscaledBlur(
        radius = { radius },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        modifier = modifier.graphicsLayer { alpha = opacity() },
    ) { strip ->
        val source = mediaLayer.size
        if (source.width <= 0 || source.height <= 0) return@DownscaledBlur
        val fullHeight = strip.height / .59f
        val floor = artworkReflectionFloor(fullHeight, artwork(), fullBleed = true)
        val line = floor - (fullHeight - strip.height)
        val reflectedHeight = minOf((strip.height - line).coerceAtLeast(1f), source.height * .60f) + radius
        val reflectedWidth = strip.width
        translate(left = (strip.width - reflectedWidth) / 2f, top = line + reflectedHeight) {
            scale(reflectedWidth / source.width, -reflectedHeight / source.height, pivot = Offset.Zero) {
                clipRect(right = source.width.toFloat(), bottom = source.height.toFloat()) {
                    drawLayer(mediaLayer)
                    drawRect(Color.Black.copy(alpha = .28f), size = Size(source.width.toFloat(), source.height.toFloat()))
                }
            }
        }
    }
}
