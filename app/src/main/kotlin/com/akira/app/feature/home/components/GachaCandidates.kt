package com.akira.app.feature.home.components

import com.akira.app.core.model.Song
import kotlin.random.Random

internal fun reconcileGachaCandidates(
    current: List<Song>, pool: List<Song>, limit: Int = 10, random: Random = Random.Default
): List<Song> {
    val available = pool.distinctBy { it.id }.associateBy { it.id }
    val retained = current.distinctBy { it.id }.mapNotNull { available[it.id] }.take(limit)
    val retainedIds = retained.map { it.id }.toSet()
    return retained + available.values.filterNot { it.id in retainedIds }.shuffled(random).take(limit - retained.size)
}

internal fun currentGachaWinner(candidate: Song, pool: List<Song>): Song? = pool.firstOrNull { it.id == candidate.id }
