package com.akira.app.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val albumId: Long = 0L,
    val addedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val toneHex: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val iconName: String = "queue_music"
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val orderIndex: Int = 0,
    val addedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val playedAtMillis: Long = System.currentTimeMillis()
)
