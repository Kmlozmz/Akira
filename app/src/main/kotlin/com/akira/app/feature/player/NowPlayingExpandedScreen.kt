package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import com.akira.app.core.design.components.akiraTransportPress
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.provider.MediaStore
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import android.content.ContentResolver
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.lyrics.sidecarText
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.glass.LocalAppBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.theme.noRippleClickable
import com.akira.app.core.media.MusicServiceConnection
import com.akira.app.core.media.SleepTimerBus
import com.akira.app.core.lyrics.LoadedLyrics
import com.akira.app.core.model.Song
import kotlinx.coroutines.Job
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.design.components.AkiraIconButton
import com.akira.app.core.design.icon.AkiraIcons

private enum class ExpandedPage {
    PLAYER,
    LYRICS,
    QUEUE
}

private const val DETAIL_CONTROLS_HIDE_DELAY_MS = 3_000L

// -------------------------------------------------------------
// Interactive Animated Control Button with Spring & Touch Halo
// -------------------------------------------------------------

@Composable
private fun AnimatedControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 54.dp,
    haloSize: androidx.compose.ui.unit.Dp = 48.dp,
    onHoldStart: (() -> (() -> Unit))? = null,
    onContactChanged: (Boolean) -> Unit = {},
    onBoostChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptics = rememberAkiraHaptics()
    var boosting by remember { mutableStateOf(false) }
    val reduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduced) 0.88f else 1.0f,
        animationSpec = if(reduced)snap() else if(isPressed)spring(.88f,1400f) else spring(.66f,1100f),
        label = "btn_spring"
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.18f else 0.0f,
        animationSpec = if(reduced)snap() else tween(if(isPressed)65 else 160),
        label = "btn_halo"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .akiraTransportPress(onClick,onHoldStart,
                onPressedChanged={isPressed=it;onContactChanged(it)},
                onBoostChanged={if(boosting!=it) {boosting=it;onBoostChanged(it);if(it)haptics.tick()}}),
        contentAlignment = Alignment.Center
    ) {
        if (haloAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(haloSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = haloAlpha))
            )
        }
        Box(Modifier.graphicsLayer { scaleX=scale;scaleY=scale },contentAlignment=Alignment.Center) { content() }

    }
}


@Composable
private fun LyricsBubbleIcon(
    active: Boolean,
    modifier: Modifier = Modifier
) {
                    // Burbuja de diálogo con cita dentro, como el video de referencia.
    // Tint animado para que el cambio de estado nunca parpadee.
    val bubbleTint by animateColorAsState(
        targetValue = if (active) Color.White else Color.White.copy(alpha = 0.50f),
        animationSpec = tween(150),
        label = "lyrics_bubble_tint"
    )
    val strings = LocalStrings.current
    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ChatBubbleOutline,
            contentDescription = strings.lyrics,
            tint = bubbleTint,
            modifier = Modifier.size(25.dp)
        )
        Icon(
            imageVector = Icons.Rounded.FormatQuote,
            contentDescription = null,
            tint = bubbleTint,
            modifier = Modifier
                .size(14.dp)
                .padding(bottom = 3.dp)
        )
    }
}

@Composable
private fun ActiveDot(visible: Boolean) {
    // Animamos el COLOR del fondo en vez de un alpha en graphicsLayer: dentro de
    // graphicsLayer{} el receptor (GraphicsLayerScope) ya tiene `alpha`, así que una
    // variable local homónima se auto-asignaba y el punto quedaba siempre visible.
    // Con el color no hay ambigüedad posible.
    val dotColor by animateColorAsState(
        targetValue = if (visible) Color.White else Color.Transparent,
        animationSpec = tween(160),
        label = "active_dot"
    )
    Spacer(modifier = Modifier.height(3.dp))
    Box(
        modifier = Modifier
            .size(3.5.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}

// -------------------------------------------------------------
// Full-Screen Now Playing Expanded Screen
// -------------------------------------------------------------

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun NowPlayingExpandedScreen(
    song: Song,
    isPlaying: Boolean,
    musicService: MusicServiceConnection,
    isShuffleActive: Boolean,
    repeatMode: Int,
    playerExpansionProgress: Animatable<Float, *>,
    sheetTravelPx: Float,
    dynamicPrimaryColor: Color,
    artworkPrimaryColor: Color = dynamicPrimaryColor,
    artworkColors: PlayerArtworkColors = PlayerArtworkColors(artworkPrimaryColor),
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDeleteSong: ((Song) -> Unit)? = null,
    onGoToAlbum: ((Song) -> Unit)? = null,
    onGoToArtist: ((Song) -> Unit)? = null,
    onSettle: ((target: Float, initialVelocity: Float) -> Unit)? = null,
    onCancelSettle: (() -> Unit)? = null,
    onDraggingChanged: ((Boolean) -> Unit)? = null,
    onArtworkBounds: ((androidx.compose.ui.geometry.Rect, androidx.compose.ui.unit.Dp, Float) -> Unit)? = null,
    travellingArtwork: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val artworkMotion=rememberPlayerArtworkMotion(playerExpansionProgress,onArtworkBounds)
    val artworkDensity=LocalDensity.current
    var artworkRoot by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var artworkCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var reportedArtworkRadius by remember { mutableStateOf(20.dp) }
    fun reportArtwork() {
        val root = artworkRoot; val art = artworkCoordinates
        if (root?.isAttached == true && art?.isAttached == true) {
            // Full-layout coordinates, including the paused cover's transform,
            // without clipping them to the travelling parent surface.
            val bounds = root.localBoundingBoxOf(art, clipBounds = false)
            if (bounds.width > 0f && bounds.height > 0f) {
                val scale = bounds.width / art.size.width.coerceAtLeast(1)
                val radius=reportedArtworkRadius*scale
                artworkMotion.target=ArtworkTarget(bounds,with(artworkDensity){radius.toPx()},
                    if(radius==0.dp && bounds.height>bounds.width*1.2f)1f else 0f)
                if(!artworkMotion.ready)onArtworkBounds?.invoke(bounds,radius,artworkMotion.target?.mask ?: 0f)
            }
        }
    }
    fun artworkAnchor(radius: androidx.compose.ui.unit.Dp, active: Boolean = true): Modifier = Modifier.onGloballyPositioned { coords ->
        if(active) { artworkCoordinates = coords; reportedArtworkRadius = radius; reportArtwork() }
    }.graphicsLayer { alpha = 0f }
    LaunchedEffect(playerExpansionProgress) {
        androidx.compose.runtime.snapshotFlow { playerExpansionProgress.value >= .999f }
            .collect { settled ->
                if (settled) {
                    androidx.compose.runtime.withFrameNanos { }
                    reportArtwork()
                }
            }
    }
    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()

    fun playHapticFeedback(heavy: Boolean = false) {
        if (heavy) haptics.heavy() else haptics.light()
    }

    var isSeeking by remember { mutableStateOf(false) }
    var volumeDragging by remember { mutableStateOf(false) }
    var transportPressed by remember { mutableStateOf(false) }
    var speedHoldCount by remember { mutableIntStateOf(0) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    val favoriteDao=remember(context) {com.akira.app.core.database.AppDatabase.getInstance(context).favoritesDao()}
    val favoriteFlow=remember(favoriteDao,song.id) {com.akira.app.core.online.songFavoriteFlow(context, song)}
    val isFavorite by favoriteFlow.collectAsStateWithLifecycle(initialValue=song.isFavorite)
    var isLyricsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var isQueueOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    // Punto de entrada del temporizador de apagado. El temporizador vivía en el
    // servicio y la hoja existía, pero no había ningún control que la abriera:
    // era una función inalcanzable.
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    // Cuenta atrás viva: si hay temporizador activo se muestra como pastilla y
    // el icono pasa a estar resaltado.
    val sleepRemaining by SleepTimerBus.remainingMs.collectAsStateWithLifecycle()
    val sleepMode by SleepTimerBus.activeMode.collectAsStateWithLifecycle()
    val sleepActive = sleepMode != 0L
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    val timerSource=com.akira.app.core.design.components.rememberAkiraMenuSource(icon=AkiraIcons.SleepTimer,hideOnOpen=false)

    val queue by musicService.currentQueue.collectAsStateWithLifecycle()
    val currentMediaItemIndex by musicService.currentMediaItemIndex.collectAsStateWithLifecycle()

    val prefsRepo = remember { UserPreferencesRepository(context) }
    val initialPlayerUi=remember(context) { com.akira.app.core.datastore.AppUiSnapshot.read(context) }
    val userPrefs by prefsRepo.userPreferences.collectAsStateWithLifecycle(initialValue = initialPlayerUi)
    val isAutoplayActive = userPrefs?.autoplay ?: false
    val fullscreenArtwork = userPrefs?.fullscreenPlayerArtwork ?: false
    val landscape = config.screenWidthDp > config.screenHeightDp
    val compactPortrait = !landscape && config.screenHeightDp < 700
    val playerMotionReduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    val fullBleedArtwork=fullscreenArtwork && !landscape
    val motionAllowed=useMotionArtwork(fullscreenArtwork,isLyricsOpen,isQueueOpen,landscape,
        userPrefs?.animatedPlayerArtwork != false && !playerMotionReduced)
    val playerSettled by remember(playerExpansionProgress) {derivedStateOf {playerExpansionProgress.value>=.999f}}
    var motionArtwork by remember(song.id,song.album,song.artist) {mutableStateOf<MotionArtwork?>(null)}
    LaunchedEffect(song.id,song.album,song.artist,motionAllowed,playerSettled) {
        if(motionAllowed && playerSettled) {
            motionArtwork=MotionArtworkRepository.find(context,song)
            if(com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraHomeArtwork", "player motion matched=${motionArtwork != null}")
        }
    }
    // One transport profile for both artwork styles. Only the cover changes.
    val referencePortrait = !landscape
    val playerInk = if(referencePortrait) androidx.compose.ui.graphics.lerp(Color.White, artworkPrimaryColor, .10f) else Color.White
    val transportInk=playerInk
    // The persistent artwork surface owns the spring; anchors report only
    // final geometry, avoiding a second spring that chases animated bounds.
    val restingCoverScale = rememberUpdatedState(playerCoverRestScale(isPlaying,fullBleedArtwork,landscape))
    LaunchedEffect(restingCoverScale) {
        androidx.compose.runtime.snapshotFlow { restingCoverScale.value }.collect {
            androidx.compose.runtime.withFrameNanos { }
            if(playerExpansionProgress.value >= .999f) reportArtwork()
        }
    }
    val outputSource=com.akira.app.core.design.components.rememberAkiraMenuSource(
        icon=if(referencePortrait)AkiraPlayerGlyphs.Airplay else AkiraIcons.Airplay)
    var previousGlyphPulse by remember { mutableIntStateOf(0) }
    var nextGlyphPulse by remember { mutableIntStateOf(0) }

    // Queue and lyrics share the same reveal/idle/scroll policy. Returning to
    // the main player must never inherit a hidden transport from either page.
    var hideScrubber by remember { mutableStateOf(false) }
    var showControlsOnScroll by remember { mutableStateOf(false) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var lyricsAtTopBoundary by remember { mutableStateOf(true) }
    var lyricsListTopPx by remember { mutableFloatStateOf(0f) }
    // Offset del propio sheet respecto a la raíz. Necesario porque
    // detectVerticalDragGestures entrega onDragStart en coordenadas LOCALES del
    // nodo con pointerInput, mientras que lyricsListTopPx viene de
    // positionInRoot(). Compararlos sin sumar este offset mezcla dos sistemas de
    // coordenadas y sólo acierta por casualidad si el sheet empieza en y=0.
    val sheetRootTopPx = remember { mutableFloatStateOf(0f) }
    var loadedLyrics by remember(song.path, song.id) { mutableStateOf(LoadedLyrics()) }
    val detailScrollActive by androidx.compose.runtime.rememberUpdatedState(!isQueueOpen && isLyricsOpen && loadedLyrics.hasLyrics)
    LaunchedEffect(isQueueOpen, isLyricsOpen, loadedLyrics.hasLyrics, isSeeking, volumeDragging, transportPressed, controlsInteraction) {
        if (!isQueueOpen && isLyricsOpen && loadedLyrics.hasLyrics) {
            hideScrubber = false
            showControlsOnScroll = false
            if (!isSeeking && !volumeDragging && !transportPressed) {
                delay(DETAIL_CONTROLS_HIDE_DELAY_MS)
                hideScrubber = true
            }
        } else {
            hideScrubber = false
            showControlsOnScroll = false
        }
    }
    LaunchedEffect(isQueueOpen, isLyricsOpen, hideScrubber, showControlsOnScroll) {
        if (com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraMotion",
            "detail=${if (isQueueOpen) "queue" else if (isLyricsOpen) "lyrics" else "player"} controls=${playerControlsVisible(true, isQueueOpen, isLyricsOpen, loadedLyrics.hasLyrics, hideScrubber, showControlsOnScroll)}")
    }
    // Al hacer seek se vuelve a mostrar para no perder el control
    LaunchedEffect(isSeeking) {
        if (isSeeking && detailScrollActive) hideScrubber = false
    }

    // Letras: carga en IO sin bloquear la composición. Orden: sidecar en disco,
    // tags embebidos del audio (USLT/Vorbis/©lyr vía ContentResolver) y sidecar
    // indexado en MediaStore.Files. Soporta .lrc/.txt/.srt/.vtt.
    val internetAvailable = com.akira.app.core.lyrics.rememberInternetAvailable()
    val savedLyricsStore = remember(context) { com.akira.app.core.lyrics.SavedLyricsStore(java.io.File(context.filesDir, "downloaded_lyrics")) }
    val lyricsSongKey = remember(song.id,song.path,song.contentUri,song.durationMs) { com.akira.app.core.model.SongKey.of(song).encoded }
    var lyricsSaved by remember(lyricsSongKey) { mutableStateOf(false) }
    var lyricsSaving by remember(lyricsSongKey) { mutableStateOf(false) }
    var lyricsSaveError by remember(lyricsSongKey) { mutableStateOf(false) }
    val exportStore = remember(context) { com.akira.app.core.lyrics.SavedLyricsStore(java.io.File(context.cacheDir, "lyrics_exports")) }
    var lyricsEditor by remember { mutableStateOf<Pair<Song, LoadedLyrics>?>(null) }
    var editorSaving by remember { mutableStateOf(false) }
    var exportLyrics by remember { mutableStateOf<LoadedLyrics?>(null) }
    var exportLyricsKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val exportLyricsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val download = exportLyrics
        val savedKey = exportLyricsKey
        exportLyrics = null
        exportLyricsKey = null
        if (uri != null && (download != null || savedKey != null)) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val payload = download ?: savedKey?.let(exportStore::read) ?: error("Missing export snapshot")
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("No output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.sidecarText()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { lyricsSaveError = true }
            finally { savedKey?.let { withContext(Dispatchers.IO) { exportStore.delete(it) } } }
        } else savedKey?.let { key -> scope.launch(Dispatchers.IO) { exportStore.delete(key) } }
    }
    var lyricsLoading by remember(song.path, song.id) { mutableStateOf(true) }
    var lyricsLookupFailed by remember(song.path,song.id) { mutableStateOf(false) }
    val lyricsGeneration=remember(song.path,song.id) {com.akira.app.core.lyrics.LyricsLookupGeneration()}
    var lyricsRequestVersion by remember(song.path, song.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    var showLyricsSources by remember { mutableStateOf(false) }
    var lyricsSourceOverride by remember(song.path, song.id) { mutableStateOf<String?>(null) }
    val onlineLyricsEnabled = lyricsSourceOverride?.let { it != "LOCAL" } ?: (userPrefs?.onlineLyrics == true)
    val selectedLyricsProvider = com.akira.app.core.lyrics.LyricsProvider.fromId(lyricsSourceOverride ?: userPrefs?.lyricsProvider)
    val onlineLyricsRepo = remember(context, selectedLyricsProvider) {
        com.akira.app.core.lyrics.OnlineLyricsRepository(java.io.File(context.filesDir, selectedLyricsProvider.cacheDirectory),
            selectedLyricsProvider.createFetcher(com.akira.app.BuildConfig.VERSION_NAME))
    }
    LaunchedEffect(song.path, song.id, song.title, song.artist, song.contentUri, userPrefs?.onlineLyrics, selectedLyricsProvider, lyricsSourceOverride, lyricsRequestVersion, internetAvailable) {
        if (userPrefs == null) return@LaunchedEffect
        val token=lyricsGeneration.begin()
        val preferOnline=lyricsSourceOverride!=null && onlineLyricsEnabled
        lyricsLoading = true
        lyricsLookupFailed = false
        try {
        val localResult = withContext(Dispatchers.IO) {
            if(lyricsRequestVersion>0)com.akira.app.core.lyrics.LyricsRepository.invalidateSong(song.id,song.path)
            val saved = savedLyricsStore.read(lyricsSongKey)
            val local=saved ?: loadLyricsForSong(
                context.contentResolver,
                song.contentUri,
                song.path,
                song.title,
                song.artist,
                song.mimeType,
                songId=song.id
            )
            local to (saved!=null && !preferOnline)
        }
        val localLyrics=localResult.first
        lyricsSaved=localResult.second
        val resolution = onlineLyricsRepo.resolveDetailed(onlineLyricsEnabled, if (preferOnline) LoadedLyrics() else localLyrics,
            com.akira.app.core.lyrics.LyricsRequest(com.akira.app.core.model.SongKey.of(song).encoded,
                song.title,song.artist,song.album,song.durationMs), forceRefresh = lyricsRequestVersion > 0,
                networkAvailable = internetAvailable)
        lyricsLookupFailed=resolution.status==com.akira.app.core.lyrics.LyricsLookupStatus.FAILED
        loadedLyrics=resolution.lyrics.let {if(it.hasLyrics)it else localLyrics}
        } finally { if(lyricsGeneration.owns(token))lyricsLoading = false }
    }
    val menuBackdrop=rememberLayerBackdrop()
    val modalBackdrop = com.akira.app.core.design.backdrop.backdrops.rememberCombinedBackdrop(
        LocalAppBackdrop.current, menuBackdrop)
    // The artwork is an immersive dark scene even when the library is light.
    MaterialTheme(colorScheme=com.akira.app.core.design.theme.DarkColorScheme) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides Color.White,
        com.akira.app.core.design.components.LocalAkiraOverlayBackdrop provides modalBackdrop) {
    if (lyricsSaveError) com.akira.app.core.design.components.AkiraModalSheet({ lyricsSaveError = false }) {
        Column(Modifier.padding(24.dp)) {
            Text(LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.LyricsSaveFailed))
            androidx.compose.material3.TextButton(onClick = { haptics.click(); lyricsSaveError = false }) { Text(LocalStrings.current.ok) }
        }
    }
    if (showLyricsSources) com.akira.app.feature.settings.LyricsSourcesSheet(
        enabled = onlineLyricsEnabled,
        provider = selectedLyricsProvider.name,
        forCurrentSong = true,
        onDismiss = { showLyricsSources = false },
        onSave = { online, provider ->
            showLyricsSources = false
            lyricsSourceOverride = if (online) provider else "LOCAL"
            lyricsRequestVersion++
        }
    )
    val lyricLines = loadedLyrics.synced
    val plainLyricLines = loadedLyrics.plain

    val servicePositionMs by musicService.currentPositionMs.collectAsStateWithLifecycle()
    val widgetConnected by musicService.isConnected.collectAsStateWithLifecycle()
    val widgetMotion = (context as? com.akira.app.MainActivity)?.widgetLaunchMotion
    val widgetWindow = widgetMotion != null || context is com.akira.app.core.widget.WidgetLaunchActivity
    val seedMatchesSong = (context as? com.akira.app.MainActivity)?.widgetInitialSongId == song.id
    val widgetPosition = remember(song.id, widgetMotion) { (widgetMotion?.initialPositionMs
        ?: if (widgetWindow) (context as? com.akira.app.MainActivity)?.widgetInitialPositionMs ?: 0L else 0L)
        .coerceIn(0L, song.durationMs.coerceAtLeast(0L)) }
    var widgetPositionReady by remember(song.id, widgetMotion) { mutableStateOf(!widgetWindow || !seedMatchesSong) }
    LaunchedEffect(widgetConnected, servicePositionMs) {
        if (widgetConnected && (musicService.mediaController?.mediaItemCount ?: 0) > 0 &&
            (widgetPosition == 0L || kotlin.math.abs(servicePositionMs - widgetPosition) < 2000L)) widgetPositionReady = true
    }
    LaunchedEffect(song.id, widgetMotion) {
        if (widgetWindow) { kotlinx.coroutines.delay(3000); widgetPositionReady = true }
    }
    val currentMs = if (widgetWindow && !widgetPositionReady) widgetPosition
        else if (widgetWindow) musicService.mediaController?.currentPosition?.coerceAtLeast(0L) ?: servicePositionMs else servicePositionMs
    lyricsEditor?.let { (editedSong, initialLyrics) ->
        val key = com.akira.app.core.model.SongKey.of(editedSong).encoded
        LyricsEditorScreen(editedSong, initialLyrics, currentMs.takeIf { song.id == editedSong.id },
            { lyricsEditor = null }, editorSaving) { edited ->
            scope.launch {
                editorSaving = true
                try {
                    withContext(Dispatchers.IO) { savedLyricsStore.save(key, edited) }
                    if (lyricsSongKey == key) { loadedLyrics = edited; lyricsSaved = true }
                    lyricsEditor = null
                    com.akira.app.core.design.components.AkiraNotices.show(strings.ui(com.akira.app.core.i18n.AkiraUiText.LyricsSavedOffline))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { lyricsSaveError = true }
                finally { editorSaving = false }
            }
        }
    }
    val durationMs by musicService.durationMs.collectAsStateWithLifecycle()
    val progress = if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val currentDisplayProgress = if (isSeeking) seekFraction else progress
    val safeDurationMs = durationMs.takeIf { it > 0 } ?: song.durationMs
    val currentPositionMs = (currentDisplayProgress * safeDurationMs).toLong()
    val remainingMs = (safeDurationMs - currentPositionMs).coerceAtLeast(0L)

    // Índice de la línea activa (barato, un Int). Al cambiar de verso se
    // re-lanza el auto-hide de controles, así que no tarda 2s en ocultarse.
    val lyricActiveLineKey = remember(lyricLines, currentPositionMs) {
        if (lyricLines.isEmpty()) -1
        else lyricLines.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
    }

    // Al pasar de verso se ocultan de inmediato si el usuario los había
    // revivido al scrollear (antes tardaban ~2s y se veían colgados).
    LaunchedEffect(showControlsOnScroll, isQueueOpen, isLyricsOpen, loadedLyrics.hasLyrics, isSeeking, volumeDragging, transportPressed) {
        if (showControlsOnScroll && detailScrollActive && !isSeeking && !volumeDragging && !transportPressed) {
            delay(DETAIL_CONTROLS_HIDE_DELAY_MS)
            showControlsOnScroll = false
            hideScrubber = true
        }
    }

    // Audio Manager for Volume Slider
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    var volumeLevel by remember {
        mutableFloatStateOf(
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: (maxVolume * 0.7f)
        )
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f
                        if (!volumeDragging) volumeLevel = currentVolume
                    }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val currentSheetTravel by rememberUpdatedState(sheetTravelPx)
    val currentCancelSettle by rememberUpdatedState(onCancelSettle)
    val currentSettle by rememberUpdatedState(onSettle)
    val currentDraggingChanged by rememberUpdatedState(onDraggingChanged)
    // Swipe liviano: sigue al dedo 1:1 y al soltar decide por VELOCIDAD (fling)
    // o por arrastre acumulado corto. El spring arranca con la velocidad real.
    Box(
        modifier = modifier.layerBackdrop(menuBackdrop).graphicsLayer {
            val outputSceneBlur=if(showAudioOutputSheet)14f*outputSource.presentationProgress() else 0f
            renderEffect=com.akira.app.core.design.rendering.BlurEffectCache.get(outputSceneBlur*density.density)
        }.then(Modifier.playerDynamicBackdrop(artworkColors, userPrefs?.backgroundMotion != false,
            opacity = { com.akira.app.feature.player.dock.playerMaterialReveal(playerExpansionProgress.value) }))
            .onGloballyPositioned { artworkRoot = it; reportArtwork() }
            .fillMaxSize()
            .onGloballyPositioned { sheetRootTopPx.floatValue = it.positionInRoot().y }
            .pointerInput(detailScrollActive) {
                detectTapGestures(onTap = {
                    if(detailScrollActive) {
                        hideScrubber=false
                        showControlsOnScroll=true
                        controlsInteraction++
                    }
                })
            }
            .pointerInput(Unit) {
                var travelPx = currentSheetTravel.coerceAtLeast(1f)
                val velocityTracker = VelocityTracker()
                var settleJob: Job? = null
                var dragDeltaPx = 0f
                var trackedY = 0f
                var startedInHeader = false
                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        currentCancelSettle?.invoke() ?: settleJob?.cancel()
                        travelPx = currentSheetTravel.coerceAtLeast(1f)
                        currentDraggingChanged?.invoke(true)
                        velocityTracker.resetTracking()
                        dragDeltaPx = 0f
                        trackedY = 0f
                        // Con las letras abiertas, un arrastre que EMPIEZA en la cabecera
                        // (por encima de la lista) siempre es del sheet. Antes, con la lista
                        // desplazada (lo normal: se auto-desplaza a la línea activa) no se
                        // podía minimizar con swipe en ningún punto.
                        // startOffset es LOCAL al sheet; lyricsListTopPx está en
                        // coordenadas raíz. Se comparan en el mismo sistema.
                        val startYInRoot = sheetRootTopPx.floatValue + startOffset.y
                        startedInHeader = startYInRoot < lyricsListTopPx
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Con las letras abiertas y el dedo sobre la lista, el arrastre es del
                        // scroll mientras la lista tenga letras anteriores.
                        val listOwnsGesture = isLyricsOpen && lyricsAtTopBoundary && !startedInHeader
                        if (listOwnsGesture) {
                            change.consume()
                            hideScrubber = true
                            showControlsOnScroll = false
                            return@detectVerticalDragGestures
                        }
                        if (dragAmount > 0f || playerExpansionProgress.value < 1f) {
                            change.consume()
                            dragDeltaPx += dragAmount
                            trackedY += dragAmount
                            velocityTracker.addPosition(change.uptimeMillis, Offset(0f, trackedY))
                            val newProgress = (playerExpansionProgress.value - dragAmount / travelPx).coerceIn(0f, 1f)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                runCatching { playerExpansionProgress.snapTo(newProgress) }
                            }
                        }
                    },
                    onDragEnd = {
                        currentDraggingChanged?.invoke(false)
                        val velocityY = try { velocityTracker.calculateVelocity().y } catch (_: Exception) { 0f }
                        val target = resolvePlayerSheetTarget(
                            progress = playerExpansionProgress.value,
                            velocityY = velocityY,
                            dragDeltaPx = dragDeltaPx,
                            referencePx = travelPx,
                            commitDistancePx = with(density) { PlayerSheetSwipeCommit.toPx() }
                        )
                        val initialVelocity = (-velocityY / travelPx).coerceIn(-8f, 8f)
                        if (currentSettle != null) {
                            currentSettle?.invoke(target, initialVelocity)
                        } else {
                            settleJob?.cancel()
                            settleJob = scope.launch {
                                runCatching {
                                    playerExpansionProgress.animateTo(
                                        targetValue = target,
                                        initialVelocity = initialVelocity,
                                        animationSpec = playerSheetAnimationSpec()
                                    )
                                    playerExpansionProgress.snapTo(target)
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        currentDraggingChanged?.invoke(false)
                        val target = if (playerExpansionProgress.value < SheetPositionHysteresis) 0f else 1f
                        if (currentSettle != null) {
                            currentSettle?.invoke(target, 0f)
                        } else {
                            settleJob?.cancel()
                            settleJob = scope.launch {
                                runCatching {
                                    playerExpansionProgress.animateTo(
                                        targetValue = target,
                                        animationSpec = playerSheetAnimationSpec()
                                    )
                                    playerExpansionProgress.snapTo(target)
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // La progresión se lee SÓLO en la fase de dibujo (graphicsLayer) o a través de
        // derivedStateOf. Antes `.value` se leía aquí, en composición, así que cada frame
        // del arrastre recomponía toda la pantalla (letras, controles, sliders...): esa
        // era la causa principal de que el sheet se sintiera "pesado".
        val controlsAlphaOf: () -> Float = {
            if(referencePortrait) {
                val reveal=((playerExpansionProgress.value-.22f)/.60f).coerceIn(0f,1f)
                reveal*reveal*(3f-2f*reveal)
            } else ((playerExpansionProgress.value - 0.72f) / 0.23f).coerceIn(0f, 1f)
        }

        // Back cierra primero la cola o las letras, luego el player
        BackHandler(enabled = isLyricsOpen || isQueueOpen) {
            if (isQueueOpen) isQueueOpen = false
            else if (isLyricsOpen) isLyricsOpen = false
        }

        travellingArtwork?.invoke()
        // The video is a native AndroidView. Keep it beneath metadata and controls:
        // a visually transparent part of its texture still owns pointer hit testing.
        PlayerArtworkSurface(artworkMotion,song,playerExpansionProgress,config.screenWidthDp*density.density,
            motionArtwork=motionArtwork.takeIf {motionAllowed && playerSettled},isPlaying=isPlaying)

        // Persistent layout and a single visible artwork surface across all pages.
        // Solo el hero colapsa/expande al entrar/salir de lyrics o cola; scrubber, playback,
        // volumen y barra inferior no se mueven.
        AkiraExpandedPlayerLayout(wide=landscape,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (compactPortrait) 18.dp else 26.dp)
                .padding(top = 4.dp, bottom = if(compactPortrait)12.dp else if(landscape)8.dp else if(referencePortrait)33.dp else 48.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Invisible header slot; dismissal remains available through swipe-down.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if(landscape || compactPortrait)18.dp else 34.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        playHapticFeedback()
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Keep the header slot and swipe area without a visible handle.
            }

            Spacer(modifier = Modifier.height(if(compactPortrait)8.dp else 16.dp))

            // Page bodies switch directly. Their invisible artwork anchors
            // feed one persistent visible surface, independent of page lifecycle.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val page=when {
                    isQueueOpen -> ExpandedPage.QUEUE
                    isLyricsOpen -> ExpandedPage.LYRICS
                    else -> ExpandedPage.PLAYER
                }
                    when (page) {
                        ExpandedPage.QUEUE -> {
                            QueueHero(
                                currentSong = song.copy(isFavorite=isFavorite),
                                onUserScroll = { delta ->
                                    if (detailScrollActive) {
                                        if(delta != 0f) {
                                            hideScrubber = true
                                            showControlsOnScroll = false
                                        }
                                    }
                                },
                                queue = queue,
                                currentPlayingIndex = currentMediaItemIndex,
                                isShuffleActive = isShuffleActive,
                                repeatMode = repeatMode,
                                isAutoplayActive = isAutoplayActive,
                                onCoverClick = {
                                    playHapticFeedback()
                                    isQueueOpen = false
                                },
                                onToggleFavorite = {
                                    playHapticFeedback()
                                    onToggleFavorite(it)
                                },
                                onOptionsClick = {
                                    playHapticFeedback()
                                    showOptionsSheet = true
                                },
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onToggleAutoplay = {
                                    playHapticFeedback()
                                    scope.launch { prefsRepo.setAutoplay(!isAutoplayActive) }
                                },
                                onPlayItem = { index ->
                                    playHapticFeedback()
                                    musicService.skipToQueueIndex(index)
                                },
                                onMoveItem = { from, to ->
                                    musicService.moveQueueItem(from, to)
                                },
                                onRemoveItem = { song ->
                                    musicService.removeQueueEntry(song.queueEntryId)
                                },
                                onClearQueue = {
                                    musicService.clearQueue()
                                },
                                onRestoreQueue = { songs, idx ->
                                    musicService.restoreQueue(songs, idx)
                                },
                                onRestoreItem = { idx, s ->
                                    musicService.insertQueueItem(idx, s)
                                },
                                thumbModifier = artworkAnchor(14.dp, isQueueOpen),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ExpandedPage.LYRICS -> {
                            LyricsHero(
                        song = song,
                        lyricLines = lyricLines,
                        actionsReferencePortrait = referencePortrait,
                        actionsInk = playerInk,
                        plainLyricLines = plainLyricLines,
                        lyricsLoading = lyricsLoading,
                        onlineLyricsEnabled = onlineLyricsEnabled,
                        lyricsProvider = selectedLyricsProvider.name,
                        showSourcesControl = playerControlsVisible(true, isQueueOpen, isLyricsOpen, loadedLyrics.hasLyrics, hideScrubber, showControlsOnScroll),
                        onOpenLyricsSources = { haptics.click(); showLyricsSources = true },
                        onRetryLyrics = { if(!lyricsLoading) {haptics.click();lyricsLoading=true;lyricsRequestVersion++} },
                        lyricsLookupFailed=lyricsLookupFailed,
                        internetAvailable = internetAvailable,
                        onSearchOnline = { if(!lyricsLoading) {haptics.click();lyricsLoading=true;lyricsSourceOverride=selectedLyricsProvider.name;lyricsRequestVersion++} },
                        lyricsSaved = lyricsSaved,
                        lyricsSaving = lyricsSaving,
                        onExportLyrics = {
                            haptics.click()
                            val download = loadedLyrics
                            val key = lyricsSongKey
                            val baseName = song.path.substringAfterLast('/').substringBeforeLast('.').ifBlank { song.title }
                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            scope.launch {
                                try {
                                    // A saved payload survives process recreation while Android's file picker is open.
                                    withContext(Dispatchers.IO) { exportStore.save(key, download) }
                                    exportLyricsKey = key
                                    exportLyrics = download
                                    exportLyricsLauncher.launch(baseName + if (download.isSynced) ".lrc" else ".txt")
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { lyricsSaveError = true }
                            }
                        },
                        onEditLyrics = { haptics.click(); lyricsEditor = song to loadedLyrics },
                        onRemoveSavedLyrics = {
                            haptics.click()
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { check(savedLyricsStore.delete(lyricsSongKey)) }
                                    lyricsSaved = false; lyricsSourceOverride = "LOCAL"; lyricsRequestVersion++
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { lyricsSaveError = true }
                            }
                        },
                        onSaveLyrics = {
                            haptics.click()
                            if (!lyricsSaving && loadedLyrics.hasLyrics) {
                                val download = loadedLyrics
                                scope.launch {
                                    lyricsSaving = true
                                    try {
                                        withContext(Dispatchers.IO) { savedLyricsStore.save(lyricsSongKey, download) }
                                        lyricsSaved = true
                                        com.akira.app.core.design.components.AkiraNotices.show(strings.ui(com.akira.app.core.i18n.AkiraUiText.LyricsSavedOffline))
                                    }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (_: Exception) { lyricsSaveError = true }
                                    finally { lyricsSaving = false }
                                }
                            }
                        },
                        currentPositionMs = currentPositionMs,
                        onSeekLyric = { timeMs: Long ->
                            playHapticFeedback()
                            val dur = durationMs.takeIf { it > 0 } ?: (song.durationMs.takeIf { it > 0 } ?: 1L)
                            widgetPositionReady = true
                            onSeekTo((timeMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                        },
                        activeLineKey = lyricActiveLineKey,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        onToggleFavorite = {
                            playHapticFeedback()
                            onToggleFavorite(song)
                        },
                        onOptionsClick = {
                            playHapticFeedback()
                            showOptionsSheet = true
                        },
                        onCoverClick = {
                            playHapticFeedback()
                            isLyricsOpen = false
                        },
                        onUserScroll = scroll@{ delta ->
                            if (!detailScrollActive) return@scroll
                            if (delta != 0f) {
                                showControlsOnScroll = false
                                hideScrubber = true
                            }
                        },
                        onListTopChanged = { lyricsListTopPx = it },
                        onScrollBoundary = { canScrollBack ->
                            // Si la lista todavía tiene letras anteriores por
                            // recorrer, el gesto vertical es del scroll, no del
                            // reproductor: se lo cedemos.
                            lyricsAtTopBoundary = canScrollBack
                        },
                        thumbModifier = artworkAnchor(14.dp, isLyricsOpen && !isQueueOpen)
                    )
                }
                ExpandedPage.PLAYER -> {
                    // Measure the cover within the available window, after reserving the controls.
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val coverWidth = if (compactPortrait) minOf(maxWidth * .98f,
                        (maxHeight - 16.dp).coerceAtLeast(1.dp)) else maxWidth * .98f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                // Both artwork styles report their real final rectangle to the
                // persistent artwork owner; this node does not paint a cover.
                val fullCoverTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+54.dp
                Box(Modifier.width(coverWidth).aspectRatio(1f).graphicsLayer {
                    scaleX = restingCoverScale.value; scaleY = restingCoverScale.value
                }.then(
                    if(fullBleedArtwork) Modifier.layout { measurable, constraints ->
                        val slot=constraints.maxWidth
                        val width=config.screenWidthDp.dp.roundToPx()
                        val height=(width*FullscreenArtworkHeightRatio).roundToInt()
                        val art=measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width,height))
                        layout(slot,slot) { art.place(-(width-slot)/2,-fullCoverTop.roundToPx()) }
                    } else Modifier
                )) {
                    Box(modifier=artworkAnchor(if(fullBleedArtwork)0.dp else 20.dp,
                        !isLyricsOpen && !isQueueOpen).fillMaxSize(),contentAlignment=Alignment.Center) {
                        // Measurement anchor only. PlayerArtworkSurface owns the image.
                    }
                }

                Spacer(modifier = Modifier.height(if(compactPortrait)8.dp else 20.dp))

// El pill de formato (FLAC · 24-bit / 96 kHz) se eliminó por petición del
// usuario, pero se conserva SU ESPACIO con un Spacer de la misma altura
// (6dp padding vertical + línea de texto ≈ 26dp) para que el resto del layout
// (título, scrubber, controles) no se reacomode al quitarlo.
                Spacer(modifier = Modifier.height(if(compactPortrait)8.dp else 26.dp))


                    } // Cover and spacing
                    } // Available hero bounds
                } // ExpandedPage.PLAYER
            } // when (page)
            } // Persistent hero slot

            Box(Modifier.fillMaxWidth()) {
                if (!isQueueOpen && (!isLyricsOpen || landscape)) {
            run {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val controlsAlpha = controlsAlphaOf()
                            alpha = controlsAlpha
                            translationY = if(referencePortrait)0f else (1f-controlsAlpha)*80f
                        }
                ) {
                // 1. Song Title & Artist (Smooth slide transition on track change) + Star + 28dp 3-dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).padding(start=if(referencePortrait)9.5.dp else 0.dp)) {
                        AnimatedContent(
                            targetState = song,
                            transitionSpec = {
                                (slideInHorizontally { width -> (width * 0.35f).toInt() } + fadeIn(tween(220)))
                                    .togetherWith(slideOutHorizontally { width -> (-width * 0.35f).toInt() } + fadeOut(tween(180))).using(SizeTransform(clip = false))
                            },
                            label = "song_title_anim"
                        ) { currentSongTarget ->
                            Column {
                                com.akira.app.core.design.components.AkiraPlayerMarquee(
                                    text = currentSongTarget.title, fontSize = if(compactPortrait)18.sp else 20.sp, lineHeight = 25.sp,
                                    fontWeight = FontWeight.SemiBold, color = playerInk,
                                    fontFamily = if(referencePortrait)androidx.compose.ui.text.font.FontFamily.SansSerif else AkiraFontFamily)
                                Spacer(modifier = Modifier.height(2.dp))
                                com.akira.app.core.design.components.AkiraPlayerMarquee(
                                    text = currentSongTarget.artist, fontSize = if(compactPortrait)16.sp else if(referencePortrait)20.sp else 18.sp,
                                    fontWeight = FontWeight.Normal, color = playerInk.copy(alpha = .65f),
                                    modifier=Modifier.heightIn(min=if(compactPortrait)24.dp else 32.dp).clickable(enabled=onGoToArtist!=null,role=androidx.compose.ui.semantics.Role.Button) {
                                        haptics.light();onGoToArtist?.invoke(currentSongTarget)
                                    },
                                    fontFamily = if(referencePortrait)androidx.compose.ui.text.font.FontFamily.SansSerif else AkiraFontFamily)
                            }
                        }
                    }

                    PlayerTrackActions(isFavorite,playerInk,referencePortrait,
                        onFavorite={onToggleFavorite(song)},onMore={showOptionsSheet=true})
                }
                } // COL-C título (dentro del hero)
            } // IF1 controlsAlpha (hero)
                }
            }
            // Cover-complete portrait uses the reference's deeper metadata gap.
            Box(Modifier.fillMaxWidth().height(if(compactPortrait)10.dp else if(landscape)20.dp else 30.dp),contentAlignment=Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(speedHoldCount>0,enter=fadeIn(tween(90)),exit=fadeOut(tween(120))) {
                    Text("2×",Modifier.semantics { contentDescription="2×" }
                        .background(Color(0xFF858589).copy(alpha=.88f),RoundedCornerShape(7.dp))
                        .padding(horizontal=13.dp,vertical=3.dp),fontSize=13.sp,
                        fontWeight=FontWeight.SemiBold,color=Color.White)
                }
            }

            // -------------------------------------------------------------
            // Controles: se ELIMINAN del árbol (no sólo se atenúan) cuando las
            // letras o la cola están a pantalla completa, para que no ocupen espacio ni
            // sigan siendo pulsables. AnimatedVisibility colapsa el alto.
            // -------------------------------------------------------------
            Box(Modifier.fillMaxWidth().then(if(landscape || compactPortrait)Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = playerControlsVisible(true, isQueueOpen, isLyricsOpen, loadedLyrics.hasLyrics, hideScrubber, showControlsOnScroll),
                enter = expandVertically(if(playerMotionReduced)snap() else spring(.86f,850f)),
                exit = shrinkVertically(if(playerMotionReduced)snap() else spring(.90f,1000f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val controlsAlpha = controlsAlphaOf()
                            alpha = controlsAlpha
                            translationY = if(referencePortrait)0f else (1f-controlsAlpha)*80f
                        }
                ) {
                // 2. Scrubber / Time Bar + Timestamps (1:52 and -2:23)
                // (el bloque completo de controles se atenúa arriba, en lyrics)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal=if(referencePortrait)9.5.dp else 0.dp)) {
                    AkiraPlayerSlider(currentDisplayProgress, {
                        if (!isSeeking) playHapticFeedback()
                        widgetPositionReady=true;isSeeking=true;seekFraction=it
                    }, label=com.akira.app.core.design.components.localizedText("Playback position", "Posición de reproducción"),
                        onFinished={isSeeking=false;playHapticFeedback();onSeekTo(seekFraction)},
                        onActiveChanged={ if(!it)isSeeking=false },
                        tint=playerInk, restingThickness=if(referencePortrait)7.25.dp else 6.dp,
                        modifier=Modifier.fillMaxWidth().height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs),
                            fontSize = 12.sp,
                            fontFamily = if(referencePortrait)androidx.compose.ui.text.font.FontFamily.SansSerif else AkiraFontFamily,
                            fontWeight = if(referencePortrait)FontWeight.Medium else FontWeight.Normal,
                            letterSpacing = if(referencePortrait).6.sp else 0.sp,
                            color = playerInk.copy(alpha = if(referencePortrait).35f else .55f)
                        )
                        // Temporizador activo: tiempo restante tocable que abre la hoja.
                        if (sleepActive) {
                            val sleepLabel = when (sleepMode) {
                                SleepTimerBus.END_OF_SONG -> strings.sleepTimerEndOfSong
                                SleepTimerBus.END_OF_QUEUE -> strings.sleepTimerEndOfQueue
                                else -> {
                                    val totalSec = (sleepRemaining / 1000).coerceAtLeast(0)
                                    "%d:%02d".format(totalSec / 60, totalSec % 60)
                                }
                            }
                            Row(
                                modifier = Modifier.then(timerSource.modifier).noRippleClickable { timerSource.open { showSleepTimerSheet = true } },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = AkiraIcons.SleepTimer,
                                    contentDescription = strings.sleepTimer,
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = sleepLabel,
                                    fontSize = 12.sp,
                                    fontFamily = AkiraFontFamily,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Text(
                            text = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.S2, formatDuration(remainingMs)),
                            fontSize = 12.sp,
                            fontFamily = if(referencePortrait)androidx.compose.ui.text.font.FontFamily.SansSerif else AkiraFontFamily,
                            fontWeight = if(referencePortrait)FontWeight.Medium else FontWeight.Normal,
                            letterSpacing = if(referencePortrait).6.sp else 0.sp,
                            color = playerInk.copy(alpha = if(referencePortrait).35f else .55f)
                        )
                    }
                } // fin Column scrubber

                Spacer(modifier = Modifier.height(if(compactPortrait)8.dp else if(landscape)18.dp else if(referencePortrait)11.dp else 18.dp))

                // 3. Playback Controls with Spring Bounce + Glowing Halo + Haptics (Matching pasar.mp4!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind / Previous Track
                    AnimatedControlButton(
                        onClick = {
                            haptics.transport()
                            previousGlyphPulse++
                            onPrev()
                        },
                        size = if(compactPortrait)52.dp else if(landscape)48.dp else 76.dp,
                        haloSize = 64.dp,
                        onContactChanged={ transportPressed=it },
                        onHoldStart = musicService::beginTemporaryDoubleSpeed,
                        onBoostChanged={speedHoldCount=(speedHoldCount+if(it)1 else -1).coerceAtLeast(0)}
                    ) {
                        AkiraTransportGlyph(-1,previousGlyphPulse,strings.previousTrack,
                            if(landscape || compactPortrait)Modifier.size(32.dp) else Modifier.offset(x=.3.dp).size(45.dp,44.7.dp), tint=transportInk)
                    }

                    // Play / Pause Hero Button with Glyph Morph
                    AnimatedControlButton(
                        onClick = {
                            haptics.transport()
                            onTogglePlay()
                        },
                        size = if(compactPortrait)60.dp else if(landscape)52.dp else 84.dp,
                        haloSize = 76.dp,
                        onContactChanged={ transportPressed=it }
                    ) {
                        com.akira.app.core.design.components.AkiraAnimatedGlyph(
                            icon=if(isPlaying)AkiraIcons.Pause else AkiraIcons.Play,
                            label=if(isPlaying)strings.pause else strings.play,
                            tint=transportInk,modifier=Modifier.size(if(compactPortrait)44.dp else if(landscape)38.dp else 62.dp))
                    }

                    // Fast Forward / Next Track
                    AnimatedControlButton(
                        onClick = {
                            haptics.transport()
                            nextGlyphPulse++
                            onNext()
                        },
                        size = if(compactPortrait)52.dp else if(landscape)48.dp else 76.dp,
                        haloSize = 64.dp,
                        onContactChanged={ transportPressed=it },
                        onHoldStart = musicService::beginTemporaryDoubleSpeed,
                        onBoostChanged={speedHoldCount=(speedHoldCount+if(it)1 else -1).coerceAtLeast(0)}
                    ) {
                        AkiraTransportGlyph(1,nextGlyphPulse,strings.nextTrack,
                            if(landscape || compactPortrait)Modifier.size(32.dp) else Modifier.offset(x=(-2).dp).size(45.dp,44.7.dp), tint=transportInk)
                    }
                }

                Spacer(modifier = Modifier.height(if(compactPortrait)12.dp else if(landscape)6.dp else if(referencePortrait)34.dp else 20.dp))

                // 4. Volume Slider Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if(referencePortrait)8.dp else 4.dp,
                            end = if(referencePortrait)12.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if(referencePortrait)6.dp else 10.dp)
                ) {
                    Icon(
                        imageVector = if(referencePortrait)AkiraPlayerGlyphs.VolumeLow else Icons.AutoMirrored.Rounded.VolumeDown,
                        contentDescription = strings.volume,
                        tint = playerInk.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )

                    AkiraPlayerSlider(volumeLevel, {
                        volumeDragging=true
                        volumeLevel=it;audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC,it.toInt(),0)
                    }, label=strings.volume, valueRange=0f..maxVolume.toFloat(),volume=true,
                        onFinished={ volumeDragging=false },
                        onActiveChanged={ volumeDragging=it },
                        tint=playerInk, restingThickness=if(referencePortrait)7.25.dp else 6.dp,
                        modifier=Modifier.weight(1f).height(18.dp))

                    Icon(
                        imageVector = if(referencePortrait)AkiraPlayerGlyphs.VolumeHigh else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = strings.volume,
                        tint = playerInk.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start=if(referencePortrait)6.5.dp else 0.dp).size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if(compactPortrait)6.dp else if(landscape)6.dp else if(referencePortrait)12.dp else 20.dp))

                // 5. Bottom Utilities: Lyrics (left), Audio Output (center), Queue (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if(compactPortrait)12.dp else if(referencePortrait)37.dp else 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Lyrics Button
                    val lyricsTouch=remember { MutableInteractionSource() }
                    val lyricsPressed by lyricsTouch.collectIsPressedAsState()
                    val lyricsResponse=com.akira.app.core.design.components.rememberAkiraPressResponse(lyricsPressed)
                    val outputTouch=remember { MutableInteractionSource() }
                    val outputPressed by outputTouch.collectIsPressedAsState()
                    val outputResponse=com.akira.app.core.design.components.rememberAkiraPressResponse(outputPressed)
                    val queueTouch=remember { MutableInteractionSource() }
                    val queuePressed by queueTouch.collectIsPressedAsState()
                    val queueResponse=com.akira.app.core.design.components.rememberAkiraPressResponse(queuePressed)
                    val lyricsActiveBgScale by animateFloatAsState(
                        targetValue = if (isLyricsOpen) 1f else 0f,
                        animationSpec = if(playerMotionReduced)snap() else spring(dampingRatio = 0.74f, stiffness = 1000f),
                        label = "lyrics_bg_scale"
                    )
                    val lyricsAlpha by animateFloatAsState(
                        targetValue = if (isLyricsOpen) 1.0f else 0.62f,
                        animationSpec = tween(150),
                        label = "lyrics_glyph_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(lyricsResponse.modifier)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource=lyricsTouch,indication=null) {
                                playHapticFeedback()
                                if (isLyricsOpen) {
                                    isLyricsOpen = false
                                } else {
                                    isQueueOpen = false
                                    isLyricsOpen = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (lyricsActiveBgScale > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = lyricsActiveBgScale
                                        scaleY = lyricsActiveBgScale
                                        alpha = lyricsActiveBgScale
                                    }
                                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            )
                        }
                        Icon(
                            imageVector = AkiraIcons.Lyrics,
                            contentDescription = strings.lyrics,
                            tint = playerInk.copy(alpha = lyricsAlpha),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Center: Audio Output / Airplay
                    Box(
                        modifier = Modifier.then(outputSource.modifier)
                            .size(48.dp)
                            .then(outputResponse.modifier)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource=outputTouch,indication=null) {
                                playHapticFeedback()
                                outputSource.open { showAudioOutputSheet = true }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if(referencePortrait)AkiraPlayerGlyphs.Airplay else AkiraIcons.Airplay,
                            contentDescription = strings.audioOutput,
                            tint = playerInk.copy(alpha = 0.62f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Right: Queue Button
                    val queueActiveBgScale by animateFloatAsState(
                        targetValue = if (isQueueOpen) 1f else 0f,
                        animationSpec = if(playerMotionReduced)snap() else spring(dampingRatio = 0.74f, stiffness = 1000f),
                        label = "queue_bg_scale"
                    )
                    val queueAlpha by animateFloatAsState(
                        targetValue = if (isQueueOpen) 1.0f else 0.62f,
                        animationSpec = tween(150),
                        label = "queue_glyph_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(queueResponse.modifier)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource=queueTouch,indication=null) {
                                playHapticFeedback()
                                if (isQueueOpen) {
                                    isQueueOpen = false
                                } else {
                                    isLyricsOpen = false
                                    isQueueOpen = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (queueActiveBgScale > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = queueActiveBgScale
                                        scaleY = queueActiveBgScale
                                        alpha = queueActiveBgScale
                                    }
                                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            )
                        }
                        Icon(
                            imageVector = AkiraIcons.Queue,
                            contentDescription = strings.queue,
                            tint = playerInk.copy(alpha = queueAlpha),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                } // inner Column (controles fijos)
            } // if (controlsAlpha)
            } // Persistent transport slot in both orientations.
        } // Column persistente (header + hero + controles)


        // Sheets y diálogos modales
androidx.compose.material3.MaterialTheme(colorScheme = com.akira.app.core.design.theme.DarkColorScheme) {
androidx.compose.runtime.CompositionLocalProvider(LocalAppBackdrop provides modalBackdrop) {
if (showOptionsSheet) {
                SongOptionsSheet(
                song = song,
                isFromPlayer = true,
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist,
                onDismiss = { showOptionsSheet = false },
                onPlayNext = {
                    musicService.playNext(it)
                    showOptionsSheet = false
                },
                onAddToQueue = {
                    musicService.addToQueue(it)
                    showOptionsSheet = false
                },
                onToggleFavorite = {
                    onToggleFavorite(it)
                },
                onViewLyrics = {
                    isLyricsOpen = true
                    isQueueOpen = false
                },
                onViewDetails = {
                    showOptionsSheet = false
                    showDetailsDialog = true
                },
                onSleepTimer = {
                    showOptionsSheet = false
                    showSleepTimerSheet = true
                },
                onDelete = onDeleteSong?.let { del ->
                    { s ->
                        showOptionsSheet = false
                        del(s)
                    }
                }
            )
        }

        if (showDetailsDialog) {
            SongDetailsDialog(
                song = song,
                onDismiss = { showDetailsDialog = false }
            )
        }

        if (showAudioOutputSheet) {
            AudioOutputSheet(song=song,isPlaying=isPlaying,
                onDismiss = { showAudioOutputSheet = false }
            )
        }

        // Temporizador de apagado. Se manda por el bus porque el temporizador
        // vive en el servicio y debe sobrevivir a que se cierre esta pantalla.
        if (showSleepTimerSheet) {
            SleepTimerSheet(
                onDismiss = { showSleepTimerSheet = false },
                onStartTimer = { durationMs ->
                    SleepTimerBus.dispatch(SleepTimerBus.COMMAND_START_TIMER, durationMs)
                    showSleepTimerSheet = false
                },
                onCancelTimer = {
                    SleepTimerBus.dispatch(SleepTimerBus.COMMAND_CANCEL_TIMER, 0L)
                    showSleepTimerSheet = false
                }
            )
        }
        } // Overlay capture excludes the separate Dialog windows.
        } // Player panels follow the immersive player, independently of the app theme.
    } // Box raíz
    } // All player menus sample the same live foreground scene.
    } // Immersive palette includes lyrics, queue and every player selector.
} // NowPlayingExpandedScreen
