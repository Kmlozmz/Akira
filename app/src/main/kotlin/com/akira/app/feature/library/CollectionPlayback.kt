package com.akira.app.feature.library

import com.akira.app.core.model.Song

internal data class CollectionPlayback(val song: Song, val queue: List<Song>, val shuffle: Boolean)

internal fun collectionPlayback(song: Song, collection: List<Song>, shuffle: Boolean): CollectionPlayback? {
    val selected = collection.firstOrNull { it.id == song.id } ?: return null
    return CollectionPlayback(selected, collection.toList(), shuffle)
}
