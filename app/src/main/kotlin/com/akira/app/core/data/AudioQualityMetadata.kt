package com.akira.app.core.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.akira.app.core.media.AudioQualityInfo
import com.akira.app.core.media.AudioQuality
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File

/** Read only technical metadata here; opening this control must not trigger a lyrics lookup. */
internal suspend fun readAudioQualityInfo(context: Context, song: Song, initial: AudioQualityInfo): AudioQualityInfo =
    withContext(Dispatchers.IO) {
        if (song.contentUri.startsWith("akira-youtube:") ||
            (initial.codec != "FLAC" && initial.sampleRateHz != null &&
                (initial.bitDepth != null || initial.quality == AudioQuality.DOLBY_ATMOS))) return@withContext initial
        val file = song.path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
        val uri = Uri.parse(song.contentUri)
        if (file == null && uri.scheme !in setOf("content", "file")) return@withContext initial
        var rate = initial.sampleRateHz
        var bits = initial.bitDepth
        if (initial.codec == "FLAC") {
            runCatching {
                val stream = file?.inputStream() ?: context.contentResolver.openInputStream(uri)
                stream?.use { input ->
                    val data = DataInputStream(input)
                    if (data.readInt() != 0x664C6143) return@use
                    val type = data.readUnsignedByte() and 0x7f
                    val length = (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
                    if (type != 0 || length != 34) return@use
                    val info = ByteArray(34)
                    data.readFully(info)
                    // STREAMINFO: sample rate (20 bits), channels (3), depth minus one (5).
                    val encodedRate = ((info[10].toInt() and 0xff) shl 12) or
                        ((info[11].toInt() and 0xff) shl 4) or ((info[12].toInt() and 0xff) ushr 4)
                    val encodedBits = (((info[12].toInt() and 1) shl 4) or
                        ((info[13].toInt() and 0xff) ushr 4)) + 1
                    if (encodedRate in 1..655350) rate = encodedRate
                    if (encodedBits in 4..32) bits = encodedBits
                }
            }
        }
        if ((rate == null || bits == null) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val retriever = MediaMetadataRetriever()
            try {
                if (file != null) retriever.setDataSource(file.absolutePath) else retriever.setDataSource(context, uri)
                if (rate == null) rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull()?.takeIf { it > 0 }
                if (bits == null) bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull()?.takeIf { it in 4..64 }
            } catch (_: Exception) {
                // Keep known stream fields; an unavailable field is never replaced with a guessed value.
            } finally {
                runCatching { retriever.release() }
            }
        }
        initial.copy(sampleRateHz = rate, bitDepth = bits)
    }
