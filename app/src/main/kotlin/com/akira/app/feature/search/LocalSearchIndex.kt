package com.akira.app.feature.search

import com.akira.app.core.model.Song
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private val CombiningMarks = Regex("\\p{M}+")
private val Apostrophes = Regex("['’]")
private val Separators = Regex("[^\\p{L}\\p{N}]+")

internal fun normalizeSearchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(CombiningMarks, "").lowercase(Locale.ROOT).replace(Apostrophes, "")
    .replace(Separators, " ").trim()

internal class LocalSearchIndex private constructor(private val tracks: List<IndexedSong>) {
    private data class IndexedSong(val song: Song, val title: String, val artist: String, val album: String, val genre: String)

    suspend fun search(query: String): List<Song> {
        val phrase = normalizeSearchText(query)
        if (phrase.isEmpty()) return emptyList()
        val terms = phrase.split(' ')
        val matches = mutableListOf<Pair<Song, Int>>()
        for ((index, entry) in tracks.withIndex()) {
            if (index % 128 == 0) currentCoroutineContext().ensureActive()
            if (!terms.all { it in entry.title || it in entry.artist || it in entry.album || it in entry.genre }) continue
            val rank = when {
                entry.title == phrase -> 0
                entry.title.startsWith(phrase) -> 1
                phrase in entry.title -> 2
                entry.artist == phrase || entry.album == phrase -> 3
                else -> 4
            }
            matches.add(entry.song to rank)
        }
        return matches.sortedBy { it.second }.map { it.first }
    }

    companion object {
        suspend fun build(songs: List<Song>): LocalSearchIndex = LocalSearchIndex(songs.mapIndexed { index, song ->
            if (index % 128 == 0) currentCoroutineContext().ensureActive()
            IndexedSong(song, normalizeSearchText(song.title), normalizeSearchText(song.artist),
                normalizeSearchText(song.album), normalizeSearchText(song.genre))
        })
    }
}
