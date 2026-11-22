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
                when (initial.quality) {
                    AudioQuality.LOSSLESS -> initial.bitDepth != null
                    AudioQuality.DOLBY_ATMOS -> true
                    AudioQuality.COMPRESSED -> initial.bitrateBps != null
                })) return@withContext initial
        val file = song.path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
        val uri = Uri.parse(song.contentUri)
        if (file == null && uri.scheme !in setOf("content", "file")) return@withContext initial
        var rate = initial.sampleRateHz
        var bits = initial.bitDepth
        var bitrate = initial.bitrateBps
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
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (rate == null || (bits == null && initial.quality == AudioQuality.LOSSLESS))) ||
            (bitrate == null && initial.quality == AudioQuality.COMPRESSED)) {
            val retriever = MediaMetadataRetriever()
            try {
                if (file != null) retriever.setDataSource(file.absolutePath) else retriever.setDataSource(context, uri)
                if (rate == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull()?.takeIf { it > 0 }
                if (bits == null && initial.quality == AudioQuality.LOSSLESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull()?.takeIf { it in 4..64 }
                if (bitrate == null && initial.quality == AudioQuality.COMPRESSED) {
                    bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()?.takeIf { it >= 1_000 }
                }
            } catch (_: Exception) {
                // Keep known stream fields; an unavailable field is never replaced with a guessed value.
            } finally {
                runCatching { retriever.release() }
            }
        }
        initial.copy(sampleRateHz = rate, bitDepth = bits, bitrateBps = bitrate)
    }
