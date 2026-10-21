package com.akira.app.core.lyrics

import java.util.Base64
import java.util.Locale
import java.util.zip.InflaterInputStream

/** KuGou's KRC wire format, converted to our existing Enhanced LRC cache format. */
internal object KrcLyrics {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val key = intArrayOf(64,71,97,119,94,50,116,71,81,54,49,45,206,210,110,105)
    private val line = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val word = Regex("<(\\d+),(\\d+),(\\d+)>([^<]*)")

    fun decode(encoded: String): String {
        require(encoded.length <= MAX_BYTES)
        val bytes=Base64.getDecoder().decode(encoded)
        require(bytes.size>4 && bytes.copyOfRange(0,4).contentEquals(byteArrayOf(107,114,99,49)))
        val compressed=ByteArray(bytes.size-4) {i -> (bytes[i+4].toInt() xor key[i%key.size]).toByte()}
        val raw=InflaterInputStream(compressed.inputStream()).use {input ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                val count=input.read(buffer)
                if(count<0)break
                require(output.size()+count<=MAX_BYTES)
                output.write(buffer,0,count)
            }
            output.toString("UTF-8")
        }
        return toEnhancedLrc(raw)
    }

    internal fun timestamp(ms: Long): String = String.format(Locale.ROOT,"%02d:%02d.%03d",ms/60000,(ms/1000)%60,ms%1000)

    internal fun toEnhancedLrc(raw: String): String = buildString {
        for(rawLine in raw.lineSequence()) {
            // Translation/romanization metadata is not mixed into the original lyric.
            if(rawLine.startsWith("[offset:",ignoreCase=true)) {appendLine(rawLine);continue}
            val match=line.matchEntire(rawLine) ?: continue
            val start=match.groupValues[1].toLong()
            val length=match.groupValues[2].toLong()
            require(start in 0..86_400_000 && length in 0..86_400_000)
            val words=word.findAll(match.groupValues[3]).toList()
            if(words.isEmpty())continue
            append('[').append(timestamp(start)).append(']')
            var previous=-1L
            for(part in words) {
                val relative=part.groupValues[1].toLong()
                val duration=part.groupValues[2].toLong()
                require(relative>=previous && relative<=length && duration<=length-relative)
                previous=relative
                append('<').append(timestamp(start+relative)).append('>').append(part.groupValues[4])
            }
            appendLine()
        }
    }
}
