package com.akira.app.core.online

import android.content.Context
import android.util.AtomicFile
import com.akira.app.core.widget.WidgetPlaybackStore
import com.akira.app.core.widget.WidgetQueueSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Independent, device-only bookmarks. No expiring stream URLs or session cookies. */
internal object SourceQueueStore {
    @Volatile var onlineGeneration: Long = 0L
        private set

    /** Share the logout barrier with both persistent queue destinations. */
    @Synchronized fun writeIfCurrent(snapshot: WidgetQueueSnapshot, write: () -> Unit) {
        if (snapshot.sourceOnline &&
            snapshot.onlineGeneration != onlineGeneration) return
        write()
    }
    private fun file(context: Context, online: Boolean) = AtomicFile(File(context.noBackupFilesDir,
        if (online) "online-queue.json" else "offline-queue.json"))

    @Synchronized fun write(context: Context, snapshot: WidgetQueueSnapshot) {
        val online = snapshot.sourceOnline
        if (online && snapshot.onlineGeneration != onlineGeneration) return
        if (snapshot.songs.isEmpty()) {
            file(context, online).delete()
            return
        }
        if (snapshot.songs.any { it.contentUri.startsWith("akira-youtube:") != online }) return
        val value = JSONObject().put("songs", JSONArray().apply {
            snapshot.songs.forEach { put(WidgetPlaybackStore.encodeSong(it)) }
        }).put("index", snapshot.safeIndex).put("position", snapshot.safePosition)
            .put("shuffle", snapshot.shuffle).put("repeat", snapshot.repeat)
            .put("order", JSONArray(snapshot.order))
        val target = file(context, online)
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
        WidgetQueueSnapshot(songs, value.optInt("index"), value.optLong("position"),
            value.optBoolean("shuffle"), value.optInt("repeat").coerceIn(0, 2),
            (0 until order.length()).map { order.getString(it) })
    }.getOrNull()

    @Synchronized fun clearOnline(context: Context) {
        file(context, true).delete()
        if (WidgetPlaybackStore.read(context)?.songs?.any { it.contentUri.startsWith("akira-youtube:") } == true) {
            context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).edit().clear().apply()
        }
        // Advance only after the in-memory preferences have been cleared. A
        // lock-free widget reader must never label old bytes with a new epoch.
        onlineGeneration++
    }
}
