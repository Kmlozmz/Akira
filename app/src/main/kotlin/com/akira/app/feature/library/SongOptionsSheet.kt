package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.akiraStrings
import com.akira.app.core.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    isFromPlayer: Boolean = false,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: ((Song) -> Unit)? = null,
    onShare: ((Song) -> Unit)? = null,
    onGoToAlbum: ((Song) -> Unit)? = null,
    onGoToArtist: ((Song) -> Unit)? = null,
    onViewLyrics: (() -> Unit)? = null,
    onViewDetails: (Song) -> Unit,
    onDelete: ((Song) -> Unit)? = null,
    onSleepTimer: (() -> Unit)? = null
) {
    val context=LocalContext.current
    val dao=remember(context) {com.akira.app.core.database.AppDatabase.getInstance(context).favoritesDao()}
    val favoriteFlow=remember(dao,song.id) {com.akira.app.core.online.songFavoriteFlow(context, song)}
    val favorite by favoriteFlow.collectAsStateWithLifecycle(initialValue=song.isFavorite)
    val strings=akiraStrings()
    val haptics=rememberAkiraHaptics()
    var showAdd by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    val quick=listOf(
        AkiraContextAction(AkiraIcons.Queue,strings.addToQueue){haptics.click();onAddToQueue(song)},
        AkiraContextAction(if(favorite)AkiraIcons.StarFilled else AkiraIcons.Star,strings.favorite,selected=favorite){haptics.toggle(!favorite);onToggleFavorite?.invoke(song)},
        AkiraContextAction(AkiraIcons.Share,strings.share){haptics.click();if(onShare!=null)onShare(song)else shareSongIntent(context,song)}
    )
    val navigation=buildList {
        add(AkiraContextAction(AkiraIcons.FastForward,strings.playNext){haptics.click();onPlayNext(song)})
        if (!song.contentUri.startsWith("akira-youtube:")) add(AkiraContextAction(AkiraIcons.Queue,strings.library.addToList,opensPresentation=true){showAdd=true})
        if(onGoToAlbum!=null || onGoToArtist!=null)add(AkiraContextAction(Icons.Rounded.NorthEast,
            com.akira.app.core.design.components.localizedText("Go to","Ir a"),opensPresentation=true,hasSubmenu=true){showGoTo=true})
        if(isFromPlayer&&onViewLyrics!=null)add(AkiraContextAction(AkiraIcons.Lyrics,strings.viewLyrics){onViewLyrics()})
        if(isFromPlayer&&onSleepTimer!=null)add(AkiraContextAction(AkiraIcons.SleepTimer,strings.sleepTimer){onSleepTimer()})
        add(AkiraContextAction(AkiraIcons.Info,strings.viewCredits,opensPresentation=true){onViewDetails(song)})
    }
    val destructive=if(onDelete==null || song.contentUri.startsWith("akira-youtube:"))emptyList()else listOf(AkiraContextAction(AkiraIcons.Trash,strings.delete,destructive=true,opensPresentation=true){onDelete(song)})
    AkiraContextMenu(onDismiss=onDismiss,quickActions=quick,groups=listOf(navigation.take(2),navigation.drop(2),destructive))
    if(showGoTo) AkiraContextMenu(onDismiss={showGoTo=false},groups=listOf(buildList {
        if(onGoToAlbum!=null)add(AkiraContextAction(AkiraIcons.Album,strings.goToAlbum){onGoToAlbum(song);onDismiss()})
        if(onGoToArtist!=null)add(AkiraContextAction(AkiraIcons.Artist,strings.goToArtist){onGoToArtist(song);onDismiss()})
    }))
    if(showAdd) AddToPlaylistSheet(listOf(song), onDismiss = { showAdd = false }, onComplete = onDismiss)
}

private fun shareSongIntent(context: Context, song: Song) {
    val online = song.contentUri.startsWith("akira-youtube:")
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (online) "text/plain" else "audio/*"
            val uri = Uri.parse(song.contentUri)
            if (!online) putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, if (online) "https://music.youtube.com/watch?v=${song.id.removePrefix("youtube:")}" else "${song.title} - ${song.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, song.title))
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, if (online) "https://music.youtube.com/watch?v=${song.id.removePrefix("youtube:")}" else "${song.title} - ${song.artist}")
        }
        context.startActivity(Intent.createChooser(shareIntent, song.title))
    }
}
