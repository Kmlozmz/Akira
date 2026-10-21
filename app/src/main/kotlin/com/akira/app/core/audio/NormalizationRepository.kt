package com.akira.app.core.audio

import android.content.Context
import android.net.Uri
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class NormalizationRepository(private val context: Context) {
    private val cache = mutableMapOf<String, GainTags>()
    suspend fun read(song: Song): GainTags = withContext(Dispatchers.IO) {
        val modified = if (song.path.isNotBlank()) File(song.path).lastModified() else song.dateModifiedSec * 1000
        val key = "${song.path.ifBlank { song.contentUri }}|$modified"
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(context.cacheDir, "gain_tags/$digest.json")
        val saved = runCatching { JSONObject(file.readText()).let { GainTags(if (it.isNull("track")) null else it.getDouble("track").toFloat(), if (it.isNull("album")) null else it.getDouble("album").toFloat()) } }.getOrNull()
        val tags = saved ?: runCatching {
            if (song.mimeType.contains("mp4") || song.path.endsWith(".m4a",true)) {
                context.contentResolver.openFileDescriptor(Uri.parse(song.contentUri), "r")?.use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use { input ->
                        val channel = input.channel
                        val header = java.nio.ByteBuffer.allocate(8)
                        var position = 0L
                        var result = GainTags()
                        while (position + 8 <= channel.size()) {
                            header.clear(); channel.position(position)
                            while (header.hasRemaining() && channel.read(header) >= 0) { }
                            if (header.hasRemaining()) break
                            header.flip()
                            val size = header.int.toLong() and 0xffffffffL
                            val type = ByteArray(4); header.get(type)
                            if (size < 8 || size > channel.size()-position) break
                            if (type.toString(Charsets.US_ASCII) == "moov" && size <= 2 * 1024 * 1024) {
                                channel.position(position)
                                val atom = java.nio.ByteBuffer.allocate(size.toInt())
                                while (atom.hasRemaining() && channel.read(atom) >= 0) { }
                                result = GainTagParser.parse(atom.array()); break
                            }
                            position += size
                        }
                        result
                    }
                } ?: GainTags()
            } else context.contentResolver.openInputStream(Uri.parse(song.contentUri))?.use { stream ->
                GainTagParser.parse(stream.readBytesBounded(2 * 1024 * 1024))
            } ?: GainTags()
        }.getOrDefault(GainTags())
        synchronized(cache) { cache[key] = tags }
        if (saved == null) runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSONObject().put("track", tags.trackDb ?: JSONObject.NULL).put("album", tags.albumDb ?: JSONObject.NULL).toString())
        }
        tags
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val n = read(buffer,0,minOf(buffer.size,limit-output.size()))
        if (n <= 0) break
        output.write(buffer,0,n)
    }
    return output.toByteArray()
}
