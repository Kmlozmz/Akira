package com.akira.app.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akira.innertube.models.*
import com.akira.app.core.design.components.AkiraSegmentedControl
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.model.Song
import com.akira.app.core.online.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*

@Composable internal fun OnlineSearchField(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(query, onQuery, singleLine = true,
        placeholder = { Text(localizedText("Artists, songs, albums…", "Artistas, canciones, álbumes…")) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
            Icon(AkiraIcons.Close, localizedText("Clear search", "Borrar búsqueda"))
        } },
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
}

internal fun onlineKinds() = listOf("all", "songs", "artists", "albums", "playlists")
@Composable internal fun onlineKindLabel(kind: String): String = when (kind) {
    "songs" -> localizedText("Songs", "Canciones")
    "artists" -> localizedText("Artists", "Artistas")
    "albums" -> localizedText("Albums", "Álbumes")
    "playlists" -> "Playlists"
    else -> localizedText("All", "Todo")
}
@Composable internal fun OnlineSearchKinds(selected: String, onSelect: (String) -> Unit) {
    val haptics = rememberAkiraHaptics()
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(onlineKinds()) { kind ->
            FilterChip(selected == kind, { if (selected != kind) { haptics.light(); onSelect(kind) } },
                label = { Text(onlineKindLabel(kind)) }, border = null, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
        }
    }
}
@Composable internal fun OnlineLibraryKinds(selected: String, connected: Boolean = true, onSelect: (String) -> Unit) {
    val haptics = rememberAkiraHaptics()
    Box(Modifier.padding(horizontal = 24.dp)) {
        val options = if(connected) listOf("playlists", "albums", "artists", "songs").map { it to onlineKindLabel(it) }
            else listOf("songs" to localizedText("Liked songs", "Me gusta"), "recent" to localizedText("Recently played", "Recientes"))
        AkiraSegmentedControl(options,
            selected, { haptics.light(); onSelect(it) })
    }
}

@Composable internal fun OnlineLibraryIntro(account: OnlineAccount, onAccount: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainer)))
        .clickable(onClick = onAccount).padding(20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        com.akira.app.core.design.components.AkiraAccountAvatar(account, Modifier.size(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (account.connected) account.name.ifBlank { "YouTube Music" } else localizedText("Saved on this device", "Guardada en este dispositivo"),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (account.connected) localizedText("Saved in YouTube Music", "Guardada en YouTube Music")
                else localizedText("Link an account to access your YouTube Music library", "Vincula una cuenta para acceder a tu biblioteca de YouTube Music"),
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(AkiraIcons.ChevronRight, localizedText("Account and mode", "Cuenta y modo"), Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}

@Composable internal fun OnlineRecentSearches(queries: List<String>, onSelect: (String) -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(localizedText("Recent searches", "Búsquedas recientes"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClear) { Text(localizedText("Clear", "Borrar")) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(queries, key = { it }) { query ->
                FilledTonalButton({ onSelect(query) }, shape = CircleShape) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(query, Modifier.widthIn(max = 220.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Results use their actual media type: songs stay compact, artists round, collections in a grid. */
internal fun LazyListScope.onlineBrowseSections(shelves: List<OnlineShelf>, search: Boolean, discovery: Boolean,
    current: Song?, playing: Boolean, sourcePrefix: String, choose: (YTItem, String?) -> Unit, more: (Song) -> Unit) {
    shelves.filter { it.items.isNotEmpty() }.forEachIndexed { index, shelf ->
        val entries = shelf.items.distinctBy { it.id }
        item("browse-title-$index") { Text(shelf.title, Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        val artists = entries.filterIsInstance<ArtistItem>()
        val collections = entries.filter { it is AlbumItem || it is PlaylistItem }
        val songs = entries.filterIsInstance<SongItem>()
        if (search && !discovery && artists.isNotEmpty()) {
            item("browse-artists-$index") {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artists, key = { it.id }) { artist ->
                        val source = artist.onlineCollectionKey("$sourcePrefix-artists-$index")
                        HomeCoverCard(artist.title, null, artist.thumbnail,
                            Modifier.containerTransformSource(source, 68.5.dp, artist.thumbnail).clickable { choose(artist, source) }, circular = true)
                    }
                }
            }
        } else if (artists.isNotEmpty()) {
            items(artists.chunked(2), key = { "artist-grid-$index-${it.first().id}" }) { row -> OnlineBrowseGridRow(row, "$sourcePrefix-artists-$index", choose) }
        }
        if (collections.isNotEmpty()) items(collections.chunked(2), key = { "collection-grid-$index-${it.first().id}" }) { row ->
            OnlineBrowseGridRow(row, "$sourcePrefix-collections-$index", choose)
        }
        if (songs.isNotEmpty()) itemsIndexed(songs, key = { row, song -> "browse-song-$index-$row-${song.id}" }) { row, song ->
            Column(Modifier.padding(horizontal = 24.dp)) {
                HomeTrackRow(song.thumbnail, song.title, song.onlineSubtitle(), current?.id == "youtube:${song.id}", playing,
                    { choose(song, null) }, { more(song.asOnlineSong()) }, { more(song.asOnlineSong()) })
                if (row != songs.lastIndex) Spacer(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)))
            }
        }
    }
}

@Composable private fun OnlineBrowseGridRow(items: List<YTItem>, sourcePrefix: String, choose: (YTItem, String?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            val source = item.onlineCollectionKey(sourcePrefix)
            Column(Modifier.weight(1f).containerTransformSource(source, if(item is ArtistItem) 90.dp else 20.dp, item.thumbnail)
                .clip(RoundedCornerShape(20.dp)).clickable { choose(item, source) }.padding(bottom = 8.dp)) {
                val shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(20.dp)
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
                    contentAlignment = Alignment.Center) {
                    if (item.thumbnail != null) AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(if (item.id == "LM") AkiraIcons.StarFilled else AkiraIcons.Album, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(item.title, Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                val subtitle = item.onlineSubtitle()
                if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (items.size == 1) Spacer(Modifier.weight(1f))
    }
}
