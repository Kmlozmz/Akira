package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import com.akira.app.core.design.components.showAkiraSnackbar

import com.akira.app.core.design.components.AkiraModalSheet

import com.akira.app.core.data.playlist.PlaylistStore

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akira.app.core.database.model.*
import com.akira.app.core.design.components.AkiraNameDialog
import com.akira.app.core.design.components.AkiraIconButton
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.*
import com.akira.app.core.media.MusicServiceConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistSheet(songs: List<Song>, onDismiss: () -> Unit) {
    AddToPlaylistSheet(songs, onDismiss, onDismiss)
}

@Composable
internal fun AddToPlaylistSheet(songs: List<Song>, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val store = remember { PlaylistStore(context) }
    val playlists by store.playlists.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var create by remember { mutableStateOf(false) }
    val createSource = com.akira.app.core.design.components.rememberAkiraMenuSource(icon=null)
    AkiraModalSheet(onDismissRequest = onDismiss, title = strings.library.addToList) {
        Text(strings.library.addToList, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { createSource.open { create = true } }, modifier = Modifier.padding(horizontal = 12.dp).then(createSource.modifier)) {
            Icon(AkiraIcons.Queue, null); Spacer(Modifier.width(8.dp)); Text(strings.library.newList)
        }
        LazyColumn(Modifier.heightIn(max = 360.dp).fillMaxWidth(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
            items(playlists, key = { it.id }) { playlist ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                    scope.launch { store.add(playlist.id, songs); onComplete() }
                }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AkiraIcons.Queue, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)); Spacer(Modifier.width(16.dp))
                    Text(playlist.title, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
    if (create) AkiraNameDialog(onDismiss = { create = false }, onSave = { title ->
        scope.launch { store.create(title, songs); onComplete() }
    })
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlaylistTab(library: List<Song>, musicService: MusicServiceConnection, backEnabled: Boolean = true, bottomContentPadding: androidx.compose.ui.unit.Dp = 110.dp, onSongMore: (Song) -> Unit = {}) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val store = remember { PlaylistStore(context) }
    val playlists by store.playlists.collectAsStateWithLifecycle(emptyList())
    val allItems by store.items.collectAsStateWithLifecycle(emptyList())
    val stats by store.stats.collectAsStateWithLifecycle(emptyList())
    val smart = remember(library, stats) { SmartPlaylists.calculate(library, stats, System.currentTimeMillis()) }
    val smartNames = listOf(strings.library.recentlyAdded, strings.library.mostPlayed, strings.library.neverPlayed, strings.library.forgottenFavorites)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSmart by rememberSaveable { mutableStateOf<Int?>(null) }
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistEntity?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var smartMenu by remember { mutableStateOf<Int?>(null) }
    var savingSmart by remember { mutableStateOf<Int?>(null) }
    val playlist = playlists.firstOrNull { it.id == selectedId }
    val entries = allItems.filter { it.playlistId == selectedId }
    val byKey = remember(library) { library.associateBy { SongKey.of(it).encoded } }
    val currentSong by musicService.currentSong.collectAsStateWithLifecycle()
    val isPlaying by musicService.isPlaying.collectAsStateWithLifecycle()
    fun resolve(item: PlaylistItemEntity): Song = byKey[item.songKey]?.copy(id = item.songKey)
        ?: library.firstOrNull { "legacy:${it.id}" == item.songKey }?.copy(id = item.songKey)
        ?: Song(item.songKey, strings.library.unavailable, "", "", 0, "")
    fun original(song: Song) = byKey[song.id] ?: library.firstOrNull { "legacy:${it.id}" == song.id } ?: song
    fun play(songs: List<Song>, shuffle: Boolean, start: Song? = null) {
        val available = songs.filter { it.contentUri.isNotBlank() }.map(::original).let { if (shuffle) it.shuffled() else it }
        if (available.isNotEmpty()) {
            musicService.mediaController?.shuffleModeEnabled = false
            musicService.playSong(start?.let(::original) ?: available.first(), available)
        }
    }
    BackHandler(backEnabled && (playlist != null || selectedSmart != null)) { selectedId = null; selectedSmart = null }
    val detail = playlist != null || selectedSmart != null
    val detailSongs = if (playlist != null) entries.map(::resolve) else selectedSmart?.let { smart[it] }.orEmpty()
    val detailName = playlist?.title ?: selectedSmart?.let { smartNames[it] }.orEmpty()
    val nameSource = com.akira.app.core.design.components.rememberAkiraMenuSource(icon=null)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!detail) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.library.lists, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nameSource.open { renaming = null; naming = true } }, modifier=Modifier.then(nameSource.modifier)) {
                        Icon(Icons.Rounded.Add, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(4.dp))
                        Text(strings.library.newList, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = bottomContentPadding), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                    item { Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.ForYou), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) }
                    items(4, key = { "smart:$it" }) { index ->
                        PlaylistCollectionRow(smartNames[index], smart[index], { selectedSmart = index }, { smartMenu = index })
                    }
                    item { Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.YourPlaylists), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) }
                    if (playlists.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.StartYourCollection), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.CreateAPlaylistWithYourFavoriteSongs),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 13.sp)
                        }
                    }
                    items(playlists, key = { it.id }) { item ->
                        val songs = allItems.filter { it.playlistId == item.id }.map(::resolve)
                        PlaylistCollectionRow(item.title, songs, { selectedId = item.id }, { menuId = item.id })
                    }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.akira.app.core.design.navigation.PageBackButton(onBack = { selectedId = null; selectedSmart = null })
                        Text(strings.library.lists, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (playlist != null) AkiraIconButton(AkiraIcons.MoreHoriz, strings.options, { menuId = playlist.id }, showBackground = false)
                        else TextButton(onClick = { nameSource.open { savingSmart = selectedSmart } }, modifier=Modifier.then(nameSource.modifier)) { Text(strings.library.saveAsList, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) }
                    }
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CollectionArtwork(detailSongs.map { it.albumArtUri }, detailName, Modifier.size(72.dp))
                        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(detailName, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(collectionCount(detailSongs.size), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 13.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(false to strings.play, true to strings.shuffle).forEach { (shuffle, label) ->
                            Button(onClick = { play(detailSongs, shuffle) }, enabled = detailSongs.any { it.contentUri.isNotBlank() },
                                modifier = Modifier.weight(1f).height(48.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202024), contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)) {
                                Icon(if (shuffle) AkiraIcons.Shuffle else AkiraIcons.Play, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp)); Text(label)
                            }
                        }
                    }
                    if (playlist == null) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottomContentPadding), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                        if (detailSongs.isEmpty()) item { Text(strings.library.emptyList, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), modifier = Modifier.padding(vertical = 24.dp)) }
                        items(detailSongs, key = { it.id }) { song ->
                            com.akira.app.core.design.components.LocalSongSwipeRow(original(song)) {
                                SongRowItem(song, currentSong?.id == song.id, isPlaying && currentSong?.id == song.id,
                                    { play(detailSongs, false, song) }, { onSongMore(song) }, Modifier.padding(horizontal = 24.dp))
                            }
                        }
                    } else EditableSongList(detailSongs,
                        onClick = { if (it.contentUri.isNotBlank()) play(detailSongs, false, it) },
                        onRemove = { song -> scope.launch {
                            val removed = entries.firstOrNull { it.songKey == song.id } ?: return@launch
                            store.removeItem(playlist.id, song.id)
                            if (snackbar.showAkiraSnackbar(strings.songRemoved, strings.undo) == SnackbarResult.ActionPerformed)
                                store.restoreItem(playlist.id, removed, removed.position)
                        } },
                        onOrder = { order -> scope.launch { store.reorder(playlist.id, order.map { it.id }) } },
                        modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        com.akira.app.core.design.components.AkiraSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
    }
    menuId?.let { id -> playlists.firstOrNull { it.id == id }?.let { item ->
        com.akira.app.core.design.components.AkiraContextMenu(onDismiss={menuId=null},groups=listOf(listOf(
            com.akira.app.core.design.components.AkiraContextAction(AkiraIcons.Edit,strings.library.rename,opensPresentation=true){renaming=item;naming=true},
            com.akira.app.core.design.components.AkiraContextAction(AkiraIcons.Queue,strings.library.duplicate){scope.launch{store.duplicate(item)}}
        ),listOf(com.akira.app.core.design.components.AkiraContextAction(AkiraIcons.Trash,strings.remove,destructive=true){
            val snapshot=allItems.filter{it.playlistId==item.id}
            scope.launch{store.delete(item.id);if(snackbar.showAkiraSnackbar(strings.library.listDeleted,strings.undo)==SnackbarResult.ActionPerformed)store.restore(item,snapshot)}
        })))
    } }
    smartMenu?.let { index -> com.akira.app.core.design.components.AkiraContextMenu(onDismiss={smartMenu=null},groups=listOf(listOf(
        com.akira.app.core.design.components.AkiraContextAction(AkiraIcons.Queue,strings.library.saveAsList,opensPresentation=true){savingSmart=index}
    ))) }
    if (naming) AkiraNameDialog(renaming?.title.orEmpty(), { naming = false }, { name ->
        scope.launch { val item = renaming; if (item == null) store.create(name) else store.rename(item, name); naming = false }
    })
    savingSmart?.let { index -> AkiraNameDialog(smartNames[index], { savingSmart = null }, { name -> scope.launch { store.create(name, smart[index]); savingSmart = null } }) }
}

@Composable
private fun PlaylistCollectionRow(title: String, songs: List<Song>, onClick: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CollectionArtwork(songs.map { it.albumArtUri }, title, Modifier.size(64.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(collectionCount(songs.size), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 13.sp)
        }
        AkiraIconButton(AkiraIcons.MoreHoriz, LocalStrings.current.options, onMore, showBackground = false)
    }
}
