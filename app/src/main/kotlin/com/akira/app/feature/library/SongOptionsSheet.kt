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
    val online=song.contentUri.startsWith("akira-youtube:")
    val quick=buildList {
        if(onToggleFavorite!=null)add(AkiraContextAction(
            if(favorite)AkiraIcons.StarFilled else AkiraIcons.Star,
            if(favorite)localizedText("Favorited","En favoritos")else strings.favorite,selected=favorite) {
            haptics.toggle(!favorite);onToggleFavorite(song)
        })
        add(AkiraContextAction(AkiraIcons.Share,strings.share){
            if(!isFromPlayer)haptics.click();if(onShare!=null)onShare(song)else shareSongIntent(context,song)
        })
    }
    val queueActions=buildList {
        add(AkiraContextAction(AkiraIcons.FastForward,strings.playNext){if(!isFromPlayer)haptics.click();onPlayNext(song)})
        add(AkiraContextAction(AkiraIcons.Queue,strings.addToQueue){if(!isFromPlayer)haptics.click();onAddToQueue(song)})
        if(!online)add(AkiraContextAction(AkiraIcons.Queue,strings.library.addToList,opensPresentation=true){showAdd=true})
    }
    val destinations=buildList {
        if(onGoToAlbum!=null)add(AkiraContextAction(AkiraIcons.Album,strings.goToAlbum){onGoToAlbum(song)})
        if(onGoToArtist!=null)add(AkiraContextAction(AkiraIcons.Artist,strings.goToArtist){onGoToArtist(song)})
    }
    val tools=buildList {
        if(isFromPlayer&&onViewLyrics!=null)add(AkiraContextAction(AkiraIcons.Lyrics,strings.viewLyrics){onViewLyrics()})
        if(isFromPlayer&&onSleepTimer!=null)add(AkiraContextAction(AkiraIcons.SleepTimer,strings.sleepTimer,
            opensPresentation=true){onSleepTimer()})
        add(AkiraContextAction(AkiraIcons.Info,localizedText("Track information","Información de la canción"),
            opensPresentation=true){onViewDetails(song)})
    }
    val destructive=if(onDelete==null || online)emptyList()else listOf(
        AkiraContextAction(AkiraIcons.Trash,strings.delete,destructive=true,opensPresentation=true){onDelete(song)})
    AkiraContextMenu(onDismiss=onDismiss,quickActions=quick,dense=true,
        groups=listOf(queueActions,destinations,tools,destructive),
        header={AkiraContextHeader(song.title,song.artist,song.albumArtUri,AkiraIcons.Album)})
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
