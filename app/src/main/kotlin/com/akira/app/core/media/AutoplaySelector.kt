package com.akira.app.core.media

import com.akira.app.core.model.Song
import java.util.Random

/** Selección local determinista por grupos, dejando aleatoriedad solo al final. */
object AutoplaySelector {
    fun select(
        candidates: List<Song>,
        currentArtist: String,
        currentGenre: String,
        limit: Int = 10,
        random: Random = Random(),
        excludedIds: Set<String> = emptySet()
    ): List<Song> {
        val eligible = candidates.filterNot { it.id in excludedIds }
        val selected = LinkedHashMap<String, Song>()
        fun add(songs: Iterable<Song>) {
            for (song in songs) {
                if (selected.size >= limit) break
                selected.putIfAbsent(song.id, song)
            }
        }
        add(eligible.filter { currentArtist.isNotBlank() && it.artist.equals(currentArtist, ignoreCase = true) })
        add(eligible.filter { it.genre.isNotBlank() && it.genre.equals(currentGenre, ignoreCase = true) })
        add(eligible.filter { it.isFavorite })
        add(eligible.filterNot { it.id in selected }.shuffled(random))
        return selected.values.take(limit)
    }
}
