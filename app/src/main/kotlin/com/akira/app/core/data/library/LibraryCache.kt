package com.akira.app.core.data.library

import android.content.Context
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.AtomicFile

/** Cache the last scanned library in filesDir and emit it immediately at startup; the live MediaStore scan replaces it when ready. */
class LibraryCache(context: Context) {

    private val file = File(context.filesDir, "library_cache.json")

    suspend fun read(): List<Song> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext emptyList()
            val arr = JSONArray(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Song(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            album = o.optString("album"),
                            genre = o.optString("genre"),
                            durationMs = o.optLong("durationMs"),
                            contentUri = o.optString("contentUri"),
                            albumArtUri = o.optString("albumArtUri").takeIf { it.isNotBlank() },
                            albumId = o.optLong("albumId"),
                            isFavorite = false,
                            year = o.optInt("year", 0),
                            trackNumber = o.optInt("trackNumber", 0),
                            format = o.optString("format"),
                            bitrate = o.optString("bitrate", ""),
                            mimeType = o.optString("mimeType", "audio/*"),
                            path = o.optString("path"),
                            dateAddedSec = o.optLong("dateAddedSec"),
                            dateModifiedSec = o.optLong("dateModifiedSec")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun write(songs: List<Song>) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            songs.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("title", s.title)
                        put("artist", s.artist)
                        put("album", s.album)
                        put("genre", s.genre)
                        put("durationMs", s.durationMs)
                        put("contentUri", s.contentUri)
                        put("albumArtUri", s.albumArtUri ?: "")
                        put("albumId", s.albumId)
                        put("year", s.year)
                        put("trackNumber", s.trackNumber)
                        put("format", s.format)
                        put("bitrate", s.bitrate)
                        put("mimeType", s.mimeType)
                        put("path", s.path)
                        put("dateAddedSec", s.dateAddedSec)
                        put("dateModifiedSec", s.dateModifiedSec)
                    }
                )
            }
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(arr.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        } catch (_: Exception) { }
    }
}
