package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*

import com.akira.app.core.database.model.PlayStatsEntity
import com.akira.app.core.model.Song
import com.akira.app.core.model.SongKey

object SmartPlaylists {
    fun calculate(songs: List<Song>, stats: List<PlayStatsEntity>, now: Long): List<List<Song>> {
        val byKey = stats.associateBy { it.songKey }
        val day = 86_400_000L
        return listOf(
            songs.filter { it.dateAddedSec * 1000L in (now - 30 * day)..now }.sortedByDescending { it.dateAddedSec }.take(100),
            songs.filter { (byKey[SongKey.of(it).encoded]?.playCount ?: 0) > 0 }
                .sortedByDescending { byKey[SongKey.of(it).encoded]?.playCount }.take(50),
            songs.filter { (byKey[SongKey.of(it).encoded]?.playCount ?: 0) == 0L },
            songs.filter { it.isFavorite && (byKey[SongKey.of(it).encoded]?.lastPlayedAt ?: 0L) < now - 90 * day }
        )
    }
}
