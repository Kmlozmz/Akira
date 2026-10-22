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
    private var writtenProfile: String? = null
    private val writtenPages = mutableMapOf<String, OnlinePage>()
    private fun hash(value: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun pageDirectory(profile: String) = File(directory, hash(profile) + ".pages")
    private fun file(profile: String): AtomicFile {
        val name = java.security.MessageDigest.getInstance("SHA-256")
            .digest(profile.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$name.json"))
    }

    fun read(profile: String, homeOnly: Boolean = false): Map<String, OnlinePage> = runCatching {
        val folder = pageDirectory(profile)
        val records = if (homeOnly) listOf(File(folder, hash("home") + ".json"))
            else folder.listFiles()?.filter { it.extension == "json" }?.take(20).orEmpty()
        val pages = records.mapNotNull { record -> runCatching {
            if (!record.isFile || record.length() > MAX_BYTES) return@runCatching null
            val row = JSONObject(String(AtomicFile(record).readFully(), Charsets.UTF_8))
            if (row.optInt("schema") != 2 || !valid(row)) return@runCatching null
            row.getString("key") to decode(row.getJSONObject("page"))
        }.getOrNull() }.toMap()
        if (pages.isNotEmpty()) return pages
        val file = file(profile)
        if (!file.baseFile.isFile || file.baseFile.length() > MAX_BYTES) return emptyMap()
        val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
        if (root.optInt("schema") != 1 || !valid(root)) return emptyMap()
        val legacy = root.getJSONObject("pages")
        legacy.keys().asSequence().filter { !homeOnly || it == "home" }.take(20)
            .associateWith { key -> decode(legacy.getJSONObject(key)) }
    }.getOrDefault(emptyMap())

    private fun valid(value: JSONObject) = value.optString("locale") == Locale.getDefault().toLanguageTag() &&
        System.currentTimeMillis() - value.optLong("saved") in 0..TimeUnit.DAYS.toMillis(7)

    fun write(profile: String, pages: Map<String, OnlinePage>) {
        if (pages.isEmpty()) return
        val folder = pageDirectory(profile)
        if (!folder.isDirectory && !folder.mkdirs()) return
        if (writtenProfile != profile) { writtenPages.clear(); writtenProfile = profile }
        var remaining = MAX_BYTES
        val kept = mutableSetOf<String>()
        pages.entries.toList().takeLast(20).sortedBy { if (it.key == "home") 0 else 1 }.forEach { (key, page) ->
            val target = AtomicFile(File(folder, hash(key) + ".json"))
            if (writtenPages[key] === page && target.baseFile.isFile && target.baseFile.length() <= remaining) {
                remaining -= target.baseFile.length(); kept += target.baseFile.name
                return@forEach
            }
            val bytes = JSONObject().put("schema", 2).put("key", key)
                .put("locale", Locale.getDefault().toLanguageTag()).put("saved", System.currentTimeMillis())
                .put("page", encode(page, if (key == "search-library-index") Int.MAX_VALUE else 800))
                .toString().toByteArray()
            if (bytes.size > remaining) return@forEach
            val output = target.startWrite()
            try { output.write(bytes); target.finishWrite(output) }
            catch (error: Exception) { target.failWrite(output); throw error }
            remaining -= bytes.size; kept += target.baseFile.name; writtenPages[key] = page
        }
        folder.listFiles()?.filter { it.extension == "json" && it.name !in kept }?.forEach { AtomicFile(it).delete() }
        writtenPages.keys.retainAll(pages.keys)
        file(profile).delete()
    }

    fun clear(profile: String) {
        file(profile).delete()
        pageDirectory(profile).let { folder -> folder.listFiles()?.forEach { it.delete() }; folder.delete() }
        if (writtenProfile == profile) writtenPages.clear()
    }

    private fun encode(page: OnlinePage, itemLimit: Int): JSONObject {
        var remaining = itemLimit
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
