package com.akira.app.core.online

import android.content.Context
import android.util.AtomicFile
import com.akira.innertube.models.Artist
import com.akira.innertube.utils.YTItemJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Account-scoped display data, stored in Android's disposable, unbacked-up cache. */
internal class OnlinePageCache(context: Context) {
    private val directory = File(context.cacheDir, "online-pages")
    private fun file(profile: String): AtomicFile {
        val name = java.security.MessageDigest.getInstance("SHA-256")
            .digest(profile.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$name.json"))
    }

    fun read(profile: String): Map<String, OnlinePage> = runCatching {
        val file = file(profile)
        if (!file.baseFile.isFile || file.baseFile.length() > MAX_BYTES) return emptyMap()
        val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
        if (root.optInt("schema") != 1 || root.optString("locale") != Locale.getDefault().toLanguageTag() ||
            System.currentTimeMillis() - root.optLong("saved") !in 0..TimeUnit.DAYS.toMillis(7)) return emptyMap()
        val pages = root.getJSONObject("pages")
        pages.keys().asSequence().take(20).associateWith { key -> decode(pages.getJSONObject(key)) }
    }.getOrDefault(emptyMap())

    fun write(profile: String, pages: Map<String, OnlinePage>) {
        if (pages.isEmpty()) return
        val encoded = JSONObject()
        pages.entries.toList().takeLast(20).forEach { (key, page) -> encoded.put(key, encode(page)) }
        val bytes = JSONObject().put("schema", 1).put("locale", Locale.getDefault().toLanguageTag())
            .put("saved", System.currentTimeMillis()).put("pages", encoded).toString().toByteArray()
        if (bytes.size > MAX_BYTES || !directory.isDirectory && !directory.mkdirs()) return
        val target = file(profile)
        val output = target.startWrite()
        try { output.write(bytes); target.finishWrite(output) }
        catch (error: Exception) { target.failWrite(output); throw error }
    }

    fun clear(profile: String) { file(profile).delete() }

    private fun encode(page: OnlinePage): JSONObject {
        var remaining = 800
        val shelves = JSONArray()
        page.shelves.take(24).forEach { shelf ->
            val items = shelf.items.take(remaining)
            remaining -= items.size
            shelves.put(JSONObject().put("title", shelf.title).put("reason", shelf.reason)
                .put("thumbnail", shelf.thumbnail).put("layout", shelf.layout.name)
                .put("items", JSONArray(YTItemJson.encode(items)))
                .put("artistAlbums", JSONObject().apply {
                    shelf.artistAlbums.forEach { (id, albums) -> put(id, JSONArray(YTItemJson.encode(albums))) }
                }))
        }
        return JSONObject().put("title", page.title).put("shelves", shelves).apply {
            page.collection?.let { collection -> put("collection", JSONObject()
                .put("item", JSONArray(YTItemJson.encode(listOf(collection.item))))
                .put("description", collection.description).put("stats", collection.stats)
                .put("creators", JSONArray().apply { collection.creators.forEach {
                    put(JSONObject().put("name", it.name).put("id", it.id))
                } })) }
        }
    }

    private fun decode(value: JSONObject): OnlinePage {
        fun JSONObject.text(key: String) = optString(key).takeUnless { it.isBlank() || it == "null" }
        val shelves = value.getJSONArray("shelves")
        val collection = value.optJSONObject("collection")?.let { row ->
            OnlineCollection(YTItemJson.decode(row.getJSONArray("item").toString()).first(), row.text("description"),
                row.text("stats"), row.getJSONArray("creators").let { list -> (0 until list.length()).map {
                    list.getJSONObject(it).let { artist -> Artist(artist.getString("name"), artist.text("id")) }
                } })
        }
        return OnlinePage(value.getString("title"), (0 until shelves.length()).map { index ->
            val shelf = shelves.getJSONObject(index)
            val albums = shelf.optJSONObject("artistAlbums")
            OnlineShelf(shelf.getString("title"), YTItemJson.decode(shelf.getJSONArray("items").toString()),
                shelf.text("reason"), shelf.text("thumbnail"), OnlineShelfLayout.valueOf(shelf.getString("layout")),
                albums?.keys()?.asSequence()?.associateWith { id -> YTItemJson.decode(albums.getJSONArray(id).toString())
                    .filterIsInstance<com.akira.innertube.models.AlbumItem>() }.orEmpty())
        }, collection = collection)
    }

    private companion object { const val MAX_BYTES = 8L * 1024 * 1024 }
}
