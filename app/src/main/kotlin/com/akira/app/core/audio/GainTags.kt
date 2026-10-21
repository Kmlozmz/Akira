package com.akira.app.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

data class GainTags(val trackDb: Float? = null, val albumDb: Float? = null) {
    fun factor(mode: String): Float = dbToFactor(when (mode) { "TRACK" -> trackDb; "ALBUM" -> albumDb; else -> null })
    companion object {
        fun dbToFactor(db: Float?): Float = if (db == null || !db.isFinite()) 1f else 10.0.pow(db.coerceAtMost(0f) / 20.0).toFloat().coerceIn(0f, 1f)
        fun from(tags: Map<String, String>): GainTags {
            val normalized = tags.mapKeys { it.key.uppercase(java.util.Locale.ROOT) }
            fun gain(which: String): Float? = normalized["REPLAYGAIN_${which}_GAIN"]?.trim()?.substringBefore(' ')?.toFloatOrNull()
                ?: normalized["R128_${which}_GAIN"]?.trim()?.toIntOrNull()?.let { it / 256f }
            return GainTags(gain("TRACK"), gain("ALBUM"))
        }

    }
}

/** Encabezados ID3 TXXX y comentarios Vorbis/Opus, con límites de tamaño. */
object GainTagParser {
    fun parse(data: ByteArray): GainTags = runCatching {
        val tags = mutableMapOf<String, String>()
        fun u(i: Int) = data[i].toInt() and 255
        fun be(i: Int) = (u(i) shl 24) or (u(i+1) shl 16) or (u(i+2) shl 8) or u(i+3)
        fun sync(i: Int) = (u(i) shl 21) or (u(i+1) shl 14) or (u(i+2) shl 7) or u(i+3)
        fun comments(packet: ByteArray, start: Int) {
            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(start)
            val vendor = buffer.int
            if (vendor < 0 || vendor > buffer.remaining()) return
            buffer.position(buffer.position() + vendor)
            val count = buffer.int
            if (count !in 0..10000) return
            repeat(count) {
                if (buffer.remaining() < 4) return
                val length = buffer.int
                if (length < 0 || length > buffer.remaining()) return
                val bytes = ByteArray(length); buffer.get(bytes)
                val value = bytes.toString(Charsets.UTF_8)
                if ('=' in value) tags[value.substringBefore('=')] = value.substringAfter('=')
            }
        }
        fun mp4(start: Int, end: Int, depth: Int) {
            if (depth > 8) return
            var offset = start
            while (offset + 8 <= end) {
                val size = be(offset)
                if (size < 8 || size > end - offset) break
                val type = data.copyOfRange(offset+4, offset+8).toString(Charsets.ISO_8859_1)
                val atomEnd = offset + size
                if (type == "----") {
                    var child = offset + 8
                    var name: String? = null
                    var value: String? = null
                    while (child + 8 <= atomEnd) {
                        val childSize = be(child)
                        if (childSize < 12 || childSize > atomEnd-child) break
                        val childType = data.copyOfRange(child+4, child+8).toString(Charsets.US_ASCII)
                        if (childType == "name") name = data.copyOfRange(child+12,child+childSize).toString(Charsets.UTF_8)
                        if (childType == "data" && childSize >= 16) value = data.copyOfRange(child+16,child+childSize).toString(Charsets.UTF_8)
                        child += childSize
                    }
                    if (name != null && value != null) tags[name] = value
                } else if (type in listOf("moov", "udta", "ilst", "meta")) {
                    mp4(offset+8 + if (type == "meta") 4 else 0, atomEnd, depth+1)
                }
                offset = atomEnd
            }
        }
        when {
            data.size >= 10 && data.copyOfRange(0,3).toString(Charsets.US_ASCII) == "ID3" -> {
                val version = u(3)
                val end = minOf(data.size, 10 + sync(6))
                var offset = 10
                while (offset + 10 <= end) {
                    val id = data.copyOfRange(offset, offset+4).toString(Charsets.US_ASCII)
                    val size = if (version == 4) sync(offset+4) else be(offset+4)
                    offset += 10
                    if (size <= 0 || size > end-offset) break
                    if (id == "TXXX") {
                        val encoding = u(offset)
                        val charset = when (encoding) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
                        val text = data.copyOfRange(offset+1,offset+size).toString(charset).split('\u0000')
                        if (text.size >= 2) tags[text[0].trim('\uFEFF')] = text[1].trim('\uFEFF')
                    }
                    offset += size
                }
            }
            data.size > 4 && data.copyOfRange(0,4).toString(Charsets.US_ASCII) == "fLaC" -> {
                var offset = 4
                while (offset + 4 <= data.size) {
                    val type = u(offset) and 127
                    val last = u(offset) and 128 != 0
                    val size = (u(offset+1) shl 16) or (u(offset+2) shl 8) or u(offset+3)
                    offset += 4
                    if (size > data.size-offset) break
                    if (type == 4) comments(data.copyOfRange(offset,offset+size),0)
                    offset += size
                    if (last) break
                }
            }
            data.size > 27 && data.copyOfRange(0,4).toString(Charsets.US_ASCII) == "OggS" -> {
                var offset = 0
                val packet = java.io.ByteArrayOutputStream()
                while (offset + 27 <= data.size) {
                    if (data.copyOfRange(offset,offset+4).toString(Charsets.US_ASCII) != "OggS") break
                    val segments = u(offset+26)
                    var payload = offset + 27 + segments
                    if (payload > data.size) break
                    for (segment in 0 until segments) {
                        val length = u(offset+27+segment)
                        if (payload + length > data.size) break
                        packet.write(data,payload,length); payload += length
                        if (length < 255) {
                            val bytes = packet.toByteArray(); packet.reset()
                            if (bytes.size >= 8 && bytes.copyOfRange(0,8).toString(Charsets.US_ASCII) == "OpusTags") comments(bytes,8)
                            else if (bytes.size >= 7 && bytes[0] == 3.toByte() && bytes.copyOfRange(1,7).toString(Charsets.US_ASCII) == "vorbis") comments(bytes,7)
                        }
                    }
                    offset = payload
                    if (tags.isNotEmpty()) break
                }
            }
            data.size >= 8 -> mp4(0, data.size, 0)
        }
        GainTags.from(tags)
    }.getOrDefault(GainTags())
}
