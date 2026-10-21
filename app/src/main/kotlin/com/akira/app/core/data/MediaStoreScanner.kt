package com.akira.app.core.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.akira.app.core.design.theme.PlayerTone
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import java.io.IOException
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val context: Context) {

    fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanLocalSongs(
        minDurationSeconds: Int = 30,
        excludedPaths: Set<String> = emptySet(),
        whitelistedSongIds: Set<String> = emptySet()
    ): List<Song> = scanSongsInFolder("all").filter { song ->
        passesLibraryFilters(song, minDurationSeconds, excludedPaths, whitelistedSongIds)
    }

    suspend fun scanSongsInFolder(
        folderPath: String,
        onProgress: suspend (currentCount: Int, currentSongTitle: String) -> Unit = { _, _ -> },
        throwOnFailure: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        if (!hasStoragePermission()) {
            if (throwOnFailure) throw SecurityException("Audio permission unavailable")
            return@withContext emptyList()
        }

        val songs = mutableListOf<Song>()
        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.AudioColumns.BITRATE)
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }
        val projection = projectionList.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            val scanCursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            ) ?: throw IOException("MediaStore returned no cursor")
            scanCursor.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.AudioColumns.BITRATE)
                } else -1

                val tones = PlayerTone.entries
                val normalizedTarget = folderPath.trim().trimEnd('/').lowercase()

                val scanStartMs = System.currentTimeMillis()
                var lastProgressTimeMs = 0L

                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val filePath = cursor.getString(dataCol) ?: ""

                    val matchesFolder = if (normalizedTarget.isEmpty() || normalizedTarget == "all") {
                        true
                    } else {
                        belongsToFolder(filePath, normalizedTarget)
                    }

                    if (!matchesFolder) {
                        continue
                    }

                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val indexedDuration = if (cursor.isNull(durationCol)) 0L else cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val track = cursor.getInt(trackCol)
                    val year = cursor.getInt(yearCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val duration = AudioDurationReader.read(context, contentUri, indexedDuration, cursor.getLong(dateModifiedCol))
                    val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                    val toneIndex = (album.hashCode() xor artist.hashCode()).mod(tones.size)

                    val format = when {
                        mime.contains("flac", ignoreCase = true) -> "FLAC"
                        mime.contains("wav", ignoreCase = true) -> "WAV"
                        mime.contains("ogg", ignoreCase = true) -> "OGG"
                        mime.contains("aac", ignoreCase = true) -> "AAC"
                        mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> "M4A"
                        else -> "MP3"
                    }

                    val hasLyrics = com.akira.app.core.lyrics.LyricsRepository.scanAndCacheSidecarLyrics(filePath, id.toString())

                    val rawBitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0
                    val bitrateLabel = when {
                        rawBitrate >= 1_000_000 -> "${rawBitrate / 1_000_000} Mbps"
                        rawBitrate >= 1_000 -> "${rawBitrate / 1000} kbps"
                        format == "FLAC" -> "FLAC"
                        else -> format
                    }

                    val song = Song(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        genre = if (genreCol >= 0) cursor.getString(genreCol).orEmpty() else "",
                        durationMs = duration,
                        contentUri = contentUri,
                        albumArtUri = albumArtUri,
                        albumId = albumId,
                        tone = tones[toneIndex],
                        isFavorite = false,
                        year = year,
                        trackNumber = track,
                        dateAddedSec = cursor.getLong(dateAddedCol),
                        dateModifiedSec = cursor.getLong(dateModifiedCol),
                        format = format,
                        bitrate = bitrateLabel,
                        mimeType = mime,
                        path = filePath,
                        hasLyrics = hasLyrics
                    )
                    songs.add(song)

                    val now = System.currentTimeMillis()
                    if (songs.size == 1 || songs.size % 10 == 0 || (now - lastProgressTimeMs) >= 30L) {
                        lastProgressTimeMs = now
                        onProgress(songs.size, title)
                    }
                }
                com.akira.app.core.lyrics.LyricsRepository.clearDirCache()
                // Ensure final count is emitted
                if (songs.isNotEmpty()) {
                    onProgress(songs.size, "Finalizing library...")
                }
                val elapsed = System.currentTimeMillis() - scanStartMs
                Log.d("MediaStoreScanner", "escaneo scanSongsInFolder: ${songs.size} canciones en ${elapsed}ms")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            com.akira.app.core.logging.AkiraLog.e("MediaStoreScanner", "Audio scan failed", error)
            if (throwOnFailure) throw error
            return@withContext emptyList()
        } finally {
            com.akira.app.core.lyrics.LyricsRepository.clearDirCache()
        }

        // Sin datos de ejemplo: si no hay canciones o falta el permiso devolvemos vacío
        // para que la UI muestre el estado vacío real en vez de canciones falsas.
        songs
    }

    /**
     * Observa cambios en MediaStore (canciones agregadas, eliminadas o modificadas por el usuario).
     */
    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        trySend(Unit) // Emisión inicial
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.conflate().flowOn(Dispatchers.IO)
}
