package com.akira.app.core.data

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** Timing-only fallback for fragmented MP4, including FLAC-in-M4A unsupported by Android's indexer.
 * Skips mdat payloads; validates atom/sample bounds and never decodes or reads the audio samples.
 */
internal fun readIsoBmffDuration(channel: SeekableByteChannel): Long =
    runCatching { IsoAudioTimeline(channel).durationMs() }.getOrDefault(0L)

private class IsoAudioTimeline(private val channel: SeekableByteChannel) {
    private data class Atom(val type: String, val body: Long, val end: Long)
    private data class Track(val scale: Long, var end: Long, var defaultDuration: Long = 0L)
    private val size = channel.size()
    private val tracks = mutableMapOf<Long, Track>()
    private var atomsRead = 0

    private fun bytes(position: Long, count: Int, end: Long): ByteBuffer {
        require(position >= 0L && position <= end && count.toLong() <= end - position)
        val data = ByteBuffer.allocate(count)
        channel.position(position)
        while (data.hasRemaining()) if (channel.read(data) <= 0) throw IOException("Incomplete MP4 metadata")
        data.flip()
        return data
    }
    private fun uint(position: Long, end: Long): Long = bytes(position, 4, end).int.toLong() and 0xffffffffL
    private fun ulong(position: Long, end: Long): Long = bytes(position, 8, end).long.also { require(it >= 0L) }
    private fun children(start: Long, end: Long): List<Atom> {
        val result = mutableListOf<Atom>()
        var offset = start
        while (offset < end) {
            require(++atomsRead <= 20_000 && end - offset >= 8L)
            val header = bytes(offset, 8, end)
            var length = header.int.toLong() and 0xffffffffL
            val type = ByteArray(4).also(header::get).toString(Charsets.ISO_8859_1)
            var headerSize = 8L
            if (length == 1L) { length = ulong(offset + 8, end); headerSize = 16L }
            if (length == 0L) length = end - offset
            require(length >= headerSize && length <= end - offset)
            result.add(Atom(type, offset + headerSize, offset + length))
            offset += length
        }
        return result
    }
    private fun children(atom: Atom): List<Atom> = children(atom.body, atom.end)

    fun durationMs(): Long {
        val top = children(0, size)
        require(top.any { it.type == "ftyp" })
        top.filter { it.type == "moov" }.forEach { movie ->
            val boxes = children(movie)
            boxes.filter { it.type == "trak" }.forEach trackLoop@ { track ->
                val trackBoxes = children(track)
                val header = trackBoxes.firstOrNull { it.type == "tkhd" } ?: return@trackLoop
                val version = bytes(header.body, 1, header.end).get().toInt()
                require(version in 0..1)
                val id = uint(header.body + if (version == 1) 20 else 12, header.end)
                val media = trackBoxes.firstOrNull { it.type == "mdia" } ?: return@trackLoop
                val mediaBoxes = children(media)
                val handler = mediaBoxes.firstOrNull { it.type == "hdlr" } ?: return@trackLoop
                val kind = ByteArray(4).also { bytes(handler.body + 8, 4, handler.end).get(it) }.toString(Charsets.ISO_8859_1)
                if (kind != "soun") return@trackLoop
                val timing = mediaBoxes.firstOrNull { it.type == "mdhd" } ?: return@trackLoop
                val timingVersion = bytes(timing.body, 1, timing.end).get().toInt()
                require(timingVersion in 0..1)
                val scale = uint(timing.body + if (timingVersion == 1) 20 else 12, timing.end)
                val ticks = if (timingVersion == 1) ulong(timing.body + 24, timing.end)
                    else uint(timing.body + 16, timing.end).takeUnless { it == 0xffffffffL } ?: 0L
                if (scale > 0L) tracks[id] = Track(scale, ticks)
            }
            boxes.filter { it.type == "mvex" }.flatMap(::children).filter { it.type == "trex" }.forEach { defaults ->
                tracks[uint(defaults.body + 4, defaults.end)]?.defaultDuration = uint(defaults.body + 12, defaults.end)
            }
        }
        top.filter { it.type == "moof" }.flatMap(::children).filter { it.type == "traf" }.forEach(::fragment)
        return tracks.values.maxOfOrNull { track ->
            Math.addExact(Math.multiplyExact(track.end / track.scale, 1000L),
                Math.multiplyExact(track.end % track.scale, 1000L) / track.scale)
        } ?: 0L
    }

    private fun fragment(fragment: Atom) {
        val boxes = children(fragment)
        val header = boxes.firstOrNull { it.type == "tfhd" } ?: return
        val flags = uint(header.body, header.end).toInt() and 0xffffff
        val track = tracks[uint(header.body + 4, header.end)] ?: return
        var offset = header.body + 8
        if (flags and 1 != 0) offset += 8
        if (flags and 2 != 0) offset += 4
        val defaultDuration = if (flags and 8 != 0) uint(offset, header.end) else track.defaultDuration
        val base = boxes.firstOrNull { it.type == "tfdt" }?.let { timing ->
            when (bytes(timing.body, 1, timing.end).get().toInt()) {
                0 -> uint(timing.body + 4, timing.end)
                1 -> ulong(timing.body + 4, timing.end)
                else -> throw IOException("Unsupported decode-time version")
            }
        } ?: track.end
        var total = 0L
        boxes.filter { it.type == "trun" }.forEach { run ->
            val runFlags = uint(run.body, run.end).toInt() and 0xffffff
            val count = uint(run.body + 4, run.end)
            var sample = run.body + 8
            if (runFlags and 1 != 0) sample += 4
            if (runFlags and 4 != 0) sample += 4
            val stride = listOf(0x100, 0x200, 0x400, 0x800).count { runFlags and it != 0 } * 4
            require(sample <= run.end && count <= 10_000_000L && count * stride <= run.end - sample)
            if (runFlags and 0x100 == 0) {
                require(count == 0L || defaultDuration > 0L)
                total = Math.addExact(total, Math.multiplyExact(count, defaultDuration))
            } else repeat(count.toInt()) {
                total = Math.addExact(total, uint(sample, run.end))
                sample += stride
            }
        }
        track.end = maxOf(track.end, Math.addExact(base, total))
    }
}
