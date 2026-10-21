package com.akira.app.feature.player.dock

import com.akira.app.core.design.glass.*

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.akira.app.core.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal fun miniSwipeOffset(distance: Float, width: Float): Float {
    val limit=width.coerceAtLeast(1f)
    return distance.coerceIn(-limit,limit)
}

/** Retains the outgoing metadata until it clears the capsule. The next item
 * enters from the opposite edge; a rejected/cancelled swipe springs home.
 * No screenshot texture or playback command is retained after disposal. */
@Stable
internal class MiniPlayerSwipe(private val scope: CoroutineScope, song: Song) {
    var shownSong by mutableStateOf(song)
        private set
    private var latestSong by mutableStateOf(song)
    var offset by mutableFloatStateOf(0f)
        private set
    var width=1f
    var reduced=false
    private var dragging=false
    private var settling: Job?=null
    private var startOffset=0f

    fun update(song: Song) {
        latestSong=song
        if(!dragging && settling?.isActive!=true)shownSong=song
    }
    fun begin() {
        settling?.cancel();settling=null
        shownSong=latestSong
        dragging=true;startOffset=offset
    }
    fun drag(distance: Float) {offset=miniSwipeOffset(startOffset+distance,width)}
    fun release(direction: Int, commit: () -> Unit = {}) {
        dragging=false
        settling?.cancel()
        settling=scope.launch {
            if(direction!=0) {
                val outgoing=shownSong.queueEntryId
                commit()
                if(!reduced)animate(initialValue=offset,targetValue=direction*width,
                    animationSpec=tween(130,easing=FastOutLinearInEasing)) {value,_ -> offset=value}
                val incoming=withTimeoutOrNull(600) {snapshotFlow {latestSong}.first {it.queueEntryId!=outgoing}}
                if(incoming!=null) {
                    shownSong=incoming
                    offset=if(reduced)0f else -direction*width
                }
            }
            if(reduced)offset=0f
            else animate(initialValue=offset,targetValue=0f,
                animationSpec=spring(dampingRatio=.82f,stiffness=520f)) {value,_ -> offset=value}
            shownSong=latestSong
            offset=0f
        }
    }
    fun dispose() {settling?.cancel();settling=null;dragging=false;offset=0f}
}
