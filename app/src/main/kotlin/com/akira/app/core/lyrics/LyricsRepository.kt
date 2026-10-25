package com.akira.app.core.lyrics

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

object LyricsRepository {
    private const val TAG = "LyricsRepository"

    private val memoryCache = ConcurrentHashMap<String, LoadedLyrics>()

    fun getCached(key: String): LoadedLyrics? = memoryCache[key]
    fun invalidateSong(songId: String,audioPath: String) {
        if(songId.isNotBlank())memoryCache.remove(songId)
        if(audioPath.isNotBlank())memoryCache.remove(audioPath)
    }

    fun cacheLyrics(key: String, lyrics: LoadedLyrics) {
        if (key.isNotBlank()) {
            memoryCache[key] = lyrics
        }
    }

    private val dirLrcCache = ConcurrentHashMap<String, Map<String, String>>()

    fun clearDirCache() {
        dirLrcCache.clear()
    }

    /**
     * Fast sidecar scan: Checks for companion .lrc file in the same directory.
     * Uses directory-level caching to avoid thousands of disk stat calls during library scanning.
     */
    fun scanAndCacheSidecarLyrics(filePath: String, songId: String = ""): Boolean {
        if (filePath.isBlank()) return false
        memoryCache.remove(filePath)
        if (songId.isNotBlank()) memoryCache.remove(songId)
        try {
            val dotIdx = filePath.lastIndexOf('.')
            if (dotIdx <= 0) return false
            val slashIdx = filePath.lastIndexOf('/')
            if (slashIdx < 0) return false
            val parentDir = filePath.substring(0, slashIdx)
            val lrcFileName = filePath.substring(slashIdx + 1, dotIdx) + ".lrc"

            val lrcSet = dirLrcCache.getOrPut(parentDir) {
                try {
                    val dir = File(parentDir)
                    if (dir.exists() && dir.isDirectory) {
                        dir.list { _, name -> name.endsWith(".lrc", ignoreCase = true) }
                            ?.associateBy { it.lowercase(java.util.Locale.ROOT) } ?: emptyMap()
                    } else emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }
            }

            val actualName = lrcSet[lrcFileName.lowercase(java.util.Locale.ROOT)]
            if (actualName != null) {
                val lrcPath = "$parentDir/$actualName"
                val lrcFile = File(lrcPath)
                if (lrcFile.exists() && lrcFile.isFile && lrcFile.length() in 1..400_000) {
                    val parsed = parseLrcText(decodeLyricBytes(lrcFile.readBytes()))
                    if (parsed.hasLyrics) {
                        memoryCache[filePath] = parsed
                        if (songId.isNotBlank()) memoryCache[songId] = parsed
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    suspend fun loadLyricsForSong(
        resolver: ContentResolver,
        song: Song
    ): LoadedLyrics = withContext(Dispatchers.IO) {
        loadLyricsForSong(
            resolver = resolver,
            contentUri = song.contentUri,
            audioPath = song.path,
            title = song.title,
            artist = song.artist,
            mimeType = song.mimeType,
            songId = song.id
        )
    }

    fun loadLyricsForSong(
        resolver: ContentResolver,
        contentUri: String,
        audioPath: String,
        title: String,
        artist: String,
        mimeType: String,
        songId: String = ""
    ): LoadedLyrics {
        // 0. Cache hit
        if (songId.isNotBlank()) {
            memoryCache[songId]?.let { return it }
        }
        if (audioPath.isNotBlank()) {
            memoryCache[audioPath]?.let { return it }
        }

        var plainFallback: List<String> = emptyList()

        // 1. Sidecar on filesystem (fast direct match)
        try {
            val file = findLyricsFile(audioPath, title, artist)
            if (file != null && file.length() in 1..400_000) {
                val parsed = parseLrcText(decodeLyricBytes(file.readBytes()))
                if (parsed.synced.isNotEmpty()) {
                    cacheResult(songId, audioPath, parsed)
                    return parsed
                }
                if (parsed.plain.isNotEmpty()) plainFallback = parsed.plain
            }
        } catch (_: Exception) {}

        // 2. Embedded tags in audio file (USLT/SYLT, Vorbis LYRICS, MP4 ©lyr)
        try {
            val embedded = readEmbeddedLyrics(resolver, contentUri, mimeType)
            if (embedded.synced.isNotEmpty()) {
                cacheResult(songId, audioPath, embedded)
                return embedded
            }
            if (embedded.plain.isNotEmpty() && plainFallback.isEmpty()) {
                plainFallback = embedded.plain
            }
        } catch (_: Exception) {}

        // 3. Sidecar via MediaStore.Files
        try {
            val audioBase = try {
                audioPath.substringAfterLast('/').substringBeforeLast('.')
            } catch (_: Exception) { "" }
            val indexed = findSidecarViaMediaStore(resolver, title, artist, audioBase)
            if (indexed.synced.isNotEmpty()) {
                cacheResult(songId, audioPath, indexed)
                return indexed
            }
            if (indexed.plain.isNotEmpty() && plainFallback.isEmpty()) {
                plainFallback = indexed.plain
            }
        } catch (_: Exception) {}

        val result = LoadedLyrics(plain = plainFallback)
        cacheResult(songId, audioPath, result)
        return result
    }

    private fun cacheResult(songId: String, audioPath: String, lyrics: LoadedLyrics) {
        if (songId.isNotBlank()) memoryCache[songId] = lyrics
        if (audioPath.isNotBlank()) memoryCache[audioPath] = lyrics
    }

    fun parseLrcText(rawText: String): LoadedLyrics = LyricsParser.parseLrcText(rawText)

    fun decodeLyricBytes(bytes: ByteArray): String = LyricsParser.decodeLyricBytes(bytes)

    private fun slugify(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
        return n.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun findLyricsFile(audioPath: String, title: String, artist: String): File? {
        if (audioPath.isBlank()) return null
        val audioFile = File(audioPath)
        val parent = audioFile.parentFile ?: return null
        if (!parent.exists() || !parent.canRead()) return null
        val base = audioFile.nameWithoutExtension

        // 1. Direct same-base name
        for (ext in listOf("lrc", "LRC", "txt", "TXT")) {
            val f = File(parent, "$base.$ext")
            if (f.exists() && f.canRead() && f.length() > 0) return f
        }

        // Subfolder "Lyrics" or "lyrics"
        val lyricsDir = File(parent, "Lyrics").takeIf { it.exists() && it.isDirectory }
            ?: File(parent, "lyrics").takeIf { it.exists() && it.isDirectory }
        if (lyricsDir != null) {
            for (ext in listOf("lrc", "LRC", "txt", "TXT")) {
                val f = File(lyricsDir, "$base.$ext")
                if (f.exists() && f.canRead() && f.length() > 0) return f
            }
        }

        val siblings = try {
            parent.listFiles { f -> f.isFile && f.canRead() && f.length() > 0 }?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
        if (siblings.isEmpty()) return null

        val lyricCandidates = siblings.filter {
            val e = it.extension.lowercase()
            e == "lrc" || e == "txt" || e == "srt" || e == "vtt"
        }
        if (lyricCandidates.isEmpty()) return null

        val baseLower = base.lowercase()
        lyricCandidates.firstOrNull { it.nameWithoutExtension.lowercase() == baseLower }?.let { return it }

        val titleSlug = slugify(title)
        val artistSlug = slugify(artist)
        if (titleSlug.length >= 3 || artistSlug.length >= 3) {
            var best: File? = null
            var bestScore = 0
            lyricCandidates.forEach { f ->
                val nameSlug = slugify(f.nameWithoutExtension)
                var score = 0
                if (titleSlug.length >= 3 && nameSlug.contains(titleSlug)) score += 3
                else if (titleSlug.length >= 3 && titleSlug.split(" ").any { w -> w.length >= 4 && nameSlug.contains(w) }) score += 1
                if (artistSlug.length >= 3 && nameSlug.contains(artistSlug)) score += 2
                if (baseLower.length >= 4 && nameSlug.contains(baseLower.take(6))) score += 2
                // An extension cannot associate an unrelated lyric with a song.
                if (score >= 3 && f.extension.lowercase() == "lrc") score += 1
                if (score > bestScore) { bestScore = score; best = f }
            }
            if (best != null && bestScore >= 3) return best
        }

        return null
    }

    private fun findSidecarViaMediaStore(
        resolver: ContentResolver,
        title: String,
        artist: String,
        audioBase: String
    ): LoadedLyrics {
        val titleSlug = slugify(title)
        val artistSlug = slugify(artist)
        val baseSlug = slugify(audioBase)
        if (titleSlug.length < 3 && artistSlug.length < 3 && baseSlug.length < 4) return LoadedLyrics()

        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )
        val sel = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.lrc' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.txt') AND ${MediaStore.Files.FileColumns.SIZE} > 0 AND ${MediaStore.Files.FileColumns.SIZE} < 400000"

        val scored = mutableListOf<Pair<Long, Int>>()
        try {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                proj,
                sel,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                var checked = 0
                while (c.moveToNext() && checked < 300) {
                    checked++
                    val name = c.getString(nameCol) ?: continue
                    val slug = slugify(name.substringBeforeLast('.'))
                    var score = 0
                    if (baseSlug.length >= 4 && slug == baseSlug) score += 10
                    else if (baseSlug.length >= 4 && slug.contains(baseSlug)) score += 5
                    if (titleSlug.length >= 3 && slug.contains(titleSlug)) score += 4
                    if (artistSlug.length >= 3 && slug.contains(artistSlug)) score += 3
                    if (name.endsWith(".lrc", ignoreCase = true)) score += 1
                    if (score >= 4) {
                        scored.add(c.getLong(idCol) to score)
                    }
                }
            }
        } catch (_: Exception) { return LoadedLyrics() }
        if (scored.isEmpty()) return LoadedLyrics()

        scored.sortByDescending { it.second }
        var plainFallback: List<String> = emptyList()
        for ((id, _) in scored.take(3)) {
            val u = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
            try {
                resolver.openInputStream(u)?.use { s ->
                    val bytes = s.readBytes()
                    val parsed = parseLrcText(decodeLyricBytes(bytes))
                    if (parsed.synced.isNotEmpty()) return parsed
                    if (parsed.plain.isNotEmpty() && plainFallback.isEmpty()) plainFallback = parsed.plain
                }
            } catch (_: Exception) {}
        }
        return LoadedLyrics(plain = plainFallback)
    }

    private fun readEmbeddedLyrics(resolver: ContentResolver, contentUri: String, mimeType: String): LoadedLyrics {
        if (contentUri.isBlank() || contentUri.startsWith("sample://")) return LoadedLyrics()
        val uri = try { Uri.parse(contentUri) } catch (_: Exception) { return LoadedLyrics() }
        val mime = mimeType.lowercase()
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                when {
                    mime.contains("flac") -> parseFlacLyrics(stream)
                    mime.contains("ogg") -> parseOggLyrics(stream)
                    mime.contains("mp4") || mime.contains("m4a") -> parseMp4Lyrics(stream)
                    else -> parseId3Lyrics(stream)
                }
            } ?: LoadedLyrics()
        } catch (_: Exception) { LoadedLyrics() }
    }

    private fun ByteArray.le32At(o: Int): Int =
        (this[o].toInt() and 0xFF) or
            ((this[o + 1].toInt() and 0xFF) shl 8) or
            ((this[o + 2].toInt() and 0xFF) shl 16) or
            ((this[o + 3].toInt() and 0xFF) shl 24)

    private fun readFullyOrNull(s: InputStream, buf: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val r = s.read(buf, off, len - off)
            if (r < 0) return false
            off += r
        }
        return true
    }

    private fun parseId3Lyrics(s: InputStream): LoadedLyrics {
        return EmbeddedLyricsReader.readId3(s)
    }


    private fun parseFlacLyrics(s: InputStream): LoadedLyrics {
        val magic = ByteArray(4)
        if (!readFullyOrNull(s, magic, 4)) return LoadedLyrics()
        if (magic[0] != 'f'.code.toByte() || magic[1] != 'L'.code.toByte() || magic[2] != 'a'.code.toByte() || magic[3] != 'C'.code.toByte()) {
            return LoadedLyrics()
        }
        var last = false
        val header = ByteArray(4)
        while (!last) {
            if (!readFullyOrNull(s, header, 4)) break
            val b0 = header[0].toInt() and 0xFF
            last = (b0 and 0x80) != 0
            val type = b0 and 0x7F
            val len = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            if (len <= 0) continue
            if (type == 4) {
                val block = ByteArray(len.coerceAtMost(512_000))
                if (!readFullyOrNull(s, block, block.size)) break
                return parseVorbisCommentBlock(block)
            } else {
                s.skip(len.toLong())
            }
        }
        return LoadedLyrics()
    }

    private fun parseOggLyrics(s: InputStream): LoadedLyrics {
        val buf = ByteArray(128_000)
        val read = s.read(buf)
        if (read < 16) return LoadedLyrics()
        val needle = "LYRICS=".toByteArray(StandardCharsets.US_ASCII)
        val idx = indexOfSubarray(buf, 0, read, needle)
        if (idx >= 0) {
            val textStart = idx + needle.size
            var textEnd = textStart
            while (textEnd < read && buf[textEnd] != 0.toByte()) textEnd++
            val raw = String(buf, textStart, textEnd - textStart, StandardCharsets.UTF_8)
            return parseLrcText(raw)
        }
        return LoadedLyrics()
    }

    private fun parseMp4Lyrics(s: InputStream): LoadedLyrics {
        return EmbeddedLyricsReader.readMp4(s)
    }


    private fun parseVorbisCommentBlock(block: ByteArray): LoadedLyrics {
        try {
            var p = 0
            if (block.size < 4) return LoadedLyrics()
            val vendorLen = block.le32At(p)
            p += 4 + vendorLen
            if (p + 4 > block.size) return LoadedLyrics()
            val count = block.le32At(p)
            p += 4
            for (i in 0 until count.coerceAtMost(200)) {
                if (p + 4 > block.size) break
                val len = block.le32At(p)
                p += 4
                if (len <= 0 || p + len > block.size) break
                val entry = String(block, p, len, StandardCharsets.UTF_8)
                p += len
                val eq = entry.indexOf('=')
                if (eq > 0) {
                    val key = entry.substring(0, eq).uppercase()
                    if (key == "LYRICS" || key == "UNSYNCEDLYRICS") {
                        val valStr = entry.substring(eq + 1)
                        val parsed = parseLrcText(valStr)
                        if (parsed.hasLyrics) return parsed
                    }
                }
            }
        } catch (_: Exception) {}
        return LoadedLyrics()
    }

    private fun indexOfSubarray(haystack: ByteArray, off: Int, len: Int, needle: ByteArray): Int {
        val limit = (off + len) - needle.size
        for (i in off..limit) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { match = false; break }
            }
            if (match) return i
        }
        return -1
    }
}
