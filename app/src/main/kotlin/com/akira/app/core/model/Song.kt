package com.akira.app.core.model

import com.akira.app.core.design.theme.PlayerTone

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String? = null,
    val albumId: Long = 0L,
    val tone: PlayerTone = PlayerTone.INDIGO,
    val isFavorite: Boolean = false,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val format: String = "",
    val bitrate: String = "",
    val mimeType: String = "audio/*",
    val path: String = "",
    // Segundos desde epoch (MediaStore.DATE_ADDED / DATE_MODIFIED). 0 = desconocido.
    val dateAddedSec: Long = 0L,
    val dateModifiedSec: Long = 0L,
    val hasLyrics: Boolean = false,
    val genre: String = "",
    // Occurrence in the playback queue; SongKey and library identity still use id/path.
    val queueEntryId: String = id
) {
    val durationSeconds: Int get() = (durationMs / 1000).toInt()
}

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val tone: PlayerTone,
    val songs: List<Song> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val songCount: Int,
    val year: Int,
    val tone: PlayerTone
)

data class Artist(
    val name: String,
    val songCount: Int,
    val tone: PlayerTone
)
