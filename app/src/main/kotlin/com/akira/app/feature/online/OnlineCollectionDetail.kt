package com.akira.app.feature.online

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.innertube.models.*
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import com.akira.app.core.model.Song
import com.akira.app.core.online.*
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import com.akira.app.feature.collection.*
import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Real provider collections, presented with exactly the same album/artist building blocks as local music. */
@Composable internal fun OnlineCollectionDetail(page: OnlinePage, current: Song?, playing: Boolean,
    bottom: Dp, obscured: Boolean, onChoose: (YTItem, String?) -> Unit, onPlay: (Song, List<Song>) -> Unit,
    onToggle: () -> Unit, onMore: (Song) -> Unit, onBack: () -> Unit,
    loadingMore: Boolean, moreFailed: Boolean, onLoadMore: () -> Unit,
    transitionKey: String?, sourcePrefix: String, loading: Boolean, failure: OnlineFailure?, onRetry: () -> Unit, onAccount: () -> Unit) {
    val collection = page.collection ?: return
    val sessions = LocalDetailSessions.current
    val session = remember { Any() }
    DisposableEffect(sessions, session, collection.item) {
        sessions?.set(session, true); onDispose { sessions?.remove(session) }
    }
    MaterialTheme(colorScheme = com.akira.app.core.design.theme.DarkColorScheme) {
        CompositionLocalProvider(LocalDetailInk provides Color.White) {
            val feedback: @Composable () -> Unit = {
                if(loading && page.shelves.isEmpty()) OnlineCollectionLoading()
                failure?.let { OnlineLoadFeedback(it, page.shelves.isNotEmpty(), onRetry, onAccount) }
            }
            if (collection.item is ArtistItem) OnlineArtistDetail(page, current, playing, bottom, obscured,
                onChoose, onPlay, onToggle, onMore, onBack, transitionKey, sourcePrefix, feedback)
            else OnlineAlbumPlaylistDetail(page, current, playing, bottom, onChoose, onPlay, onToggle, onMore, onBack,
                loadingMore, moreFailed, onLoadMore, transitionKey, sourcePrefix, loading, feedback)
        }
    }
}

@Composable private fun OnlineAlbumPlaylistDetail(page: OnlinePage, current: Song?, playing: Boolean, bottom: Dp,
    onChoose: (YTItem, String?) -> Unit, onPlay: (Song, List<Song>) -> Unit, onToggle: () -> Unit,
    onMore: (Song) -> Unit, onBack: () -> Unit, loadingMore: Boolean, moreFailed: Boolean, onLoadMore: () -> Unit,
    transitionKey: String?, sourcePrefix: String, loading: Boolean, feedback: @Composable () -> Unit) {
    val collection = page.collection!!
    val tracks = page.shelves.flatMap { it.items }.filterIsInstance<SongItem>().distinctBy { it.id }
    val queue = tracks.map { it.asOnlineSong() }
    val state = rememberLazyListState()
    val artists = collection.creators
    PlaylistDetailScaffold(transitionKey, collection.item.thumbnail, state, onBack, onBack,
        contentPadding = PaddingValues(bottom = bottom + 28.dp)) {
        item("header") {
            PlaylistHeader(page.title, artists.map { CreatorUi(it.name, artistId = it.id) },
                listOfNotNull(collection.stats, if(loading && tracks.isEmpty()) null else localizedText("${tracks.size} songs", "${tracks.size} canciones"),
                    queue.sumOf { it.durationMs }.takeIf { it > 0 }?.let { "${it / 60000} min" }).joinToString(" Â· "),
                isPlaying = playing && queue.any { it.id == current?.id },
                onPlayClick = { if (queue.any { it.id == current?.id }) onToggle() else queue.firstOrNull()?.let { onPlay(it, queue) } },
                onShuffleClick = { queue.shuffled().let { q -> q.firstOrNull()?.let { onPlay(it, q) } } },
                description = collection.description,
                onCreatorClick = { creator -> creator.artistId?.let { id -> onChoose(ArtistItem(id, creator.name, null,
                    shuffleEndpoint = null, radioEndpoint = null), null) } })
        }
        item("feedback") { feedback() }
        items(tracks, key = { "track-${it.id}" }) { item -> OnlineDetailTrack(item, queue, current, playing, onPlay, onToggle, onMore) }
        if (page.continuation != null) item("next") {
            TextButton(onClick = onLoadMore, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                Text(if (loadingMore) localizedText("Loadingâ€¦", "Cargandoâ€¦") else if (moreFailed) localizedText("Retry", "Reintentar") else localizedText("More songs", "MÃ¡s canciones"))
            }
        }
        if(tracks.isNotEmpty()) item { PlaylistEndOfLine(tracks.size, queue.sumOf { it.durationSeconds }) }
        page.shelves.filter { shelf -> shelf.items.any { it !is SongItem } }.forEachIndexed { index, shelf ->
            item("collection-$index-title") { PlaylistSectionTitle(shelf.title, showChevron = false) }
            item("collection-$index") { OnlineDetailShelf(shelf.items.filterNot { it is SongItem }, "$sourcePrefix-related-$index", onChoose) }
        }
    }
}

@Composable private fun OnlineDetailTrack(item: SongItem, queue: List<Song>, current: Song?, playing: Boolean,
    onPlay: (Song, List<Song>) -> Unit, onToggle: () -> Unit, onMore: (Song) -> Unit) {
    val song = item.asOnlineSong()
    PlaylistTrackRow(item.thumbnail, item.title, item.onlineSubtitle(), current?.id == song.id, playing,
        { if (song.id == current?.id) onToggle() else onPlay(song, queue) }, { onMore(song) }, { onMore(song) })
}

@Composable private fun OnlineDetailShelf(items: List<YTItem>, sourcePrefix: String, onChoose: (YTItem, String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = HomeContentInset), horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing)) {
        items(items.distinctBy { it.id }, key = { it.id }) { item ->
            val source = item.onlineCollectionKey(sourcePrefix)
            HomeCoverCard(item.title, item.onlineSubtitle(), item.thumbnail,
                Modifier.containerTransformSource(source, if(item is ArtistItem) 68.5.dp else ContentCardCornerRadius, item.thumbnail)
                    .clickable { onChoose(item, source) }, circular = item is ArtistItem)
        }
    }
}

@Composable private fun OnlineArtistDetail(page: OnlinePage, current: Song?, playing: Boolean, bottom: Dp, obscured: Boolean,
    onChoose: (YTItem, String?) -> Unit, onPlay: (Song, List<Song>) -> Unit, onToggle: () -> Unit,
    onMore: (Song) -> Unit, onBack: () -> Unit, transitionKey: String?, sourcePrefix: String, feedback: @Composable () -> Unit) {
    val collection = page.collection!!
    val artist = collection.item as ArtistItem
    val biography = remember(collection.description) { artistBiography(collection.description) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { UserPreferencesRepository(context) }
    val prefs by prefsRepo.userPreferences.collectAsStateWithLifecycle(UserPreferences())
    val favorite = artist.title.trim().lowercase(java.util.Locale.ROOT) in prefs.favoriteArtists
    var showInfo by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editorial by remember(artist.id) { mutableStateOf<ArtistEditorialArtwork?>(null) }
    val reduced = LocalContentMotionReduced.current
    LaunchedEffect(artist.id, reduced) { if (!reduced) editorial = MotionArtworkRepository.findArtist(artist.title) }
    val image = editorial?.image ?: artist.thumbnail
    val state = rememberLazyListState()
    val height = playlistCoverHeight()
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val progress by remember { derivedStateOf { if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / heightPx).coerceIn(0f, 1f) } }
    val color by rememberCoverThemeColor(image)
    val background = animateColorAsState(color ?: Color.Black, label = "onlineArtistColor")
    val backdrop = rememberLayerBackdrop()
    val artworkBackdrop = rememberLayerBackdrop()
    val glass = remember { GlassState() }
    val tracks = page.shelves.flatMap { it.items }.filterIsInstance<SongItem>().distinctBy { it.id }
    val queue = tracks.map { it.asOnlineSong() }
    val radioError = localizedText("Couldn't start this artist's station. Try again.", "No se pudo iniciar la radio del artista. Reintenta.")
    val albumSection = page.shelves.indexOfFirst { shelf -> shelf.items.any { it is AlbumItem } }
    fun albums() {
        if (albumSection < 0) return
        val index = 2 + page.shelves.take(albumSection).sumOf { shelf ->
            1 + shelf.items.filterIsInstance<SongItem>().size + if (shelf.items.any { it !is SongItem }) 1 else 0
        }
        scope.launch { state.animateScrollToItem(index) }
    }
    fun share() { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, artist.shareLink), artist.title)) }
    fun play() { if (queue.any { it.id == current?.id }) onToggle() else queue.firstOrNull()?.let { onPlay(it, queue) } }
    CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().containerTransformTarget(transitionKey, 68.5.dp).background(background.value)) {
            Box(Modifier.fillMaxSize().glassRoot(glass) { progress > .01f }.layerBackdrop(backdrop)) {
                Box(Modifier.fillMaxSize().layerBackdrop(artworkBackdrop).background(background.value)) {
                    LocalArtistBanner(image, height, { progress }, background) {
                        editorial?.motion?.takeUnless { reduced }?.let {
                            MotionArtworkVideo(it, isPlaying = !obscured && progress < .08f, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                LazyColumn(state = state, contentPadding = PaddingValues(bottom = bottom + 30.dp), modifier = Modifier.fillMaxSize()) {
                    item("masthead") { CompositionLocalProvider(LocalAppBackdrop provides artworkBackdrop) {
                        AkiraArtistMasthead(artist.title, collection.stats ?: "YouTube Music", background.value, favorite,
                            playing && queue.any { it.id == current?.id }, ::play,
                            onFavorite = { scope.launch { prefsRepo.setArtistFavorite(artist.title, !favorite) } },
                            onInfo = { showInfo = true }, modifier = Modifier.padding(top = height * .56f).padding(horizontal = 30.dp))
                    } }
                    item("feedback") { feedback() }
                    page.shelves.forEachIndexed { index, shelf ->
                        item("title-$index") { PlaylistSectionTitle(shelf.title, Modifier.padding(top = 24.dp), showChevron = false) }
                        val songs = shelf.items.filterIsInstance<SongItem>()
                        if (songs.isNotEmpty()) items(songs, key = { "song-$index-${it.id}" }) { OnlineDetailTrack(it, queue, current, playing, onPlay, onToggle, onMore) }
                        val collections = shelf.items.filterNot { it is SongItem }
                        if (collections.isNotEmpty()) item("shelf-$index") { OnlineDetailShelf(collections, "$sourcePrefix-shelf-$index", onChoose) }
                    }
                    biography?.takeIf(String::isNotBlank)?.let { bio -> item("bio") {
                        Column(Modifier.padding(36.dp).clickable { showInfo = true }) {
                            Text(localizedText("About", "Acerca de"), style = MaterialTheme.typography.titleLarge)
                            Text(bio, Modifier.padding(top = 12.dp), maxLines = 5, color = Color.White.copy(alpha = .7f))
                        }
                    } }
                }
            }
            ArtistBar(artist.title, glass, progress, background.value, onBack,
                onShare = ::share,
                onMore = { showMenu = true }, onTitle = { scope.launch { state.animateScrollToItem(0) } })
        }
    }
    if (showMenu) AkiraContextMenu(onDismiss = { showMenu = false }, groups = listOf(
        listOf(AkiraContextAction(AkiraIcons.Play, localizedText("Play top songs", "Reproducir canciones destacadas")) { play() },
            AkiraContextAction(AkiraIcons.Shuffle, localizedText("Shuffle", "Aleatorio")) {
                queue.shuffled().let { q -> q.firstOrNull()?.let { onPlay(it, q) } }
            },
            AkiraContextAction(Icons.Rounded.Radio, localizedText("Start artist radio", "Iniciar radio del artista")) { scope.launch {
                try {
                    val q = OnlineRepository.get(context).artistRadio(artist, queue.firstOrNull())
                    q.firstOrNull()?.let { onPlay(it, q) } ?: AkiraNotices.show(radioError)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { AkiraNotices.show(radioError) }
            } }),
        buildList {
            add(AkiraContextAction(if (favorite) AkiraIcons.StarFilled else AkiraIcons.Star,
                if (favorite) localizedText("Remove from Favorites", "Quitar de favoritos") else localizedText("Favorite artist", "Artista favorito")) {
                scope.launch { prefsRepo.setArtistFavorite(artist.title, !favorite) }
            })
            if (albumSection >= 0) add(AkiraContextAction(AkiraIcons.Album, localizedText("Browse albums", "Ver Ã¡lbumes")) { albums() })
            add(AkiraContextAction(AkiraIcons.Info, localizedText("Artist information", "InformaciÃ³n del artista"), opensPresentation = true) { showInfo = true })
        },
        listOf(AkiraContextAction(AkiraIcons.Share, localizedText("Share artist", "Compartir artista")) { share() })
    ))
    if (showInfo) ArtistInformationSheet(artist.title, image, background.value, collection.stats,
        biography ?: localizedText("YouTube Music hasn't provided a biography for this artist.", "YouTube Music no ha proporcionado una biografÃ­a para este artista."),
        onDismiss = { showInfo = false })
}

/** Keep the provider's original metadata; the reading view shows the biography without its source footer. */
internal fun artistBiography(raw: String?): String? = raw?.split(
    Regex("(?i)(?:\\n\\s*)+(?:from wikipedia|de wikipedia|fuente: wikipedia|source: wikipedia|wikipedia).*"), limit = 2)
    ?.firstOrNull()?.trim()?.takeIf(String::isNotBlank)
