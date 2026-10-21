package com.akira.app.core.database.dao

import androidx.room.*
import com.akira.app.core.database.model.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaybackHistoryDao {
    @Query("SELECT * FROM play_stats") abstract fun observeStats(): Flow<List<PlayStatsEntity>>
    @Query("SELECT * FROM playlist_items ORDER BY position") abstract fun observeItems(): Flow<List<PlaylistItemEntity>>
    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    abstract suspend fun items(id: String): List<PlaylistItemEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertStats(value: PlayStatsEntity)
    @Query("UPDATE play_stats SET playCount = playCount + 1, firstPlayedAt = CASE WHEN firstPlayedAt = 0 THEN :now ELSE firstPlayedAt END, lastPlayedAt = :now WHERE songKey = :key")
    abstract suspend fun increment(key: String, now: Long)
    @Transaction open suspend fun recordPlay(key: String, now: Long) {
        insertStats(PlayStatsEntity(key))
        increment(key, now)
    }
    @Insert abstract suspend fun insertItems(items: List<PlaylistItemEntity>)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id") abstract suspend fun clearItems(id: String)
    @Transaction open suspend fun replaceItems(id: String, items: List<PlaylistItemEntity>) {
        clearItems(id)
        insertItems(items.mapIndexed { position, item -> item.copy(playlistId = id, position = position) })
    }
    @Query("UPDATE playlist_items SET songKey = :key WHERE songKey = :legacy") abstract suspend fun resolveLegacy(legacy: String, key: String)
    @Query("UPDATE OR IGNORE lyric_offsets SET song_id = :key WHERE song_id = :oldId") abstract suspend fun resolveOffset(oldId: String, key: String)
}
