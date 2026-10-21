package com.akira.app.core.lyrics

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

internal fun lyricsOvhUrl(artist: String, title: String): String {
    fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return "https://api.lyrics.ovh/v1/${segment(artist)}/${segment(title)}"
}

class LyricsOvhFetcher : LyricsFetcher {
    override fun fetch(request: LyricsRequest): LyricsFetchResult {
        val connection = URL(lyricsOvhUrl(request.artist, request.title)).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 3000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            when (connection.responseCode) {
                404 -> LyricsFetchResult.Missing
                200 -> {
                    val body = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 2 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    val json = JSONObject(body)
                    val lyrics = if (json.isNull("lyrics")) "" else json.optString("lyrics")
                    if (lyrics.isBlank()) LyricsFetchResult.Missing else LyricsFetchResult.Found(LyricsPayload(plainLyrics = lyrics))
                }
                else -> LyricsFetchResult.Failed
            }
        } catch (_: Exception) {
            LyricsFetchResult.Failed
        } finally {
            connection.disconnect()
        }
    }
}
