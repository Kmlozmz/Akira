package com.akira.app.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akira.app.core.design.components.*
import com.akira.app.core.design.navigation.containerTransformSource
import com.akira.app.core.model.Song
import com.akira.app.core.online.*
import com.akira.app.feature.home.components.*
import com.akira.innertube.models.*

/** Keeps the provider's shelf structure while sharing the local Home components. */
internal fun LazyListScope.onlineHomeSections(shelves: List<OnlineShelf>, current: Song?, playing: Boolean,
    sourcePrefix: String, choose: (YTItem, String?) -> Unit, onMore: (Song) -> Unit) {
    shelves.forEachIndexed { section, shelf ->
        item("section-$section") {
            HomeSectionTitle(shelf.title, label = shelf.reason, thumbnail = shelf.thumbnail?.let { url -> {
                AsyncImage(url, null, Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } })
        }
        item("shelf-$section") {
            val width = LocalConfiguration.current.screenWidthDp.dp
            val unique = shelf.items.distinctBy { it.id }
            when (shelf.layout) {
                OnlineShelfLayout.TRACKS, OnlineShelfLayout.RANKED_TRACKS -> {
                    val rows = unique.size.coerceIn(1, 3)
                    LazyHorizontalGrid(rows = GridCells.Fixed(rows),
                        modifier = Modifier.height((54 * rows + 12 * (rows - 1)).dp),
                        contentPadding = PaddingValues(horizontal = HomeContentInset),
                        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gridItemsIndexed(unique, key = { _, item -> item.id }) { index, item ->
                            val key = item.onlineCollectionKey("$sourcePrefix-shelf-$section")
                            HomeTrackRow(item.thumbnail, item.title, item.onlineSubtitle(),
                                current?.id == "youtube:${item.id}", playing, { choose(item, key) },
                                { if (item is SongItem) onMore(item.asOnlineSong()) },
                                if (item is SongItem) ({ onMore(item.asOnlineSong()) }) else null,
                                modifier = Modifier.width(width - 72.dp).containerTransformSource(key, coverUrl = item.thumbnail),
                                rank = if (shelf.layout == OnlineShelfLayout.RANKED_TRACKS) index + 1 else null,
                                coverShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp))
                        }
                    }
                }
                OnlineShelfLayout.VIDEOS -> LazyRow(contentPadding = PaddingValues(horizontal = HomeContentInset),
                    horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing)) {
                    items(unique, key = { it.id }) { item ->
                        HomeVideoCard(item.title, item.onlineSubtitle(), item.thumbnail, width - 72.dp,
                            current?.id == "youtube:${item.id}", playing,
                            Modifier.clickable { choose(item, null) })
                    }
                }
                else -> LazyRow(contentPadding = PaddingValues(horizontal = HomeContentInset),
                    horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing)) {
                    items(unique, key = { it.id }) { item ->
                        val key = item.onlineCollectionKey("$sourcePrefix-shelf-$section")
                        val albums = shelf.artistAlbums[item.id].orEmpty()
                        if (item is ArtistItem && albums.size >= 2) {
                            OnlineArtistCollection(item, albums,
                                Modifier.containerTransformSource(key, ContentCardCornerRadius, item.thumbnail)
                                    .clickable { choose(item, key) })
                        } else HomeCoverCard(item.title, item.onlineSubtitle(), item.thumbnail,
                            Modifier.containerTransformSource(key, if (item is ArtistItem) 68.5.dp else ContentCardCornerRadius, item.thumbnail)
                                .clickable { choose(item, key) }, circular = item is ArtistItem,
                            isActive = current?.id == "youtube:${item.id}", isPlaying = playing)
                    }
                }
            }
        }
        item("section-space-$section") { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable private fun OnlineArtistCollection(artist: ArtistItem, albums: List<AlbumItem>, modifier: Modifier) {
    Column(modifier.width(260.dp).clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(artist.thumbnail, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Text(artist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            albums.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { album -> AsyncImage(album.thumbnail, album.title,
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
