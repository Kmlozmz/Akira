package com.akira.app.core.lyrics

import java.net.URLEncoder
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

private fun encodeLyricsParameter(value: String)=URLEncoder.encode(value.filterNot {it.isISOControl()},"UTF-8")

/** Direct, unofficial endpoints. No credentials, proxies or account cookies. */
class KugouFetcher internal constructor(private val version: String,
    private val transport: LyricsHttpTransport=DefaultLyricsHttpTransport): LyricsFetcher {
    override fun fetch(request: LyricsRequest): LyricsFetchResult = try {
        if(request.title.isBlank() || request.artist.isBlank() || request.durationMs<=0) LyricsFetchResult.Missing
        else fetchMatched(request)
    } catch(cancelled: CancellationException) {throw cancelled
    } catch(_: Exception) {LyricsFetchResult.Failed}

    private fun fetchMatched(request: LyricsRequest): LyricsFetchResult {
        fun get(path: String): JSONObject {
            val response=transport.get("https://lyrics.kugou.com/$path","Akira/$version")
            check(response.code==200)
            val json=JSONObject(response.body)
            check(json.optInt("status")==200)
            return json
        }
        val query=encodeLyricsParameter("${request.artist} ${lyricSearchTitle(request.title)}")
        val rows=get("search?ver=1&man=yes&client=pc&keyword=$query&duration=${request.durationMs}").optJSONArray("candidates")
            ?: return LyricsFetchResult.Missing
        val matches=(0 until rows.length().coerceAtMost(100)).mapNotNull {index ->
            val row=rows.getJSONObject(index)
            val duration=row.optDouble("duration",0.0)/1000
            if(duration<=0)return@mapNotNull null
            val distance=lyricMatchDistance(request,row.optString("song"),row.optString("singer"),duration) ?: return@mapNotNull null
            row to distance
        }.sortedBy {it.second}.take(3)
        var failed=false
        for((row,_) in matches) {
            try {
                val id=encodeLyricsParameter(row.getString("id"))
                val access=encodeLyricsParameter(row.getString("accesskey"))
                val data=get("download?ver=1&client=pc&id=$id&accesskey=$access&fmt=krc&charset=utf8")
                val payload=LyricsPayload(syncedLyrics=KrcLyrics.decode(data.getString("content")))
                if(payload.parse().isSynced)return LyricsFetchResult.Found(payload)
            } catch(cancelled: CancellationException) {throw cancelled
            } catch(_: Exception) {failed=true}
        }
        return if(failed)LyricsFetchResult.Failed else LyricsFetchResult.Missing
    }
}

class NeteaseFetcher internal constructor(private val version: String,
    private val transport: LyricsHttpTransport=DefaultLyricsHttpTransport): LyricsFetcher {
    override fun fetch(request: LyricsRequest): LyricsFetchResult = try {
        if(request.title.isBlank() || request.artist.isBlank() || request.durationMs<=0) LyricsFetchResult.Missing
        else fetchMatched(request)
    } catch(cancelled: CancellationException) {throw cancelled
    } catch(_: Exception) {LyricsFetchResult.Failed}

    private fun fetchMatched(request: LyricsRequest): LyricsFetchResult {
        fun get(path: String): JSONObject {
            val response=transport.get("https://music.163.com/api/$path","Akira/$version")
            check(response.code==200)
            val json=JSONObject(response.body)
            check(json.optInt("code")==200)
            return json
        }
        val query=encodeLyricsParameter("${request.artist} ${lyricSearchTitle(request.title)}")
        val rows=get("search/get?s=$query&type=1&limit=20").optJSONObject("result")?.optJSONArray("songs")
            ?: return LyricsFetchResult.Missing
        val matches=(0 until rows.length().coerceAtMost(20)).mapNotNull {index ->
            val row=rows.getJSONObject(index)
            val duration=row.optDouble("duration",0.0)/1000
            if(duration<=0)return@mapNotNull null
            val artists=row.optJSONArray("artists") ?: return@mapNotNull null
            val distances=(0 until artists.length().coerceAtMost(20)).mapNotNull {
                lyricMatchDistance(request,row.optString("name"),artists.getJSONObject(it).optString("name"),duration)
            }
            row to (distances.minOrNull() ?: return@mapNotNull null)
        }.sortedBy {it.second}.take(3)
        var failed=false
        for((row,_) in matches) {
            try {
                val id=row.getLong("id");check(id>0)
                val data=get("song/lyric?id=$id&lv=-1&kv=-1&tv=-1")
                val payload=LyricsPayload(syncedLyrics=data.optJSONObject("lrc")?.optString("lyric").orEmpty())
                if(payload.parse().isSynced)return LyricsFetchResult.Found(payload)
            } catch(cancelled: CancellationException) {throw cancelled
            } catch(_: Exception) {failed=true}
        }
        return if(failed)LyricsFetchResult.Failed else LyricsFetchResult.Missing
    }
}
