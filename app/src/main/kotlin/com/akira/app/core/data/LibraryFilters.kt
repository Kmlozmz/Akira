package com.akira.app.core.data

import com.akira.app.core.model.Song

/** Missing metadata is not proof that a complete file is a short track. */
internal fun isShortTrack(durationMs: Long, minimumSeconds: Int): Boolean =
    durationMs > 0L && durationMs < minimumSeconds.coerceAtLeast(0) * 1000L

internal fun passesLibraryFilters(song: Song, minimumSeconds: Int, excluded: Set<String>, allowed: Set<String>): Boolean =
    song.id in allowed || (!isShortTrack(song.durationMs, minimumSeconds) && excluded.none { belongsToFolder(song.path, it) })

internal fun resolveIndexedDuration(indexedMs: Long, readMetadata: () -> Long): Long =
    if (indexedMs > 0L) indexedMs else readMetadata().coerceAtLeast(0L)

/** Matches directory boundaries: Music must never also exclude Music Videos. */
internal fun belongsToFolder(filePath: String, folderPath: String): Boolean {
    val directory = folderPath.replace('\\', '/').trim().trimEnd('/')
    if (directory.isEmpty()) return false
    val path = filePath.replace('\\', '/')
    return if (directory.startsWith('/')) {
        path.startsWith("$directory/", ignoreCase = true)
    } else {
        path.startsWith("$directory/", ignoreCase = true) || path.contains("/$directory/", ignoreCase = true)
    }
}
