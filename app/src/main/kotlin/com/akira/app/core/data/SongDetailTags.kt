package com.akira.app.core.data

import android.content.Context
import android.net.Uri
import com.akira.app.core.model.Song
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded reads of local FLAC Vorbis comments; artwork/audio blocks are skipped, never decoded. */
internal fun readFlacDetailTags(context: Context, song: Song): Map<String, String> {
    if (song.contentUri.startsWith("akira-youtube:")) return emptyMap()
    return runCatching {
        val file = song.path.takeIf(String::isNotBlank)?.let(::File)?.takeIf { it.isFile }
        val input = file?.inputStream() ?: context.contentResolver.openInputStream(Uri.parse(song.contentUri))
            ?: return@runCatching emptyMap()
        DataInputStream(input.buffered()).use { stream ->
            if (stream.readInt() != 0x664c6143) return@use emptyMap()
            var budget = 8 * 1024 * 1024
            repeat(128) {
                val header = stream.readUnsignedByte()
                val length = (stream.readUnsignedByte() shl 16) or (stream.readUnsignedByte() shl 8) or stream.readUnsignedByte()
                budget -= length + 4
                if (budget < 0) return@use emptyMap()
                if (header and 0x7f == 4 && length <= 1024 * 1024) {
                    val bytes = ByteArray(length); stream.readFully(bytes)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    fun string(): String {
                        val size = buffer.int
                        require(size in 0..buffer.remaining() && size <= 65536)
                        return ByteArray(size).also { buffer.get(it) }.toString(Charsets.UTF_8)
                    }
                    string() // vendor
                    val count = buffer.int
                    require(count in 0..4096)
                    val tags = linkedMapOf<String, String>()
                    repeat(count) {
                        val text = string(); val key = text.substringBefore('=').uppercase(java.util.Locale.ROOT)
                        val value = text.substringAfter('=', "").trim()
                        if (value.isNotEmpty()) tags[key] = listOfNotNull(tags[key], value).joinToString("; ")
                    }
                    return@use tags
                }
                var remaining = length
                while (remaining > 0) {
                    val skipped = stream.skipBytes(remaining)
                    if (skipped == 0) { stream.readByte(); remaining-- } else remaining -= skipped
                }
                if (header and 0x80 != 0) return@use emptyMap()
            }
            emptyMap()
        }
    }.getOrDefault(emptyMap())
}
