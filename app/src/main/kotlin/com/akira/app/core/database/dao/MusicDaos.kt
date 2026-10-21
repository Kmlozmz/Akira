package com.akira.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.akira.app.core.database.model.FavoriteSongEntity
import com.akira.app.core.database.model.PlaybackHistoryEntity
import com.akira.app.core.database.model.PlaylistEntity
import com.akira.app.core.database.model.PlaylistSongCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite_songs ORDER BY addedAtMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    suspend fun deleteFavorite(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    suspend fun containsFavorite(songId: String): Boolean

    @androidx.room.Transaction
    suspend fun toggleFavorite(entity: FavoriteSongEntity) {
        if (containsFavorite(entity.songId)) deleteFavorite(entity.songId) else insertFavorite(entity)
    }
}

@Dao
interface PlaylistsDao {
    @Query("SELECT * FROM playlists ORDER BY createdAtMillis DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    @androidx.room.Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun observeSongsForPlaylist(playlistId: String): Flow<List<PlaylistSongCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAtMillis DESC LIMIT :limit")
    fun observeHistory(limit: Int = 50): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
