package com.akira.app.feature.lyrics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.flow.first

/** Interpolates the player's sampled position between updates for the word sweep. */
@Composable
internal fun rememberSmoothPositionMs(positionMs: Long, isPlaying: Boolean): State<Long> {
    val smooth = remember { mutableLongStateOf(positionMs) }
    val latestPos by rememberUpdatedState(positionMs)
    val latestPlaying by rememberUpdatedState(isPlaying)
    LaunchedEffect(Unit) {
        var anchorPos = latestPos
        var anchorAt = SystemClock.elapsedRealtime()
        var lastSeen = latestPos
        while (true) {
            if (!latestPlaying) {
                snapshotFlow { Pair(latestPos, latestPlaying) }.first { (pos, playing) ->
                    smooth.longValue = pos
                    playing
                }
                anchorPos = latestPos
                anchorAt = SystemClock.elapsedRealtime()
                lastSeen = latestPos
            }
            withFrameMillis { }
            val now = SystemClock.elapsedRealtime()
            val seen = latestPos
            if (seen != lastSeen) {
                lastSeen = seen
                val predicted = anchorPos + (now - anchorAt)
                if (kotlin.math.abs(seen - predicted) > 150L) {
                    anchorPos = seen
                    anchorAt = now
                }
            }
            smooth.longValue = anchorPos + (now - anchorAt)
        }
    }
    return smooth
}
