package com.akira.app.core.online

import com.akira.innertube.models.SongItem
import java.util.Locale
import kotlin.random.Random

/** Sampling is over provider recommendations, not an enumeration of the global catalogue. */
internal fun discoveryPool(discoveries: List<SongItem>, related: List<SongItem>, familiar: List<SongItem>,
    recentWinners: Set<String>, currentId: String?, random: Random = Random): List<SongItem> {
    val sources = listOf(discoveries, related, familiar).map { items -> items.distinctBy { it.id }.shuffled(random).toMutableList() }
    val result = linkedMapOf<String, SongItem>()
    val artists = mutableMapOf<String, Int>()
    val available = sources.flatten().distinctBy { it.id }
    val avoid = if(available.count { it.id !in recentWinners && it.id != currentId } >= 12) recentWinners else emptySet()
    // Interleave sources so a long playlist cannot drown out discovery or contextual radio.
    val schedule = listOf(0, 1, 0, 2, 1, 0)
    repeat(120) { turn ->
        if(result.size >= 60) return@repeat
        val bucket = sources[schedule[turn % schedule.size]]
        val index = bucket.indexOfFirst { song ->
            val artist = song.artists.firstOrNull()?.name.orEmpty().trim().lowercase(Locale.ROOT)
            song.id != currentId && song.id !in avoid && song.id !in result && (artists[artist] ?: 0) < 3
        }
        if(index >= 0) {
            val song = bucket.removeAt(index)
            result[song.id] = song
            val artist = song.artists.firstOrNull()?.name.orEmpty().trim().lowercase(Locale.ROOT)
            artists[artist] = (artists[artist] ?: 0) + 1
        }
    }
    if(result.size < 12) available.shuffled(random).filter { it.id != currentId && it.id !in avoid }.forEach {
        if(result.size < 12) result.putIfAbsent(it.id, it)
    }
    return result.values.shuffled(random)
}
