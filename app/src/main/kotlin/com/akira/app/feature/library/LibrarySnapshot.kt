package com.akira.app.feature.library

import com.akira.app.core.model.Song

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val initialized: Boolean = false,
    val loadFailed: Boolean = false
)

internal fun librarySnapshot(
    raw: List<Song>?, favoriteIds: Set<String>, failed: Boolean, include: (Song) -> Boolean
): LibraryUiState = if (raw == null) LibraryUiState(loadFailed = failed) else LibraryUiState(
    songs = buildList {
        for (song in raw) {
            if (!include(song)) continue
            val favorite = song.id in favoriteIds
            add(if (song.isFavorite == favorite) song else song.copy(isFavorite = favorite))
        }
    },
    initialized = true,
    loadFailed = failed
)
