package com.akira.innertube.pages

import com.akira.innertube.models.MusicPlaylistShelfRenderer
import com.akira.innertube.models.MusicShelfRenderer
import com.akira.innertube.models.getContinuation
import com.akira.innertube.models.response.BrowseResponse
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PaginationPage<T>(val items: List<T>, val continuation: String?)

internal fun MusicPlaylistShelfRenderer.paginationPage() =
    PaginationPage(contents, contents.getContinuation() ?: continuations?.getContinuation())

internal fun BrowseResponse.playlistContinuationPage(): PaginationPage<MusicShelfRenderer.Content> {
    val sections = continuationContents?.sectionListContinuation
    val playlists = sections?.contents.orEmpty().mapNotNull { it.musicPlaylistShelfRenderer }
    val shelves = sections?.contents.orEmpty().mapNotNull { it.musicShelfRenderer }
    val playlist = continuationContents?.musicPlaylistShelfContinuation
    val shelf = continuationContents?.musicShelfContinuation
    check(sections != null || playlist != null || shelf != null ||
        onResponseReceivedActions.orEmpty().any { it.appendContinuationItemsAction != null }) {
        "Playlist continuation content is missing"
    }
    val appended = onResponseReceivedActions.orEmpty().flatMap {
        it.appendContinuationItemsAction?.continuationItems.orEmpty()
    }
    val items = playlists.flatMap { it.contents } + shelves.flatMap { it.contents.orEmpty() } +
        playlist?.contents.orEmpty() + shelf?.contents.orEmpty() + appended
    val next = items.getContinuation()
        ?: playlists.firstNotNullOfOrNull { it.continuations?.getContinuation() }
        ?: shelves.firstNotNullOfOrNull { it.continuations?.getContinuation() }
        ?: playlist?.continuations?.getContinuation()
        ?: shelf?.continuations?.getContinuation()
        ?: sections?.continuations?.getContinuation()
    return PaginationPage(items, next)
}

internal suspend fun <T> collectPages(
    first: PaginationPage<T>,
    maxRequests: Int = 50,
    fetch: suspend (String) -> PaginationPage<T>,
): List<T> {
    val items = first.items.toMutableList()
    var token = first.continuation
    val visited = mutableSetOf<String>()
    while (token != null) {
        currentCoroutineContext().ensureActive()
        if (visited.size >= maxRequests || !visited.add(token)) {
            throw IOException("Pagination did not finish within $maxRequests requests")
        }
        val page = fetch(token)
        items += page.items
        token = page.continuation
    }
    currentCoroutineContext().ensureActive()
    return items
}
