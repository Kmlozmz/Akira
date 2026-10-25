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
    songs = raw.filter(include).map { it.copy(isFavorite = it.id in favoriteIds) },
    initialized = true,
    loadFailed = failed
)
