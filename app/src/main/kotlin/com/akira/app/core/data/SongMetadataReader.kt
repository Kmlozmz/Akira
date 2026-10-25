package com.akira.app.core.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.akira.app.core.i18n.AkiraStrings
import com.akira.app.core.lyrics.LyricsRepository
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class SongDetailsModel(
    val album: String,
    val albumArtist: String,
    val year: String,
    val trackAndDisc: String,
    val genre: String,
    val realCodec: String,
    val container: String,
    val bitrate: String,
    val sampleRate: String,
    val bitDepth: String,
    val channels: String,
    val audioBadge: String,
    val isLossless: Boolean,
    val isHiRes: Boolean,
    val folder: String,
    val fileName: String,
    val fileSize: String,
    val dateAdded: String,
    val dateModified: String,
    val lyricsSource: String,
    val lyricsSync: String,
    val composer: String = "",
    val releaseDate: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val isrc: String = "",
    val label: String = "",
    val copyright: String = ""
)

object SongMetadataReader {

    private val memoryCache = ConcurrentHashMap<String, SongDetailsModel>()

    suspend fun getSongDetails(
        context: Context,
        song: Song,
        strings: AkiraStrings
    ): SongDetailsModel = withContext(Dispatchers.IO) {
        val file = if (song.path.isNotBlank()) File(song.path) else null
        val lastMod = file?.takeIf { it.exists() }?.lastModified() ?: song.dateModifiedSec * 1000L
        val cacheKey = "${song.path.ifBlank { song.contentUri }}|$lastMod|${strings.hashCode()}"

        memoryCache[cacheKey]?.let { return@withContext it }

        val details = extractDetails(context, song, file, strings)
        memoryCache[cacheKey] = details
        details
    }

    private suspend fun extractDetails(
        context: Context,
        song: Song,
        file: File?,
        strings: AkiraStrings
    ): SongDetailsModel {
        val online = song.contentUri.startsWith("akira-youtube:")
        val tags = readFlacDetailTags(context, song)
        var composer = ""
        var releaseDate = ""
        var albumArtist = ""
        var genre = ""
        var discNumber = ""
        var cdTrack = ""
        var mmrBitrate = 0
        var mmrSampleRate = 0
        var mmrBitsPerSample: Int? = null

        val mmr = MediaMetadataRetriever()
        try {
            if (file != null && file.exists()) {
                mmr.setDataSource(file.absolutePath)
            } else {
                mmr.setDataSource(context, Uri.parse(song.contentUri))
            }
            composer = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER).orEmpty()
            releaseDate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE).orEmpty()
            albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: ""
            genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            discNumber = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER) ?: ""
            cdTrack = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: ""
            mmrBitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mmrSampleRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                mmrBitsPerSample = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }

        var extractedMime: String? = null
        var extractorSampleRate = 0
        var extractorChannels = 2
        var extractorBitrate = 0
        var extractorPcmEncoding = 0

        val extractor = MediaExtractor()
        try {
            if (file != null && file.exists()) {
                extractor.setDataSource(file.absolutePath)
            } else {
                extractor.setDataSource(context, Uri.parse(song.contentUri), null)
            }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    extractedMime = mime
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        extractorSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        extractorChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        extractorBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        extractorPcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        val ext = file?.extension?.uppercase() ?: when {
            song.mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            song.mimeType.contains("wav", ignoreCase = true) -> "WAV"
            song.mimeType.contains("mp4", ignoreCase = true) || song.mimeType.contains("m4a", ignoreCase = true) -> "M4A"
            song.mimeType.contains("ogg", ignoreCase = true) -> "OGG"
            song.mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            else -> "MP3"
        }
        val container = ext.ifBlank { song.format.uppercase() }

        // M4A can contain AAC or ALAC; prefer the extracted audio codec.
        val realCodec = when {
            extractedMime?.contains("alac", ignoreCase = true) == true -> "ALAC"
            extractedMime?.contains("mp4a-latm", ignoreCase = true) == true || container == "AAC" -> "AAC"
            extractedMime?.contains("flac", ignoreCase = true) == true || container == "FLAC" -> "FLAC"
            extractedMime?.contains("mpeg", ignoreCase = true) == true || container == "MP3" -> "MP3"
            extractedMime?.contains("opus", ignoreCase = true) == true || container == "OPUS" -> "Opus"
            extractedMime?.contains("vorbis", ignoreCase = true) == true || container == "OGG" -> "Vorbis"
            extractedMime?.contains("raw", ignoreCase = true) == true || container == "WAV" -> "PCM (WAV)"
            else -> extractedMime?.removePrefix("audio/")?.uppercase() ?: container
        }

        var bitDepth = when (extractorPcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> mmrBitsPerSample ?: 0
        }

        // FLAC STREAMINFO stores bit depth when Android omits it.
        if (bitDepth <= 0 && container == "FLAC" && file != null && file.exists()) {
            try {
                file.inputStream().use { stream ->
                    val header = ByteArray(34)
                    if (stream.read(header) == 34) {
                        if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                            header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
                        ) {
                            val bitsPerSample = (((header[20].toInt() and 0x01) shl 4) or ((header[21].toInt() ushr 4) and 0x0F)) + 1
                            if (bitsPerSample in 4..32) {
                                bitDepth = bitsPerSample
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val finalSampleRateHz = when {
            extractorSampleRate > 0 -> extractorSampleRate
            mmrSampleRate > 0 -> mmrSampleRate
            else -> 0
        }
        val sampleRateStr = if (finalSampleRateHz > 0) {
            val khz = finalSampleRateHz / 1000.0
            if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", khz)
        } else "—"

        val finalBitrateBps = when {
            extractorBitrate > 0 -> extractorBitrate
            mmrBitrate > 0 -> mmrBitrate
            file != null && file.exists() && song.durationMs > 0 -> {
                ((file.length() * 8) / (song.durationMs / 1000.0)).toInt()
            }
            else -> 0
        }
        val bitrateStr = when {
            finalBitrateBps >= 1_000_000 -> String.format(Locale.US, "%.2f Mbps", finalBitrateBps / 1_000_000.0)
            finalBitrateBps >= 1_000 -> "${finalBitrateBps / 1000} kbps"
            else -> "—"
        }

        val channelsStr = when (extractorChannels) {
            1 -> strings.audioMono
            2 -> strings.audioStereo
            else -> "$extractorChannels ${strings.audioChannels.lowercase()}"
        }

        val classification = classifyAudioMetadata(realCodec, container, finalSampleRateHz, bitDepth)
        val isLossless = classification.isLossless
        val isHiRes = classification.isHiRes

        val audioBadge = when {
            isHiRes -> "${strings.lossless} · ${strings.hiRes}"
            isLossless -> strings.lossless
            else -> strings.audioLossy
        }

        val bitDepthStr = if (bitDepth > 0) "$bitDepth-bit" else if (isLossless) "16-bit" else "—"

        val cleanFolder = audioMetadataFolder(song.path) ?: "—"

        val fileNameStr = file?.name ?: song.title
        val fileSizeStr = if (file != null && file.exists() && file.length() > 0) {
            val mb = file.length() / (1024.0 * 1024.0)
            String.format(Locale.US, "%.2f MB", mb)
        } else "—"

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateAddedStr = if (song.dateAddedSec > 0) {
            dateFormat.format(Date(song.dateAddedSec * 1000L))
        } else "—"

        val dateModifiedStr = if (song.dateModifiedSec > 0) {
            dateFormat.format(Date(song.dateModifiedSec * 1000L))
        } else if (file != null && file.exists()) {
            dateFormat.format(Date(file.lastModified()))
        } else "—"

        val sidecarLrc = if (file != null && file.exists()) {
            LyricsRepository.findLyricsFile(file.absolutePath, song.title, song.artist)
        } else null

        val loadedLyrics = LyricsRepository.loadLyricsForSong(context.contentResolver, song)
        val (lyricsSourceStr, lyricsSyncStr) = when {
            sidecarLrc != null && sidecarLrc.exists() -> {
                strings.lyricsSidecar to if (loadedLyrics.synced.isNotEmpty()) strings.lyricsSynced else strings.lyricsNotSynced
            }
            loadedLyrics.hasLyrics -> {
                strings.lyricsEmbedded to if (loadedLyrics.synced.isNotEmpty()) strings.lyricsSynced else strings.lyricsNotSynced
            }
            else -> {
                strings.lyricsNone to "—"
            }
        }

        val trackAndDiscStr = buildString {
            if (song.trackNumber > 0) {
                append(song.trackNumber)
            } else if (cdTrack.isNotBlank() && cdTrack != "0") {
                append(cdTrack)
            } else {
                append("—")
            }
            if (discNumber.isNotBlank() && discNumber != "0") {
                append(" (Disco $discNumber)")
            }
        }

        return SongDetailsModel(
            album = song.album.ifBlank { "—" },
            albumArtist = tags["ALBUMARTIST"] ?: albumArtist.ifBlank { song.artist.ifBlank { "—" } },
            year = if (song.year > 0) song.year.toString() else "—",
            trackAndDisc = trackAndDiscStr,
            genre = tags["GENRE"] ?: genre.ifBlank { "—" },
            realCodec = if (online) "—" else realCodec,
            container = if (online) "—" else container,
            bitrate = bitrateStr,
            sampleRate = sampleRateStr,
            bitDepth = bitDepthStr,
            channels = channelsStr,
            audioBadge = audioBadge,
            isLossless = isLossless,
            isHiRes = isHiRes,
            folder = cleanFolder,
            fileName = fileNameStr,
            fileSize = fileSizeStr,
            dateAdded = dateAddedStr,
            dateModified = dateModifiedStr,
            lyricsSource = lyricsSourceStr,
            lyricsSync = lyricsSyncStr,
            composer = tags["COMPOSER"] ?: composer,
            releaseDate = tags["DATE"] ?: releaseDate.ifBlank { if (song.year > 0) song.year.toString() else "" },
            trackNumber = tags["TRACKNUMBER"]?.substringBefore('/') ?: song.trackNumber.takeIf { it > 0 }?.toString() ?: cdTrack.substringBefore('/'),
            discNumber = tags["DISCNUMBER"]?.substringBefore('/') ?: discNumber.substringBefore('/'),
            isrc = tags["ISRC"].orEmpty(),
            label = tags["LABEL"] ?: tags["ORGANIZATION"] ?: tags["PUBLISHER"].orEmpty(),
            copyright = tags["COPYRIGHT"].orEmpty()
        )
    }
}
