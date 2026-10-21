package com.akira.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.akira.app.core.database.model.LyricOffsetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricOffsetDao {
    @Query("SELECT * FROM lyric_offsets WHERE song_id = :songId")
    suspend fun getOffsetForSong(songId: String): LyricOffsetEntity?

    @Query("SELECT * FROM lyric_offsets WHERE song_id = :songId")
    fun observeOffsetForSong(songId: String): Flow<LyricOffsetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOffset(offset: LyricOffsetEntity)

    @Query("DELETE FROM lyric_offsets WHERE song_id = :songId")
    suspend fun deleteOffset(songId: String)
}
