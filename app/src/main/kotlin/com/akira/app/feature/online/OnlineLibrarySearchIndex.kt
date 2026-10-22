package com.akira.app.feature.online

import com.akira.app.core.online.OnlinePage
import com.akira.app.core.online.onlineSubtitle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.text.Normalizer
import java.util.Locale

internal class OnlineLibrarySearchIndex private constructor(
    private val page: OnlinePage,
    private val texts: List<List<String>>,
) {
    suspend fun filter(query: String): OnlinePage {
        val terms = normalize(query).split(Whitespace).filter(String::isNotBlank)
        if (terms.isEmpty()) return page
        return page.copy(shelves = page.shelves.mapIndexed { shelfIndex, shelf ->
            shelf.copy(items = shelf.items.filterIndexed { itemIndex, _ ->
                if (itemIndex % 128 == 0) currentCoroutineContext().ensureActive()
                terms.all { term -> texts[shelfIndex][itemIndex].contains(term) }
            })
        })
    }

    companion object {
        private val Whitespace = Regex("\\s+")
        private val CombiningMarks = Regex("\\p{M}+")
        private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(CombiningMarks, "").lowercase(Locale.ROOT)

        suspend fun build(page: OnlinePage): OnlineLibrarySearchIndex {
            val texts = page.shelves.map { shelf -> shelf.items.mapIndexed { index, item ->
                if (index % 128 == 0) currentCoroutineContext().ensureActive()
                normalize(item.title + " " + item.onlineSubtitle())
            } }
            return OnlineLibrarySearchIndex(page, texts)
        }
    }
}

internal class OnlineLibrarySearchCache {
    var source: OnlinePage? = null
    var index: OnlineLibrarySearchIndex? = null
}
