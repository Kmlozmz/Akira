package com.akira.app.feature.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.design.components.*
import com.akira.app.core.design.navigation.containerTransformSource
import com.akira.app.core.model.Song
import com.akira.app.core.online.*
import com.akira.app.feature.search.*
import com.akira.innertube.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable internal fun OnlineSearchPage(repository: OnlineRepository, current: Song?, playing: Boolean,
    bottomPadding: Dp, onPlay: (Song, List<Song>) -> Unit, onTogglePlay: () -> Unit,
    onOpen: (YTItem, String?) -> Unit, onMore: (Song) -> Unit, onSettings: () -> Unit) {
    val session = LocalSearchSession.current ?: return
    val account by repository.account.collectAsStateWithLifecycle()
    val libraryRevision by repository.libraryRevision.collectAsStateWithLifecycle()
    val recent by repository.recentSearches.collectAsStateWithLifecycle()
    var categories by remember(account) { mutableStateOf(emptyList<OnlineSearchCategory>()) }
    var discovery by remember(account) { mutableStateOf(repository.cachedPage("search-discovery", account)
        ?: repository.cachedPage("home", account)?.takeIf { page -> page.shelves.any { shelf ->
            shelf.items.any { it is AlbumItem || it is PlaylistItem }
        } }) }
    var category by remember(account) { mutableStateOf<OnlineSearchCategory?>(null) }
    var browseLoading by remember(account) { mutableStateOf(true) }
    var browseFailure by remember(account) { mutableStateOf<OnlineFailure?>(null) }
    var libraryLoading by remember(account) { mutableStateOf(false) }
    var libraryFailure by remember(account) { mutableStateOf<OnlineFailure?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val covers = remember(account) { mutableStateMapOf<OnlineSearchCategory, String>() }
    val coverRequests = remember(account) { Semaphore(3) }
    var library by remember(account) { mutableStateOf(repository.cachedPage("search-library-index", account)) }
    var libraryComplete by remember(account) { mutableStateOf(repository.hasFreshPage("search-library-index", account)) }
    var filteredLibrary by remember(account) { mutableStateOf<OnlinePage?>(null) }
    var filteringLibrary by remember(account) { mutableStateOf(false) }
    val librarySearch = remember(account) { OnlineLibrarySearchCache() }
    val requestKey = "search:${session.query}:${session.kind}"
    val activeKey = if (session.library) "library:${session.query}" else category?.let { "search-category:${it.endpoint}" } ?: requestKey
    val query = session.query
    val kind = session.kind
    val request = remember(account, query, kind, session.library, category, revision) { OnlineSearchRequestState() }
    val liveRequest by rememberUpdatedState(request)
    DisposableEffect(request) { onDispose { request.cancel() } }
    val loading = if (session.library) libraryLoading || filteringLibrary else request.loading
    val failure = if (session.library) libraryFailure else request.failure
    LaunchedEffect(account, revision) {
        browseLoading = true; browseFailure = null
        try {
            supervisorScope {
                val mood = launch {
                    try { categories = withTimeout(12_000) { repository.searchCategories() } }
                    catch (_: TimeoutCancellationException) { }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                }
                val recommendations = launch {
                    try {
                        discovery = withTimeout(12_000) { repository.discovery(force = revision > 0) }
                            .also { repository.cachePage("search-discovery", it, account) }
                    } catch (_: TimeoutCancellationException) { }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                }
                joinAll(mood, recommendations)
                if (categories.isEmpty() && discovery == null) browseFailure = OnlineFailure.SERVICE
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { browseLoading = false }
    }
    LaunchedEffect(account, session.library, session.editing, revision, libraryRevision) {
        if (!session.library || !session.editing || libraryComplete && revision == 0 &&
            repository.hasFreshPage("search-library-index", account)) return@LaunchedEffect
        libraryLoading = true; libraryFailure = null
        try {
            library = withTimeout(46_000) { repository.searchableLibrary(force = revision > 0) { library = it } }
            libraryComplete = true
        } catch (timeout: TimeoutCancellationException) { libraryFailure = timeout.onlineFailure() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { libraryFailure = error.onlineFailure() }
        finally { libraryLoading = false }
    }
    LaunchedEffect(request) {
        if (session.library || query.isBlank() && category == null) return@LaunchedEffect
        val key = activeKey
        request.page = repository.cachedPage(key, account)
        if (request.page != null && revision == 0 && repository.hasFreshPage(key, account)) return@LaunchedEffect
        request.loading = true
        try {
            if (category == null) delay(300)
            val loaded = withTimeout(22_000) {
                category?.let { repository.searchCategory(it) } ?: repository.search(query, kind)
            }
            ensureActive()
            if (liveRequest === request && request.active) {
                request.page = loaded
                repository.cachePage(key, loaded, account)
            }
        } catch (timeout: TimeoutCancellationException) { request.failure = timeout.onlineFailure() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { request.failure = error.onlineFailure() }
        finally { request.loading = false }
    }
    LaunchedEffect(session.submit) { if (session.submit > 0) repository.rememberSearch(session.query) }
    LaunchedEffect(session.editing) { if (session.editing) category = null }
    BackHandler(category != null && !session.editing) { category = null }
    LaunchedEffect(account, library, session.query, session.library) {
        val source = library
        if (!session.library || source == null) {
            filteringLibrary = false
            if (source == null) filteredLibrary = null
            return@LaunchedEffect
        }
        filteringLibrary = true
        try {
            val index = librarySearch.index?.takeIf { librarySearch.source === source }
                ?: withContext(Dispatchers.Default) { OnlineLibrarySearchIndex.build(source) }.also {
                    librarySearch.source = source; librarySearch.index = it
                }
            val query = session.query
            filteredLibrary = withContext(Dispatchers.Default) { index.filter(query) }
        } finally { if (currentCoroutineContext().isActive) filteringLibrary = false }
    }
    val page = if (session.library) filteredLibrary else request.page
    fun choose(item: YTItem, source: String?) {
        repository.rememberSearch(session.query)
        if (item is SongItem) {
            val song = item.asOnlineSong()
            if (song.id == current?.id) onTogglePlay()
            else onPlay(song, page?.shelves.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>()
                .distinctBy { it.id }.map { it.asOnlineSong() }.ifEmpty { listOf(song) })
        } else onOpen(item, source)
    }
    fun loadMore() {
        val source = request.page ?: return
        val token = source.continuation ?: return
        if (!request.active || request.loading || request.loadingMore) return
        val key = activeKey
        request.loadingMore = true
        request.moreFailed = false
        request.paginationJob = scope.launch {
            try {
                val next = withTimeout(15_000) { repository.moreSearch(token) }
                ensureActive()
                if (liveRequest === request && request.active && request.page === source) {
                    val combined = listOf(OnlineShelf(source.shelves.firstOrNull()?.title.orEmpty(),
                        (source.shelves + next.shelves).flatMap { it.items }.distinctBy { it.id }))
                    val loaded = source.copy(shelves = combined, continuation = next.continuation?.takeUnless { it == token })
                    request.page = loaded
                    repository.cachePage(key, loaded, account)
                }
            } catch (_: TimeoutCancellationException) { request.moreFailed = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { request.moreFailed = true }
            finally { request.loadingMore = false }
        }
    }
    val resultContent: LazyListScope.() -> Unit = {
        if (!session.library && category == null) item("search-kinds") { OnlineSearchKinds(session.kind) { session.kind = it } }
        if (loading && page == null) item("search-loading") { SearchLoading() }
        failure?.let { problem -> item("search-error") { OnlineLoadFeedback(problem, page != null, { revision++ }, onSettings) } }
        if (!loading && failure == null && page != null && page.shelves.all { it.items.isEmpty() }) item("search-empty") {
            SearchEmpty(localizedText("No results", "Sin resultados"))
        }
        onlineBrowseSections(page?.shelves.orEmpty(), true, false, current, playing, "search-results", ::choose, onMore)
        if (loading && page != null) item("search-refreshing") { SearchLoading() }
        if (!session.library && request.page?.continuation != null) item("search-next") {
            LaunchedEffect(request, request.page?.continuation, request.loading) {
                if (!request.moreFailed && !request.loading) loadMore()
            }
            if (request.loadingMore) SearchLoading()
            if (request.moreFailed) TextButton(onClick = ::loadMore, modifier = Modifier.fillMaxWidth()) { Text(localizedText("Retry", "Reintentar")) }
        }
    }
    SearchPage(true, bottomPadding, onSettings, recent, repository::rememberSearch, repository::clearSearches,
        browse = {
            if (category != null) {
                item("category-title") { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { category = null }) { Text(localizedText("Back", "Volver")) }
                    Text(category!!.title, style = MaterialTheme.typography.titleLarge)
                } }
                resultContent()
            } else {
                if (browseLoading && categories.isEmpty() && discovery == null) item("browse-loading") { SearchLoading() }
                browseFailure?.let { problem -> item("browse-error") { OnlineLoadFeedback(problem, discovery != null, { revision++ }, onSettings) } }
                val featured = discovery?.shelves.orEmpty().flatMap { shelf -> shelf.items.filter { it is AlbumItem || it is PlaylistItem } }
                    .distinctBy { it.id }.take(4)
                items(featured.chunked(2), key = { "feature-${it.first().id}" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            val source = item.onlineCollectionKey("search-discovery")
                            SearchCategoryCard(item.title, item.thumbnail, MaterialTheme.colorScheme.primaryContainer,
                                Modifier.weight(1f).containerTransformSource(source, 14.dp, item.thumbnail)) { choose(item, source) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                items(categories.chunked(2), key = { "genres-${it.first().endpoint}" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            LaunchedEffect(item) {
                                if (covers[item] == null) coverRequests.withPermit {
                                    try {
                                        val key = "search-category:${item.endpoint}"
                                        val categoryPage = repository.cachedPage(key, account) ?: withTimeout(10_000) { repository.searchCategory(item) }
                                            .also { repository.cachePage(key, it, account) }
                                        categoryPage.shelves.flatMap { it.items }.firstNotNullOfOrNull { it.thumbnail }?.let { covers[item] = it }
                                    } catch (_: TimeoutCancellationException) { }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { }
                                }
                            }
                            SearchCategoryCard(item.title, covers[item], Color(item.color.toInt()), Modifier.weight(1f)) {
                                session.library = false; category = item
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }, results = resultContent)
}

@Composable private fun SearchLoading() {
    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }
}
