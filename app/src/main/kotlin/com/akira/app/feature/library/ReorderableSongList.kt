// SPDX-License-Identifier: GPL-3.0-only
// Page QueueScreen bb28993: Calvin reorder state, local drag copy, handle and swipe actions.
package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.akira.app.R
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun ReorderableSongList(
    songs: List<Song>, onClick: (Song) -> Unit, onRemove: (Song) -> Unit,
    onMove: (Int, Int) -> Unit, modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(), closeSignal: Int = 0,
    currentEntryId: String? = null, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onNext: ((Song) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val localActions = LocalSongSwipeActions.current
    val currentRemove by rememberUpdatedState(onRemove)
    var localRows by remember { mutableStateOf<List<Song>?>(null) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var awaitingCommit by remember { mutableStateOf(false) }
    val rows = localRows ?: songs
    val currentIndex = songs.indexOfFirst { it.queueEntryId == currentEntryId }
    val ink = if (onNext != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    LaunchedEffect(songs) { if (draggingKey == null) { localRows = null; awaitingCommit = false } }
    LaunchedEffect(awaitingCommit) { if (awaitingCommit) { delay(800); localRows = null; awaitingCommit = false } }
    val reorder = rememberReorderableLazyListState(state,
        scrollThresholdPadding = PaddingValues(bottom = bottomPadding)) { from, to ->
        val current = localRows ?: return@rememberReorderableLazyListState
        val fromIndex = current.indexOfFirst { it.queueEntryId == from.key }
        val toIndex = current.indexOfFirst { it.queueEntryId == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            localRows = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(bottom = bottomPadding), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
        items(rows, key = { it.queueEntryId }) { song ->
            val current = song.queueEntryId == currentEntryId
            val reorderable = currentEntryId == null || songs.indexOfFirst { it.queueEntryId == song.queueEntryId } > currentIndex
            ReorderableItem(reorder, key = song.queueEntryId, enabled = reorderable) { isDragging ->
                SwipeActionsBox(enabled = draggingKey == null && !current, ink = ink, dismissStart = true,
                    start = { SwipeAction(R.drawable.ic_action_remove, SwipeRemoveColor,
                        if (onNext != null) localizedText("Remove from queue", "Quitar de la cola") else localizedText("Remove from playlist", "Quitar de la playlist")) { currentRemove(song) } },
                    end = { SwipeAction(if (onNext != null) R.drawable.ic_play_next else R.drawable.ic_add_to_queue,
                        SwipeQueueColor, if (onNext != null) strings.playNext else strings.addToQueue) {
                        if (onNext != null) onNext(song) else localActions?.queue?.invoke(song)
                    } }
                ) {
                    QueueSongItemRow(song, isDragging, { onClick(song) }, isCurrent = current, ink = ink,
                        handle = if (!reorderable) null else { {
                            Box(Modifier.size(44.dp).draggableHandle(
                                onDragStarted = {
                                    localRows = songs; draggingKey = song.queueEntryId
                                    if (com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraReorder", "start")
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    val from = songs.indexOfFirst { it.queueEntryId == song.queueEntryId }
                                    val to = localRows?.indexOfFirst { it.queueEntryId == song.queueEntryId } ?: -1
                                    draggingKey = null
                                    if (from >= 0 && to >= 0 && from != to) { onMove(from, to); awaitingCommit = true
                                        if (com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraReorder", "commit from=$from to=$to") }
                                    else localRows = null
                                }), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_queue_drag_handle), strings.library.reorder,
                                    Modifier.size(22.dp), tint = ink.copy(alpha = if (isDragging) .9f else .5f))
                            }
                        } })
                }
            }
        }
    }
}

internal object ReorderGeometry {
    // Direction must use raw positions: clamping first masks reversals outside the viewport.
    fun dragDelta(previousRawY: Float, currentRawY: Float): Float = currentRawY - previousRawY
    // Numeric viewport positions remain stable while the other rows animate.
    fun targetIndex(center: Float, firstIndex: Int, firstOffset: Int, stride: Float, count: Int): Int =
        (firstIndex + kotlin.math.floor((center + firstOffset) / stride).toInt()).coerceIn(0, (count - 1).coerceAtLeast(0))

    fun floatingTop(finger: Float, grab: Float, height: Float, viewport: Float): Float =
        (finger - grab).coerceIn(0f, (viewport - height).coerceAtLeast(0f))

    fun edgeScroll(finger: Float, direction: Float, top: Float, bottom: Float, edge: Float): Float = when {
        direction < 0 && finger < top + edge -> -14f * ((top + edge - finger) / edge).coerceIn(0f, 1f)
        direction > 0 && finger > bottom - edge -> 14f * ((finger - bottom + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}
