// SPDX-License-Identifier: GPL-3.0-only
// Source attribution: THIRD_PARTY_NOTICES.md.
package com.akira.app.feature.player.dock

import com.akira.app.core.design.glass.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
/** Distance that makes a horizontal drag an intentional track change. */
private val TRACK_SWIPE_THRESHOLD = 72.dp

/**
 * How much further a drag may go sideways than up and still be a pull. A
 * thumb swiping up from the bottom of the screen travels on an arc, and judged
 * level, 1:1, a good share of real pulls came out sideways — too short to skip
 * a track, so they did nothing at all. Steeper than about 34 degrees above
 * level is a pull.
 */
private const val PULL_BIAS = 1.5f

/**
 * The finger distance over which the bar's own rise slows to about half its
 * starting rate. It is the player that follows the finger; the bar gives a
 * little and then less, so the pull visibly takes hold of something before
 * the player has come up over it.
 */
private val PULL_LIFT_EASE = 72.dp

/** The bar's rise per pixel of finger as a pull starts. */
private const val PULL_LIFT_RATE = 0.5f

/**
 * How far the bar rises for [pulled] pixels of finger: [PULL_LIFT_RATE] of the
 * finger to begin with, slowing the further it goes — a rubber band with no
 * end to it, so the bar keeps creeping up for as long as the finger does
 * rather than meeting a ceiling. Its rate is RATE / sqrt(1 + pulled / ease).
 */
private fun pullLift(pulled: Float, ease: Float): Float =
    if (pulled <= 0f || ease <= 0f) {
        0f
    } else {
        PULL_LIFT_RATE * 2f * ease * (sqrt(1f + pulled / ease) - 1f)
    }

/**
 * Pulling the mini player up into the full player, the way the full player is
 * pulled back down into it: the player follows the finger the whole way, and
 * on release carries on to whichever end the gesture meant — open for a flick
 * upwards or for most of the way up, closed for anything less.
 *
 * Implemented by the host, which owns the player's sheet.
 */
interface MiniPlayerPull {
    /** The finger moved this far, in pixels — negative is upwards. */
    fun drag(delta: Float)

    /** The finger lifted, moving this fast in pixels a second. */
    fun release(velocity: Float)
}

/**
 * Both of the bar's drags, for both mini-player materials: sideways through
 * the queue — a left swipe advances, a right one goes back, matching the full
 * player's artwork — and up into the player through [pull].
 *
 * One detector rather than one per axis, so a gesture is decided once, by the
 * way it is heading as it passes the touch slop. Two detectors raced each
 * other to their own axis's slop, and the sideways one was asked first — see
 * [PULL_BIAS] for what that did to pulls. The tap to expand is left to the
 * clickable, and is cancelled by this consuming the drag, as it was before.
 *
 * Under a pull the bar itself rises with it, less and less ([pullLift]), so this goes
 * where the bar's own transforms start: the finger is read outside that lift,
 * so a rising bar doesn't take distance off the finger it is following.
 *
 * Track swipes wait until the finger lifts, so one long gesture never skips
 * more than one item.
 */
@Composable
internal fun Modifier.miniPlayerGestures(
    pull: MiniPlayerPull?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    /** Listening in a party whose host holds the controls. Swipes say so instead of skipping. */
    locked: Boolean = false,
    onBlocked: () -> Unit = {},
    swipe: MiniPlayerSwipe? = null,
): Modifier {
    // Playback state updates can recompose the bar while a finger is down.
    // Keep the gesture coroutine alive through those updates while still
    // dispatching to the latest controller callbacks when the drag finishes.
    val currentPull by rememberUpdatedState(pull)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentLocked by rememberUpdatedState(locked)
    val currentOnBlocked by rememberUpdatedState(onBlocked)
    val scope = rememberCoroutineScope()
    // Read at draw only, in the layer below: a pull moves the bar without
    // recomposing it.
    val lift = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    return pointerInput(Unit) {
        val trackThreshold = TRACK_SWIPE_THRESHOLD.toPx()
        val liftEase = PULL_LIFT_EASE.toPx()
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointer = down.id
            // Up to the slop: which way this is heading, or nothing at all.
            var start: PointerInputChange? = null
            var vertical = false
            while (start == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer }
                    ?: return@awaitEachGesture
                // Lifted inside the slop is a tap, and the tap is the clickable's.
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                vertical = moved.y < 0f && abs(moved.x) < -moved.y * PULL_BIAS
                // Downwards has nothing to do on the bar, and nor has a pull
                // with no player to pull: left as they always were, a drag that
                // ends inside the bar still counts as its tap.
                if (!vertical && abs(moved.x) < abs(moved.y)) return@awaitEachGesture
                if (vertical && currentPull == null) return@awaitEachGesture
                change.consume()
                start = change
            }
            val slopped = start ?: return@awaitEachGesture

            if (!vertical) {
                swipe?.begin()
                var dragged = slopped.position.x - down.position.x
                swipe?.drag(dragged)
                // Read before consuming: a consumed change reports no movement.
                var finished=false
                try {
                    finished = drag(pointer) { change ->
                        dragged += change.positionChange().x
                        swipe?.drag(dragged)
                        change.consume()
                    }
                } finally {
                    // ACTION_CANCEL may cancel the pointer coroutine rather
                    // than return false from drag. Return the content in both.
                    if(!finished)swipe?.release(0)
                }
                if (!finished) return@awaitEachGesture
                val crossed = dragged <= -trackThreshold || dragged >= trackThreshold
                when {
                    currentLocked -> {swipe?.release(0);if (crossed) currentOnBlocked()}
                    dragged <= -trackThreshold -> if(swipe!=null)swipe.release(-1) {currentOnNext()} else currentOnNext()
                    dragged >= trackThreshold -> if(swipe!=null)swipe.release(1) {currentOnPrevious()} else currentOnPrevious()
                    else -> swipe?.release(0)
                }
                return@awaitEachGesture
            }

            val target = currentPull ?: return@awaitEachGesture
            settle[0]?.cancel()
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(slopped)
            // The player gets all of it from the touch down, slop included, so
            // it starts out level with the finger. The bar starts from here,
            // from nothing: lifted by the slop as well, it jumped a few pixels
            // in one frame before it began to follow.
            target.drag(slopped.position.y - down.position.y)
            var pulled = 0f
            val finished = drag(pointer) { change ->
                val delta = change.positionChange().y
                change.consume()
                tracker.addPointerInputChange(change)
                pulled -= delta
                target.drag(delta)
                lift.floatValue = pullLift(pulled, liftEase)
            }
            target.release(
                if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f,
            )
            // Let go, the bar springs home: under the player on its way up, or
            // in full view if the pull fell short and the player went back.
            val from = lift.floatValue
            settle[0] = scope.launch {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ -> lift.floatValue = value }
            }
        }
    }.graphicsLayer { translationY = -lift.floatValue }
}
