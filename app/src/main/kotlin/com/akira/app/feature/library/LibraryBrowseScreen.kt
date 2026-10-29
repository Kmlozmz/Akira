@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.akira.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.motion.rememberAkiraBouncingFling
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.media.MusicServiceConnection
import com.akira.app.core.model.Song
import com.akira.app.feature.collection.localAlbumKey

internal data class LibraryCategoryLink(val key: String, val title: String, val icon: ImageVector)

@Composable internal fun localLibraryCategories() = listOf(
    LibraryCategoryLink("Playlists", localizedText("Playlists", "Playlists"), LibraryIcons.Playlists),
    LibraryCategoryLink("Artists", localizedText("Artists", "Artistas"), LibraryIcons.Artists),
    LibraryCategoryLink("Albums", localizedText("Albums", "Álbumes"), LibraryIcons.Albums),
    LibraryCategoryLink("Tracks", localizedText("Songs", "Canciones"), LibraryIcons.Songs),
    LibraryCategoryLink("Folders", localizedText("Folders", "Carpetas"), LibraryIcons.Folders))

@Composable internal fun LibraryOverview(categories: List<LibraryCategoryLink>, onSelect: (String) -> Unit,
    onAccount: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background
    val backdrop = rememberCanvasBackdrop { drawRect(background) }
    val chrome = LocalLibraryChrome.current
    val activeChrome = LocalLibraryChromeActive.current
    val albumLabel = localizedText("Albums", "Álbumes")
    val openAlbums = { onSelect(categories.firstOrNull { it.key.equals("albums", true) }?.key ?: categories.first().key) }
    SideEffect {
        if (activeChrome && chrome != null) {
            chrome.category = false; chrome.collapse = 0f; chrome.scrim = 0f; chrome.backdrop = backdrop
            chrome.icon = LibraryIcons.View; chrome.description = albumLabel
            chrome.onFirst = openAlbums; chrome.onMore = { menu = true }; chrome.onProfile = { profile = true }
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(localizedText("Library", "Biblioteca"), Modifier.weight(1f), fontFamily = AkiraFontFamily,
                    fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-.8).sp)
                if (chrome == null) {
                    CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
                        LibraryActionCapsule(LibraryIcons.View, albumLabel, openAlbums, { menu = true })
                    }
                    Spacer(Modifier.width(8.dp))
                    AkiraAccountButton({ profile = true }, alwaysShowProfile = true)
                } else {
                    Spacer(Modifier.width(148.dp))
                }
            }
            categories.forEach { category ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onSelect(category.key) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(category.icon, null, Modifier.size(23.dp), tint = if (category.key in listOf("Tracks", "songs")) Color(0xFFFC5265) else Color(0xFFFA2B45))
                    Text(category.title, Modifier.weight(1f).padding(horizontal = 16.dp), fontSize = 19.sp,
                        fontFamily = AkiraFontFamily, color = MaterialTheme.colorScheme.onSurface)
                    Icon(AkiraIcons.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(start = 55.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), thickness = .5.dp)
            }
        }
        if (menu) AkiraContextMenu(onDismiss = { menu = false }, groups = listOf(listOf(
            AkiraContextAction(Icons.Rounded.Search, localizedText("Search library", "Buscar en la biblioteca")) {
                onSelect(categories.firstOrNull { it.key in listOf("Tracks", "songs") }?.key ?: categories.first().key)
            },
            AkiraContextAction(Icons.Rounded.Settings, localizedText("Settings", "Ajustes"), onClick = onAccount))))
        if (profile) com.akira.app.feature.online.OnlineAccountSheet { profile = false }
    }
}

@Composable internal fun LibraryActionCapsule(first: ImageVector, description: String,
    onFirst: () -> Unit, onMore: () -> Unit) {
    val source = rememberAkiraMenuSource(hasOwnSurface = true, icon = null)
    val response = rememberAkiraPressResponse(false)
    DisposableEffect(source, response) {
        source.contactProvider = response.amount
        source.contactStrain = response.strain
        onDispose { source.contactProvider = { 0f }; source.contactStrain = { androidx.compose.ui.geometry.Offset.Zero } }
    }
    val probe = com.akira.app.core.design.glass.rememberGlassInk()
    val ink = probe.color
    source.visualVerticalInsetPx = with(LocalDensity.current) { 2.dp.toPx() }
    SideEffect {
        source.surfaceContent = {
            Row(Modifier.size(112.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Icon(first, null, Modifier.size(23.dp), tint = ink) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Icon(LibraryIcons.More, null, Modifier.size(23.dp), tint = ink) }
            }
        }
    }
    Box(Modifier.size(112.dp, 48.dp).then(response.modifier).then(probe.modifier).then(source.modifier).then(source.containerModifier)) {
        Box(Modifier.matchParentSize().padding(vertical = 2.dp).akiraNavigationSurface(RoundedCornerShape(50)))
        CompositionLocalProvider(LocalGlassPressHandled provides true) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AkiraIconButton(first, description, { source.open(onFirst) }, Modifier.weight(1f), tint = ink,
                showBackground = false, glyphSize = 23.dp)
            AkiraIconButton(AkiraIcons.MoreHoriz, localizedText("More options", "Más opciones"), onMore, Modifier.weight(1f),
                tint = ink, showBackground = false, glyphSize = 23.dp, menuSourceOverride = source, glyphOverride = LibraryIcons.More)
        }
        }
    }
}

/** The toolbar is a sibling of the recorded body so its glass cannot sample itself. */
@Composable internal fun LibraryFloatingBar(title: String, collapsed: Float, backdrop: Backdrop,
    onBack: () -> Unit, firstIcon: ImageVector = LibraryIcons.Filter,
    firstDescription: String = localizedText("Sort", "Ordenar"), onFirst: () -> Unit, onMore: () -> Unit,
    scrimProgress: Float = collapsed) {
    val chrome = LocalLibraryChrome.current
    val activeChrome = LocalLibraryChromeActive.current
    if (chrome != null) {
        SideEffect {
            if (activeChrome) {
                chrome.category = true; chrome.title = title; chrome.collapse = collapsed; chrome.scrim = scrimProgress
                chrome.backdrop = backdrop; chrome.icon = firstIcon; chrome.description = firstDescription
                chrome.onBack = onBack; chrome.onFirst = onFirst; chrome.onMore = onMore
            }
        }
        return
    }
    val color = MaterialTheme.colorScheme.background
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    CompositionLocalProvider(LocalPageHeaderBackdrop provides backdrop, LocalAppBackdrop provides backdrop) {
        Box(Modifier.fillMaxWidth().height(inset + 102.dp).background(Brush.verticalGradient(
            0f to color.copy(alpha = .98f * scrimProgress), .52f to color.copy(alpha = .88f * scrimProgress), 1f to Color.Transparent))) {
            Row(Modifier.fillMaxWidth().padding(top = inset + AkiraNavigationMetrics.HeaderTop,
                start = AkiraNavigationMetrics.Gutter - 4.dp, end = AkiraNavigationMetrics.Gutter),
                verticalAlignment = Alignment.CenterVertically) {
                PageBackButton(onBack)
                Text(title, Modifier.weight(1f).padding(horizontal = 8.dp).graphicsLayer { alpha = collapsed },
                    fontFamily = AkiraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LibraryActionCapsule(firstIcon, firstDescription, onFirst, onMore)
            }
        }
    }
}

@Composable internal fun LibraryBrowseHeading(title: String, query: String, onQuery: (String) -> Unit,
    searchVisible: Boolean, onPlay: (() -> Unit)? = null, onShuffle: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 66.dp, bottom = 14.dp)) {
        Text(title, fontFamily = AkiraFontFamily, fontSize = 34.sp, lineHeight = 40.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        AnimatedVisibility(searchVisible || query.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .065f)).padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                BasicTextField(query, onQuery, Modifier.weight(1f), singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = AkiraFontFamily, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
                        Box { if (query.isEmpty()) Text(localizedText("Search $title", "Buscar en $title"), fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant); field() }
                    })
                if (query.isNotEmpty()) IconButton({ onQuery("") }, Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Cancel, localizedText("Clear search", "Borrar búsqueda"), Modifier.size(18.dp))
                }
            }
        }
        if (onPlay != null && onShuffle != null) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryPlayAction(AkiraIcons.Play, localizedText("Play", "Reproducir"), onPlay, Modifier.weight(1f))
            LibraryPlayAction(AkiraIcons.Shuffle, localizedText("Shuffle", "Aleatorio"), onShuffle, Modifier.weight(1f))
        }
    }
}

@Composable private fun LibraryPlayAction(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
        .clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(19.dp)); Spacer(Modifier.width(6.dp))
        Text(title, fontFamily = AkiraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable internal fun LibraryAlbumTile(title: String, artist: String, artwork: String?, source: String,
    modifier: Modifier = Modifier, circular: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).containerTransformSource(source, if (circular) 100.dp else 5.dp, artwork)
            .clip(if (circular) CircleShape else RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
            Icon(AkiraIcons.Album, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (artwork != null) AsyncImage(artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(title, Modifier.padding(top = 6.dp), fontFamily = AkiraFontFamily, fontSize = 14.sp, lineHeight = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(artist, fontFamily = AkiraFontFamily, fontSize = 12.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun LibraryBrowseScreen(songs: List<Song>, current: Song?, playing: Boolean, bottom: Dp,
    musicService: MusicServiceConnection, sort: SortOrder, onSort: () -> Unit, onSettings: () -> Unit,
    onAlbum: (Song) -> Unit, onArtist: (String) -> Unit, onPlay: (Song, List<Song>) -> Unit,
    onMore: (Song) -> Unit, initialized: Boolean, failed: Boolean, onRetry: () -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val states = rememberSaveableStateHolder()
    val reduced = LocalContentMotionReduced.current
    val obscured = LocalContentObscured.current
    BackHandler(category != null && !obscured) { category = null }
    val categories = localLibraryCategories()
    LibraryChromeHost {
    AnimatedContent(category, transitionSpec = { pageNavigationTransform(targetState != null, reduced).using(null) },
        label = "libraryCategory", modifier = Modifier.fillMaxSize()) { key ->
        CompositionLocalProvider(LocalLibraryChromeActive provides (key == category)) {
        states.SaveableStateProvider(key ?: "overview") {
            if (key == null) LibraryOverview(categories, { category = it }, onSettings)
            else {
                var query by rememberSaveable { mutableStateOf("") }
                var searchVisible by rememberSaveable { mutableStateOf(false) }
                var menu by remember { mutableStateOf(false) }
                var albumSortName by rememberSaveable { mutableStateOf(SortOrder.TITLE_ASC.name) }
                val albumSort = SortOrder.fromName(albumSortName)
                var sortMenu by remember { mutableStateOf(false) }
                var folder by rememberSaveable { mutableStateOf<String?>(null) }
                BackHandler(folder != null && !obscured) { folder = null }
                val filtered = remember(songs, query, sort, folder) {
                    sortSongs(songs.filter { song -> (folder == null || song.path.substringBeforeLast('/') == folder) &&
                        (query.isBlank() || listOf(song.title, song.artist, song.album).any { it.contains(query, true) }) }, sort)
                }
                val albums = remember(filtered, albumSort) { filtered.groupBy(::localAlbumKey).values.map { group -> group.maxBy { it.dateAddedSec } }.let { entries ->
                    val ordered = when (albumSort.field) {
                        SortField.ARTIST -> entries.sortedBy { it.artist.lowercase() }
                        SortField.DATE_ADDED -> entries.sortedBy { it.dateAddedSec }
                        else -> entries.sortedBy { it.album.lowercase() }
                    }
                    if (albumSort.ascending) ordered else ordered.reversed()
                } }
                val title = folder?.substringAfterLast('/') ?: categories.first { it.key == key }.title
                val grid = rememberLazyGridState()
                val list = rememberLazyListState()
                val backdrop = rememberLayerBackdrop()
                val density = LocalDensity.current
                val collapse by remember(key) { derivedStateOf {
                    val index = if (key == "Albums") grid.firstVisibleItemIndex else list.firstVisibleItemIndex
                    val offset = if (key == "Albums") grid.firstVisibleItemScrollOffset else list.firstVisibleItemScrollOffset
                    if (index > 0) 1f else (offset / with(density) { 70.dp.toPx() }).coerceIn(0f, 1f)
                } }
                val atStart by remember { derivedStateOf { if (key == "Albums") grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0
                    else list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }
                val liveStart by rememberUpdatedState(atStart)
                val revealSearch = remember { object : NestedScrollConnection {
                    var pull = 0f
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput && liveStart && available.y > 0f) {
                            pull += available.y
                            if (pull > with(density) { 30.dp.toPx() }) searchVisible = true
                        } else if (consumed.y < 0f) pull = 0f
                        return Offset.Zero
                    }
                } }
                fun play(shuffle: Boolean) { val queue = if (shuffle) filtered.shuffled() else filtered; queue.firstOrNull()?.let { onPlay(it, queue) } }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.fillMaxSize().nestedScroll(revealSearch).layerBackdrop(backdrop)) {
                        when {
                            !initialized -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 100.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (failed) LibraryRefreshError(onRetry) else {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                    Text(localizedText("Loading library…", "Cargando biblioteca…"), Modifier.padding(16.dp))
                                }
                            }
                            key == "Playlists" -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 66.dp)) {
                                PlaylistTab(songs, musicService, !obscured, bottom, onMore)
                            }
                            key == "Albums" -> LazyVerticalGrid(GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
                                flingBehavior = rememberAkiraBouncingFling()) {
                                item("header", span = { GridItemSpan(maxLineSpan) }) {
                                    LibraryBrowseHeading(title, query, { query = it }, searchVisible, { play(false) }, { play(true) })
                                }
                                items(albums, key = { localAlbumKey(it) }) { album ->
                                    LibraryAlbumTile(album.album, album.artist, album.albumArtUri, "library-album-${libraryAlbumSourceKey(album)}") { onAlbum(album) }
                                }
                                if (albums.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) { LibraryEmpty(query) }
                            }
                            else -> LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom), flingBehavior = rememberAkiraBouncingFling()) {
                                item("header") { LibraryBrowseHeading(title, query, { query = it }, searchVisible,
                                    if (key == "Tracks" || folder != null) ({ play(false) }) else null,
                                    if (key == "Tracks" || folder != null) ({ play(true) }) else null) }
                                if (key == "Tracks" || folder != null) items(filtered, key = { it.id }) { song ->
                                    LocalSongSwipeRow(song) { SongRowItem(song, current?.id == song.id, playing && current?.id == song.id,
                                        { onPlay(song, filtered) }, { onMore(song) }) }
                                } else if (key == "Artists") items(filtered.distinctBy { it.artist }, key = { it.artist }) { artist ->
                                    LibraryCategoryRow(artist.artist, artist.albumArtUri, true) { onArtist(artist.artist) }
                                } else items(filtered.distinctBy { it.path.substringBeforeLast('/') }, key = { it.path.substringBeforeLast('/') }) { song ->
                                    val path = song.path.substringBeforeLast('/')
                                    LibraryCategoryRow(path.substringAfterLast('/'), song.albumArtUri, false) { folder = path }
                                }
                                if (filtered.isEmpty()) item("empty") { LibraryEmpty(query) }
                            }
                        }
                    }
                    LibraryFloatingBar(title, if (key == "Playlists") 1f else collapse, backdrop,
                        onBack = { if (folder != null) folder = null else category = null },
                        onFirst = { if (key == "Albums") sortMenu = true else onSort() }, onMore = { menu = true })
                    if (menu) AkiraContextMenu(onDismiss = { menu = false }, groups = listOf(listOf(
                        AkiraContextAction(Icons.Rounded.Search, localizedText("Search", "Buscar")) { searchVisible = true },
                        AkiraContextAction(Icons.Rounded.Settings, localizedText("Settings", "Ajustes"), onClick = onSettings))))
                    if (sortMenu) LibrarySortMenu(albumSort, { albumSortName = it.name }, { sortMenu = false })
                }
            }
        }
    }
    }
    }
}

internal fun libraryAlbumSourceKey(song: Song) = if (song.albumId != 0L) "id:${song.albumId}"
    else "nm:${song.album.trim().lowercase()}|${song.artist.trim().lowercase()}"

@Composable private fun LibraryCategoryRow(title: String, artwork: String?, artist: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (artist) Modifier.containerTransformSource("library-artist-$title", 28.dp, artwork) else Modifier)
        .heightIn(min = 76.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(artwork, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(if (artist) CircleShape else RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Text(title, Modifier.weight(1f).padding(horizontal = 14.dp), fontFamily = AkiraFontFamily, fontSize = 17.sp)
        Icon(AkiraIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), thickness = .5.dp)
}

@Composable private fun LibraryEmpty(query: String) {
    Text(if (query.isBlank()) localizedText("No music here yet", "Todavía no hay música aquí") else localizedText("No results", "Sin resultados"),
        Modifier.fillMaxWidth().padding(vertical = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
