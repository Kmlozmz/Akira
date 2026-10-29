package com.akira.app.core.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.akira.app.core.model.Song

/** Resolve an external Open with URI as a standalone playable Song without adding it to the library. Read file metadata directly and retain URI access when the provider allows it. */
object ExternalSongLoader {

    private const val TAG = "ExternalSongLoader"

    /** Use the filename as the title when embedded metadata is missing. */
    private fun fallbackTitle(uri: Uri): String {
        val last = uri.lastPathSegment ?: return "Audio"
        val base = last.substringAfterLast('/')
        val name = if (base.isBlank()) last else base
        return name.substringBeforeLast('.', name).replace('_', ' ').trim().ifBlank { "Audio" }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Try to retain URI permission so the file remains readable after process restart. */
    fun persistPermission(context: Context, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Providers may omit persistable access; playback can still proceed.
            Log.w(TAG, "no se pudo persistir el permiso de $uri")
        }
    }

    /** Return null for missing, unreadable or undecodable audio instead of crashing. */
    fun load(context: Context, uri: Uri): Song? {
        if (uri.scheme !in setOf("content", "file")) return null
        return try {
            loadReadable(context, uri)
        } catch (error: Exception) {
            com.akira.app.core.logging.AkiraLog.w(TAG, "External audio could not be read", error)
            null
        }
    }

    private fun loadReadable(context: Context, uri: Uri): Song? {
        val resolver = context.contentResolver
        val mmr = MediaMetadataRetriever()
        var durationMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        try {
            mmr.setDataSource(context, uri)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            // Some extractors expose artwork only when a frame is requested.
            mmr.embeddedPicture
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo leer metadatos de $uri", e)
            mmr.release()
            return null
        }
        mmr.release()

        // Reject missing duration because the decoder cannot open a playable stream.
        if (durationMs <= 0L) {
            Log.w(TAG, "duracion invalida en $uri; se descarta")
            return null
        }

        val name = displayName(resolver, uri)
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: fallbackTitle(name?.let { Uri.parse(it) } ?: uri)
        val resolvedArtist = artist?.takeIf { it.isNotBlank() } ?: "Artista desconocido"
        val resolvedAlbum = album?.takeIf { it.isNotBlank() } ?: "Single"

        val mime = resolver.getType(uri) ?: "audio/*"
        val format = when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("wav", true) -> "WAV"
            mime.contains("ogg", true) -> "OGG"
            mime.contains("aac", true) -> "AAC"
            mime.contains("mp4", true) || mime.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }

        return Song(
            // Prefix external IDs to avoid collisions with existing MediaStore entries.
            id = "external:$uri",
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum,
            durationMs = durationMs,
            contentUri = uri.toString(),
            format = format,
            mimeType = mime,
            // Leave the disk path empty when it cannot be resolved reliably, so scanning and lyrics lookup do not use a fabricated path.
            path = ""
        )
    }
}
