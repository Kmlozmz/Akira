package com.akira.app.core.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache

/** Only inspect audio files whose MediaStore duration is missing. Called on the scanner's IO dispatcher. */
internal object AudioDurationReader {
    private val durations = LruCache<String, Long>(128)

    fun read(context: Context, uri: String, indexedMs: Long, modifiedSeconds: Long): Long =
        resolveIndexedDuration(indexedMs) {
            val key = "$uri|$modifiedSeconds"
            durations.get(key) ?: readFile(context, Uri.parse(uri)).also { durations.put(key, it) }
        }

    private fun readFile(context: Context, uri: Uri): Long {
        val metadata = MediaMetadataRetriever()
        val retrieved = try {
            metadata.setDataSource(context, uri)
            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
        finally { runCatching { metadata.release() } }
        if (retrieved > 0L) return retrieved

        val extractor = MediaExtractor()
        val extracted = try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .filter { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                .mapNotNull { format ->
                    if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000L else null
                }.maxOrNull()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) { 0L }
        finally { runCatching { extractor.release() } }
        if (extracted > 0L) return extracted
        return runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching 0L
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input -> readIsoBmffDuration(input.channel) }
        }.getOrDefault(0L)
    }
}
