package com.akira.app.core.widget

import android.content.Context
import androidx.media3.common.Player
import com.akira.app.core.media.MediaItemMapper.toSong
import com.akira.app.core.model.Song
import com.akira.app.core.design.theme.PlayerTone
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject

internal data class WidgetQueueSnapshot(val songs: List<Song>, val index: Int, val positionMs: Long,
    val shuffle: Boolean, val repeat: Int, val order: List<String> = songs.map { it.queueEntryId }) {
    val safeIndex get() = index.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
    val safePosition get() = positionMs.coerceAtLeast(0L).let { position ->
        songs.getOrNull(safeIndex)?.durationMs?.takeIf { it > 0 }?.let { position.coerceAtMost(it) } ?: position
    }
}

/** Save queue events off the UI thread. Conflation keeps a complete latest snapshot, never a partial edit. */
internal object WidgetPlaybackStore {
    private val updates = Channel<Pair<Context, WidgetQueueSnapshot>>(Channel.CONFLATED)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        writer.launch {
            var lastEntries: List<String> = emptyList()
            for ((context, snapshot) in updates) runCatching {
                val store = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
                val edit = store.edit()
                val entries = snapshot.songs.map { it.queueEntryId }
                if (entries != lastEntries) {
                    val queue = JSONArray()
                    snapshot.songs.forEach { queue.put(encodeSong(it)) }
                    edit.putString("queue", queue.toString())
                }
                edit.putInt("queue_index", snapshot.safeIndex).putLong("queue_position", snapshot.safePosition)
                    .putInt("queue_count", snapshot.songs.size)
                    .putString("queue_order", JSONArray(snapshot.order).toString())
                    .putBoolean("queue_shuffle", snapshot.shuffle).putInt("queue_repeat", snapshot.repeat).apply()
                lastEntries = entries
                com.akira.app.core.online.SourceQueueStore.write(context, snapshot)
            }.onFailure { com.akira.app.core.logging.AkiraLog.e("Widget", "Queue snapshot failed", it) }
        }
    }
    fun capture(context: Context, player: Player) {
        if (player.mediaItemCount == 0) return
        val songs = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() }
        updates.trySend(context.applicationContext to WidgetQueueSnapshot(songs, player.currentMediaItemIndex,
            player.currentPosition, player.shuffleModeEnabled, player.repeatMode,
            com.akira.app.core.media.playbackQueueIndexes(player).map { songs[it].queueEntryId }))
    }
    fun read(context: Context): WidgetQueueSnapshot? = runCatching {
        val store = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
        val raw = store.getString("queue", null) ?: return@runCatching null
        val array = JSONArray(raw)
        val songs = (0 until array.length()).map { decodeSong(array.getJSONObject(it)) }
        if (songs.isEmpty()) null else WidgetQueueSnapshot(songs, store.getInt("queue_index", 0),
            store.getLong("queue_position", 0L), store.getBoolean("queue_shuffle", false),
            store.getInt("queue_repeat", Player.REPEAT_MODE_OFF).coerceIn(0, 2),
            store.getString("queue_order", null)?.let { rawOrder ->
                val ids = JSONArray(rawOrder); (0 until ids.length()).map { ids.getString(it) }
            } ?: songs.map { it.queueEntryId })
    }.getOrNull()

    fun encodeSong(song: Song): JSONObject = JSONObject().put("id", song.id).put("entry", song.queueEntryId)
        .put("title", song.title).put("artist", song.artist).put("album", song.album).put("duration", song.durationMs)
        .put("uri", song.contentUri).put("art", song.albumArtUri.orEmpty()).put("path", song.path).put("tone", song.tone.hex)
        .put("format", song.format).put("bitrate", song.bitrate).put("year", song.year).put("genre", song.genre)

    fun decodeSong(json: JSONObject): Song = Song(id = json.getString("id"), title = json.getString("title"),
        artist = json.getString("artist"), album = json.optString("album"), durationMs = json.optLong("duration"),
        contentUri = json.getString("uri"), albumArtUri = json.optString("art").takeIf { it.isNotEmpty() },
        path = json.optString("path"), queueEntryId = json.optString("entry", json.getString("id")),
        tone = PlayerTone.entries.firstOrNull { it.hex == json.optLong("tone") } ?: PlayerTone.INDIGO,
        format = json.optString("format"), bitrate = json.optString("bitrate"), year = json.optInt("year"), genre = json.optString("genre"))
}
