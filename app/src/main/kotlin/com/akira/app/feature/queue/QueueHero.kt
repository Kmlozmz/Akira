package com.akira.app.feature.queue

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*

import com.akira.app.core.design.components.showAkiraSnackbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.akira.app.core.design.components.AkiraIconButton
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun QueueHero(
    currentSong: Song,
    queue: List<Song>,
    currentPlayingIndex: Int,
    isShuffleActive: Boolean,
    repeatState: QueueRepeatState,
    isAutoplayActive: Boolean,
    onCoverClick: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOptionsClick: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (song: Song) -> Unit,
    onClearQueue: (() -> Unit)? = null,
    onRestoreQueue: ((List<Song>, Int) -> Unit)? = null,
    onRestoreItem: ((Int, Song) -> Unit)? = null,
    onUserScroll: (Float) -> Unit = {},
    thumbModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val haptics = rememberAkiraHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentUserScroll by rememberUpdatedState(onUserScroll)
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        androidx.compose.runtime.snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, userDragging)
        }.collect { (index, offset, dragging) ->
            if (dragging && (index != previousIndex || offset != previousOffset)) {
                val forward = index > previousIndex || (index == previousIndex && offset > previousOffset)
                currentUserScroll(if (forward) 1f else -1f)
            }
            previousIndex = index
            previousOffset = offset
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var addQueueToList by remember { mutableStateOf(false) }
    var initiallyPositioned by remember { mutableStateOf(false) }
    LaunchedEffect(queue.isNotEmpty(), currentPlayingIndex) {
        if (!initiallyPositioned && queue.isNotEmpty()) {
            withFrameNanos { }
            listState.scrollToItem(currentPlayingIndex.coerceIn(queue.indices))
            initiallyPositioned = true
        }
    }
    val currentVisible by remember(currentPlayingIndex) { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == currentPlayingIndex } } }


    val upNextSongs = remember(queue, currentPlayingIndex) {
        if (queue.isNotEmpty() && currentPlayingIndex in queue.indices) {
            queue.subList((currentPlayingIndex + 1).coerceAtMost(queue.size), queue.size)
        } else {
            emptyList()
        }
    }

    var swipeCloseSignal by remember { mutableIntStateOf(0) }
    var queueTopInRoot by remember { mutableFloatStateOf(0f) }
    var listTopInRoot by remember { mutableFloatStateOf(0f) }
    Box(modifier = modifier.fillMaxSize()
        .onGloballyPositioned { queueTopInRoot = it.positionInRoot().y }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.y + queueTopInRoot < listTopInRoot) swipeCloseSignal++
            }
        }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = strings.nowPlaying.uppercase(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AkiraFontFamily,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .then(thumbModifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E2C))
                        .clickable {
                            haptics.tick()
                            onCoverClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentSong.albumArtUri.isNullOrBlank()) {
                        AsyncImage(
                            model = rememberPlayerArtworkRequest(currentSong.albumArtUri),
                            contentDescription = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Cover),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = AkiraIcons.Lyrics,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptics.tick()
                            onCoverClick()
                        }
                ) {
                    Text(
                        text = currentSong.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AkiraFontFamily,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSong.artist,
                        fontSize = 13.sp,
                        fontFamily = AkiraFontFamily,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                AkiraIconButton(
                    icon = if (currentSong.isFavorite) AkiraIcons.StarFilled else AkiraIcons.Star,
                    contentDescription = strings.favorite,
                    showBackground = false,
                    tint = if (currentSong.isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.85f),
                    popOnTrue = currentSong.isFavorite,
                    onClick = { onToggleFavorite(currentSong) }
                )

                AkiraIconButton(
                    icon = AkiraIcons.MoreHoriz,
                    contentDescription = strings.options,
                    showBackground = false,
                    tint = Color.White.copy(alpha = 0.85f),
                    onClick = { onOptionsClick() }
                )
            }

            TextButton(onClick = { addQueueToList = true }) {
                Icon(AkiraIcons.Queue, null); Spacer(Modifier.width(8.dp)); Text(strings.library.addToList)
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QueueTogglePill(
                    icon = AkiraIcons.Shuffle,
                    contentDescription = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.ShuffleControl),
                    isActive = isShuffleActive,
                    onClick = onToggleShuffle,
                    modifier = Modifier.weight(1f)
                )

                QueueTogglePill(
                    icon = if (repeatState == QueueRepeatState.One) AkiraIcons.RepeatOne else AkiraIcons.Repeat,
                    contentDescription = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.RepeatControl),
                    isActive = repeatState != QueueRepeatState.Off,
                    stateDescription = com.akira.app.core.design.components.localizedText(repeatState.english, repeatState.spanish),
                    onClick = onToggleRepeat,
                    modifier = Modifier.weight(1f)
                )

                QueueTogglePill(
                    icon = AkiraIcons.Autoplay,
                    contentDescription = strings.autoplay,
                    isActive = isAutoplayActive,
                    onClick = onToggleAutoplay,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.ui(com.akira.app.core.i18n.AkiraUiText.QueueTitle),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AkiraFontFamily,
                        color = Color.White
                    )
                    if (queue.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.ui(com.akira.app.core.i18n.AkiraUiText.QueuePosition, currentPlayingIndex + 1, queue.size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AkiraFontFamily,
                            color = Color.White.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!currentVisible) AkiraIconButton(
                            icon = AkiraIcons.Locate,
                            contentDescription = strings.ui(com.akira.app.core.i18n.AkiraUiText.LocateCurrentTrack),
                            showBackground = false,
                            glyphSize = 20.dp,
                            modifier = Modifier.size(40.dp),
                            onClick = {
                                haptics.click()
                                if(queue.isNotEmpty()) scope.launch {
                                    listState.animateScrollToItem(currentPlayingIndex.coerceIn(queue.indices))
                                }
                            }
                        )
                    }
                }

                if (queue.size > 1 && onClearQueue != null) {
                    TextButton(
                        onClick = {
                            haptics.confirm()
                            val snapshot = queue.toList()
                            val startIndex = currentPlayingIndex
                            onClearQueue()
                            scope.launch {
                                val result = snackbarHostState.showAkiraSnackbar(
                                    message = strings.queueCleared,
                                    actionLabel = strings.undo,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    haptics.tick()
                                    onRestoreQueue?.invoke(snapshot, startIndex)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = strings.clearQueue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AkiraFontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            }
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = AkiraIcons.Queue,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = strings.emptyQueue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AkiraFontFamily,
                            color = Color.White.copy(alpha = 0.70f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.emptyQueueSubtitle,
                            fontSize = 13.sp,
                            fontFamily = AkiraFontFamily,
                            color = Color.White.copy(alpha = 0.40f)
                        )
                    }
                }
            } else {
                ReorderableSongList(
                    songs = queue,
                    currentEntryId = queue.getOrNull(currentPlayingIndex)?.queueEntryId,
                    state = listState,
                    closeSignal = swipeCloseSignal,
                    onClick = { song ->
                        val index = queue.indices.firstOrNull { queue[it].queueEntryId == song.queueEntryId }
                        if (index != null) onPlayItem(index)
                    },
                    onRemove = { song ->
                        val index = queue.indices.firstOrNull { queue[it].queueEntryId == song.queueEntryId }
                        if (index != null && index != currentPlayingIndex) {
                            // The displayed list may still precede a previous removal.
                            // An occurrence identity survives those asynchronous edits.
                            onRemoveItem(song)
                            scope.launch {
                                if (snackbarHostState.showAkiraSnackbar(strings.songRemoved, strings.undo,
                                        duration = SnackbarDuration.Short, thumbnail = song.albumArtUri) == SnackbarResult.ActionPerformed) {
                                    onRestoreItem?.invoke(index, song)
                                }
                            }
                        }
                    },
                    onMove = { from, to -> onMoveItem(from, to) },
                    onNext = { song ->
                        val from = queue.indexOfFirst { it.queueEntryId == song.queueEntryId }
                        if (from >= 0 && from != currentPlayingIndex) {
                            onMoveItem(from, if (from < currentPlayingIndex) currentPlayingIndex else currentPlayingIndex + 1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .queueEdgeFade(listState)
                        .onGloballyPositioned { listTopInRoot = it.positionInRoot().y }
                )
            }
        }
        com.akira.app.core.design.components.AkiraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
    if (addQueueToList) AddToPlaylistSheet(queue) { addQueueToList = false }

}

/** Mask the list into the live backdrop; skip each edge at its scroll boundary. */
private fun Modifier.queueEdgeFade(state: LazyListState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val top = minOf(24.dp.toPx(), size.height * .25f)
            val bottom = minOf(32.dp.toPx(), size.height * .25f)
            val topMask = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = top.coerceAtLeast(1f))
            val bottomMask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent),
                startY = size.height - bottom, endY = size.height.coerceAtLeast(1f))
            onDrawWithContent {
                drawContent()
                if (top > 0f && state.canScrollBackward) drawRect(
                    topMask, size = Size(size.width, top), blendMode = BlendMode.DstIn)
                if (bottom > 0f && state.canScrollForward) drawRect(
                    bottomMask, topLeft = Offset(0f, size.height-bottom),
                    size = Size(size.width, bottom), blendMode = BlendMode.DstIn)
            }
        }

@Composable
internal fun QueueSongItemRow(
    song: Song,
    isDragging: Boolean,
    onClick: () -> Unit,
    handle: @Composable (() -> Unit)? = null,
    isCurrent: Boolean = false,
    ink: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
) {
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .graphicsLayer { scaleX = if (isDragging) 1.02f else 1f; scaleY = scaleX }
            .padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(ink.copy(alpha = if (isDragging) .12f else if (isCurrent) .07f else 0f))
            .clickable(enabled = !isDragging, onClick = onClick)
            .padding(start = 26.dp, end = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(song.albumArtUri, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(ink.copy(alpha = .1f)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = ink, fontFamily = AkiraFontFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.artist.isNotBlank()) Text(song.artist, color = ink.copy(alpha = .6f), fontFamily = AkiraFontFamily,
                fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(4.dp))
        if (handle != null) handle() else Spacer(Modifier.size(44.dp))
    }
}
