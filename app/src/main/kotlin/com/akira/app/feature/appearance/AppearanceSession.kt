package com.akira.app.feature.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.util.Size
import com.akira.app.AkiraApplication
import com.akira.app.core.data.passesLibraryFilters
import com.akira.app.core.data.MediaStoreScanner
import com.akira.app.core.data.library.LibraryCache
import com.akira.app.core.datastore.AppearanceEdit
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.design.motion.InteractionPreferences
import com.akira.app.core.model.Song
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.renderer.FlutterUiDisplayListener
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

internal enum class AppearanceRoute { ROOT, PAGE, MODAL }

/** Owns the Flutter content for one studio entry. Navigation chrome stays in Compose. */
internal class AppearanceSession(
    private val context: Context,
    val engine: FlutterEngine,
    private val previewSongs: List<Song>,
    private val pickFontDocument: () -> Unit,
    private val onClose: () -> Unit,
    private val onReady: () -> Unit,
    onRoute: (AppearanceRoute) -> Unit,
) : AppearanceHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (context.applicationContext as AkiraApplication).preferencesRepository
    private var fontRequest: ((Result<FontData?>) -> Unit)? = null
    private val messenger = engine.dartExecutor.binaryMessenger
    private val channel = MethodChannel(messenger, "akira/appearance/navigation")
    private var pageReady = false
    private var displaying = false
    private val displayListener = object : FlutterUiDisplayListener {
        override fun onFlutterUiDisplayed() {
            displaying = true
            if (pageReady) onReady()
        }

        override fun onFlutterUiNoLongerDisplayed() { displaying = false }
    }

    init {
        AppearanceHost.setUp(messenger, this)
        engine.renderer.addIsDisplayingFlutterUiListener(displayListener)
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "route" -> {
                    val route = when (call.arguments) {
                        "modal" -> AppearanceRoute.MODAL
                        "page" -> AppearanceRoute.PAGE
                        else -> AppearanceRoute.ROOT
                    }
                    onRoute(route)
                    result.success(null)
                }
                "ready" -> {
                    pageReady = true
                    if (displaying) onReady()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        val client = AppearanceClient(messenger)
        scope.launch {
            repository.userPreferences.map(::snapshot).distinctUntilChanged().collect { preferences ->
                runCatching { client.preferencesChanged(preferences) }
            }
        }
    }

    fun pop() = channel.invokeMethod("pop", null)

    fun dispose() {
        engine.renderer.removeIsDisplayingFlutterUiListener(displayListener)
        AppearanceHost.setUp(messenger, null)
        channel.setMethodCallHandler(null)
        fontRequest?.invoke(Result.success(null))
        fontRequest = null
        scope.cancel()
        engine.destroy()
    }

    private fun snapshot(p: UserPreferences) = VisualPreferences(
        revision = p.appearanceRevision,
        theme = p.themeMode,
        accentArgb = runCatching { android.graphics.Color.parseColor(p.accentColor).toLong() and 0xffffffffL }
            .getOrDefault(0xfffa243cL),
        glass = p.liquidGlassEnabled,
        font = p.appearanceFont,
        fontName = p.appearanceFontName,
        sizeIndex = p.appearanceSizeIndex.toLong(),
        reduceTransparency = p.appearanceReduceTransparency,
        reduceMotion = InteractionPreferences.reducedMotion,
        fullscreenArtwork = p.fullscreenPlayerArtwork,
        animatedArtwork = p.animatedPlayerArtwork,
        glassOpacity = p.liquidGlassOpacity.toDouble(),
        diffusion = p.liquidGlassDiffusion.toDouble(),
        dockDiffusion = p.dockLiquidGlassEnabled,
        locale = if (p.language == "SYSTEM") context.resources.configuration.locales[0].toLanguageTag() else p.language.lowercase(Locale.ROOT),
        fontId = p.appearanceFontId.takeIf { it.isNotEmpty() }
    )

    private fun <T> asyncResult(callback: (Result<T>) -> Unit, block: suspend () -> T) {
        scope.launch {
            val result = try { Result.success(block()) }
            catch (cancelled: CancellationException) { Result.failure(FlutterError("cancelled", "Appearance closed", null)) }
            catch (error: IllegalStateException) {
                Result.failure(FlutterError(if (error.message == "stale-preferences") "stale-preferences" else "appearance-error", "Appearance changed or unavailable", null))
            }
            catch (_: Exception) { Result.failure(FlutterError("appearance-error", "Appearance request failed", null)) }
            callback(result)
        }
    }

    override fun read(callback: (Result<VisualPreferences>) -> Unit) = asyncResult(callback) {
        snapshot(repository.userPreferences.first())
    }

    override fun apply(preferences: VisualPreferences, callback: (Result<VisualPreferences>) -> Unit) = asyncResult(callback) {
        require(preferences.sizeIndex in 0L..6L)
        if (preferences.font == "custom") check(fontFile(preferences.fontId).exists()) { "font-unavailable" }
        repository.applyAppearance(AppearanceEdit(
            preferences.revision, preferences.theme, preferences.accentArgb.toInt(), preferences.glass,
            preferences.font, preferences.fontName, preferences.sizeIndex.toInt(), preferences.reduceTransparency,
            preferences.fullscreenArtwork, preferences.animatedArtwork,
            preferences.glassOpacity.toFloat(), preferences.diffusion.toFloat(), preferences.dockDiffusion, preferences.fontId
        ))
        InteractionPreferences.setReducedMotion(context, preferences.reduceMotion)
        snapshot(repository.userPreferences.first())
    }

    override fun preview(callback: (Result<List<MusicPreview>>) -> Unit) = asyncResult(callback) {
        val scanner = MediaStoreScanner(context)
        if (!scanner.hasStoragePermission()) return@asyncResult emptyList()
        val prefs = repository.userPreferences.first()
        val songs = previewSongs.ifEmpty { LibraryCache(context).read() }
        withContext(Dispatchers.IO) {
            songs.asSequence().filter { passesLibraryFilters(it, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) }
                .distinctBy { it.albumArtUri ?: it.album }.take(3).map { song -> MusicPreview(song.id, song.title, song.artist,
                    song.album, song.durationMs, artwork(song)) }.toList()
        }
    }

    private fun artwork(song: com.akira.app.core.model.Song): ByteArray? {
        val uri = song.albumArtUri?.let(Uri::parse)
        val contentResolver = context.contentResolver
        val thumbnail = if (uri != null && Build.VERSION.SDK_INT >= 29) runCatching {
            contentResolver.loadThumbnail(uri, Size(384, 384), null)
        }.getOrNull() else null
        val bitmap = thumbnail ?: uri?.let { imageUri -> runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(imageUri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            val options = android.graphics.BitmapFactory.Options().apply {
                var sample = 1
                while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
                inSampleSize = sample
            }
            contentResolver.openInputStream(imageUri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() } ?: runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            val bytes = try {
                retriever.setDataSource(context, Uri.parse(song.contentUri))
                retriever.embeddedPicture
            } finally { retriever.release() } ?: return@runCatching null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = android.graphics.BitmapFactory.Options().apply {
                var sample = 1
                while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
                inSampleSize = sample
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return null
        return try {
            ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it); it.toByteArray() }
                .takeIf { it.size <= 512 * 1024 }
        } finally { bitmap.recycle() }
    }

    private fun fontFile(id: String?): File {
        require(id != null && Regex("[0-9a-f]{64}").matches(id))
        return File(File(context.filesDir, "appearance-fonts"), "$id.ttf")
    }

    override fun readFont(callback: (Result<FontData?>) -> Unit) = asyncResult(callback) {
        val prefs = repository.userPreferences.first()
        withContext(Dispatchers.IO) {
            val id = prefs.appearanceFontId.takeIf { it.isNotEmpty() }
            if (id == null || !fontFile(id).exists()) null else FontData(prefs.appearanceFontName,
                AtomicFile(fontFile(id)).openRead().use { it.readBytes() }, id)
        }
    }

    override fun pickFont(callback: (Result<FontData?>) -> Unit) {
        if (fontRequest != null) { callback(Result.failure(FlutterError("busy", "A font picker is already open", null))); return }
        fontRequest = callback
        try {
            pickFontDocument()
        } catch (_: Exception) {
            fontRequest = null
            callback(Result.failure(FlutterError("font-picker", "No font picker is available", null)))
        }
    }

    fun onFontResult(uri: Uri?) {
        val callback = fontRequest ?: return
        fontRequest = null
        if (uri == null) {
            callback(Result.success(null))
            return
        }
        val contentResolver = context.contentResolver
        asyncResult(callback) { withContext(Dispatchers.IO) {
            val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 12 * 1024 * 1024) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("font-unavailable")
            require(bytes.size in 12..12 * 1024 * 1024)
            val tag = bytes.take(4).map { it.toInt() and 255 }
            require(tag == listOf(0, 1, 0, 0) || tag == listOf(79, 84, 84, 79))
            val id = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val temporary = File(context.cacheDir, "appearance-font-import.tmp")
            try {
                temporary.writeBytes(bytes)
                Typeface.createFromFile(temporary)
                fontFile(id).parentFile?.mkdirs()
                val atomic = AtomicFile(fontFile(id))
                val stream = atomic.startWrite()
                try { stream.write(bytes); atomic.finishWrite(stream) }
                catch (error: Exception) { atomic.failWrite(stream); throw error }
            } finally { temporary.delete() }
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0).take(120) else null
            } ?: "Custom"
            FontData(name, bytes, id)
        } }
    }

    override fun close() { onClose() }
}
