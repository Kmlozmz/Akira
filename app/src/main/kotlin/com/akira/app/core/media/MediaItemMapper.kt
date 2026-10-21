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

    /** Identity lookup in queue edits must not allocate a complete Song per comparison. */
    fun MediaItem.queueIdentity(): String = mediaMetadata.extras?.getString(KEY_QUEUE_ENTRY) ?: mediaId

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
