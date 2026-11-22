package com.akira.app.feature.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class PlayerArtworkAtmosphere(val image: ImageBitmap, val footer: Color)

@Composable
internal fun rememberPlayerArtworkAtmosphere(uri: String?, revision: Long): PlayerArtworkAtmosphere? {
    val context = LocalContext.current.applicationContext
    return produceState<PlayerArtworkAtmosphere?>(null, uri, revision) {
        value = null
        if (uri.isNullOrBlank()) return@produceState
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(112)
            .memoryCacheKey("akira-player-atmosphere:v1:$revision:$uri")
            .allowHardware(false)
            .transformations(AtmosphereBlur)
            .build()
        val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            ?: return@produceState
        // Take the footer hue from the upper field so dark blues and violet
        // survive even on covers with a large warm foreground.
        val top = bitmap.height / 12
        val sides = lerp(
            Color(bitmap.getPixel(bitmap.width / 4, top)),
            Color(bitmap.getPixel(bitmap.width * 3 / 4, top)),
            .5f,
        )
        val sky = lerp(sides, Color(bitmap.getPixel(bitmap.width / 2, top)), .40f).copy(alpha = 1f)
        value = PlayerArtworkAtmosphere(bitmap.asImageBitmap(), lerp(sky, Color(0xFF090B14), .23f))
    }.value
}

internal fun artworkReflectionFloor(height: Float, artwork: Rect?, fullBleed: Boolean): Float {
    val edge = artwork?.let { if (fullBleed) it.top + it.height * .82f else it.bottom }
        ?: (height * if (fullBleed) .57f else .48f)
    return edge.coerceIn(height * .42f, height * .66f)
}

internal fun Modifier.playerArtworkAtmosphere(
    atmosphere: PlayerArtworkAtmosphere,
    phase: () -> Float,
    floor: (Float) -> Float = { artworkReflectionFloor(it, null, false) },
    opacity: () -> Float,
): Modifier = drawWithCache {
    val imageLeft = (-size.width * .15f).roundToInt()
    val imageWidth = (size.width * 1.30f).roundToInt()
    val shade = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .07f),
        .48f to Color.Black.copy(alpha = .09f),
        .72f to Color.Black.copy(alpha = .15f),
        1f to Color.Black.copy(alpha = .11f),
    )
    val footer = Brush.verticalGradient(
        0f to Color.Transparent,
        .50f to atmosphere.footer.copy(alpha = 0f),
        .66f to atmosphere.footer.copy(alpha = .18f),
        .80f to atmosphere.footer.copy(alpha = .52f),
        1f to atmosphere.footer.copy(alpha = .92f),
    )
    onDrawBehind {
        val alpha = opacity().coerceIn(0f, 1f)
        if (alpha <= 0f || size.isEmpty()) return@onDrawBehind
        val axis = floor(size.height).roundToInt()
        // Leave enough image above the floor for the mirrored half to reach the
        // bottom. Both draws meet at the same source row and horizontal offset.
        val imageTop = minOf(-size.height * .08f, axis * 2f - size.height).roundToInt()
        val imageSize = IntSize(imageWidth.coerceAtLeast(1), (axis - imageTop).coerceAtLeast(1))
        val drift = (sin(phase()) * size.width * .012f).roundToInt()
        val imageOffset = IntOffset(imageLeft + drift, imageTop)
        drawRect(atmosphere.footer, alpha = alpha)
        clipRect {
            drawImage(atmosphere.image, dstOffset = imageOffset, dstSize = imageSize,
                alpha = alpha, filterQuality = FilterQuality.Medium)
            scale(scaleX = 1f, scaleY = -1f, pivot = Offset(0f, axis.toFloat())) {
                drawImage(atmosphere.image, dstOffset = imageOffset, dstSize = imageSize,
                    alpha = alpha, filterQuality = FilterQuality.Medium)
            }
        }
        drawRect(shade, alpha = alpha)
        drawRect(footer, alpha = alpha)
    }
}

/** A small, cached colour field that works on older phones and costs no
 * full-screen blur pass per frame. Two separable box passes soften the cover
 * while retaining the position of large light and dark areas. */
private object AtmosphereBlur : Transformation {
    override val cacheKey = "akira-player-atmosphere-blur-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val scale = (112f / maxOf(input.width, input.height)).coerceAtMost(1f)
        val width = (input.width * scale).roundToInt().coerceAtLeast(1)
        val height = (input.height * scale).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(width * height)
        val scratch = IntArray(pixels.size)
        val sample = Bitmap.createScaledBitmap(input, width, height, true)
        try {
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (sample !== input) sample.recycle()
        }
        repeat(2) {
            blurLines(pixels, scratch, width, height, stride = 1, lineStride = width)
            blurLines(scratch, pixels, height, width, stride = width, lineStride = 1)
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blurLines(
        source: IntArray,
        result: IntArray,
        length: Int,
        lines: Int,
        stride: Int,
        lineStride: Int,
    ) {
        val radius = (length * .048f).roundToInt().coerceAtLeast(1)
        val samples = radius * 2 + 1
        for (line in 0 until lines) {
            val start = line * lineStride
            for (position in 0 until length) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                for (delta in -radius..radius) {
                    val pixel = source[start + (position + delta).coerceIn(0, length - 1) * stride]
                    alpha += pixel ushr 24
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                }
                result[start + position * stride] = ((alpha / samples) shl 24) or
                    ((red / samples) shl 16) or ((green / samples) shl 8) or (blue / samples)
            }
        }
    }
}
