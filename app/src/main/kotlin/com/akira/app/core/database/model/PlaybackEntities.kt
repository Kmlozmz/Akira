package com.akira.app.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "play_stats")
data class PlayStatsEntity(
    @PrimaryKey val songKey: String,
    val playCount: Long = 0,
    val firstPlayedAt: Long = 0,
    val lastPlayedAt: Long = 0
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "position"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"],
        childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId"), Index("songKey")])
data class PlaylistItemEntity(val playlistId: String, val position: Int, val songKey: String, val addedAt: Long)
