package com.akira.app.core.lyrics

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal data class LyricsHttpResponse(val code: Int,val body: String="")
internal fun interface LyricsHttpTransport { fun get(url: String,userAgent: String): LyricsHttpResponse }
internal object DefaultLyricsHttpTransport: LyricsHttpTransport {
    override fun get(url: String,userAgent: String): LyricsHttpResponse {
        val connection=URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=3000;connection.readTimeout=4000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("User-Agent",userAgent)
            val status=connection.responseCode
            if(status!=200)return LyricsHttpResponse(status)
            val bytes=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val read=input.read(buffer)
                    if(read<0)break
                    require(output.size()+read<=2*1024*1024)
                    output.write(buffer,0,read)
                }
                output.toByteArray()
            }
            return LyricsHttpResponse(status,bytes.toString(Charsets.UTF_8))
        } finally {connection.disconnect()}
    }
}

class LrclibFetcher internal constructor(private val version: String,
    private val transport: LyricsHttpTransport=DefaultLyricsHttpTransport): LyricsFetcher {
    override fun fetch(request: LyricsRequest): LyricsFetchResult {
        var fallback: LyricsPayload? = null
        fun finish(otherwise: LyricsFetchResult): LyricsFetchResult = fallback?.let(LyricsFetchResult::Found) ?: otherwise
        try {
            fun encode(value: String)=URLEncoder.encode(value.filterNot {it.isISOControl()},"UTF-8")
            fun get(path: String)=transport.get("https://lrclib.net/api/$path","Akira/$version")
            fun payload(json: JSONObject)=LyricsPayload(
                if(json.isNull("syncedLyrics"))"" else json.optString("syncedLyrics"),
                if(json.isNull("plainLyrics"))"" else json.optString("plainLyrics"))
            val exact=get("get?track_name=${encode(request.title)}&artist_name=${encode(request.artist)}&album_name=${encode(request.album)}&duration=${request.durationMs/1000.0}")
            if(exact.code==200) {
                val found=payload(JSONObject(exact.body))
                if(found.parse().isSynced)return LyricsFetchResult.Found(found)
                if(found.parse().hasLyrics)fallback=found
            } else if(exact.code!=404)return LyricsFetchResult.Failed
            val title=lyricSearchTitle(request.title)
            val artists=listOf(request.artist,request.artist.split(Regex("[,;&]| feat\\.? | ft\\.? ",RegexOption.IGNORE_CASE)).first()).distinct()
            for(artist in artists.take(2)) {
                val response=get("search?track_name=${encode(title)}&artist_name=${encode(artist.trim())}")
                if(response.code!=200)return finish(LyricsFetchResult.Failed)
                val entries=JSONArray(response.body)
                val candidates=(0 until entries.length().coerceAtMost(100)).map {index -> entries.getJSONObject(index).let {
                    LrclibCandidate(it.optString("trackName"),it.optString("artistName"),it.optDouble("duration",0.0),payload(it))
                }}
                bestLyricMatch(request,candidates)?.let {
                    if(it.payload.parse().isSynced)return LyricsFetchResult.Found(it.payload)
                    if(fallback==null)fallback=it.payload
                }
            }
            return finish(LyricsFetchResult.Missing)
        } catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled
        } catch(_: Exception) {return finish(LyricsFetchResult.Failed)}
    }
}
