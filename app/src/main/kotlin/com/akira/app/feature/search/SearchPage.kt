package com.akira.app.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.rememberAkiraBouncingFling
import com.akira.app.core.model.Song
import com.akira.app.feature.home.components.HomeTrackRow

@Composable internal fun SearchPage(online: Boolean, bottomPadding: Dp, onSettings: () -> Unit,
    recent: List<String>, onRecent: (String) -> Unit, onClear: () -> Unit,
    browse: LazyListScope.() -> Unit, results: LazyListScope.() -> Unit) {
    val session = LocalSearchSession.current ?: return
    val browseList = rememberLazyListState()
    val resultsList = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val reduced = LocalContentMotionReduced.current
    LaunchedEffect(session.query, session.library, session.kind) { resultsList.scrollToItem(0) }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Crossfade(session.editing, animationSpec = tween(if (reduced) 0 else 140), label = "searchContent") { editing ->
            if (!editing) LazyColumn(state = browseList, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = status + 14.dp, bottom = bottomPadding + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), flingBehavior = rememberAkiraBouncingFling()) {
                item("search-title") {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(localizedText("Search", "Buscar"), Modifier.weight(1f), fontSize = 34.sp,
                            lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                        AkiraAccountButton(onSettings)
                    }
                }
                browse()
            } else Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    if (online) AkiraSegmentedControl(listOf("catalog" to "Akira Music", "library" to localizedText("Library", "Biblioteca")),
                        if (session.library) "library" else "catalog", { session.library = it == "library" },
                        cornerRadius = 50.dp, indicatorRadius = 50.dp,
                        indicatorColor = colors.surfaceContainerHighest, selectedTextColor = colors.onSurface)
                    else Text(localizedText("Your library", "Tu biblioteca"), Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (session.query.isBlank() && recent.isEmpty()) Box(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPadding), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(42.dp), tint = colors.onSurfaceVariant.copy(alpha = .6f))
                        Text(localizedText("No Recent Searches", "No hay búsquedas recientes"), fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge)
                        Text(localizedText("Your recent searches will appear here.", "Tus búsquedas recientes aparecerán aquí."),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                } else LazyColumn(state = resultsList, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), flingBehavior = rememberAkiraBouncingFling()) {
                    if (session.query.isBlank()) {
                        item("recent-header") { Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(localizedText("Recent Searches", "Búsquedas recientes"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = onClear) { Text(localizedText("Clear", "Borrar")) }
                        } }
                        items(recent, key = { "recent-$it" }) { query ->
                            Row(Modifier.fillMaxWidth().clickable { session.search(query); onRecent(query) }
                                .padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Icon(Icons.Rounded.History, null, tint = colors.onSurfaceVariant)
                                Text(query, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else results()
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(status + 10.dp).background(Brush.verticalGradient(
            listOf(colors.background, colors.background.copy(alpha = .97f), Color.Transparent))))
    }
}

@Composable internal fun SearchCategoryCard(title: String, artwork: String?, color: Color, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    Box(modifier.aspectRatio(1.72f).clip(RoundedCornerShape(14.dp)).background(color)
        .clickable(onClick = onClick)) {
        if (!artwork.isNullOrBlank()) AsyncImage(artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .02f), Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .70f)))))
        Text(title, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White,
            fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun LocalSearchPage(songs: List<Song>, current: Song?, playing: Boolean, bottomPadding: Dp,
    onPlay: (Song, List<Song>) -> Unit, onTogglePlay: () -> Unit, onMore: (Song) -> Unit,
    onAlbum: (Song) -> Unit, onArtist: (String) -> Unit, onSettings: () -> Unit) {
    val session = LocalSearchSession.current ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("local_search", android.content.Context.MODE_PRIVATE) }
    var recent by remember { mutableStateOf(runCatching {
        val stored = org.json.JSONArray(preferences.getString("recent", "[]"))
        (0 until stored.length()).map { stored.getString(it) }.take(12)
    }.getOrDefault(emptyList())) }
    fun rememberQuery(query: String) {
        if (query.isBlank()) return
        recent = (listOf(query.trim()) + recent.filterNot { it.equals(query.trim(), true) }).take(12)
        preferences.edit().putString("recent", org.json.JSONArray(recent).toString()).apply()
    }
    LaunchedEffect(session.submit) { if (session.submit > 0) rememberQuery(session.query) }
    val albums = remember(songs) { songs.distinctBy { it.albumId to (it.album to it.artist) }.take(24) }
    val filtered = remember(songs, session.query) { songs.filter { song -> session.query.split(Regex("\\s+")).all { word ->
        listOf(song.title, song.artist, song.album, song.genre).any { it.contains(word, true) }
    } } }
    SearchPage(false, bottomPadding, onSettings, recent, ::rememberQuery,
        { recent = emptyList(); preferences.edit().remove("recent").apply() }, browse = {
            items(albums.chunked(2), key = { "local-${it.first().id}" }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { song -> SearchCategoryCard(song.album.ifBlank { song.artist }, song.albumArtUri,
                        MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f)) { onAlbum(song) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (songs.isEmpty()) item("local-empty") { SearchEmpty(localizedText("No music on this phone", "No hay música en este teléfono")) }
        }, results = {
            if (filtered.isEmpty()) item("no-results") { SearchEmpty(localizedText("No results", "Sin resultados")) }
            val artists = filtered.distinctBy { it.artist }.filter { it.artist.contains(session.query, true) }.take(4)
            if (artists.isNotEmpty()) {
                item("artists-title") { SearchSectionTitle(localizedText("Artists", "Artistas")) }
                items(artists, key = { "artist-${it.artist}" }) { song ->
                    ListItem(headlineContent = { Text(song.artist) },
                        leadingContent = { AsyncImage(song.albumArtUri, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50))) },
                        modifier = Modifier.clickable { rememberQuery(session.query); onArtist(song.artist) })
                }
            }
            if (filtered.isNotEmpty()) item("songs-title") { SearchSectionTitle(localizedText("Songs", "Canciones")) }
            items(filtered, key = { "song-${it.id}" }) { song -> Box(Modifier.padding(horizontal = 24.dp)) {
                HomeTrackRow(song.albumArtUri, song.title, song.artist, current?.id == song.id, playing,
                    { rememberQuery(session.query); if (current?.id == song.id) onTogglePlay() else onPlay(song, filtered) },
                    { onMore(song) }, { onMore(song) })
            } }
        })
}

@Composable internal fun SearchEmpty(message: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable internal fun SearchSectionTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
