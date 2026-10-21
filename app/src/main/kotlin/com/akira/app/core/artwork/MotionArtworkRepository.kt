package com.akira.app.core.artwork


import android.content.Context
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.coroutines.coroutineContext

internal data class MotionArtwork(val url: String, val fallback: String? = null)
internal data class ArtistEditorialArtwork(val image: String?, val motion: MotionArtwork?)

/** Resolves matched catalogue artwork and caches short-lived access credentials. */
internal object MotionArtworkRepository {
    private val mutex=Mutex()
    private var token: String?=null
    private var tokenExpiry=0L
    private var retryAfter=0L
    private const val ua="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
    private val memory=android.util.LruCache<String,Pair<Long,MotionArtwork?>>(128)
    private val artistMemory=android.util.LruCache<String,Pair<Long,ArtistEditorialArtwork?>>(64)

    /** Artist-owned editorial imagery; exact catalogue names only, with the YouTube portrait as fallback in the UI. */
    suspend fun findArtist(name: String): ArtistEditorialArtwork? = withContext(Dispatchers.IO) {
        val key=normalizeMotionText(name)
        if(key.isBlank())return@withContext null
        mutex.withLock {
            val now=System.currentTimeMillis()
            artistMemory.get(key)?.takeIf {it.first>now}?.let {return@withLock it.second}
            val bearer=bearer() ?: return@withLock null
            val term=URLEncoder.encode(name,"UTF-8")
            val search=get("https://amp-api.music.apple.com/v1/catalog/us/search?term=$term&types=artists&limit=5",bearer)
                ?: return@withLock null
            val hits=runCatching {JSONObject(search).getJSONObject("results").getJSONObject("artists").getJSONArray("data")}.getOrNull()
            val match=hits?.let {rows -> (0 until rows.length()).mapNotNull {rows.optJSONObject(it)}
                .firstOrNull {normalizeMotionText(it.optJSONObject("attributes")?.optString("name").orEmpty())==key} }
            val id=match?.optString("id")?.takeIf {it.isNotEmpty() && it.all(Char::isDigit)}
            val body=id?.let {get("https://amp-api.music.apple.com/v1/catalog/us/artists/$it?extend=editorialVideo,editorialArtwork",bearer)}
            val attributes=body?.let {runCatching {JSONObject(it).getJSONArray("data").getJSONObject(0).getJSONObject("attributes")}.getOrNull()}
            val video=attributes?.optJSONObject("editorialVideo")
            val entries=listOf("motionArtistSquare1x1","motionDetailSquare","motionSquareVideo1x1",
                "motionArtistWide16x9","motionArtistFullscreen16x9","motionDetailRaw").mapNotNull {video?.optJSONObject(it)}
            val urls=entries.mapNotNull {it.optString("video").takeIf {url -> url.startsWith("https://")}}.distinct()
            val still=entries.firstNotNullOfOrNull {it.optJSONObject("previewFrame")?.optString("url")?.takeIf(String::isNotBlank)}
                ?: attributes?.optJSONObject("editorialArtwork")?.let {art -> listOf("bannerUber","storeFlowcase","subscriptionHero")
                    .firstNotNullOfOrNull {art.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank)} }
                ?: attributes?.optJSONObject("artwork")?.optString("url")
            val image=still?.takeIf {it.startsWith("https://")}?.replace("{w}","1200")?.replace("{h}","1200")?.replace("{f}","jpg")
            val found=if(image!=null || urls.isNotEmpty())ArtistEditorialArtwork(image,urls.firstOrNull()?.let {MotionArtwork(it,urls.getOrNull(1))}) else null
            artistMemory.put(key,(now+if(found==null)10*60_000L else 24*60*60_000L) to found)
            found
        }
    }

    suspend fun find(context: Context, song: Song): MotionArtwork? = findExact(context, if (song.contentUri.startsWith("akira-youtube:"))
        song.copy(title = com.akira.app.core.online.cleanOnlineTitle(song.title)) else song)

    private suspend fun findExact(context: Context, song: Song): MotionArtwork? = withContext(Dispatchers.IO) {
        if(song.title.isBlank() || song.artist.isBlank())return@withContext null
        val key="v2:${motionMatchKey(song.title,song.artist,song.album)}"
        mutex.withLock {
            coroutineContext.ensureActive()
            val now=System.currentTimeMillis()
            memory.get(key)?.takeIf {it.first>now}?.let {return@withLock it.second}
            val dir=java.io.File(context.cacheDir,"motion-artwork").apply {mkdirs()}
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") {"%02x".format(it)}
            val file=java.io.File(dir,"$digest.json")
            val saved=runCatching {JSONObject(file.readText())}.getOrNull()
            if(saved!=null && saved.optLong("expires")>now) {
                val asset=saved.optString("url").takeIf {it.startsWith("https://")}?.let {
                    MotionArtwork(it,saved.optString("fallback").takeIf(String::isNotBlank))}
                memory.put(key,saved.optLong("expires") to asset)
                return@withLock asset
            }
            val bearer=bearer() ?: return@withLock null
            val term=URLEncoder.encode("${song.artist} ${song.title} ${song.album}","UTF-8")
            val body=get("https://amp-api.music.apple.com/v1/catalog/us/search?term=$term&types=songs&limit=10&include=albums&extend=editorialVideo",bearer)
                ?: return@withLock null
            val hits=runCatching {JSONObject(body).getJSONObject("results").getJSONObject("songs").getJSONArray("data")}.getOrNull()
                ?: return@withLock null
            val ranked=(0 until hits.length()).mapNotNull {i ->
                val hit=hits.optJSONObject(i) ?: return@mapNotNull null
                val a=hit.optJSONObject("attributes") ?: return@mapNotNull null
                val strict=motionMatchScore(song.title,song.artist,song.album,a.optString("name"),a.optString("artistName"),a.optString("albumName"))
                val score=if(strict<0 && song.contentUri.startsWith("akira-youtube:"))
                    motionMatchScore(song.title,song.artist,"",a.optString("name"),a.optString("artistName"),a.optString("albumName")) else strict
                if(score<40)null else score to hit
            }.sortedByDescending {it.first}
            var found: MotionArtwork?=null
            var lookupFailed=false
            for((_,hit) in ranked.take(2)) {
                coroutineContext.ensureActive()
                val attributes=hit.getJSONObject("attributes")
                found=motionUrls(attributes.optJSONObject("editorialVideo"))
                if(found!=null)break
                val id=hit.optJSONObject("relationships")?.optJSONObject("albums")?.optJSONArray("data")?.optJSONObject(0)?.optString("id")
                    ?: attributes.optString("url").substringBefore('?').substringAfterLast('/')
                if(id.isBlank() || !id.all(Char::isDigit))continue
                val album=get("https://amp-api.music.apple.com/v1/catalog/us/albums/$id?extend=editorialVideo",bearer)
                if(album==null) {lookupFailed=true;continue}
                found=runCatching {motionUrls(JSONObject(album).getJSONArray("data").getJSONObject(0).getJSONObject("attributes").optJSONObject("editorialVideo"))}.getOrNull()
                if(found!=null)break
            }
            // A lost connection is not evidence that an album has no video.
            // Brief memory-only backoff, never a six-hour persisted no-match.
            if(found==null && lookupFailed) {
                memory.put(key,(now+5*60_000L) to null)
                return@withLock null
            }
            val expires=now+if(found==null)6*60*60_000L else 24*60*60_000L
            memory.put(key,expires to found)
            runCatching {
                val json=JSONObject().put("expires",expires).put("url",found?.url ?: "").put("fallback",found?.fallback ?: "")
                file.writeText(json.toString())
                // Bound persistent metadata too, independently of the in-memory LRU.
                dir.listFiles()?.sortedByDescending {it.lastModified()}?.drop(128)?.forEach {it.delete()}
            }
            found
        }
    }

    internal fun motionUrls(video: JSONObject?): MotionArtwork? {
        fun link(key: String): String? {
            val entry=video?.optJSONObject(key) ?: return null
            return listOf("video","videoUrl","hlsUrl","url").firstNotNullOfOrNull {field ->
                entry.optString(field).takeIf {it.startsWith("https://")}}
        }
        val urls=listOfNotNull(link("motionDetailTall"),link("motionTallVideo3x4"),
            link("motionDetailSquare"),link("motionSquareVideo1x1"),link("motionDetailRaw")).distinct()
        return urls.firstOrNull()?.let {MotionArtwork(it,urls.getOrNull(1))}
    }

    private suspend fun bearer(): String? {
        val now=System.currentTimeMillis()
        token?.takeIf {tokenExpiry>now+60_000}?.let {return it}
        if(now<retryAfter)return null
        val html=get("https://music.apple.com/us/browse") ?: return null.also {retryAfter=now+30*60_000}
        val scripts=Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""").findAll(html).map {it.value}.distinct().take(3)
        for(path in scripts) {
            val script=get("https://music.apple.com$path") ?: continue
            for(match in Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""").findAll(script)) {
                val jwt=match.value
                val payload=runCatching {JSONObject(String(Base64.getUrlDecoder().decode(jwt.split('.')[1]),Charsets.UTF_8))}.getOrNull() ?: continue
                val expires=payload.optLong("exp")*1000
                if(payload.optString("iss")=="AMPWebPlay" && expires>now+60_000) {
                    token=jwt;tokenExpiry=expires;return jwt
                }
            }
        }
        retryAfter=now+30*60_000
        return null
    }

    private suspend fun get(url: String,bearer: String?=null): String? {
        coroutineContext.ensureActive()
        val connection=URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout=6000;connection.readTimeout=6000
            connection.setRequestProperty("User-Agent",ua)
            if(bearer!=null) {
                connection.setRequestProperty("Authorization","Bearer $bearer")
                connection.setRequestProperty("Origin","https://music.apple.com")
                connection.setRequestProperty("Referer","https://music.apple.com/")
            }
            if(connection.responseCode==401) {token=null;tokenExpiry=0;retryAfter=System.currentTimeMillis()+30*60_000;return null}
            if(connection.responseCode !in 200..299)return null
            connection.inputStream.use {stream ->
                val output=java.io.ByteArrayOutputStream();val bytes=ByteArray(8192)
                while(true) {
                    coroutineContext.ensureActive()
                    val n=stream.read(bytes);if(n<0)break
                    if(output.size()+n>5*1024*1024)return null
                    output.write(bytes,0,n)
                }
                output.toString("UTF-8")
            }
        } catch(cancel: kotlinx.coroutines.CancellationException) {throw cancel}
        catch(_: Exception) {null}
        finally {connection.disconnect()}
    }
}

internal fun normalizeMotionText(text: String): String = Normalizer.normalize(text,Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
internal fun motionMatchKey(title: String,artist: String,album: String)=listOf(title,artist,album).joinToString("|") {normalizeMotionText(it)}
internal fun motionMatchScore(title: String,artist: String,album: String,hitTitle: String,hitArtist: String,hitAlbum: String): Int {
    fun artists(text: String)=text.split(Regex("(?i)\\s*(?:,|&| feat\\.? | featuring | x | y )\\s*")).map(::normalizeMotionText).filter(String::isNotBlank)
    val wanted=artists(artist);val credited=artists(hitArtist)
    if(wanted.isEmpty() || !wanted.all {it in credited})return -1
    fun cleanTitle(text: String)=normalizeMotionText(text.replace(Regex("(?i)[(\\[]\\s*(?:feat\\.?|featuring)\\b.*?[)\\]]"),""))
    val a=cleanTitle(title)
    val b=cleanTitle(hitTitle)
    if(a.isBlank() || a!=b)return -1
    if(listOf("remix","live","instrumental","karaoke","remaster").any {
        normalizeMotionText(title).contains(it)!=normalizeMotionText(hitTitle).contains(it)})return -1
    if(album.isNotBlank() && normalizeMotionText(album)!=normalizeMotionText(hitAlbum))return -1
    return 40+if(album.isNotBlank())20 else 0
}
