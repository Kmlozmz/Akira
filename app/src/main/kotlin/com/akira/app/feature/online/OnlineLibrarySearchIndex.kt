package com.akira.app.feature.online

import com.akira.app.core.online.OnlinePage
import com.akira.app.core.online.onlineSubtitle
import com.akira.app.feature.search.normalizeSearchText
import com.akira.innertube.models.AlbumItem
import com.akira.innertube.models.ArtistItem
import com.akira.innertube.models.PlaylistItem
import com.akira.innertube.models.SongItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class OnlineLibrarySearchIndex private constructor(
    private val page: OnlinePage,
    private val texts: List<List<String>>,
) {
    suspend fun filter(query: String, kind: String = "all"): OnlinePage {
        val terms = normalizeSearchText(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty() && kind == "all") return page
        return page.copy(shelves = page.shelves.mapIndexed { shelfIndex, shelf ->
            shelf.copy(items = shelf.items.filterIndexed { itemIndex, item ->
                if (itemIndex % 128 == 0) currentCoroutineContext().ensureActive()
                val matchesKind = when (kind) {
                    "songs" -> item is SongItem
                    "albums" -> item is AlbumItem
                    "artists" -> item is ArtistItem
                    "playlists" -> item is PlaylistItem
                    else -> true
                }
                matchesKind && terms.all { term -> texts[shelfIndex][itemIndex].contains(term) }
            })
        })
    }

    companion object {
        suspend fun build(page: OnlinePage): OnlineLibrarySearchIndex {
            val texts = page.shelves.map { shelf -> shelf.items.mapIndexed { index, item ->
                if (index % 128 == 0) currentCoroutineContext().ensureActive()
                normalizeSearchText(item.title + " " + item.onlineSubtitle())
            } }
            return OnlineLibrarySearchIndex(page, texts)
        }
    }
}

internal class OnlineLibrarySearchCache {
    var source: OnlinePage? = null
    var index: OnlineLibrarySearchIndex? = null
}
