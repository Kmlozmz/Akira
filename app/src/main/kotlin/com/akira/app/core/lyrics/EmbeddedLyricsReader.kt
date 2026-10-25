package com.akira.app.core.lyrics

import java.io.InputStream
import java.nio.charset.Charset

internal object EmbeddedLyricsReader {
    private const val MAX_TAG_BYTES = 512_000
    private const val MAX_ATOMS = 4096

    fun readId3(input: InputStream): LoadedLyrics {
        val header = ByteArray(10)
        if (!input.readFully(header) || String(header, 0, 3, Charsets.US_ASCII) != "ID3") return LoadedLyrics()
        val version = header[3].toInt() and 255
        val flags = header[5].toInt() and 255
        val tagSize = header.syncSafe(6)
        if (version !in 2..4 || tagSize !in 1..MAX_TAG_BYTES || version == 2 && flags and 0x40 != 0)
            return LoadedLyrics()
        var body = ByteArray(tagSize)
        if (!input.readFully(body)) return LoadedLyrics()
        if (version < 4 && flags and 0x80 != 0) body = body.withoutUnsynchronization()
        var position = 0
        if (version >= 3 && flags and 0x40 != 0) {
            if (body.size < 4) return LoadedLyrics()
            val extended = if (version == 4) body.syncSafe(0).toLong() else body.uint32(0) + 4
            if (extended !in 4L..body.size.toLong()) return LoadedLyrics()
            position = extended.toInt()
        }
        var fallback = LoadedLyrics()
        val headerSize = if (version == 2) 6 else 10
        while (position <= body.size - headerSize && body[position] != 0.toByte()) {
            val idLength = if (version == 2) 3 else 4
            val id = String(body, position, idLength, Charsets.ISO_8859_1)
            val length = when (version) {
                2 -> body.uint24(position + 3).toLong()
                4 -> body.syncSafe(position + 4).toLong()
                else -> body.uint32(position + 4)
            }
            val frameFlags = if (version == 2) 0 else body[position + 9].toInt() and 255
            position += headerSize
            if (length <= 0 || length > body.size - position) break
            val end = position + length.toInt()
            val unsupported = if (version == 3) frameFlags and 0xC0 != 0 else frameFlags and 0x0C != 0
            if (!unsupported && id in setOf("ULT", "USLT", "SLT", "SYLT")) {
                val prefix = when (version) {
                    3 -> if (frameFlags and 0x20 != 0) 1 else 0
                    4 -> (if (frameFlags and 0x40 != 0) 1 else 0) + (if (frameFlags and 1 != 0) 4 else 0)
                    else -> 0
                }
                if (prefix < length) {
                    var frame = body.copyOfRange(position + prefix, end)
                    if (version == 4 && (frameFlags and 2 != 0 || flags and 0x80 != 0)) frame = frame.withoutUnsynchronization()
                    val parsed = if (id == "ULT" || id == "USLT") readUnsyncedFrame(frame) else readSyncedFrame(frame)
                    if (parsed.synced.isNotEmpty()) return parsed
                    if (!fallback.hasLyrics && parsed.hasLyrics) fallback = parsed
                }
            }
            position = end
        }
        return fallback
    }

    private fun readUnsyncedFrame(frame: ByteArray): LoadedLyrics {
        if (frame.size < 5) return LoadedLyrics()
        val encoding = frame[0].toInt() and 255
        if (encoding !in 0..3) return LoadedLyrics()
        val start = frame.afterTerminator(4, encoding) ?: return LoadedLyrics()
        return LyricsRepository.parseLrcText(frame.decodeText(start, frame.size, encoding, 4).trimEnd('\u0000'))
    }

    private fun readSyncedFrame(frame: ByteArray): LoadedLyrics {
        if (frame.size < 7 || frame[4] != 2.toByte()) return LoadedLyrics()
        val encoding = frame[0].toInt() and 255
        if (encoding !in 0..3) return LoadedLyrics()
        var position = frame.afterTerminator(6, encoding) ?: return LoadedLyrics()
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        val lines = mutableListOf<LyricLine>()
        while (position < frame.size) {
            val afterText = frame.afterTerminator(position, encoding) ?: break
            if (frame.size - afterText < 4) break
            val text = frame.decodeText(position, afterText - width, encoding, 6).trim()
            val time = frame.uint32(afterText)
            if (text.isNotEmpty()) lines += LyricLine(time, text)
            position = afterText + 4
        }
        return LoadedLyrics(synced = lines.sortedBy { it.timeMs })
    }

    private fun ByteArray.afterTerminator(start: Int, encoding: Int): Int? {
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        var position = start
        while (position <= size - width) {
            if (this[position] == 0.toByte() && (width == 1 || this[position + 1] == 0.toByte()))
                return position + width
            position += width
        }
        return null
    }

    private fun ByteArray.decodeText(start: Int, end: Int, encoding: Int, descriptor: Int): String {
        val charset: Charset = when (encoding) {
            1 -> when {
                end - start >= 2 && (this[start] == 0xFF.toByte() && this[start + 1] == 0xFE.toByte() ||
                    this[start] == 0xFE.toByte() && this[start + 1] == 0xFF.toByte()) -> Charsets.UTF_16
                size - descriptor >= 2 && this[descriptor] == 0xFF.toByte() && this[descriptor + 1] == 0xFE.toByte() -> Charsets.UTF_16LE
                else -> Charsets.UTF_16BE
            }
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(this, start, end - start, charset)
    }

    fun readMp4(input: InputStream): LoadedLyrics {
        var result = LoadedLyrics()
        var atoms = 0
        fun walk(source: BoundedInput, depth: Int, lyrics: Boolean = false) {
            val header = ByteArray(8)
            while (!result.synced.any() && depth <= 8 && atoms++ < MAX_ATOMS && source.remaining >= 8) {
                if (!source.readFully(header)) return
                var length = header.uint32(0)
                var headerLength = 8L
                val type = String(header, 4, 4, Charsets.ISO_8859_1)
                if (length == 1L) {
                    val extended = ByteArray(8)
                    if (!source.readFully(extended)) return
                    if (extended[0] < 0) return
                    length = (extended.uint32(0) shl 32) or extended.uint32(4)
                    headerLength = 16
                } else if (length == 0L) length = source.remaining + headerLength
                if (length < headerLength || length - headerLength > source.remaining) return
                val payload = BoundedInput(source, length - headerLength)
                when {
                    type in setOf("moov", "udta", "ilst", "©lyr") -> walk(payload, depth + 1, lyrics || type == "©lyr")
                    type == "meta" -> if (payload.skipFully(4)) walk(payload, depth + 1, lyrics)
                    type == "data" && lyrics && payload.remaining in 8L..MAX_TAG_BYTES.toLong() -> {
                        val data = ByteArray(payload.remaining.toInt())
                        if (!payload.readFully(data)) return
                        val charset = if (data.uint32(0) and 0xFFFFFF == 2L) Charsets.UTF_16BE else Charsets.UTF_8
                        val parsed = LyricsRepository.parseLrcText(String(data, 8, data.size - 8, charset).trimEnd('\u0000'))
                        if (!result.hasLyrics || parsed.synced.isNotEmpty()) result = parsed
                    }
                }
                if (result.synced.isNotEmpty() || !payload.skipFully(payload.remaining)) return
            }
        }
        walk(BoundedInput(input, Long.MAX_VALUE), 0)
        return result
    }

    private class BoundedInput(private val source: InputStream, var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining == 0L) return -1
            return source.read().also { if (it >= 0) remaining-- }
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining == 0L) return -1
            return source.read(bytes, offset, minOf(length.toLong(), remaining).toInt()).also { if (it > 0) remaining -= it }
        }
        override fun skip(count: Long): Long = source.skip(minOf(count, remaining)).also { remaining -= it }
    }

    private fun InputStream.readFully(bytes: ByteArray): Boolean {
        var position = 0
        while (position < bytes.size) {
            val read = read(bytes, position, bytes.size - position)
            if (read < 0) return false
            if (read == 0) {
                val byte = read()
                if (byte < 0) return false
                bytes[position++] = byte.toByte()
            } else position += read
        }
        return true
    }

    private fun InputStream.skipFully(count: Long): Boolean {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped
            else {
                val read = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (read < 0) return false
                if (read == 0) { if (read() < 0) return false; remaining-- } else remaining -= read
            }
        }
        return true
    }

    private fun ByteArray.uint24(offset: Int) = ((this[offset].toInt() and 255) shl 16) or
        ((this[offset + 1].toInt() and 255) shl 8) or (this[offset + 2].toInt() and 255)
    private fun ByteArray.uint32(offset: Int): Long = (uint24(offset).toLong() shl 8) or (this[offset + 3].toLong() and 255)
    private fun ByteArray.syncSafe(offset: Int) = ((this[offset].toInt() and 127) shl 21) or
        ((this[offset + 1].toInt() and 127) shl 14) or ((this[offset + 2].toInt() and 127) shl 7) or
        (this[offset + 3].toInt() and 127)
    private fun ByteArray.withoutUnsynchronization(): ByteArray {
        val decoded = ByteArray(size)
        var count = 0
        var position = 0
        while (position < size) {
            val byte = this[position++]
            decoded[count++] = byte
            if (byte == 0xFF.toByte() && position < size && this[position] == 0.toByte()) position++
        }
        return decoded.copyOf(count)
    }
}
