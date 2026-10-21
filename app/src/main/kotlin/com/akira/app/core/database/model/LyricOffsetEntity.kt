package com.akira.app.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyric_offsets")
data class LyricOffsetEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
