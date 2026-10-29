package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import com.akira.app.feature.collection.*
import com.akira.app.core.design.navigation.containerTransformSource

import com.akira.app.feature.home.HomeDestination
import com.akira.app.feature.home.HomeNavigation
import com.akira.app.feature.search.*
import com.akira.app.feature.home.AkiraDockState
import com.akira.app.feature.home.rememberAkiraDockState
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.feature.player.dock.MiniPlayerPull
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import com.akira.app.feature.home.LocalHomeFeed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import com.akira.app.core.design.navigation.pageNavigationTransform
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.components.LocalContentMotionReduced
import com.akira.app.core.design.components.showAkiraSnackbar

import com.akira.app.core.design.components.*
import com.akira.app.core.design.theme.AkiraThemeBackground

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.luminance

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.akira.app.feature.settings.SettingsScreen
import com.akira.app.core.design.components.AkiraIconButton
import com.akira.app.core.design.icon.AkiraIcons
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.akira.app.core.widget.WidgetPlaybackStore
import com.akira.app.core.widget.AkiraWidgets
import com.akira.app.core.media.MediaItemMapper.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.akira.app.core.i18n.LocalStrings
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.lerp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.compose.AsyncImage
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.theme.noRippleClickable
import com.akira.app.core.media.MusicServiceConnection
import com.akira.app.core.model.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Size
import com.akira.app.core.design.util.rememberAkiraHaptics
import kotlinx.coroutines.flow.first

@OptIn(UnstableApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    initialSongs: List<Song>, // Kept for backwards compatibility but not actively used
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = viewModel(),
    // External files use a one-song queue and are consumed after opening the player.
    externalSong: Song? = null,
    onExternalConsumed: () -> Unit = {},
    openPlayerFromWidget: Boolean = false,
    openPlayerFromMediaSession: Boolean = false,
    onMediaSessionPlayerOpened: () -> Unit = {},
    onWidgetPlayerOpened: () -> Unit = {},
    onReturnToWidget: () -> Boolean = { false }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { UserPreferencesRepository(context) }
    val userPrefs by prefsRepo.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    val onlineRepository = remember(context) { com.akira.app.core.online.OnlineRepository.get(context) }
    val onlineEnabled by onlineRepository.enabled.collectAsStateWithLifecycle()

    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val songsList = libraryState.songs
    val wholeLibrary by libraryViewModel.allSongs.collectAsStateWithLifecycle()
    val selectedTab by libraryViewModel.currentCategory.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSortOrder by libraryViewModel.currentSortOrder.collectAsStateWithLifecycle()
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(HomeDestination.HOME) }
    val showSettings = destination == HomeDestination.SETTINGS
    var settingsStartWithSound by rememberSaveable { mutableStateOf(false) }
    val searchSession = rememberSearchSession()
    var previousDestination by rememberSaveable { mutableStateOf(HomeDestination.HOME) }
    var homeScrollRequest by remember { mutableStateOf(0) }
    val homeFeedSources=com.akira.app.feature.home.rememberHomeFeedSources()
    val homeStateHolder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    fun openSettings(startWithSound: Boolean = false) {
        settingsStartWithSound = startWithSound
        previousDestination = com.akira.app.feature.home.settingsReturnDestination(destination, previousDestination)
        destination = HomeDestination.SETTINGS
    }
    fun closeSettings() { destination = previousDestination.takeUnless { it == HomeDestination.SETTINGS } ?: HomeDestination.HOME }


    var selectedAlbumFilter by remember { mutableStateOf<String?>(null) }
    var selectedArtistFilter by remember { mutableStateOf<String?>(null) }
    var selectedFolderFilter by remember { mutableStateOf<String?>(null) }
    // Display names are not identities: duplicate album and folder names need stable keys.
    var selectedAlbumKey by remember { mutableStateOf<String?>(null) }
    var selectedFolderKey by remember { mutableStateOf<String?>(null) }

    val musicService = remember { MusicServiceConnection(context, scope) }
    // The controller belongs to this composition, including across activity recreation.
    DisposableEffect(musicService) {
        onDispose { musicService.release() }
    }
    val isConnected by musicService.isConnected.collectAsStateWithLifecycle()
    val onlineSessionReady by onlineRepository.sessionReady.collectAsStateWithLifecycle()
    val onlineAccount by onlineRepository.account.collectAsStateWithLifecycle()
    val playbackError by musicService.playbackError.collectAsStateWithLifecycle()
    val onlinePlaybackErrorText = com.akira.app.core.design.components.localizedText(
        "This song couldn't be played. Try again or choose another song.",
        "No se pudo reproducir esta canción. Reintenta o elige otra.")
    val onlineActionErrorText = com.akira.app.core.design.components.localizedText(
        "Couldn't update your YouTube Music likes. Try again.", "No se pudieron actualizar tus Me gusta. Reintenta.")
    val onlineCollectionErrorText = com.akira.app.core.design.components.localizedText(
        "No album or artist information is available for this song.", "Esta canción no tiene información de álbum o artista disponible.")
    var onlineCollection by remember { mutableStateOf<com.akira.innertube.models.YTItem?>(null) }
    fun toggleSongFavorite(song: Song) {
        if (!song.contentUri.startsWith("akira-youtube:")) libraryViewModel.toggleFavorite(song)
        else scope.launch {
            try { onlineRepository.toggleLike(song) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { com.akira.app.core.design.components.AkiraNotices.show(onlineActionErrorText) }
        }
    }
    LaunchedEffect(playbackError, onlineEnabled) {
        if (playbackError && onlineEnabled) com.akira.app.core.design.components.AkiraNotices.show(
            onlinePlaybackErrorText)
    }
    val isPlaying by musicService.isPlaying.collectAsStateWithLifecycle()
    val serviceSong by musicService.currentSong.collectAsStateWithLifecycle()
    val serviceRepeatMode by musicService.repeatMode.collectAsStateWithLifecycle()
    val isShuffleActive by musicService.shuffleMode.collectAsStateWithLifecycle()
    val durationMs by musicService.durationMs.collectAsStateWithLifecycle()

    var localCurrentSong by remember { mutableStateOf<Song?>(if (openPlayerFromWidget || openPlayerFromMediaSession) AkiraWidgets.currentSong(context) else null) }
    LaunchedEffect(onlineEnabled, isConnected, onlineSessionReady, onlineAccount, userPrefs?.autoResume) {
        if (isConnected && onlineSessionReady && userPrefs != null && externalSong == null) {
            localCurrentSong = null
            musicService.selectSourceMode(onlineEnabled, userPrefs?.autoResume == true || openPlayerFromWidget)
        }
    }
    // The service decides when to expose the incoming track during crossfade.
    val currentSong: Song? = (serviceSong ?: localCurrentSong)?.takeIf {
        it.contentUri.startsWith("akira-youtube:") == onlineEnabled
    }

    val repeatState = queueRepeatState(serviceRepeatMode)

    var restoredPlayerExpanded by rememberSaveable { mutableStateOf(openPlayerFromWidget || openPlayerFromMediaSession) }
    val playerExpansionProgress = remember { Animatable(if (restoredPlayerExpanded) 1f else 0f) }
    // Subscribe to visibility here; continuous progress is consumed by player layers.
    val miniVisible by remember { derivedStateOf { playerExpansionProgress.value <= .001f } }
    LaunchedEffect(playerExpansionProgress) {
        androidx.compose.runtime.snapshotFlow { playerExpansionProgress.value }.collect {
            if(it <= .001f) restoredPlayerExpanded=false
            else if(it >= .999f) restoredPlayerExpanded=true
        }
    }
    val strings = LocalStrings.current

    var songForOptions by remember { mutableStateOf<Song?>(null) }
    var songForDetails by remember { mutableStateOf<Song?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    fun enqueueFromSwipe(song: Song, next: Boolean) {
        val entryId = (if (next) musicService.playNext(song) else musicService.addToQueue(song)) ?: return
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showAkiraSnackbar(
                "${if (next) strings.playNext else strings.addedToQueue}: ${song.title}",
                actionLabel = strings.undo, thumbnail = song.albumArtUri)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) musicService.undoQueueInsertion(entryId)
        }
    }
    var deletePreviewSong by remember { mutableStateOf<Song?>(null) }
    val performDeleteSong = rememberSongDeleter(onDeleted = { songId ->
        if (currentSong?.id == songId) musicService.pause()
        musicService.removeSongFromQueue(songId)
        scope.launch { snackbarHostState.showAkiraSnackbar(strings.fileDeleted) }
    }, onError = { scope.launch { snackbarHostState.showAkiraSnackbar(strings.library.error) } })
    fun requestDeleteSong(song: Song) { deletePreviewSong = song }

    val visualPrefs = userPrefs ?: if (openPlayerFromWidget) com.akira.app.core.datastore.AppUiSnapshot.read(context) else null
    val dynamicColorEnabled = visualPrefs?.dynamicColor ?: false
    val cachedWidgetColor = if (openPlayerFromWidget) Color(context.getSharedPreferences("akira_widget", android.content.Context.MODE_PRIVATE).getInt("background_color", 0xFF222432.toInt())) else Color(0xFF222432)
    var dynamicPrimaryColor by remember { mutableStateOf(cachedWidgetColor) }
    var dynamicSecondaryColor by remember { mutableStateOf(cachedWidgetColor) }
    var dynamicAccentColor by remember { mutableStateOf(cachedWidgetColor) }
    var artworkColors by remember { mutableStateOf(PlayerArtworkColors(cachedWidgetColor)) }

    // Use surface tones for the ambient scene; the AMOLED primary token is white.
    val themePrimary = MaterialTheme.colorScheme.background
    val themeSecondary = MaterialTheme.colorScheme.surfaceContainer
    val themeSurface = MaterialTheme.colorScheme.surfaceContainerHigh

    val effectivePrimary by remember(dynamicColorEnabled, dynamicPrimaryColor, themePrimary) {
        derivedStateOf { if (dynamicColorEnabled) dynamicPrimaryColor else themePrimary }
    }
    val effectiveSecondary by remember(dynamicColorEnabled, dynamicSecondaryColor, themeSecondary) {
        derivedStateOf { if (dynamicColorEnabled) dynamicSecondaryColor else themeSecondary }
    }
    val effectiveAccent by remember(dynamicColorEnabled, dynamicAccentColor, themeSurface) {
        derivedStateOf { if (dynamicColorEnabled) dynamicAccentColor else themeSurface }
    }

    LaunchedEffect(dynamicColorEnabled) {
        if (!dynamicColorEnabled) {
            dynamicPrimaryColor = Color(0xFF222432)
            dynamicSecondaryColor = Color(0xFF191B26)
            dynamicAccentColor = Color(0xFF12131C)
        }
    }

    LaunchedEffect(currentSong?.albumArtUri, currentSong?.id, dynamicColorEnabled) {
        // Leave the bitmap untouched when the option is disabled.
        if (!dynamicColorEnabled) return@LaunchedEffect
        val song = currentSong ?: return@LaunchedEffect

        // Retain the previous artwork color until the new palette is available.
        if (song.albumArtUri.isNullOrBlank()) {
            artworkColors = PlayerArtworkColors(Color(song.tone.hex))
            dynamicPrimaryColor = vibrantBoost(Color(song.tone.hex), isGrayscaleFallback = Color(0xFF222432))
            dynamicSecondaryColor = vibrantBoost(Color(song.tone.hex), isGrayscaleFallback = Color(0xFF191B26))
            dynamicAccentColor = Color(0xFF12131C)
            return@LaunchedEffect
        }

        val coverColors=withContext(Dispatchers.IO) {
            try {
                val uri=Uri.parse(song.albumArtUri)
                val key="${song.albumArtUri}:${song.dateModifiedSec}"
                PlayerArtworkPaletteCache.get(key) ?: run {
                    val request=coil.request.ImageRequest.Builder(context).data(uri).size(128)
                        .allowHardware(false).build()
                    val result=context.imageLoader.execute(request)
                    val bitmap=(result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if(bitmap==null)PlayerArtworkColors(Color(song.tone.hex))
                    else {
                        // Coil may return a compatible larger cached sleeve.
                        // Bound the CPU sampler even when the full cover is warm.
                        val sample=android.graphics.Bitmap.createScaledBitmap(bitmap,128,128,true)
                        try {artworkAmbientColors(sample,Color(song.tone.hex)).also {PlayerArtworkPaletteCache.put(key,it)}}
                        finally {if(sample!==bitmap)sample.recycle()}
                    }
                }
            } catch(cancel: kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_: Exception) {PlayerArtworkColors(Color(song.tone.hex))}
        }
        artworkColors=coverColors
        // All consumers receive colours from this same decode. Never invent
        // a second hue for monochrome artwork or keep the previous song on failure.
        dynamicPrimaryColor=coverColors.primary
        dynamicSecondaryColor=coverColors.accents.getOrNull(1) ?: coverColors.primary
        dynamicAccentColor=coverColors.accents.maxByOrNull {color ->
            maxOf(color.red,color.green,color.blue)-minOf(color.red,color.green,color.blue)
        } ?: coverColors.primary
    }

    var isInitialSongRestored by remember { mutableStateOf(false) }

    // Do not invent a current song on a fresh installation without a saved session.
    LaunchedEffect(songsList, userPrefs, onlineEnabled) {
        val prefs = userPrefs ?: return@LaunchedEffect
        if (!onlineEnabled && !isInitialSongRestored && songsList.isNotEmpty()) {
            val savedSongId = prefs.lastPlayedSongId
            if (!savedSongId.isNullOrBlank() && prefs.autoResume) {
                // If the stored track was deleted from the device, fall back to the first available track.
                localCurrentSong = songsList.firstOrNull { it.id == savedSongId } ?: songsList.first()
            }
            isInitialSongRestored = true
        }
    }

    fun playSong(song: Song, collection: List<Song>, shuffle: Boolean = isShuffleActive) {
        val selection = collectionPlayback(song, collection, shuffle) ?: return
        localCurrentSong = selection.song
        scope.launch {
            prefsRepo.savePlaybackState(selection.song.id, 0L)
        }
        musicService.mediaController?.shuffleModeEnabled = selection.shuffle
        musicService.playSong(selection.song, selection.queue)
    }

    fun playCollection(collection: List<Song>, shuffle: Boolean) {
        val first = if (shuffle) collection.randomOrNull() else collection.firstOrNull()
        first?.let { playSong(it, collection, shuffle) }
    }

    // A cold launcher session needs the same real queue as a widget session.
    // Keeping only localCurrentSong leaves next/previous with an empty player.
    LaunchedEffect(isConnected, isInitialSongRestored, userPrefs?.autoResume, onlineEnabled) {
        if (!onlineEnabled && isConnected && isInitialSongRestored && userPrefs?.autoResume == true &&
            !userPrefs?.lastPlayedSongId.isNullOrBlank() && externalSong == null) {
            musicService.restorePausedSession(songsList, userPrefs?.lastPlayedSongId,
                userPrefs?.lastPositionMs ?: 0L)
        }
    }

    // System media surfaces reopen the active player without changing playback.
    LaunchedEffect(openPlayerFromMediaSession, isConnected) {
        if (!openPlayerFromMediaSession || !isConnected) return@LaunchedEffect
        if (showSettings) closeSettings()
        if ((musicService.mediaController?.mediaItemCount ?: 0) > 0) playerExpansionProgress.snapTo(1f)
        else playerExpansionProgress.snapTo(0f)
        onMediaSessionPlayerOpened()
    }

    // Widget navigation restores a cold session paused; a live session keeps its state.
    LaunchedEffect(openPlayerFromWidget, isConnected) {
        if (!openPlayerFromWidget || !isConnected) return@LaunchedEffect
        val controller = musicService.mediaController ?: return@LaunchedEffect
        if (showSettings) closeSettings()
        musicService.restorePausedSession()
        if (controller.mediaItemCount == 0) {
            val saved = withContext(Dispatchers.IO) { WidgetPlaybackStore.read(context) }
            if (saved != null && saved.songs.all { it.contentUri.startsWith("akira-youtube:") == onlineEnabled }) {
                localCurrentSong = saved.songs[saved.safeIndex]
                controller.setMediaItems(saved.songs.map { it.toMediaItem(it.queueEntryId) }, saved.safeIndex, saved.safePosition)
                controller.shuffleModeEnabled = saved.shuffle
                controller.repeatMode = saved.repeat
                musicService.restorePlaybackOrder(saved.order)
                controller.prepare()
            } else {
                AkiraWidgets.currentSong(context)?.takeIf { it.contentUri.startsWith("akira-youtube:") == onlineEnabled }?.let { song ->
                    localCurrentSong = song
                    controller.setMediaItem(song.toMediaItem(song.queueEntryId))
                    controller.prepare()
                }
            }
        }
        if (controller.mediaItemCount > 0) {
            // Widget navigation is already expanded when the launcher reveals this window.
            playerExpansionProgress.snapTo(1f)
        } else {
            playerExpansionProgress.snapTo(0f)
        }
        onWidgetPlayerOpened()
    }

    // Keep external playback isolated from the library queue.
    LaunchedEffect(externalSong) {
        val song = externalSong ?: return@LaunchedEffect
        onlineRepository.setEnabled(false)
        musicService.selectSourceMode(false, restore = false)
        localCurrentSong = song
        musicService.playSong(song, listOf(song))
        playerExpansionProgress.animateTo(
            targetValue = 1f,
            animationSpec = playerSheetAnimationSpec()
        )
        onExternalConsumed()
    }

    fun togglePlayPause() {
        if (currentSong != null) {
            if (serviceSong == null) {
                playSong(currentSong, wholeLibrary.ifEmpty { listOf(currentSong) })
            } else {
                musicService.togglePlayPause()
            }
        }
    }

    fun playNext() {
        musicService.skipToNext()
    }

    fun playPrevious() {
        musicService.skipToPrevious()
    }

    fun toggleShuffle() {
        musicService.toggleShuffle()
    }

    fun toggleRepeat() {
        musicService.toggleRepeat()
    }

    fun seekTo(fraction: Float) {
        val dur = durationMs.takeIf { it > 0 } ?: (currentSong?.durationMs ?: 0L)
        if (dur > 0) {
            musicService.seekTo((fraction * dur).toLong())
        }
    }

    // Search filtering is owned by LibraryViewModel.
    val searchFilteredSongs = songsList

    val displaySongs = remember(
        searchFilteredSongs, selectedTab,
        selectedAlbumFilter, selectedAlbumKey, selectedArtistFilter, selectedFolderKey
    ) {
        when (selectedTab) {
            "Albums" -> {
                if (selectedAlbumKey != null) {
                    searchFilteredSongs.filter { albumKeyOf(it) == selectedAlbumKey }
                } else {
                    searchFilteredSongs
                }
            }
            "Artists" -> {
                if (selectedArtistFilter != null) {
                    searchFilteredSongs.filter { it.artist.equals(selectedArtistFilter, ignoreCase = true) }
                } else {
                    searchFilteredSongs
                }
            }
            "Folders" -> {
                if (selectedFolderKey != null) {
                    searchFilteredSongs.filter { folderKeyOf(it) == selectedFolderKey }
                } else {
                    searchFilteredSongs
                }
            }
            else -> searchFilteredSongs
        }
    }

    val albumsList = remember(searchFilteredSongs) {
        searchFilteredSongs.groupBy { albumKeyOf(it) }
            .map { (_, songs) ->
                val albumName = songs.firstOrNull()?.album?.ifBlank { "Unknown Album" } ?: "Unknown Album"
                AlbumItem(
                    key = albumKeyOf(songs.first()),
                    name = albumName,
                    artist = songs.firstOrNull()?.artist ?: "Unknown Artist",
                    songCount = songs.size,
                    artUri = songs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                )
            }.sortedBy { it.name.lowercase() }
    }

    val artistsList = remember(searchFilteredSongs) {
        searchFilteredSongs.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .map { (artistName, songs) ->
                ArtistItem(
                    name = artistName,
                    songCount = songs.size,
                    artUri = songs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                    covers = songs.mapNotNull { it.albumArtUri }.distinct().take(4),
                    albumCount = songs.map { albumKeyOf(it) }.distinct().size
                )
            }.sortedBy { it.name.lowercase() }
    }

    val foldersList = remember(searchFilteredSongs) {
        searchFilteredSongs.groupBy { folderKeyOf(it) }
            .map { (_, songs) ->
                val dir = folderKeyOf(songs.first())
                FolderItem(
                    key = dir,
                    name = dir.substringAfterLast('/').ifBlank { dir.ifBlank { "Internal Storage" } },
                    path = dir,
                    songCount = songs.size,
                    covers = songs.mapNotNull { it.albumArtUri }.distinct().take(4)
                )
            }.sortedBy { it.name.lowercase() }
    }

    val studioNavigation = remember { com.akira.app.feature.settings.StudioNavigationState() }
    val dockState = rememberAkiraDockState()
    val homeMotionReduced = com.akira.app.core.design.components.rememberContentMotionReduced()
    fun openSongCollection(song: Song, artist: Boolean) {
        scope.launch {
            if (song.contentUri.startsWith("akira-youtube:")) {
                val target = onlineRepository.collectionFor(song, artist)
                if (target == null) {
                    com.akira.app.core.design.components.AkiraNotices.show(onlineCollectionErrorText)
                    return@launch
                }
                onlineCollection = target
                destination = HomeDestination.LIBRARY; songForOptions = null
                if (!miniVisible) { dockState.capture(); playerExpansionProgress.animateTo(0f, playerSheetAnimationSpec()) }
                return@launch
            }
            if(!miniVisible) {
                dockState.capture()
                if(homeMotionReduced) playerExpansionProgress.snapTo(0f)
                else playerExpansionProgress.animateTo(0f,playerSheetAnimationSpec())
            }
            destination=HomeDestination.LIBRARY
            libraryViewModel.setSearchQuery("")
            libraryViewModel.setCategory(if(artist)"Artists" else "Albums")
            selectedFolderFilter=null;selectedFolderKey=null
            selectedArtistFilter=if(artist)song.artist else null
            selectedAlbumFilter=if(artist)null else song.album
            selectedAlbumKey=if(artist)null else albumKeyOf(song)
            songForOptions=null
        }
    }
    val isPlayerExpanded by remember { derivedStateOf { playerExpansionProgress.value > 0.05f } }

    val detailSessions = remember { androidx.compose.runtime.mutableStateMapOf<Any, Boolean>() }
    val darkDetails = detailSessions.values.any { it }
    val libraryLightBars = !darkDetails && MaterialTheme.colorScheme.background.luminance() > .5f && (!isPlayerExpanded || studioNavigation.active != null)
    androidx.compose.runtime.SideEffect {
        (context as? com.akira.app.MainActivity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = libraryLightBars; isAppearanceLightNavigationBars = libraryLightBars
            }
        }
    }

    BackHandler(enabled = destination != HomeDestination.HOME || isPlayerExpanded || showSettings) {
        when {
            isPlayerExpanded -> {
                dockState.capture()
                if (onReturnToWidget()) return@BackHandler
                scope.launch {
                    playerExpansionProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = playerSheetAnimationSpec()
                    )
                }
            }
            showSettings -> closeSettings()
            destination == HomeDestination.SEARCH && searchSession.editing -> searchSession.close()
            selectedAlbumFilter != null -> { selectedAlbumFilter = null; selectedAlbumKey = null }
            selectedArtistFilter != null -> selectedArtistFilter = null
            selectedFolderFilter != null -> { selectedFolderFilter = null; selectedFolderKey = null }
            searchQuery.isNotBlank() -> libraryViewModel.setSearchQuery("")
            else -> destination=HomeDestination.HOME
        }
    }

    val glassBackdrop = rememberAkiraGlassBackdrop()
    val dockBackdrop = rememberLayerBackdrop(graphicsLayer = glassBackdrop.layer)
    val sampleGlass = userPrefs?.liquidGlassEnabled == true && com.akira.app.core.design.glass.isGlassSupported()
    val appNoticeColors=MaterialTheme.colorScheme
    com.akira.app.core.design.glass.ProvideGlassPreferences(userPrefs ?: com.akira.app.core.datastore.UserPreferences()) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSearchSession provides searchSession,
        com.akira.app.core.design.components.LocalAkiraNoticeColors provides appNoticeColors,
        com.akira.app.core.design.components.LocalAkiraMenuAnchors provides remember { com.akira.app.core.design.components.AkiraMenuAnchors() },
        LocalDetailSessions provides detailSessions,
        LocalContentMotionReduced provides (homeMotionReduced || com.akira.app.core.design.motion.InteractionPreferences.reducedMotion),
        com.akira.app.core.design.components.LocalSongSwipeActions provides com.akira.app.core.design.components.SongSwipeCallbacks({ enqueueFromSwipe(it, true) }, { enqueueFromSwipe(it, false) }),
        com.akira.app.feature.settings.LocalStudioNavigation provides studioNavigation,
        com.akira.app.core.design.components.LocalContentObscured provides (isPlayerExpanded || showSettings),
        LocalAkiraGlassBackdrop provides glassBackdrop,
        LocalAppBackdrop provides dockBackdrop,
        com.akira.app.core.design.components.LocalAkiraAccent provides androidx.compose.ui.graphics.lerp(effectivePrimary, androidx.compose.material3.MaterialTheme.colorScheme.onSurface, .5f)
    ) {
    // Immersive details own their palette. Home must keep the app palette even
    // while an outgoing detail remains composed for its shared transition.
    MaterialTheme(colorScheme=MaterialTheme.colorScheme) {
    com.akira.app.core.design.components.AkiraPresentationHost {
    com.akira.app.feature.settings.UpdatesHost(allowAutomaticPrompt =
        destination != HomeDestination.SETTINGS && !showSettings && !isPlayerExpanded &&
            !(destination == HomeDestination.SEARCH && searchSession.editing) &&
            selectedAlbumFilter == null && selectedArtistFilter == null && detailSessions.isEmpty())
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(destination) {
            if (destination != HomeDestination.SEARCH) {
                searchSession.close()
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        val strings = LocalStrings.current

        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
        ) {
            val fullScreenHeightDp = maxHeight
            val dockDensity = LocalDensity.current
            val keyboardVisible = WindowInsets.ime.getBottom(dockDensity) > 0
            val searchKeyboardFraction = (WindowInsets.ime.getBottom(dockDensity) /
                with(dockDensity) { 160.dp.toPx() }).coerceIn(0f, 1f)
            val restingDockInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(15.dp) + 2.dp
            val loading by musicService.isLoading.collectAsStateWithLifecycle()
            var dockSettleJob by remember { mutableStateOf<Job?>(null) }
            val liveReduced by androidx.compose.runtime.rememberUpdatedState(homeMotionReduced)
            val dockPull = remember(dockState, fullScreenHeightDp, dockDensity) {
                object : MiniPlayerPull {
                    private var dragging = false
                    private var draggedPx = 0f
                    override fun drag(delta: Float) {
                        if (!dragging) { dockState.capture(); dragging = true; draggedPx = 0f }
                        draggedPx += delta
                        dockSettleJob?.cancel()
                        val travel = dockState.miniBounds?.top ?: with(dockDensity) { fullScreenHeightDp.toPx() }
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            playerExpansionProgress.snapTo((playerExpansionProgress.value - delta / travel.coerceAtLeast(1f)).coerceIn(0f, 1f))
                        }
                    }
                    override fun release(velocity: Float) {
                        dragging = false
                        val velocityY = velocity
                        val travel = (dockState.miniBounds?.top ?: with(dockDensity) { fullScreenHeightDp.toPx() }).coerceAtLeast(1f)
                        val target = resolvePlayerSheetTarget(playerExpansionProgress.value, velocityY, draggedPx, travel, with(dockDensity) { PlayerSheetSwipeCommit.toPx() })
                        dockSettleJob = scope.launch {
                            if (liveReduced) playerExpansionProgress.snapTo(target)
                            else playerExpansionProgress.animateTo(target, playerSheetAnimationSpec(), initialVelocity = (-velocityY / travel).coerceIn(-8f, 8f))
                        }
                    }
                }
            }
            androidx.compose.runtime.SideEffect { dockState.scroll.enabled = miniVisible && !keyboardVisible && destination != HomeDestination.SEARCH }
            Box(Modifier.fillMaxSize().nestedScroll(dockState.scroll)
                .then(if (sampleGlass) Modifier.layerBackdrop(dockBackdrop) else Modifier)
                .graphicsLayer {translationX=-size.width/3f*studioNavigation.rootReveal}
                .onGloballyPositioned { glassBackdrop.coordinates = it }) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            com.akira.app.core.design.navigation.PageNavigationHost(
                target=destination,
                backTarget=if(destination==HomeDestination.SETTINGS)previousDestination else HomeDestination.HOME,
                backEnabled=!isPlayerExpanded && destination!=HomeDestination.HOME && selectedAlbumFilter==null && selectedArtistFilter==null && selectedFolderFilter==null && searchQuery.isBlank() && !searchSession.editing,
                onBack={if(destination==HomeDestination.SETTINGS)closeSettings() else destination=HomeDestination.HOME},
                forward={from,to->val order=listOf(HomeDestination.HOME,HomeDestination.LIBRARY,HomeDestination.SETTINGS,HomeDestination.SEARCH);order.indexOf(to)>=order.indexOf(from)},
                navigationTransform = { from, to, reduced ->
                    if (from == HomeDestination.SEARCH || to == HomeDestination.SEARCH) {
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (reduced) 0 else 140)) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 100))
                    } else com.akira.app.core.design.navigation.pageNavigationTransform(
                        listOf(HomeDestination.HOME, HomeDestination.LIBRARY, HomeDestination.SETTINGS).indexOf(to) >=
                            listOf(HomeDestination.HOME, HomeDestination.LIBRARY, HomeDestination.SETTINGS).indexOf(from), reduced)
                },
                modifier=Modifier.fillMaxSize()) { screen ->
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (onlineEnabled && screen != HomeDestination.SETTINGS) {
                homeStateHolder.SaveableStateProvider("online-$screen") {
                    com.akira.app.feature.online.OnlineScreen(onlineRepository, screen, currentSong, isPlaying,
                        with(dockDensity) { dockState.heightPx.toDp() },
                        onPlay = { song, queue -> localCurrentSong = song; musicService.playSong(song, queue) },
                        playbackError = playbackError,
                        requestedItem = if (screen == destination) onlineCollection else null,
                        onRequestConsumed = { onlineCollection = null },
                        onMore = { songForOptions = it },
                        onSettings = { openSettings() },
                        onTogglePlay = { togglePlayPause() }, obscured = isPlayerExpanded || showSettings)
                }
            } else if (screen == HomeDestination.HOME) {
                homeStateHolder.SaveableStateProvider("home") {
                LocalHomeFeed(wholeLibrary, libraryState.initialized, libraryState.loadFailed, currentSong, isPlaying,
                    obscured=isPlayerExpanded || showSettings,
                    onPlay={song,queue -> localCurrentSong=song;musicService.playSong(song,queue);scope.launch{prefsRepo.savePlaybackState(song.id,0L)}},
                    onTogglePlay={togglePlayPause()},
                    onRandomPlay={song ->
                        localCurrentSong=song
                        val existing=musicService.currentQueue.value
                        if(existing.isNotEmpty()) musicService.playHomeSelection(song)
                        else musicService.playSong(song,wholeLibrary.shuffled())
                    },
                    onMore={songForOptions=it},onSettings={openSettings()},onRetry=libraryViewModel::retryLoad,
                    scrollToTopRequest=homeScrollRequest,playerSheetProgress={playerExpansionProgress.value}, sources=homeFeedSources)
                }
            } else if (screen == HomeDestination.SEARCH && selectedAlbumFilter == null && selectedArtistFilter == null) {
                LocalSearchPage(wholeLibrary, currentSong, isPlaying,
                    with(dockDensity) { dockState.heightPx.toDp() },
                    onPlay = { song, queue -> localCurrentSong = song; musicService.playSong(song, queue) },
                    onTogglePlay = { togglePlayPause() }, onMore = { songForOptions = it },
                    onAlbum = { song -> selectedAlbumFilter = song.album; selectedAlbumKey = albumKeyOf(song) },
                    onArtist = { name -> selectedArtistFilter = name }, onSettings = { openSettings() })
            } else if (screen != HomeDestination.SETTINGS) {
            val detailLead = if(selectedAlbumFilter != null) wholeLibrary.firstOrNull { albumKeyOf(it)==selectedAlbumKey }
                else selectedArtistFilter?.let { name -> wholeLibrary.firstOrNull { it.artist.equals(name,true) } }
            val detailRoute = detailLead?.let { if(selectedArtistFilter != null) artistDetail(selectedArtistFilter!!).copy(transitionKey="library-artist-${selectedArtistFilter}")
                else albumDetail(it,"library-album-${albumKeyOf(it)}") }
            androidx.compose.animation.SharedTransitionLayout(Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(com.akira.app.core.design.navigation.LocalSharedTransitionScope provides this) {
            AnimatedContent(targetState=detailRoute, transitionSpec={
                com.akira.app.core.design.navigation.collectionContainerTransform(targetState != null,homeMotionReduced).using(null)
            },label="libraryCollectionDetail",modifier=Modifier.fillMaxSize()) { activeDetail ->
            androidx.compose.runtime.CompositionLocalProvider(com.akira.app.core.design.navigation.LocalNavAnimatedContentScope provides this) {
            if (activeDetail != null) {
                LocalCollectionDetail(activeDetail,wholeLibrary,currentSong,isPlaying,
                    with(dockDensity){dockState.heightPx.toDp()} + 32.dp,
                    onPlay={s,q -> localCurrentSong=s;musicService.playSong(s,q)},
                    onTogglePlay={togglePlayPause()},onMore={songForOptions=it},
                    onBack={selectedAlbumFilter=null;selectedAlbumKey=null;selectedArtistFilter=null})
            } else if (screen == HomeDestination.LIBRARY) {
                homeStateHolder.SaveableStateProvider("local-library-browse") {
                    LibraryBrowseScreen(wholeLibrary, currentSong, isPlaying,
                        with(dockDensity) { dockState.heightPx.toDp() } + 24.dp,
                        musicService, currentSortOrder, { showSortBottomSheet = true }, { openSettings() },
                        onAlbum = { song -> selectedAlbumFilter = song.album; selectedAlbumKey = albumKeyOf(song) },
                        onArtist = { selectedArtistFilter = it },
                        onPlay = { song, queue -> localCurrentSong = song; musicService.playSong(song, queue) },
                        onMore = { songForOptions = it }, initialized = libraryState.initialized,
                        failed = libraryState.loadFailed, onRetry = libraryViewModel::retryLoad)
                }
            } else {
            var rootScrollOffset by rememberSaveable(screen) { mutableStateOf(0f) }
            val headerScroll = remember(screen) {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                        rootScrollOffset=(rootScrollOffset-consumed.y).coerceAtLeast(0f)
                        return Offset.Zero
                    }
                }
            }
            // Header geometry follows consumed scroll distance.
            val libraryBottomClearance = with(dockDensity) { dockState.heightPx.toDp() } + 24.dp
            val collapsedHeader = (rootScrollOffset / with(dockDensity) { 62.dp.toPx() }).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding().nestedScroll(headerScroll)
            ) {
                LibraryHeader(
                    title = if(screen==HomeDestination.SEARCH) localizedText("Search","Buscar") else localizedText("Library","Biblioteca"),
                    showCategories = screen != HomeDestination.SEARCH,
                    collapseProgress = collapsedHeader,
                    songCount = searchFilteredSongs.size,
                    searchQuery = searchQuery,
                    onSearchChange = { libraryViewModel.setSearchQuery(it) },
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        libraryViewModel.setCategory(tab)
                        selectedAlbumFilter = null
                        selectedArtistFilter = null
                        selectedFolderFilter = null
                        selectedAlbumKey = null
                        selectedFolderKey = null
                    },
                    onSortClick = { showSortBottomSheet = true },
                    onSettingsClick = { openSettings() }
                )
                if (libraryState.initialized && libraryState.loadFailed) {
                    LibraryRefreshError(libraryViewModel::retryLoad, Modifier.padding(horizontal = 24.dp))
                }

                // Collection detail header shares the same bare back action as other screens.
                val activeFilterLabel = selectedAlbumFilter ?: selectedArtistFilter ?: selectedFolderFilter
                if (activeFilterLabel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.akira.app.core.design.navigation.PageBackButton({
                            selectedAlbumFilter = null; selectedArtistFilter = null; selectedFolderFilter = null
                            selectedAlbumKey = null; selectedFolderKey = null
                        })
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = activeFilterLabel,
                            modifier = Modifier.weight(1f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AkiraFontFamily,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))


                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val route=listOf(selectedTab,selectedAlbumFilter,selectedArtistFilter,selectedFolderFilter)
                    AnimatedContent(targetState=route,transitionSpec={
                        val order=listOf("Tracks","Albums","Artists","Folders","Playlists")
                        val forward=if(initialState.drop(1).any{it!=null} && targetState.drop(1).all{it==null})false
                            else targetState.drop(1).any{it!=null} || order.indexOf(targetState[0])>=order.indexOf(initialState[0])
                        pageNavigationTransform(forward,homeMotionReduced).using(null)
                    },label="pageLibraryNavigation",modifier=Modifier.fillMaxSize()) { pageRoute ->
                        var retainedSongs by remember { mutableStateOf(displaySongs) }
                        if(pageRoute==route) androidx.compose.runtime.SideEffect { retainedSongs=displaySongs }
                        val pageSongs=if(pageRoute==route)displaySongs else retainedSongs
                    when {
                        !libraryState.initialized -> {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                if (libraryState.loadFailed) {
                                    Text(strings.library.error, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontFamily = AkiraFontFamily)
                                    TextButton(onClick = libraryViewModel::retryLoad) {
                                        Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.Retry), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                                    }
                                } else CircularProgressIndicator(Modifier.size(28.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), strokeWidth = 2.dp)
                            }
                        }
                        pageRoute[0] == "Playlists" -> PlaylistTab(wholeLibrary, musicService, !isPlayerExpanded && !showSettings, bottomContentPadding = libraryBottomClearance) { songForOptions = it }
                        songsList.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = if(searchQuery.isNotBlank()) localizedText("No results", "Sin resultados") else com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.YourLibraryIsEmpty),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AkiraFontFamily,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if(searchQuery.isNotBlank()) localizedText("Try another title, artist or album.", "Prueba con otro título, artista o álbum.") else strings.emptyLibrarySubtitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = AkiraFontFamily,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        pageRoute[1] != null || pageRoute[2] != null || pageRoute[3] != null -> {
                            SongsListView(
                                songs = pageSongs,
                                bottomContentPadding = libraryBottomClearance,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onSongClick = { playSong(it, pageSongs) },
                                onSongMoreClick = { songForOptions = it },
                                onSwipeNext = { enqueueFromSwipe(it, true) },
                                onSwipeQueue = { enqueueFromSwipe(it, false) },
                                onSortClick = { showSortBottomSheet = true },
                                onPlayAllClick = {
                                    playCollection(pageSongs, shuffle = false)
                                },
                                onShuffleAllClick = {
                                    playCollection(pageSongs, shuffle = true)
                                }
                            )
                        }

                        pageRoute[0] == "Tracks" -> {
                            SongsListView(
                                songs = pageSongs,
                                bottomContentPadding = libraryBottomClearance,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onSongClick = { playSong(it, pageSongs) },
                                onSongMoreClick = { songForOptions = it },
                                onSwipeNext = { enqueueFromSwipe(it, true) },
                                onSwipeQueue = { enqueueFromSwipe(it, false) },
                                onSortClick = { showSortBottomSheet = true },
                                onPlayAllClick = {
                                    playCollection(pageSongs, shuffle = false)
                                },
                                onShuffleAllClick = {
                                    playCollection(pageSongs, shuffle = true)
                                }
                            )
                        }

                        pageRoute[0] == "Albums" -> {
                            AlbumCollectionView(albumsList, libraryBottomClearance) { album ->
                                selectedAlbumFilter = album.name; selectedAlbumKey = album.key
                            }
                        }

                        pageRoute[0] == "Artists" -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = libraryBottomClearance),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()
                            ) {
                                item { CollectionHeader(LocalStrings.current.tabArtists, artistsList.size) }
                                items(artistsList, key = { it.name }) { artist ->
                                    ArtistListItem(
                                        artist = artist,
                                        onClick = { selectedArtistFilter = artist.name }
                                    )
                                }
                            }
                        }

                        pageRoute[0] == "Folders" -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = libraryBottomClearance),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()
                            ) {
                                item { CollectionHeader(LocalStrings.current.tabFolders, foldersList.size) }
                                items(foldersList, key = { it.key }) { folder ->
                                    FolderListItem(
                                        folder = folder,
                                        onClick = { selectedFolderFilter = folder.name; selectedFolderKey = folder.key }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }

            }
            }
            }
            }
            }
            } else {
                SettingsScreen(
                    prefsRepo = prefsRepo,
                    startWithSound = settingsStartWithSound,
                    previewSongs = songsList.take(4), previewSong = currentSong,
                    onBack = { closeSettings() },
                    onTestCrossfade = {
                        val curDuration = musicService.durationMs.value
                        val xfSec = userPrefs?.crossfadeSeconds ?: 0
                        val queue = musicService.currentQueue.value
                        val curSong = currentSong
                        val curIdx = if (curSong != null) musicService.currentMediaItemIndex.value else -1
                        val hasNext = curIdx >= 0 && curIdx + 1 < queue.size

                        if (!hasNext) {
                            com.akira.app.core.design.components.AkiraNotices.show(strings.testCrossfadeNoNext)
                        } else if (curDuration <= (xfSec + 3) * 1000L) {
                            com.akira.app.core.design.components.AkiraNotices.show(strings.testCrossfadeTrackTooShort)
                        } else {
                            val targetMs = curDuration - (xfSec + 3) * 1000L
                            musicService.seekTo(targetMs)
                            if (!isPlaying) {
                                musicService.togglePlayPause()
                            }
                        }
                    },
                    bottomContentPadding = with(dockDensity) { dockState.heightPx.toDp() } + 24.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
                }
            }
            } // Captured library background and contents; player stays outside.
            if (showSortBottomSheet) {
                LibrarySortMenu(
                    current = currentSortOrder,
                    onSelect = { libraryViewModel.setSortOrder(it) },
                    onDismiss = { showSortBottomSheet = false }
                )
            }

            if (!keyboardVisible || destination == HomeDestination.SEARCH) MaterialTheme(colorScheme=if(darkDetails) com.akira.app.core.design.theme.DarkColorScheme else MaterialTheme.colorScheme) {
            HomeNavigation(destination,onSelect={next ->
                if(next==HomeDestination.HOME && destination==next)homeScrollRequest++
                dockState.scroll.expand()
                if(next==HomeDestination.SETTINGS)openSettings() else {
                    if(next != HomeDestination.SEARCH) { searchSession.close(); focusManager.clearFocus(); keyboardController?.hide() }
                    destination=next
                    if(next==HomeDestination.SEARCH){libraryViewModel.setCategory("Tracks");selectedAlbumFilter=null;selectedArtistFilter=null;selectedFolderFilter=null}
                }
            }, state = dockState, song = currentSong, isPlaying = isPlaying, isLoading = loading,
                artworkVisible = miniVisible,
                onPlayPause = { togglePlayPause() }, onNext = { playNext() }, onPrevious = { playPrevious() },
                onHoldStart = musicService::beginTemporaryDoubleSpeed,
                onExpand = {
                    dockState.capture()
                    dockSettleJob?.cancel()
                    dockSettleJob = scope.launch {
                        if (homeMotionReduced) playerExpansionProgress.snapTo(1f)
                        else playerExpansionProgress.animateTo(1f, playerSheetAnimationSpec())
                    }
                }, pull = dockPull, playerProgress = { playerExpansionProgress.value }, searchEnabled = detailSessions.isEmpty(),
                modifier=Modifier.align(Alignment.BottomCenter).widthIn(max = 440.dp).fillMaxWidth()
                    .onSizeChanged { dockState.heightPx = it.height }
                    .then(if(destination == HomeDestination.SEARCH) Modifier.imePadding() else Modifier)
                    .padding(bottom = if(destination == HomeDestination.SEARCH)
                        restingDockInset * (1f-searchKeyboardFraction) + 8.dp * searchKeyboardFraction else restingDockInset)
                    .padding(horizontal=16.dp)
                    .then(if (!miniVisible && destination != HomeDestination.SEARCH) Modifier.clearAndSetSemantics {} else Modifier))
            }
            UnifiedPlayerContainer(
                dockState = dockState,
                song = currentSong,
                isPlaying = isPlaying,
                musicService = musicService,
                isShuffleActive = isShuffleActive,
                repeatState = repeatState,
                playerExpansionProgress = playerExpansionProgress,
                dynamicPrimaryColor = effectivePrimary,
                artworkColors = if(dynamicColorEnabled)artworkColors else PlayerArtworkColors(effectivePrimary),
                dynamicSecondaryColor = effectiveSecondary,
                dynamicAccentColor = effectiveAccent,
                fullscreenArtwork = userPrefs?.fullscreenPlayerArtwork ?: false,
                fullScreenHeightDp = fullScreenHeightDp,
                onTogglePlay = { togglePlayPause() },
                onNext = { playNext() },
                onPrev = { playPrevious() },
                onToggleShuffle = { toggleShuffle() },
                onToggleRepeat = { toggleRepeat() },
                onSeekTo = { fraction -> seekTo(fraction) },
                onToggleFavorite = { toggleSongFavorite(it) },
                onOpenAudioSettings = { openSettings(startWithSound = true) },
                onDeleteSong = if (onlineEnabled) null else ({ requestDeleteSong(it) }),
                onGoToAlbum = { openSongCollection(it,false) },
                onGoToArtist = { openSongCollection(it,true) }
            )


            songForOptions?.let { s ->
                SongOptionsSheet(
                    song = s,
                    onDismiss = { songForOptions = null },
                    onPlayNext = {
                        musicService.playNext(s)
                        songForOptions = null
                    },
                    onAddToQueue = {
                        musicService.addToQueue(s)
                        songForOptions = null
                    },
                    onToggleFavorite = {
                        toggleSongFavorite(s)
                    },
                    onGoToAlbum = {
                        openSongCollection(s,false)
                    },
                    onGoToArtist = {
                        openSongCollection(s,true)
                    },
                    onViewDetails = {
                        val target = s
                        songForOptions = null
                        songForDetails = target
                    },
                    onDelete = {
                        songForOptions = null
                        requestDeleteSong(s)
                    }
                )
            }

            deletePreviewSong?.let { song ->
                DeleteSongDialog(song, onDismiss = { deletePreviewSong = null }, onConfirm = {
                    deletePreviewSong = null
                    performDeleteSong(song)
                })
            }

            songForDetails?.let { s ->
                SongDetailsDialog(
                    song = s,
                    onDismiss = { songForDetails = null }
                )
            }

            com.akira.app.feature.settings.StudioNavigationHost(studioNavigation)
            com.akira.app.core.design.components.AkiraNoticeHost(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(bottom=if(!keyboardVisible && miniVisible)
                        with(dockDensity){dockState.heightPx.toDp()}+12.dp else 68.dp),
                priority=1,hostState=snackbarHostState)
        }
    }
}

}
}
    } // Shared presentation host
} // Immersive detail theme; application preference is untouched.

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun UnifiedPlayerContainer(
    dockState: AkiraDockState,
    song: Song?,
    isPlaying: Boolean,
    musicService: MusicServiceConnection,
    isShuffleActive: Boolean,
    repeatState: QueueRepeatState,
    playerExpansionProgress: Animatable<Float, *>,
    dynamicPrimaryColor: Color,
    artworkColors: PlayerArtworkColors,
    dynamicSecondaryColor: Color,
    dynamicAccentColor: Color,
    fullscreenArtwork: Boolean,
    fullScreenHeightDp: androidx.compose.ui.unit.Dp,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAudioSettings: () -> Unit,
    onDeleteSong: ((Song) -> Unit)? = null,
    onGoToAlbum: (Song) -> Unit,
    onGoToArtist: (Song) -> Unit
) {
    val surfaceVisible by remember { derivedStateOf { playerExpansionProgress.value > .001f } }
    val motionReduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    var artworkRadius by remember { mutableStateOf(20.dp) }
    var artworkMask by remember { mutableStateOf(if(fullscreenArtwork)1f else 0f) }
    val playerConfig = androidx.compose.ui.platform.LocalConfiguration.current
    var artworkBounds by remember(fullscreenArtwork, playerConfig.screenWidthDp, playerConfig.screenHeightDp) {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var parentSettleJob by remember { mutableStateOf<Job?>(null) }
    var isDraggingSheet by remember { mutableStateOf(false) }

    fun settleTo(target: Float, velocity: Float = 0f) {
        if(target == 0f && playerExpansionProgress.value >= .98f) dockState.capture()
        parentSettleJob?.cancel()
        parentSettleJob = scope.launch {
            runCatching {
                playerExpansionProgress.animateTo(
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = if(motionReduced)androidx.compose.animation.core.snap() else playerSheetAnimationSpec()
                )
                playerExpansionProgress.snapTo(target)
            }
        }
    }

    // Captured synchronously at the start of the gesture, from the active
    // row's live coordinates, before alpha/lift/shared transitions can alter it.
    val anchor = dockState.transitionAnchor
    val mini = anchor?.mini ?: dockState.miniBounds
    val miniHeightDp = with(density) { mini?.height?.toDp() ?: 56.dp }
    val miniRestingTopDp = with(density) { mini?.top?.toDp() ?: (fullScreenHeightDp - 160.dp) }
    val miniRestingTopPx = with(density) { miniRestingTopDp.toPx() }
    val miniLeft = with(density) { mini?.left?.toDp() ?: 16.dp }
    val miniWidth = with(density) { mini?.width?.toDp() ?: (playerConfig.screenWidthDp - 32).dp }
    val miniArt = anchor?.artwork ?: dockState.artBounds
    val miniArtLeft = with(density) { ((miniArt?.left ?: 0f) - (mini?.left ?: 0f)).toDp() }
    val miniArtTop = with(density) { ((miniArt?.top ?: 0f) - (mini?.top ?: 0f)).toDp() }
    val miniArtSize = with(density) { miniArt?.width?.toDp() ?: 40.dp }

    if (surfaceVisible || isDraggingSheet) song?.let { currentSongTarget ->
        val miniRect=androidx.compose.ui.geometry.Rect(
            with(density){miniLeft.toPx()},miniRestingTopPx,
            with(density){(miniLeft+miniWidth).toPx()},miniRestingTopPx+with(density){miniHeightDp.toPx()})
        val surfaceShape=remember(playerExpansionProgress,miniRect,density) {
            com.akira.app.core.design.motion.ElasticGlassShape({
                val p=playerExpansionProgress.value.coerceIn(0f,1f)
                miniRect.height/2f*(1f-p*p*p*p)
            },{0f},{androidx.compose.ui.geometry.Offset.Zero},density)
        }

        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.2f
        val pillBg = if (isDark) Color(0xFF141416) else MaterialTheme.colorScheme.surfaceContainerHigh

        Box(
            modifier = Modifier
                .offset {
                    val raw=playerExpansionProgress.value.coerceIn(-.015f,1.015f)
                    val p=raw.coerceIn(0f,1f)
                    val widthProgress=1f-(1f-p)*(1f-p)*(1f-p)
                    IntOffset((miniRect.left*(1f-widthProgress)).roundToInt(),((1f-raw)*miniRect.top).roundToInt())
                }
                .clip(surfaceShape)
                .layout { measurable, _ ->
                    // The full player is measured at its resting size throughout
                    // the gesture. Only this parent frame changes, not its UI.
                    val p=playerExpansionProgress.value.coerceIn(0f,1f)
                    val widthProgress=1f-(1f-p)*(1f-p)*(1f-p)
                    val fullWidth=playerConfig.screenWidthDp.dp.roundToPx()
                    val fullHeight=fullScreenHeightDp.roundToPx()
                    val child=measurable.measure(androidx.compose.ui.unit.Constraints.fixed(fullWidth,fullHeight))
                    val width=(miniRect.width+(fullWidth-miniRect.width)*widthProgress).roundToInt().coerceAtLeast(1)
                    val height=(miniRect.height+(fullHeight-miniRect.height)*p).roundToInt().coerceAtLeast(1)
                    layout(width,height) { child.place(0,0) }
                }
                .pointerInput(Unit) {
                    var travelPx = 1f
                    val velocityTracker = VelocityTracker()
                    var dragDeltaPx = 0f
                    var trackedY = 0f
                    // This handler receives mini-player gestures; the inner handler receives expanded-player gestures.
                    var ownsGesture = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            parentSettleJob?.cancel()
                            travelPx = (dockState.transitionAnchor?.mini?.top ?: with(density) { fullScreenHeightDp.toPx() }).coerceAtLeast(1f)
                            velocityTracker.resetTracking()
                            dragDeltaPx = 0f
                            trackedY = 0f
                            ownsGesture = playerExpansionProgress.value <= 0.05f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!ownsGesture) return@detectVerticalDragGestures
                            change.consume()
                            dragDeltaPx += dragAmount
                            trackedY += dragAmount
                            // Track accumulated finger travel because this node moves with the finger.
                            velocityTracker.addPosition(change.uptimeMillis, Offset(0f, trackedY))
                            val newProgress = (playerExpansionProgress.value - dragAmount / travelPx).coerceIn(0f, 1f)
                            // Apply snapTo within this input event, before a later frame can settle the sheet.
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                runCatching { playerExpansionProgress.snapTo(newProgress) }
                            }
                        },
                        onDragEnd = {
                            if (!ownsGesture) return@detectVerticalDragGestures
                            val velocityY = try { velocityTracker.calculateVelocity().y } catch (_: Exception) { 0f }
                            val target = resolvePlayerSheetTarget(
                                progress = playerExpansionProgress.value,
                                velocityY = velocityY,
                                dragDeltaPx = dragDeltaPx,
                                referencePx = travelPx,
                                commitDistancePx = with(density) { PlayerSheetSwipeCommit.toPx() }
                            )
                            val initialVelocity = (-velocityY / travelPx).coerceIn(-8f, 8f)
                            settleTo(target, initialVelocity)
                        },
                        onDragCancel = {
                            if (!ownsGesture) return@detectVerticalDragGestures
                            val target = if (playerExpansionProgress.value < SheetPositionHysteresis) 0f else 1f
                            settleTo(target)
                        }
                    )
                }
        ) {
            val playerBackdrop = rememberLayerBackdrop()
            run {
                val ambientModifier=Modifier.layerBackdrop(playerBackdrop).graphicsLayer {
                    alpha=com.akira.app.feature.player.dock.playerMaterialReveal(playerExpansionProgress.value)
                }
                // Cover style changes only the artwork, never the scene palette.
                Box(ambientModifier.fillMaxSize().playerArtworkBackdrop(playerArtworkBase(artworkColors.primary),
                    playerArtworkBase(artworkColors.left),playerArtworkBase(artworkColors.right)))
            }

            val travellingArtwork: @Composable () -> Unit = {
            // Retain the artwork painter during travel; both copies exchange visibility using the same progress without loading another image.
                val artworkRequest = rememberPlayerArtworkRequest(currentSongTarget.albumArtUri)
                val fullStyle=fullscreenArtwork && playerConfig.screenWidthDp<=playerConfig.screenHeightDp
                val fallback=with(density) {
                    val width=(if(fullStyle)playerConfig.screenWidthDp.dp else (playerConfig.screenWidthDp-52).dp).toPx()
                    androidx.compose.ui.geometry.Rect(if(fullStyle)0f else 26.dp.toPx(),if(fullStyle)0f else 100.dp.toPx(),
                        if(fullStyle)width else 26.dp.toPx()+width,
                        (if(fullStyle)0f else 100.dp.toPx())+width*(if(fullStyle)FullscreenArtworkHeightRatio else 1f))
                }
                // These states are read only in draw/measure. The inner artwork
                // can report its live bounds without recomposing the whole player.
                val liveBounds: () -> androidx.compose.ui.geometry.Rect = { artworkBounds ?: fallback }
                val travellerShape=remember(anchor,playerExpansionProgress,density) {
                    com.akira.app.core.design.motion.ElasticGlassShape({
                        with(density){lerp(anchor?.corner ?: 8.dp,artworkRadius,
                            playerArtworkTravelProgress(playerExpansionProgress.value)).toPx()}
                    },{0f},{androidx.compose.ui.geometry.Offset.Zero},density)
                }
                Box(Modifier.graphicsLayer {
                    val live=playerExpansionProgress.value
                    val p=playerArtworkTravelProgress(live)
                    val target=liveBounds()
                    alpha=playerArtworkTravellerAlpha(live)
                    translationX=with(density){miniArtLeft.toPx()}*(1f-p)+target.left*p
                    translationY=with(density){miniArtTop.toPx()}*(1f-p)+target.top*p
                }.clip(travellerShape).layout { measurable,_ ->
                    val p=playerArtworkTravelProgress(playerExpansionProgress.value)
                    val target=liveBounds()
                    val from=miniArtSize.toPx()
                    val w=(from+(target.width-from)*p).roundToInt().coerceAtLeast(1)
                    val h=(from+(target.height-from)*p).roundToInt().coerceAtLeast(1)
                    val child=measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w,h))
                    layout(w,h){child.place(0,0)}
                }.then(if(fullStyle)Modifier.playerArtworkFade(playerConfig.screenWidthDp*density.density) {
                    playerArtworkTravelProgress(playerExpansionProgress.value)*artworkMask
                } else Modifier)) {
                    if (!currentSongTarget.albumArtUri.isNullOrBlank()) AsyncImage(artworkRequest, null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Rounded.MusicNote, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center).size(76.dp).graphicsLayer {
                            val p=playerArtworkTravelProgress(playerExpansionProgress.value)
                            scaleX=(20f+56f*p)/76f;scaleY=scaleX
                        })
                }
            }

            // Artwork travels in this layer while mini-player text and controls fade.
            if (surfaceVisible || isDraggingSheet) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppBackdrop provides playerBackdrop) {
                NowPlayingExpandedScreen(
                    song = currentSongTarget,
                    isPlaying = isPlaying,
                    musicService = musicService,
                    isShuffleActive = isShuffleActive,
                    repeatState = repeatState,
                    playerExpansionProgress = playerExpansionProgress,
                    sheetTravelPx = miniRestingTopPx,
                    dynamicPrimaryColor = dynamicPrimaryColor,
                    artworkPrimaryColor = artworkColors.primary,
                    artworkColors = artworkColors,
                    onClose = {
                        settleTo(0f)
                    },
                    onSettle = { target, velocity ->
                        settleTo(target, velocity)
                    },
                    onCancelSettle = {
                        if(playerExpansionProgress.value >= .98f) dockState.capture()
                        parentSettleJob?.cancel()
                    },
                    onDraggingChanged = { dragging ->
                        isDraggingSheet = dragging
                    },
                    onArtworkBounds = { bounds, radius, mask ->
                        // Freeze measurements during travel; invalidate on artwork-style or orientation changes.
                        if (artworkBounds == null || playerExpansionProgress.value >= .999f) {
                            artworkBounds = bounds; artworkRadius = radius; artworkMask=mask
                        }
                    },
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrev = onPrev,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onSeekTo = onSeekTo,
                    onToggleFavorite = onToggleFavorite,
                    onOpenAudioSettings = {
                        onOpenAudioSettings()
                        settleTo(0f)
                    },
                    onDeleteSong = onDeleteSong,
                    onGoToAlbum = onGoToAlbum,
                    onGoToArtist = onGoToArtist,
                    travellingArtwork = travellingArtwork,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(playerConfig.screenWidthDp.dp, fullScreenHeightDp)
                        .graphicsLayer {
                            alpha=1f
                        }
                )
            }
                }

            // Move the compact header with its surface so the surface does not cover it.
            val inlineMini = dockState.scroll.isInline
            val compactInk = if (isDark) Color.White else Color.Black
            val compactSlot = if (inlineMini) 32.dp else 34.dp
            Row(Modifier.fillMaxWidth().height(miniHeightDp).clearAndSetSemantics {}
                .graphicsLayer { alpha = com.akira.app.feature.player.dock.playerCompactHeaderReveal(playerExpansionProgress.value) }
                .padding(start = miniArtLeft + miniArtSize + if(inlineMini)8.dp else 10.dp,
                    end = if(inlineMini)8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(currentSongTarget.title, color=compactInk, fontSize=14.sp, lineHeight=16.sp,
                        fontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif, fontWeight=FontWeight.SemiBold,
                        maxLines=1, overflow=TextOverflow.Ellipsis)
                    Text(currentSongTarget.artist, color=compactInk.copy(alpha=.7f),
                        fontSize=12.sp, lineHeight=14.sp, fontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif,
                        maxLines=1, overflow=TextOverflow.Ellipsis)
                }
                Box(Modifier.size(compactSlot), contentAlignment=Alignment.Center) {
                    Icon(if(isPlaying)Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                        tint=compactInk, modifier=Modifier.size(if(inlineMini)24.dp else 28.dp))
                }
                if(!inlineMini) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(compactSlot), contentAlignment=Alignment.Center) {
                        com.akira.app.feature.player.dock.MiniPlayerNextGlyph(compactInk)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SongsListView(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit = {},
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onSortClick: () -> Unit,
    onSwipeNext: ((Song) -> Unit)? = null,
    onSwipeQueue: ((Song) -> Unit)? = null,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 110.dp
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
        flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()
    ) {
        item {
            LibraryActions(onPlayAllClick,onShuffleAllClick,onSortClick,songs.isNotEmpty())
        }

        items(songs, key = { it.id }) { song ->
            val isCurrent = currentSong?.id == song.id
            val row: @Composable (Modifier) -> Unit = { rowModifier -> SongRowItem(
                song = song,
                isCurrent = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onClick = { onSongClick(song) },
                onMoreClick = { onSongMoreClick(song) },
                modifier = rowModifier.padding(horizontal = 24.dp)
            ) }
            if (onSwipeNext != null && onSwipeQueue != null) {
                com.akira.app.core.design.components.LocalSongSwipeRow(song, Modifier.fillMaxWidth().animateItem()) { row(Modifier) }
            } else row(Modifier.animateItem())
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun SongRowItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rowInk = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - .5.dp.toPx() / 2f
                drawLine(rowInk.copy(alpha=.10f), Offset(62.dp.toPx(), y), Offset(size.width, y), .5.dp.toPx())
            }
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!song.albumArtUri.isNullOrBlank()) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1C1C2C), Color(0xFF12121A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else Color(0xFF71717A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    NowPlayingIndicator(Modifier.size(22.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.5.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = AkiraFontFamily,
                color = if (isCurrent) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = song.artist,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 12.5.sp,
                    fontFamily = AkiraFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "·",
                    fontSize = 12.sp,
                    color = Color(0xFF71717A)
                )
                Text(
                    text = if (song.durationMs > 0L) formatDuration(song.durationMs)
                        else LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.DurationUnknown),
                    maxLines = 1,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF71717A)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        AkiraIconButton(
            icon = AkiraIcons.MoreHoriz,
            contentDescription = null,
            tint = Color(0xFFA1A1AA),
            glyphSize = 20.dp,
            showBackground = false,
            onClick = { onMoreClick() }
        )
    }
}


    private data class AlbumItem(val key: String, val name: String, val artist: String, val songCount: Int, val artUri: String?)
private data class ArtistItem(val name: String, val songCount: Int, val artUri: String?, val covers: List<String>, val albumCount: Int)
    private data class FolderItem(val key: String, val name: String, val path: String, val songCount: Int, val covers: List<String>)

    /** Prefer the MediaStore albumId. Fall back to normalized album and artist names so identically titled albums by different artists remain distinct. */
    private fun albumKeyOf(song: Song): String =
        if (song.albumId != 0L) {
            "id:${song.albumId}"
        } else {
            "nm:${song.album.trim().lowercase()}|${song.artist.trim().lowercase()}"
        }

    /** Use the complete directory path as the stable folder key. */
    private fun folderKeyOf(song: Song): String =
        song.path.substringBeforeLast('/').ifBlank { "/" }

@Composable
private fun CollectionHeader(title: String, count: Int, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.S, count), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .4f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun AlbumCollectionView(albums: List<AlbumItem>, bottomContentPadding: androidx.compose.ui.unit.Dp, onClick: (AlbumItem) -> Unit) {
    var grid by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        CollectionHeader(LocalStrings.current.tabAlbums, albums.size) {
            IconButton(onClick = { grid = !grid }) {
                Icon(if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.ChangeLayout), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .8f))
            }
        }
        if (grid) LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = bottomContentPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
            items(albums, key = { it.key }) { album ->
                Column(Modifier.containerTransformSource("library-album-${album.key}",coverUrl=album.artUri).noRippleClickable { onClick(album) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CollectionArtwork(listOf(album.artUri), album.name, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(album.name, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = bottomContentPadding), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
            items(albums, key = { it.key }) { album ->
                Row(Modifier.fillMaxWidth().containerTransformSource("library-album-${album.key}",coverUrl=album.artUri).noRippleClickable { onClick(album) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CollectionArtwork(listOf(album.artUri), album.name, Modifier.size(68.dp))
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(album.name, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(collectionCount(album.songCount), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .3f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ArtistListItem(artist: ArtistItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().containerTransformSource("library-artist-${artist.name}",coverUrl=artist.artUri).noRippleClickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CollectionArtwork(artist.covers, artist.name, Modifier.size(60.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(artist.name, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(collectionCount(artist.songCount) + " · " + artist.albumCount + " " + (if (artist.albumCount == 1) LocalStrings.current.library.album else LocalStrings.current.tabAlbums).lowercase(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FolderListItem(folder: FolderItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().noRippleClickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(68.dp)) {
            CollectionArtwork(folder.covers, folder.name, Modifier.fillMaxSize())
            Icon(Icons.Rounded.Folder, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).background(Color(0xFF151519), RoundedCornerShape(4.dp)).padding(3.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(folder.name, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(collectionCount(folder.songCount), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 13.sp)
            Text(folder.path, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .3f), modifier = Modifier.size(20.dp))
    }
}


@Composable
private fun ApplePauseIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 14.dp, height = 16.5.dp)) {
        val barW = 4.5.dp.toPx()
        val barH = 16.5.dp.toPx()
        val corner = 2.2.dp.toPx()
        val gap = 3.5.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(barW + gap, 0f),
            size = androidx.compose.ui.geometry.Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )
    }
}

@Composable
private fun ApplePlayIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 15.dp, height = 16.5.dp)) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(1.dp.toPx(), 0f)
            lineTo(w, h / 2f)
            lineTo(1.dp.toPx(), h)
            close()
        }
        drawPath(path = path, color = color)
    }
}

// Fling velocity and accumulated travel can commit before the positional threshold.
private const val SheetFlingVelocityPxPerSec = 450f
private const val SheetDragDismissFraction = 0.08f
private const val SheetDragMinPx = 56f
internal const val SheetPositionHysteresis = 0.42f


internal fun resolvePlayerSheetTarget(
    progress: Float,
    velocityY: Float,
    dragDeltaPx: Float,
    referencePx: Float,
    commitDistancePx: Float = maxOf(referencePx * SheetDragDismissFraction, SheetDragMinPx)
): Float {
    val dismissThreshold = commitDistancePx
    return when {
        velocityY > SheetFlingVelocityPxPerSec -> 0f
        velocityY < -SheetFlingVelocityPxPerSec -> 1f
        dragDeltaPx > dismissThreshold -> 0f
        dragDeltaPx < -dismissThreshold -> 1f
        progress < SheetPositionHysteresis -> 0f
        else -> 1f
    }
}

private fun vibrantBoost(
    color: Color,
    fallbackHue: Float = 210f,
    isGrayscaleFallback: Color = Color(0xFF222432)
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    // Preserve brightness for monochrome covers while applying a subtle tint.
    if (hsv[1] < 0.12f) {
        hsv[0] = fallbackHue
        hsv[1] = 0.10f
        hsv[2] = hsv[2].coerceIn(0.30f, 0.97f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    hsv[1] = hsv[1].coerceIn(0.72f, 1.0f)
    hsv[2] = hsv[2].coerceIn(0.62f, 1.0f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun luminanceOf(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

private fun isLightColor(color: Color): Boolean = luminanceOf(color) > 0.56f

// Preserve hue and saturation while limiting brightness.
private fun capLuminance(color: Color, maxLuminance: Float): Color {
    val l = luminanceOf(color)
    if (l <= maxLuminance) return color
    val factor = maxLuminance / l
    return Color(
        (color.red * factor).coerceIn(0f, 1f),
        (color.green * factor).coerceIn(0f, 1f),
        (color.blue * factor).coerceIn(0f, 1f)
    )
}

// Readable foreground over the dynamic light or dark background.
fun contentColorForBackground(bg: Color): Color =
    if (isLightColor(bg)) Color(0xFF14161C) else Color.White

fun mutedContentColorForBackground(bg: Color): Color =
    if (isLightColor(bg)) Color(0xFF3A3F4B).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.62f)

@Composable
private fun DynamicAmbientBackground(
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedPrimary by animateColorAsState(
        targetValue = capLuminance(primaryColor, .18f),
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "ambient_prim"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = capLuminance(secondaryColor, .14f),
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "ambient_sec"
    )
    val animatedAccent by animateColorAsState(
        targetValue = capLuminance(accentColor, .10f),
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "ambient_acc"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ambient_motion")
    val phaseX1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "px1"
    )
    val phaseY1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "py1"
    )
    val phaseX2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(17_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "px2"
    )

    val lightBase = isLightColor(animatedPrimary)
    val baseCanvasColor = if (lightBase) {
        capLuminance(animatedPrimary, 0.18f).copy(alpha = 0.65f).compositeOver(Color(0xFF0B0B14))
    } else {
        animatedPrimary.copy(alpha = 0.42f).compositeOver(Color(0xFF0B0B14))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseCanvasColor) // Tinted with the album's primary tone!
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f }
        ) {
            val width = size.width
            val height = size.height


            val g1c = Offset(
                width * (0.10f + 0.78f * phaseX1),
                height * (0.08f + 0.30f * phaseY1)
            )
            val g1r = width * (0.62f + 0.14f * phaseY1)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimary.copy(alpha = 1f),
                        animatedPrimary.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = g1c,
                    radius = g1r
                ),
                center = g1c,
                radius = g1r
            )

            val g2c = Offset(
                width * (0.90f - 0.80f * phaseX2),
                height * (0.38f + 0.36f * phaseY1)
            )
            val g2r = width * (0.58f + 0.13f * phaseX2)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedSecondary.copy(alpha = 0.95f),
                        animatedSecondary.copy(alpha = 0.48f),
                        Color.Transparent
                    ),
                    center = g2c,
                    radius = g2r
                ),
                center = g2c,
                radius = g2r
            )

            val g3c = Offset(
                width * (0.20f + 0.62f * phaseX1),
                height * (0.72f - 0.26f * phaseX2)
            )
            val g3r = width * (0.45f + 0.11f * phaseY1)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.55f),
                        animatedAccent.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = g3c,
                    radius = g3r
                ),
                center = g3c,
                radius = g3r
            )
        }

        // Moderate the ambient scene behind player text.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (lightBase) {
                            listOf(
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.30f)
                            )
                        } else {
                            listOf(
                                Color.Black.copy(alpha = 0.14f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.44f)
                            )
                        }
                    )
                )
        )
    }
}


internal fun formatDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.ROOT, "%d:%02d", min, sec)
}
