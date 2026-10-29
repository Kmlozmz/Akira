package com.akira.app.feature.lyrics

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import com.akira.app.core.design.components.AkiraModalSheet

import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricCardSheet(song: Song, candidates: List<String>, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(1) }
    var aspect by remember { mutableFloatStateOf(1f) }
    var watermark by remember { mutableStateOf(true) }
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var tint by remember { mutableStateOf(Color(0xFF433A71)) }
    var exporting by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    LaunchedEffect(song.albumArtUri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(ImageRequest.Builder(context).data(song.albumArtUri).allowHardware(false).build())
                result.drawable?.toBitmap()?.let { bitmap ->
                    val rgb = Palette.from(bitmap).generate().getDominantColor(0xFF433A71.toInt())
                    bitmap to Color(rgb)
                }
            }.getOrNull()
        }?.let { (bitmap, color) -> cover = bitmap; tint = color }
    }
    AkiraModalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.library.shareLyrics, style = MaterialTheme.typography.titleLarge)
            Text(strings.library.selectLines)
            Row {
                (1..minOf(5,candidates.size)).forEach { amount ->
                    FilterChip(count == amount, { count = amount }, label = { Text(amount.toString()) }, modifier = Modifier.padding(end = 4.dp))
                }
            }
            Row {
                listOf("1:1" to 1f, "4:5" to .8f, "9:16" to .5625f).forEach { (label, ratio) ->
                    FilterChip(aspect == ratio, { aspect = ratio }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            val previewWidth = minOf(maxWidth, 300.dp * aspect)
            Canvas(Modifier.width(previewWidth).aspectRatio(aspect)
                .drawWithContent {
                    val factor = 1080f / size.width
                    layer.record(size = IntSize(1080, (1080 / aspect).toInt())) {
                        scale(factor, factor, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
                    }
                    scale(1f / factor, 1f / factor, pivot = Offset.Zero) { drawLayer(layer) }
                }) {
                drawRect(Brush.linearGradient(listOf(tint, Color(0xFF101019))))
                val unit = size.width / 1080f
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    fun text(value: String, x: Float, y: Float, font: Float, bold: Boolean = false) {
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE; textSize = font*unit
                            typeface = if (bold) Typeface.create("sans-serif",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL)
                        }
                        val trimmed = android.text.TextUtils.ellipsize(value, TextPaint(paint), 800*unit, android.text.TextUtils.TruncateAt.END).toString()
                        native.drawText(trimmed,x*unit,y*unit,paint)
                    }
                    cover?.let { bitmap ->
                        native.save()
                        val path = android.graphics.Path().apply { addRoundRect(android.graphics.RectF(72*unit,72*unit,196*unit,196*unit),20*unit,20*unit,android.graphics.Path.Direction.CW) }
                        native.clipPath(path)
                        native.drawBitmap(bitmap,null,android.graphics.RectF(72*unit,72*unit,196*unit,196*unit),android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
                        native.restore()
                    }
                    text(song.title,224f,120f,34f,true)
                    text(song.artist,224f,172f,28f)
                    val content = candidates.take(count).joinToString("\n")
                    val availableHeight = size.height - 340*unit
                    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; typeface = Typeface.create("sans-serif",Typeface.BOLD) }
                    var font = 72f*unit
                    fun layout(): StaticLayout = StaticLayout.Builder.obtain(content,0,content.length,paint, (936*unit).toInt().coerceAtLeast(1)).setIncludePad(false).setLineSpacing(8*unit,1f).build()
                    paint.textSize = font
                    var lines = layout()
                    while (lines.height > availableHeight && font > 24*unit) { font -= 2*unit; paint.textSize = font; lines = layout() }
                    native.save(); native.translate(72*unit, 260*unit + (availableHeight-lines.height).coerceAtLeast(0f)/2); lines.draw(native); native.restore()
                    if (watermark) text(strings.appName,72f,size.height/unit-60,24f)
                }
            }
            } // Preserve preview aspect ratio and maximum height.
            Row { Checkbox(watermark, { watermark = it }); Text(strings.library.watermark, Modifier.padding(top = 12.dp)) }
            Button(enabled = !exporting, onClick = {
                scope.launch {
                    exporting = true
                    try {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        val file = withContext(Dispatchers.IO) {
                            val directory = File(context.cacheDir, "exports/lyrics").apply { mkdirs() }
                            File.createTempFile("lyric_card_", ".png", directory).also { output ->
                                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                            }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newUri(context.contentResolver,strings.library.shareLyrics,uri) }
                        context.startActivity(Intent.createChooser(send,strings.library.shareLyrics))
                    } catch (error: Exception) {
                        com.akira.app.core.design.components.AkiraNotices.show(strings.library.error)
                    } finally { exporting = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(AkiraIcons.Lyrics,null); Spacer(Modifier.width(8.dp)); Text(strings.library.share) }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
