package com.akira.app.core.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.akira.app.core.design.theme.PlayerTone
import com.akira.app.core.model.Song

object MediaItemMapper {

    private const val KEY_DURATION = "extra_duration"
    private const val KEY_TONE_HEX = "extra_tone_hex"
    private const val KEY_FORMAT = "extra_format"
    private const val KEY_BITRATE = "extra_bitrate"
    private const val KEY_YEAR = "extra_year"
    private const val KEY_GENRE = "extra_genre"
    private const val KEY_PATH = "extra_path"
    private const val KEY_QUEUE_ENTRY = "extra_queue_entry"
    private const val KEY_ALBUM_ID = "extra_album_id"
    private const val KEY_MIME_TYPE = "extra_mime_type"
    private const val KEY_TRACK_NUMBER = "extra_track_number"
    private const val KEY_DATE_ADDED = "extra_date_added"
    private const val KEY_DATE_MODIFIED = "extra_date_modified"
    private const val KEY_HAS_LYRICS = "extra_has_lyrics"
    private const val KEY_FAVORITE = "extra_favorite"
    private const val KEY_ONLINE_GENERATION = "extra_online_queue_generation"

    /** Identity lookup in queue edits must not allocate a complete Song per comparison. */
    fun MediaItem.queueIdentity(): String = mediaMetadata.extras?.getString(KEY_QUEUE_ENTRY) ?: mediaId

    internal fun MediaItem.onlineQueueGeneration(): Long =
        mediaMetadata.extras?.getLong(KEY_ONLINE_GENERATION) ?: 0L

    fun Song.toMediaItem(entryId: String = java.util.UUID.randomUUID().toString()): MediaItem {
        val extras = Bundle().apply {
            putLong(KEY_DURATION, durationMs)
            putLong(KEY_TONE_HEX, tone.hex)
            putString(KEY_FORMAT, format)
            putString(KEY_BITRATE, bitrate)
            putInt(KEY_YEAR, year)
            putString(KEY_GENRE, genre)
            putString(KEY_PATH, path)
            putString(KEY_QUEUE_ENTRY, entryId)
            putLong(KEY_ALBUM_ID, albumId)
            putString(KEY_MIME_TYPE, mimeType)
            putInt(KEY_TRACK_NUMBER, trackNumber)
            putLong(KEY_DATE_ADDED, dateAddedSec)
            putLong(KEY_DATE_MODIFIED, dateModifiedSec)
            putBoolean(KEY_HAS_LYRICS, hasLyrics)
            putBoolean(KEY_FAVORITE, isFavorite)
            putLong(KEY_ONLINE_GENERATION, com.akira.app.core.online.SourceQueueStore.onlineGeneration)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)
            .setArtworkUri(albumArtUri?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .setExtras(extras)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(if (contentUri.isNotBlank()) Uri.parse(contentUri) else null)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(contentUri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)
            .build()
    }

    fun MediaItem.toSong(): Song {
        val meta = mediaMetadata
        val extras = meta.extras
        val durationMs = extras?.getLong(KEY_DURATION) ?: 0L
        val toneHex = extras?.getLong(KEY_TONE_HEX) ?: PlayerTone.INDIGO.hex
        val tone = PlayerTone.entries.firstOrNull { it.hex == toneHex } ?: PlayerTone.INDIGO
        val format = extras?.getString(KEY_FORMAT).orEmpty()
        val bitrate = extras?.getString(KEY_BITRATE).orEmpty()
        val year = extras?.getInt(KEY_YEAR) ?: 0

        val uriStr = localConfiguration?.uri?.toString()
            ?: requestMetadata.mediaUri?.toString()
            ?: if (mediaId.startsWith("content://") || mediaId.startsWith("file://")) mediaId else ""

        return Song(
            id = mediaId,
            title = meta.title?.toString() ?: "Desconocido",
            artist = meta.artist?.toString() ?: "Artista Desconocido",
            album = meta.albumTitle?.toString() ?: "Álbum Desconocido",
            durationMs = durationMs,
            contentUri = uriStr,
            albumArtUri = meta.artworkUri?.toString(),
            albumId = extras?.getLong(KEY_ALBUM_ID) ?: 0L,
            mimeType = extras?.getString(KEY_MIME_TYPE) ?: localConfiguration?.mimeType ?: "audio/*",
            trackNumber = extras?.getInt(KEY_TRACK_NUMBER) ?: 0,
            dateAddedSec = extras?.getLong(KEY_DATE_ADDED) ?: 0L,
            dateModifiedSec = extras?.getLong(KEY_DATE_MODIFIED) ?: 0L,
            hasLyrics = extras?.getBoolean(KEY_HAS_LYRICS) ?: false,
            isFavorite = extras?.getBoolean(KEY_FAVORITE) ?: false,
            tone = tone,
            year = year,
            genre = (meta.genre?.toString() ?: extras?.getString(KEY_GENRE)).orEmpty(),
            path = extras?.getString(KEY_PATH).orEmpty(),
            format = format,
            bitrate = bitrate,
            queueEntryId = extras?.getString(KEY_QUEUE_ENTRY) ?: mediaId
        )
    }
}
