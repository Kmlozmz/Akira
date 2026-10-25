// SPDX-License-Identifier: GPL-3.0-only
package com.akira.app.feature.collection
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalDetailSessions = staticCompositionLocalOf<androidx.compose.runtime.snapshots.SnapshotStateMap<Any, Boolean>?> { null }

val LocalDetailInk = staticCompositionLocalOf { Color.White }
val LocalDetailAccentColor = staticCompositionLocalOf { mutableStateOf<Color?>(null) }

/** Palette work stays off the UI thread and is cancelled on artwork/theme changes. */
@Composable internal fun rememberCoverThemeColor(uri: String?): State<Color?> {
    val context = LocalContext.current
    val dark = true // Immersive details keep the artwork palette in both app modes.
    return produceState<Color?>(null, uri, dark) {
        value = null
        if (uri == null) return@produceState
        value = try { withContext(Dispatchers.IO) {
            val drawable = (context.imageLoader.execute(ImageRequest.Builder(context).data(uri).size(100).allowHardware(false).build()) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
            val palette = Palette.from(bitmap).maximumColorCount(8).generate()
            val raw = Color(palette.getDarkVibrantColor(palette.getDominantColor(android.graphics.Color.DKGRAY)))
            if (dark) lerp(raw, Color.Black, .42f) else lerp(raw, Color.White, .87f)
        } } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }
}
