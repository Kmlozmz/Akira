package com.akira.app.core.online

import com.akira.innertube.models.SongItem
import java.util.Locale
import kotlin.random.Random

internal val onlineVideoIdPattern = Regex("[A-Za-z0-9_-]{11}")

/** Seeds come from separate signals, so the last played song cannot anchor every refresh. */
internal fun listeningSeeds(
    favorites: List<SongItem>,
    frequent: List<SongItem>,
    history: List<SongItem>,
    latest: SongItem?,
    random: Random,
    limit: Int = 3,
): List<SongItem> {
    val candidates = listOf(frequent, favorites, history)
        .map { source ->
            source.distinctBy { it.id }.filter { it.id != latest?.id && it.id.matches(onlineVideoIdPattern) }
                .take(16).shuffled(random)
        }
    val artists = mutableSetOf<String>()
    val seeds = interleave(candidates).distinctBy { it.id }.filter { artists.add(it.artistKey()) }.take(limit).toList()
    return seeds.ifEmpty { listOfNotNull(latest?.takeIf { it.id.matches(onlineVideoIdPattern) }) }
}

internal fun recommendationTracks(
    sources: List<List<SongItem>>,
    recentlyPlayed: Set<String>,
    excluded: Set<String>,
    limit: Int,
    allowFamiliar: Boolean = false,
): List<SongItem> {
    val candidates = interleave(sources).distinctBy { it.id }.filter { it.id !in excluded && it.id.matches(onlineVideoIdPattern) }
    val artistCounts = mutableMapOf<String, Int>()
    val result = mutableListOf<SongItem>()
    fun add(items: Sequence<SongItem>, artistLimit: Int) {
        for (song in items) {
            if (result.size == limit) break
            val artist = song.artistKey()
            val count = artistCounts.getOrDefault(artist, 0)
            if (count < artistLimit) {
                result += song
                artistCounts[artist] = count + 1
            }
        }
    }
    val fresh = candidates.filterNot { it.id in recentlyPlayed }
    add(fresh, artistLimit = 2)
    if (result.size < limit) {
        val selected = result.mapTo(hashSetOf()) { it.id }
        add(fresh.filterNot { it.id in selected }, artistLimit = 4)
    }
    if (allowFamiliar && result.size < limit) {
        add(candidates.filter { it.id in recentlyPlayed }, artistLimit = 4)
    }
    return result
}

private fun interleave(sources: List<List<SongItem>>): Sequence<SongItem> = sequence {
    val count = sources.maxOfOrNull { it.size } ?: 0
    for (index in 0 until count) {
        for (bucket in sources) {
            bucket.getOrNull(index)?.let { yield(it) }
        }
    }
}

private fun SongItem.artistKey(): String =
    artists.firstOrNull()?.name?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank) ?: id
