package com.akira.app.core.widget

import android.content.Context
import androidx.media3.common.Player
import com.akira.app.core.media.MediaItemMapper.toSong
import com.akira.app.core.media.MediaItemMapper.onlineQueueGeneration
import com.akira.app.core.model.Song
import com.akira.app.core.design.theme.PlayerTone
import com.akira.app.core.online.SourceQueueStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject

internal data class WidgetQueueSnapshot(val songs: List<Song>, val index: Int, val positionMs: Long,
    val shuffle: Boolean, val repeat: Int, val order: List<String> = songs.map { it.queueEntryId },
    val onlineGeneration: Long = com.akira.app.core.online.SourceQueueStore.onlineGeneration,
    val sourceOnline: Boolean = songs.firstOrNull()?.contentUri?.startsWith("akira-youtube:") == true) {
    val safeIndex get() = index.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
    val safePosition get() = positionMs.coerceAtLeast(0L).let { position ->
        songs.getOrNull(safeIndex)?.durationMs?.takeIf { it > 0 }?.let { position.coerceAtMost(it) } ?: position
    }
}

/** Immutable playlist data shared by position and transport updates until a queue edit. */
internal class WidgetPlaybackQueue(player: Player) {
    val songs = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() }
    val indexes = com.akira.app.core.media.playbackQueueIndexes(player)
    val order = indexes.map { songs[it].queueEntryId }
    private val positions = IntArray(songs.size) { -1 }.apply {
        indexes.forEachIndexed { position, index -> this[index] = position }
    }
    val shuffle = player.shuffleModeEnabled
    val onlineGeneration = if (songs.isEmpty()) com.akira.app.core.online.SourceQueueStore.onlineGeneration
        else player.getMediaItemAt(0).onlineQueueGeneration()

    fun positionOf(index: Int) = positions.getOrElse(index) { -1 }
}

/** Save queue events off the UI thread. Conflation keeps a complete latest snapshot, never a partial edit. */
internal object WidgetPlaybackStore {
    private const val LAST_SOURCE_ONLINE = "last_queue_online"
    private val updates = Channel<Pair<Context, WidgetQueueSnapshot>>(Channel.CONFLATED)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        writer.launch {
            for ((context, snapshot) in updates) runCatching {
                SourceQueueStore.writeIfCurrent(snapshot) {
                    if (!SourceQueueStore.write(context, snapshot)) return@writeIfCurrent
                    val store = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
                    // Validate the durable queue before retiring the legacy large preference entries.
                    store.edit().remove("queue").remove("queue_order")
                        .putBoolean(LAST_SOURCE_ONLINE, snapshot.sourceOnline)
                        .putInt("queue_index", snapshot.safeIndex).putLong("queue_position", snapshot.safePosition)
                        .putInt("queue_count", snapshot.songs.size)
                        .putBoolean("queue_shuffle", snapshot.shuffle).putInt("queue_repeat", snapshot.repeat).apply()
                }
            }.onFailure { com.akira.app.core.logging.AkiraLog.e("Widget", "Queue snapshot failed", it) }
        }
    }
    fun capture(context: Context, player: Player, queue: WidgetPlaybackQueue, emptySourceOnline: Boolean? = null) {
        // Ordinary source switching keeps its parked bookmark. A deletion can
        // explicitly enqueue an empty snapshot to supersede older writes.
        val songs = queue.songs
        if (songs.isEmpty() && emptySourceOnline == null) return
        val generation = queue.onlineGeneration
        val online = songs.firstOrNull()?.contentUri?.startsWith("akira-youtube:") ?: emptySourceOnline ?: false
        // Do not let a stale deck displace a newer valid snapshot in the
        // conflated channel. The writer checks again at the commit boundary.
        if (online && generation != com.akira.app.core.online.SourceQueueStore.onlineGeneration) return
        updates.trySend(context.applicationContext to WidgetQueueSnapshot(songs, player.currentMediaItemIndex,
            player.currentPosition, player.shuffleModeEnabled, player.repeatMode,
            queue.order,
            generation, online))
    }
    fun snapshotIsOnline(context: Context): Boolean {
        val store = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
        return if (store.contains(LAST_SOURCE_ONLINE)) store.getBoolean(LAST_SOURCE_ONLINE, false)
        else read(context)?.sourceOnline == true
    }

    fun read(context: Context): WidgetQueueSnapshot? = runCatching {
        val generation = SourceQueueStore.onlineGeneration
        val store = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
        if (store.contains(LAST_SOURCE_ONLINE)) {
            return@runCatching SourceQueueStore.read(context, store.getBoolean(LAST_SOURCE_ONLINE, false))
                ?.takeIf { !it.sourceOnline || generation == SourceQueueStore.onlineGeneration }
        }
        val raw = store.getString("queue", null) ?: return@runCatching null
        val array = JSONArray(raw)
        val songs = (0 until array.length()).map { decodeSong(array.getJSONObject(it)) }
        if (songs.isEmpty()) null else WidgetQueueSnapshot(songs, store.getInt("queue_index", 0),
            store.getLong("queue_position", 0L), store.getBoolean("queue_shuffle", false),
            store.getInt("queue_repeat", Player.REPEAT_MODE_OFF).coerceIn(0, 2),
            store.getString("queue_order", null)?.let { rawOrder ->
                val ids = JSONArray(rawOrder); (0 until ids.length()).map { ids.getString(it) }
            } ?: songs.map { it.queueEntryId }, onlineGeneration = generation)
            .takeIf { !it.sourceOnline || generation == SourceQueueStore.onlineGeneration }
    }.getOrNull()

    fun encodeSong(song: Song): JSONObject = JSONObject().put("id", song.id).put("entry", song.queueEntryId)
        .put("title", song.title).put("artist", song.artist).put("album", song.album).put("duration", song.durationMs)
        .put("uri", song.contentUri).put("art", song.albumArtUri.orEmpty()).put("path", song.path).put("tone", song.tone.hex)
        .put("format", song.format).put("bitrate", song.bitrate).put("year", song.year).put("genre", song.genre)
        .put("album_id", song.albumId).put("mime_type", song.mimeType).put("track_number", song.trackNumber)
        .put("date_added", song.dateAddedSec).put("date_modified", song.dateModifiedSec)
        .put("has_lyrics", song.hasLyrics).put("favorite", song.isFavorite)

    fun decodeSong(json: JSONObject): Song = Song(id = json.getString("id"), title = json.getString("title"),
        artist = json.getString("artist"), album = json.optString("album"), durationMs = json.optLong("duration"),
        contentUri = json.getString("uri"), albumArtUri = json.optString("art").takeIf { it.isNotEmpty() },
        path = json.optString("path"), queueEntryId = json.optString("entry", json.getString("id")),
        tone = PlayerTone.entries.firstOrNull { it.hex == json.optLong("tone") } ?: PlayerTone.INDIGO,
        albumId = json.optLong("album_id"), mimeType = json.optString("mime_type", "audio/*"),
        trackNumber = json.optInt("track_number"), dateAddedSec = json.optLong("date_added"),
        dateModifiedSec = json.optLong("date_modified"), hasLyrics = json.optBoolean("has_lyrics"),
        isFavorite = json.optBoolean("favorite"),
        format = json.optString("format"), bitrate = json.optString("bitrate"), year = json.optInt("year"), genre = json.optString("genre"))
}
