package com.akira.app.core.online

import android.content.Context
import android.util.AtomicFile
import com.akira.app.core.widget.WidgetPlaybackStore
import com.akira.app.core.widget.WidgetQueueSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Independent, device-only bookmarks. No expiring stream URLs or session cookies. */
internal object SourceQueueStore {
    @Volatile var onlineGeneration: Long = 0L
        private set

    private data class Cursor(val index: Int, val position: Long, val shuffle: Boolean, val repeat: Int) {
        fun json() = JSONObject().put("index", index).put("position", position)
            .put("shuffle", shuffle).put("repeat", repeat)
    }

    private class SavedPlaylist(val revision: String, val songs: List<com.akira.app.core.model.Song>,
        val order: List<String>, var cursor: Cursor)
    private val savedPlaylists = mutableMapOf<File, SavedPlaylist>()

    /** Share the logout barrier with both persistent queue destinations. */
    @Synchronized fun writeIfCurrent(snapshot: WidgetQueueSnapshot, write: () -> Unit) {
        if (snapshot.sourceOnline &&
            snapshot.onlineGeneration != onlineGeneration) return
        write()
    }
    private fun file(context: Context, online: Boolean) = AtomicFile(File(context.noBackupFilesDir,
        if (online) "online-queue.json" else "offline-queue.json"))

    private fun cursorFile(playlist: AtomicFile) = AtomicFile(File(playlist.baseFile.path + ".cursor"))

    @Synchronized fun write(context: Context, snapshot: WidgetQueueSnapshot): Boolean {
        val online = snapshot.sourceOnline
        if (online && snapshot.onlineGeneration != onlineGeneration) return false
        val target = file(context, online)
        val bookmark = cursorFile(target)
        if (snapshot.songs.isEmpty()) {
            target.delete()
            bookmark.delete()
            savedPlaylists.remove(target.baseFile)
            return true
        }
        val cursor = Cursor(snapshot.safeIndex, snapshot.safePosition, snapshot.shuffle, snapshot.repeat)
        val saved = savedPlaylists[target.baseFile]
        if (saved != null && saved.songs == snapshot.songs && saved.order == snapshot.order && target.baseFile.isFile) {
            if (saved.cursor != cursor) {
                writeJson(bookmark, cursor.json().put("revision", saved.revision))
                saved.cursor = cursor
            }
            return true
        }
        if (snapshot.songs.any { it.contentUri.startsWith("akira-youtube:") != online }) return false
        val revision = UUID.randomUUID().toString()
        val value = cursor.json().put("revision", revision).put("songs", JSONArray().apply {
            snapshot.songs.forEach { put(WidgetPlaybackStore.encodeSong(it)) }
        }).put("order", JSONArray(snapshot.order))
        writeJson(target, value)
        savedPlaylists[target.baseFile] = SavedPlaylist(revision, snapshot.songs, snapshot.order, cursor)
        return true
    }

    private fun writeJson(target: AtomicFile, value: JSONObject) {
        val output = target.startWrite()
        try { output.write(value.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(output) }
        catch (e: Exception) { target.failWrite(output); throw e }
    }

    @Synchronized fun read(context: Context, online: Boolean): WidgetQueueSnapshot? = runCatching {
        val target = file(context, online)
        if (!target.baseFile.exists()) return@runCatching null
        val value = JSONObject(String(target.readFully(), Charsets.UTF_8))
        val rows = value.getJSONArray("songs")
        val songs = (0 until rows.length()).map { WidgetPlaybackStore.decodeSong(rows.getJSONObject(it)) }
        if (songs.isEmpty() || songs.any { it.contentUri.startsWith("akira-youtube:") != online }) return@runCatching null
        val order = value.getJSONArray("order")
        // A cursor from an interrupted queue replacement belongs to the previous playlist.
        val revision = value.optString("revision")
        val cursor = if (revision.isEmpty()) value else runCatching {
            JSONObject(String(cursorFile(target).readFully(), Charsets.UTF_8)).takeIf {
                it.optString("revision") == revision
            }
        }.getOrNull() ?: value
        WidgetQueueSnapshot(songs, cursor.optInt("index"), cursor.optLong("position"),
            cursor.optBoolean("shuffle"), cursor.optInt("repeat").coerceIn(0, 2),
            (0 until order.length()).map { order.getString(it) })
    }.getOrNull()

    @Synchronized fun clearOnline(context: Context) {
        val target = file(context, true)
        val clearWidget = WidgetPlaybackStore.snapshotIsOnline(context)
        target.delete()
        cursorFile(target).delete()
        savedPlaylists.remove(target.baseFile)
        if (clearWidget) {
            context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).edit().clear().apply()
        }
        // Advance only after the in-memory preferences have been cleared. A
        // lock-free widget reader must never label old bytes with a new epoch.
        onlineGeneration++
    }
}
