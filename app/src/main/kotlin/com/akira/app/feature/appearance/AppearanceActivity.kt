package com.akira.app.feature.appearance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

private object AppearanceEngine {
    private var engine: FlutterEngine? = null
    fun get(context: Context): FlutterEngine = engine ?: FlutterEngine(context.applicationContext).also {
        engine = it
    }
}

class AppearanceActivity : FlutterActivity(), AppearanceHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as AkiraApplication).preferencesRepository
    private var fontRequest: ((Result<FontData?>) -> Unit)? = null
    private var client: AppearanceClient? = null
    private var attached = false

    override fun provideFlutterEngine(context: Context): FlutterEngine = AppearanceEngine.get(context)
    override fun shouldDestroyEngineWithHost(): Boolean = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        AppearanceHost.setUp(messenger, this)
        client = AppearanceClient(messenger)
        if (!flutterEngine.dartExecutor.isExecutingDart) {
            flutterEngine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        } else {
            scope.launch { runCatching { client?.refresh() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attached = true
        scope.launch {
            repository.userPreferences.collect { prefs ->
                runCatching { client?.preferencesChanged(snapshot(prefs)) }
            }
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        AppearanceHost.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onDestroy() {
        attached = false
        fontRequest?.invoke(Result.success(null))
        fontRequest = null
        scope.cancel()
        super.onDestroy()
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
        locale = if (p.language == "SYSTEM") resources.configuration.locales[0].toLanguageTag() else p.language.lowercase(Locale.ROOT),
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
        InteractionPreferences.setReducedMotion(this@AppearanceActivity, preferences.reduceMotion)
        snapshot(repository.userPreferences.first())
    }

    override fun preview(callback: (Result<List<MusicPreview>>) -> Unit) = asyncResult(callback) {
        val scanner = MediaStoreScanner(this@AppearanceActivity)
        if (!scanner.hasStoragePermission()) return@asyncResult emptyList()
        val prefs = repository.userPreferences.first()
        val cached = LibraryCache(this@AppearanceActivity).read()
        val songs = if (cached.isNotEmpty()) cached else scanner.scanLocalSongs(
            prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds)
        withContext(Dispatchers.IO) {
            songs.filter { passesLibraryFilters(it, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) }
                .distinctBy { it.albumArtUri ?: it.album }.take(3).map { song -> MusicPreview(song.id, song.title, song.artist,
                    song.album, song.durationMs, artwork(song)) }
        }
    }

    private fun artwork(song: com.akira.app.core.model.Song): ByteArray? {
        val uri = song.albumArtUri?.let(Uri::parse)
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
                retriever.setDataSource(this, Uri.parse(song.contentUri))
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
        return File(File(filesDir, "appearance-fonts"), "$id.ttf")
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
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream"))
            }, 1401)
        } catch (_: Exception) {
            fontRequest = null
            callback(Result.failure(FlutterError("font-picker", "No font picker is available", null)))
        }
    }

    @Deprecated("Activity result bridge retained for FlutterActivity ownership")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1401) return
        val callback = fontRequest ?: return
        fontRequest = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) { callback(Result.success(null)); return }
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
            val temporary = File(cacheDir, "appearance-font-import.tmp")
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

    override fun close() { if (attached) finish() }
}
