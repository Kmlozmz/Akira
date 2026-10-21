package com.akira.app.core.data.playlist

import android.content.Context
import androidx.room.withTransaction
import com.akira.app.core.database.AppDatabase
import com.akira.app.core.database.model.*
import com.akira.app.core.model.*
import com.akira.app.core.design.theme.PlayerTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PlaylistStore(context: Context) {
    private val db = AppDatabase.getInstance(context)
    val playlists = db.playlistsDao().observePlaylists()
    val items = db.playbackHistoryDao().observeItems()
    val stats = db.playbackHistoryDao().observeStats()
    suspend fun create(title: String, songs: List<Song> = emptyList()): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = UUID.randomUUID().toString()
            db.playlistsDao().insertPlaylist(PlaylistEntity(id, title.trim(), "", PlayerTone.INDIGO.hex))
            add(id, songs)
            id
        }
    }
    suspend fun rename(playlist: PlaylistEntity, title: String) = withContext(Dispatchers.IO) {
        db.playlistsDao().updatePlaylist(playlist.copy(title = title.trim()))
    }
    suspend fun duplicate(playlist: PlaylistEntity): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = create(playlist.title)
            db.playbackHistoryDao().replaceItems(id, db.playbackHistoryDao().items(playlist.id))
            id
        }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { db.playlistsDao().deletePlaylist(id) }
    suspend fun restore(playlist: PlaylistEntity, items: List<PlaylistItemEntity>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (db.playlistsDao().getPlaylistById(playlist.id) == null) {
                db.playlistsDao().insertPlaylist(playlist)
                db.playbackHistoryDao().replaceItems(playlist.id, items)
            }
        }
    }
    suspend fun add(id: String, songs: List<Song>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (db.playlistsDao().getPlaylistById(id) != null) {
                val existing = db.playbackHistoryDao().items(id)
                val keys = existing.map { it.songKey }.toSet()
                val now = System.currentTimeMillis()
                val newItems = songs.distinctBy { SongKey.of(it).encoded }.filterNot { SongKey.of(it).encoded in keys }
                    .mapIndexed { index, song -> PlaylistItemEntity(id, existing.size + index, SongKey.of(song).encoded, now + index) }
                db.playbackHistoryDao().replaceItems(id, existing + newItems)
            }
        }
    }
    suspend fun removeItem(id: String, songKey: String) = editItems(id) { items ->
        items.filterNot { it.songKey == songKey }
    }

    suspend fun restoreItem(id: String, item: PlaylistItemEntity, index: Int) = editItems(id) { items ->
        PlaylistEdits.restore(items, item, index)
    }

    suspend fun reorder(id: String, orderedKeys: List<String>) = editItems(id) { items ->
        PlaylistEdits.reorder(items, orderedKeys)
    }

    private suspend fun editItems(id: String, edit: (List<PlaylistItemEntity>) -> List<PlaylistItemEntity>) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                if (db.playlistsDao().getPlaylistById(id) != null) {
                    db.playbackHistoryDao().replaceItems(id, edit(db.playbackHistoryDao().items(id)))
                }
            }
        }
}

/** Applies UI operations to the latest contents, retaining concurrently added tracks. */
internal object PlaylistEdits {
    fun restore(items: List<PlaylistItemEntity>, item: PlaylistItemEntity, index: Int): List<PlaylistItemEntity> {
        if (items.any { it.songKey == item.songKey }) return items
        return items.toMutableList().apply { add(index.coerceIn(0, size), item) }
    }

    fun reorder(items: List<PlaylistItemEntity>, keys: List<String>): List<PlaylistItemEntity> {
        val byKey = items.associateBy { it.songKey }
        val requested = keys.toSet()
        return keys.distinct().mapNotNull(byKey::get) + items.filterNot { it.songKey in requested }
    }
}
