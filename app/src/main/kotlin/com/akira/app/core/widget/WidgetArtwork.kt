package com.akira.app.core.widget

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.model.AppearancePreset
import com.akira.app.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class WidgetArt(val bitmap: Bitmap?, val dominant: Int)

internal object WidgetArtwork {
    private var cachedUri: String? = null
    private var cached: WidgetArt? = null

    fun peek(uri: String?): Bitmap? = cached?.bitmap?.takeIf { uri != null && uri == cachedUri }

    suspend fun load(context: Context, song: Song?): WidgetArt {
        val uri = song?.albumArtUri
        if (uri != null && uri == cachedUri && cached != null) return cached!!
        val image = uri?.let { try {
            context.imageLoader.execute(ImageRequest.Builder(context).data(it).size(400).allowHardware(false).build())
                .drawable?.toBitmap(400, 400)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } }
        currentCoroutineContext().ensureActive()
        val dominant = image?.let { Palette.from(it).maximumColorCount(12).generate().dominantSwatch?.rgb }
            ?: song?.tone?.hex?.toInt() ?: 0xFF22232B.toInt()
        return WidgetArt(image, dominant).also {
            if (image != null) { cachedUri = uri; cached = it }
        }
    }

    fun backgroundColor(art: WidgetArt, prefs: UserPreferences): Int {
        val theme = AppearancePreset.fromId(prefs.appearancePreset).background.toInt()
        var color = if (prefs.dynamicColor) ColorUtils.blendARGB(art.dominant, 0xFF101116.toInt(), .42f) else theme
        while (ColorUtils.calculateLuminance(color) > .12) color = ColorUtils.blendARGB(color, Color.BLACK, .12f)
        return color
    }

    fun background(widthDp: Int, heightDp: Int, art: WidgetArt, prefs: UserPreferences, full: Boolean, colorOverride: Int? = null): Bitmap {
        val width = widthDp * 2
        val height = heightDp * 2
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.clipPath(Path().apply { addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 48f, 48f, Path.Direction.CW) })
            val color = colorOverride ?: backgroundColor(art, prefs)
            canvas.drawColor(color)
            if (full && art.bitmap != null) drawCrop(canvas, art.bitmap, RectF(0f, 0f, width.toFloat(), height.toFloat()))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (full) {
                paint.shader = LinearGradient(0f, height * .48f, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0xDD000000.toInt()), null, Shader.TileMode.CLAMP)
            } else {
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, .06f), color), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    fun cover(image: Bitmap?, radius: Float = 12f): Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        // Ratio scales with the actual cover; Small uses a softer 20 dp radius at 104 dp width.
        val round = if (radius == 20f) 320f * 20f / 104f else 320f * 12f / 144f
        canvas.clipPath(Path().apply { addRoundRect(0f, 0f, 320f, 320f, round, round, Path.Direction.CW) })
        canvas.drawColor(0xFF22232B.toInt())
        if (image != null) drawCrop(canvas, image, RectF(0f, 0f, 320f, 320f))
        else canvas.drawBitmap(icon(AkiraIcons.Play), null, RectF(110f, 110f, 210f, 210f), null)
    }

    private fun drawCrop(canvas: Canvas, image: Bitmap, target: RectF) {
        val scale = maxOf(target.width() / image.width, target.height() / image.height)
        val cropWidth = target.width() / scale
        val cropHeight = target.height() / scale
        val source = Rect(((image.width - cropWidth) / 2).toInt(), ((image.height - cropHeight) / 2).toInt(),
            ((image.width + cropWidth) / 2).toInt(), ((image.height + cropHeight) / 2).toInt())
        canvas.drawBitmap(image, source, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    /** Rasterize the actual AkiraIcons paths for RemoteViews; no second transport icon system. */
    fun icon(vector: ImageVector): Bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        canvas.scale(72f / vector.viewportWidth, 72f / vector.viewportHeight)
        fun drawGroup(group: VectorGroup) {
            for (node in group) when (node) {
                is VectorGroup -> drawGroup(node)
                is VectorPath -> {
                    val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                    if (node.fill is SolidColor) { paint.style = Paint.Style.FILL; canvas.drawPath(path, paint) }
                    if (node.stroke is SolidColor) { paint.style = Paint.Style.STROKE; paint.strokeWidth = node.strokeLineWidth; canvas.drawPath(path, paint) }
                }
            }
        }
        drawGroup(vector.root)
    }

    fun brand(context: Context): Bitmap {
        val pixels = (24f * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val path = Path(com.akira.app.core.design.brand.AkiraBrand.symbol.asAndroidPath())
        val bounds = RectF().also { path.computeBounds(it, true) }
        val scale = pixels * .88f / maxOf(bounds.width(), bounds.height())
        return Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.translate(pixels / 2f, pixels / 2f)
            canvas.scale(scale, scale)
            canvas.translate(-bounds.centerX(), -bounds.centerY())
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
    }
}
