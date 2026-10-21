package com.akira.app.core.lyrics

import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Permanent per-song downloads, separate from provider caches. Call on IO. */
class SavedLyricsStore(private val directory: File) {
    fun delete(key: String): Boolean = file(key).let { !it.exists() || it.delete() }
    private fun file(key: String) = File(directory,
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".lyrics")

    fun read(key: String): LoadedLyrics? = try {
        val target = file(key)
        if (target.length() !in 1..2_000_000) null else DataInputStream(target.inputStream().buffered()).use { input ->
            require(input.readInt() == 1)
            fun count() = input.readInt().also { require(it in 0..20_000) }
            val synced = List(count()) {
                val time = input.readLong().also { require(it >= 0) }
                val text = input.readUTF()
                val vocalist = Vocalist.entries[input.readInt()]
                val words = List(count()) { LyricWord(input.readLong(), input.readUTF()) }
                LyricLine(time, text, words, vocalist)
            }
            LoadedLyrics(synced, List(count()) { input.readUTF() }).takeIf { it.hasLyrics }
        }
    } catch (_: Exception) { null }

    fun save(key: String, lyrics: LoadedLyrics) {
        require(key.isNotBlank() && lyrics.hasLyrics)
        check(directory.isDirectory || directory.mkdirs())
        val target = file(key)
        val temp = File.createTempFile("download-", ".tmp", directory)
        try {
            DataOutputStream(temp.outputStream().buffered()).use { out ->
                out.writeInt(1); out.writeInt(lyrics.synced.size)
                lyrics.synced.forEach { line ->
                    out.writeLong(line.timeMs); out.writeUTF(line.text); out.writeInt(line.vocalist.ordinal)
                    out.writeInt(line.words.size)
                    line.words.forEach { out.writeLong(it.timeMs); out.writeUTF(it.text) }
                }
                out.writeInt(lyrics.plain.size); lyrics.plain.forEach { out.writeUTF(it) }
            }
            check(temp.length() <= 2_000_000)
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }
}
