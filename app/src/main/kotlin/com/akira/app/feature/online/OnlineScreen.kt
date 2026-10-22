package com.akira.app.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.innertube.models.*
import com.akira.app.core.model.Song
import com.akira.app.core.online.*
import com.akira.app.feature.home.HomeDestination
import com.akira.app.feature.home.HomeTopBar
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import com.akira.app.core.design.backdrop.backdrops.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** Same Page cards/carousel as local Home, with account-scoped online data. */
@Composable internal fun OnlinePageScreen(repository: OnlineRepository, destination: HomeDestination,
    current: Song?, playing: Boolean, bottomPadding: androidx.compose.ui.unit.Dp,
    onPlay: (Song, List<Song>) -> Unit, onTogglePlay: () -> Unit, obscured: Boolean,
    playbackError: Boolean, route: OnlineRoute?, depth: Int, navigationSettled: Boolean,
    onOpen: (YTItem, String?) -> Unit, onBack: () -> Unit,
    onMore: (Song) -> Unit = {}, onSettings: () -> Unit = {}) {
    if (destination == HomeDestination.SEARCH && route == null) {
        OnlineSearchPage(repository, current, playing, bottomPadding, onPlay, onTogglePlay, onOpen, onMore, onSettings)
        return
    }
    val account by repository.account.collectAsStateWithLifecycle()
    val likedIds by repository.likedIds.collectAsStateWithLifecycle()
    val recentSearches by repository.recentSearches.collectAsStateWithLifecycle()
    val recentlyPlayed by repository.recentSongs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searchKind by rememberSaveable { mutableStateOf("all") }
    var libraryKind by rememberSaveable { mutableStateOf("playlists") }
    var loadingMore by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var homePageCount by remember { mutableIntStateOf(1) }
    val consumedTokens = remember { mutableSetOf<String>() }
    var revision by remember { mutableIntStateOf(0) }
    var showAccount by remember { mutableStateOf(false) }
    var paginationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val homeGlass = remember { GlassState() }
    val liveCurrent by rememberUpdatedState(current)
    val settled by rememberUpdatedState(navigationSettled)
    val active = route?.item
    val sourcePrefix = "online-${destination.name}-$depth-${active?.id ?: "root"}"
    val effectiveLibraryKind = if(account.connected) libraryKind.takeIf { it in listOf("playlists", "albums", "artists", "songs") } ?: "playlists"
        else if(libraryKind == "recent") "recent" else "songs"
    val requestKey = when {
        active != null -> "detail:${active::class.simpleName}:${active.id}"
        destination == HomeDestination.SEARCH -> "search:${query.trim()}:$searchKind"
        destination == HomeDestination.LIBRARY -> "library:$effectiveLibraryKind"
        else -> "home"
    }
    val liveRequestKey by rememberUpdatedState(requestKey)
    fun initialPage() = repository.cachedPage(requestKey, account) ?: active?.let {
        OnlinePage(it.title, emptyList(), collection = OnlineCollection(it, creators = when(it) {
            is AlbumItem -> it.artists.orEmpty()
            is PlaylistItem -> listOfNotNull(it.author)
            else -> emptyList()
        }))
    }
    var page by remember(account, requestKey) { mutableStateOf(initialPage()) }
    var loading by remember(account, requestKey) { mutableStateOf(repository.cachedPage(requestKey, account) == null) }
    var failure by remember(account, requestKey) { mutableStateOf<OnlineFailure?>(null) }
    val scrolls = rememberSaveable(account, saver = listSaver<LinkedHashMap<String, LazyListState>, Any>(
        save = { states -> states.entries.flatMap { listOf(it.key, it.value.firstVisibleItemIndex, it.value.firstVisibleItemScrollOffset) } },
        restore = { values -> linkedMapOf<String, LazyListState>().apply {
            values.chunked(3).forEach { put(it[0] as String, LazyListState(it[1] as Int, it[2] as Int)) }
        } }
    )) { linkedMapOf<String, LazyListState>() }
    val list = remember(requestKey, account) {
        scrolls.getOrPut(requestKey) { LazyListState() }.also { if(scrolls.size > 20) scrolls.remove(scrolls.keys.first()) }
    }
    var rouletteSongs by remember(account) { mutableStateOf(emptyList<Song>()) }
    var rouletteFailed by remember(account) { mutableStateOf(false) }
    var searchControlsHeight by remember { mutableStateOf(76.dp) }
    val density = LocalDensity.current
    val guestLibraryContents = if (!account.connected && destination == HomeDestination.LIBRARY && active == null)
        if(effectiveLibraryKind == "recent") recentlyPlayed.map { it.id } else likedIds.sorted() else emptyList()
    LaunchedEffect(account, requestKey, revision, guestLibraryContents) {
        paginationJob?.cancel(); loadingMore = false; moreFailed = false; homePageCount = 1; consumedTokens.clear()
        page = initialPage(); failure = null
        loading = repository.cachedPage(requestKey, account) == null
        if (revision == 0 && repository.hasFreshPage(requestKey, account) &&
            !(destination == HomeDestination.LIBRARY && !account.connected)) {
            loading = false
            return@LaunchedEffect
        }
        try {
            if(repository.cachedPage(requestKey, account) != null) {
                withFrameNanos { }
                snapshotFlow { settled }.first { it }
            }
            loading = true
            if (destination == HomeDestination.SEARCH && active == null && query.isNotBlank()) delay(350)
            val loaded = kotlinx.coroutines.withTimeout(22_000) { when {
                active != null -> repository.open(active)
                destination == HomeDestination.SEARCH -> if (query.isBlank()) repository.discovery() else repository.search(query.trim(), searchKind)
                destination == HomeDestination.LIBRARY -> repository.library(effectiveLibraryKind, force = revision > 0)
                else -> repository.home(seed = liveCurrent, force = revision > 0)
            } }
            repository.cachePage(requestKey, loaded, account)
            page = loaded
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) { failure = timeout.onlineFailure()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.onlineFailure() }
        finally { loading = false }
    }
    LaunchedEffect(account, active, destination) {
        if (active == null && destination == HomeDestination.HOME) repository.homePages.collect { snapshot ->
            if (snapshot?.first == account) { page = snapshot.second; loading = false; failure = null }
        }
    }
    val shelves = page?.shelves.orEmpty()
    fun choose(item: YTItem, sourceKey: String? = null) {
        if(destination == HomeDestination.SEARCH && active == null && query.isNotBlank()) repository.rememberSearch(query)
        if (item is SongItem) {
            val song = item.asOnlineSong()
            if (current?.id == song.id) onTogglePlay()
            else onPlay(song, shelves.flatMap { it.items }.filterIsInstance<SongItem>()
                .distinctBy { it.id }.map { it.asOnlineSong() }.ifEmpty { listOf(song) })
        } else onOpen(item, sourceKey)
    }
    val songs = shelves.flatMap { it.items }.filterIsInstance<SongItem>().distinctBy { it.id }
    val continueLabel = localizedText("Continue listening", "Continuar escuchando")
    val heroTargets = remember(shelves) { shelves.sortedBy { shelf ->
        when {
            shelf.reason != null -> 0
            shelf.items.any { it is AlbumItem || it is PlaylistItem } -> 1
            else -> 2
        }
    }.flatMap { shelf ->
        shelf.items.take(2).map { it to HeroLabel(shelf.reason ?: shelf.title, shelf.thumbnail, roundCover = true) }
    }.distinctBy { it.first.id }.take(8) }
    val heroItems = remember(heroTargets, current?.id, continueLabel) { buildList {
        current?.takeIf { it.contentUri.startsWith("akira-youtube:") }?.let {
            add(HeroCarouselItem("continue", it.title, it.artist, it.albumArtUri, it, HeroLabel(continueLabel)))
        }
        heroTargets.take(7 - size).forEach { (item, reason) -> if (current?.id != "youtube:${item.id}")
            add(HeroCarouselItem("online-${item.id}", item.title, if (item is ArtistItem) "" else item.onlineSubtitle(), item.thumbnail,
                (item as? SongItem)?.asOnlineSong(), reason, opensPage = item !is SongItem, artistName = when (item) {
                    is ArtistItem -> item.title
                    is SongItem -> item.artists.firstOrNull()?.name
                    is AlbumItem -> item.artists?.firstOrNull()?.name
                    else -> null
                },
                transitionKey = item.onlineCollectionKey("$sourcePrefix-hero")))
        }
        add(HeroCarouselItem("online-roulette", "", isGacha = true))
    } }
    val heroHeight = with(LocalDensity.current) { (LocalConfiguration.current.screenHeightDp * HeroHeightFraction).dp.toPx() }
    val heroProgress = remember(list, heroHeight) { {
        if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / heroHeight).coerceIn(0f, 1f)
    } }
    val browseRoot = active == null && destination != HomeDestination.HOME
    val libraryRoot = active == null && destination == HomeDestination.LIBRARY
    val searchRoot = active == null && destination == HomeDestination.SEARCH
    val headerProgress by rememberPageCollapse(list)
    val homeRoot = destination == HomeDestination.HOME && active == null
    fun loadMore() {
        val token = page?.continuation ?: return
        if (loadingMore || loading) return
        paginationJob = scope.launch {
            loadingMore = true; moreFailed = false
            try {
                val next = kotlinx.coroutines.withTimeout(15_000) { when {
                    active is PlaylistItem -> repository.morePlaylist(token)
                    libraryRoot -> if (effectiveLibraryKind == "songs") repository.morePlaylist(token) else repository.moreLibrary(token)
                    searchRoot -> repository.moreSearch(token)
                    else -> repository.home(token)
                } }
                if (liveRequestKey == requestKey && page?.continuation == token) {
                    consumedTokens.add(token)
                    if (homeRoot || libraryRoot || searchRoot) homePageCount++
                    var remaining = 500
                    val combined = if (homeRoot) (shelves + next.shelves).groupBy { Triple(it.title, it.reason, it.layout) }.values.map { rows ->
                        rows.first().copy(items = rows.flatMap { it.items }.distinctBy { it.id })
                    }.take(24).map { shelf -> shelf.copy(items = shelf.items.take(remaining)).also { remaining -= it.items.size } }
                        .filter { it.items.isNotEmpty() }
                    else if (libraryRoot || searchRoot) listOf(OnlineShelf(shelves.firstOrNull()?.title.orEmpty(),
                        (shelves + next.shelves).flatMap { it.items }.distinctBy { it.id }.take(500)))
                    else shelves + next.shelves
                    val finished = homeRoot && (homePageCount >= 6 || combined.size >= 24 || combined.sumOf { it.items.size } >= 500)
                    page = page?.copy(shelves = combined, continuation = next.continuation?.takeUnless {
                        finished || homePageCount >= 6 || (libraryRoot && combined.sumOf { shelf -> shelf.items.size } >= 500) || it in consumedTokens })
                    page?.let { repository.cachePage(requestKey, it, account) }
                    if (homeRoot && com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraOnline",
                        "home page=$homePageCount sections=${combined.size} recommendations=${combined.sumOf { it.items.size }} end=${page?.continuation == null}")
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { moreFailed = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { moreFailed = true }
            finally { loadingMore = false }
        }
    }
    LaunchedEffect(homeRoot, libraryRoot, searchRoot, page?.continuation, loading, loadingMore, moreFailed, obscured) {
        if ((homeRoot || libraryRoot || searchRoot) && !loading && !loadingMore && !moreFailed && !obscured && page?.continuation != null) {
            snapshotFlow { list.layoutInfo.let { it.totalItemsCount > 0 &&
                (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 5 } }.collect { nearEnd ->
                if (nearEnd) loadMore()
            }
        }
    }
    val title = active?.title ?: when (destination) {
        HomeDestination.SEARCH -> localizedText("Search", "Buscar")
        HomeDestination.LIBRARY -> localizedText("Library", "Biblioteca")
        else -> localizedText("Home", "Inicio")
    }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTitle = title
    val retryLabel = localizedText("Retry", "Reintentar")
    if (showAccount) OnlineAccountSheet { showAccount = false }
    if (active != null && page?.collection != null) {
        key(active.id) { OnlineCollectionDetail(page!!, current, playing, bottomPadding, obscured, ::choose,
            onPlay, onTogglePlay, onMore, onBack, loadingMore, moreFailed, ::loadMore,
            route?.transitionKey, sourcePrefix, loading,
            if(failure == OnlineFailure.SESSION && !account.connected) OnlineFailure.SERVICE else failure,
            { revision++ }, { showAccount = true }) }
        return
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
                .then(if (homeRoot) Modifier.glassRoot(homeGlass, isActive = { heroProgress() > 0f }) else Modifier),
            contentPadding = PaddingValues(top = if (homeRoot) 0.dp else if(searchRoot) status + 82.dp + searchControlsHeight else status + 82.dp,
                bottom = bottomPadding + 28.dp), verticalArrangement = Arrangement.spacedBy(if (browseRoot) 12.dp else 20.dp),
            flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
            if (libraryRoot) {
                item("library-intro") { OnlineLibraryIntro(account) { showAccount = true } }
                item("library-kinds") { OnlineLibraryKinds(effectiveLibraryKind, account.connected) { libraryKind = it } }
            }
            run {
                if (homeRoot) item("hero") { key(account) { HeroCarousel(if (loading && page == null) emptyList() else heroItems, songs.map { it.asOnlineSong() }, current?.id, playing,
                    onItemClick = { hero ->
                        if (hero.key == "continue") hero.song?.let { song ->
                            if (current?.id == song.id) onTogglePlay() else onPlay(song, (listOf(song) + songs.map { it.asOnlineSong() }).distinctBy { it.id })
                        } else heroTargets.firstOrNull { "online-${it.first.id}" == hero.key }?.first?.let { choose(it, hero.transitionKey) }
                    },
                    onRandomPlay = { song -> repository.recordRouletteChoice(song); onPlay(song, (listOf(song) + rouletteSongs).distinctBy { it.id }) },
                    heroScrollProgressProvider = heroProgress, held = { obscured || heroProgress() > .08f }, isLoading = loading,
                    prepareRandom = { repository.roulette(liveCurrent, songs).also { rouletteSongs = it; rouletteFailed = false } },
                    onRandomFailure = { rouletteFailed = true }) } }
                if(homeRoot && rouletteFailed) item("roulette-error") {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(localizedText("Couldn't get new suggestions.", "No se pudieron obtener nuevas sugerencias."), style = MaterialTheme.typography.titleSmall)
                        Text(localizedText("Try the roulette again in a moment.", "Vuelve a girar la ruleta en unos momentos."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(searchRoot && query.isBlank() && recentSearches.isNotEmpty()) item("recent-searches") {
                    OnlineRecentSearches(recentSearches, { query = it; repository.rememberSearch(it) }, repository::clearSearches)
                }
                if (destination == HomeDestination.SEARCH && active == null && query.isBlank()) item("search-intro") {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(localizedText("Browse music", "Explorar música"),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(localizedText("New releases and recommendations from YouTube Music.", "Novedades y recomendaciones de YouTube Music."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (playbackError && current?.contentUri?.startsWith("akira-youtube:") == true) item("playback-error") {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text(localizedText("This song couldn't be played. Try again or choose another song.",
                            "No se pudo reproducir esta canción. Reintenta o elige otra."))
                        TextButton(onClick = onTogglePlay) { Text(retryLabel) }
                    }
                }
                if (loading && page == null) item("loading") { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                } }
                failure?.let { problem -> item("error") { OnlineLoadFeedback(
                    if(problem == OnlineFailure.SESSION && !account.connected) OnlineFailure.SERVICE else problem,
                    page != null, { revision++ }, { showAccount = true }) } }
                if (!loading && failure == null && page != null && shelves.all { it.items.isEmpty() }) item("empty") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (libraryRoot) localizedText("Nothing saved here yet", "Todavía no hay contenido aquí")
                            else if(query.isBlank()) localizedText("No recommendations available", "No hay recomendaciones disponibles")
                            else localizedText("No matches yet", "No hay resultados"), style = MaterialTheme.typography.titleLarge)
                        Text(if (libraryRoot) when(effectiveLibraryKind) {
                            "recent" -> localizedText("Songs you play online will appear here.", "Las canciones que reproduzcas online aparecerán aquí.")
                            "songs" -> localizedText("Use the star in the player to save songs here.", "Usa la estrella del reproductor para guardar canciones aquí.")
                            "albums" -> localizedText("Albums saved in YouTube Music will appear here.", "Los álbumes guardados en YouTube Music aparecerán aquí.")
                            "artists" -> localizedText("Artists saved in YouTube Music will appear here.", "Los artistas guardados en YouTube Music aparecerán aquí.")
                            else -> localizedText("Playlists saved in YouTube Music will appear here.", "Las playlists guardadas en YouTube Music aparecerán aquí.")
                        }
                            else if(query.isBlank()) localizedText("Search for a song, album or artist above.", "Busca una canción, álbum o artista arriba.")
                            else localizedText("Try another title or a different filter.", "Prueba otro título o filtro."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (browseRoot) onlineBrowseSections(shelves, destination == HomeDestination.SEARCH, query.isBlank(),
                    current, playing, sourcePrefix, ::choose, onMore)
                else onlineHomeSections(shelves, current, playing, sourcePrefix, ::choose, onMore)
                if (homeRoot || libraryRoot || searchRoot) {
                    if (loadingMore) item("more-loading") { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } }
                    if (moreFailed) item("more-retry") { TextButton(onClick = ::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(localizedText("Couldn't load more content. Retry", "No se pudo cargar más contenido. Reintentar"))
                    } }
                } else page?.continuation?.let { item("more") {
                    TextButton(enabled = !loadingMore, modifier = Modifier.fillMaxWidth(), onClick = ::loadMore) {
                        Text(if (loadingMore) localizedText("Loading…", "Cargando…") else if (moreFailed) retryLabel else localizedText("Load more", "Cargar más"))
                    }
                } }
            }
        }
        if(searchRoot) Box(Modifier.fillMaxWidth().padding(top = status + 76.dp)) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .onSizeChanged { searchControlsHeight = with(density) { it.height.toDp() } }.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineSearchField(query, { query = it }, { repository.rememberSearch(query); revision++ })
                if(query.isNotBlank()) OnlineSearchKinds(searchKind) { searchKind = it }
            }
        }
        if (homeRoot) HomeTopBar(heroProgress, onSettings, homeGlass, { if (obscured) 1f else 0f }, heroHeight)
        else CompositionLocalProvider(LocalPageHeaderBackdrop provides backdrop) {
            PagePageHeader(headerTitle, if (active != null) onBack else null,
                progress = headerProgress, topInset = status,
                trailing = { Row {
                    com.akira.app.core.design.components.AkiraAccountButton({ showAccount = true })
                } })
        }
    }
}

@Composable private fun OnlineAccountSheet(onDismiss: () -> Unit) {
    AkiraModalSheet(onDismissRequest = onDismiss, title = "YouTube Music") {
        Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp)) {
            OnlineModeControl()
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(localizedText("Done", "Listo")) }
        }
    }
}
