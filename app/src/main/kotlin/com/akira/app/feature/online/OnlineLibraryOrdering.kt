package com.akira.app.feature.online

import com.akira.app.feature.library.SortField
import com.akira.app.feature.library.SortOrder
import com.akira.app.core.online.onlineSubtitle
import com.akira.innertube.models.*
import java.text.Collator

internal fun orderOnlineLibraryItems(items: List<YTItem>, query: String, order: SortOrder): List<YTItem> {
    val entries = items.distinctBy { it.id }.filter {
        query.isBlank() || it.title.contains(query, true) || it.onlineSubtitle().contains(query, true)
    }
    // The service supplies an order, but no per-item incorporation timestamp.
    if (order.field == SortField.DATE_ADDED) return if (order.ascending) entries.reversed() else entries
    val collator = Collator.getInstance()
    fun key(item: YTItem): String = when {
        order.field != SortField.ARTIST -> item.title
        item is SongItem -> item.artists.joinToString { it.name }
        item is AlbumItem -> item.artists.orEmpty().joinToString { it.name }
        else -> item.title
    }
    val sorted = entries.sortedWith { a, b -> collator.compare(key(a), key(b)) }
    return if (order.ascending) sorted else sorted.reversed()
}
