package com.akira.app.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.akira.app.core.media.PlaybackBeatLights
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp

@Composable
internal fun PlayerRhythmLighting(songId: String, colors: PlayerArtworkColors, modifier: Modifier) {
    val owner = LocalLifecycleOwner.current
    val bass = remember(songId) { mutableFloatStateOf(0f) }
    val highs = remember(songId) { mutableFloatStateOf(0f) }
    val kick = remember(songId) { mutableFloatStateOf(0f) }
    val accent = remember(songId) { mutableFloatStateOf(0f) }
    val sweep = remember(songId) { mutableFloatStateOf(0f) }
    LaunchedEffect(owner, songId) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            bass.floatValue = 0f
            highs.floatValue = 0f
            kick.floatValue = 0f
            accent.floatValue = 0f
            coroutineScope {
                var bassTarget = 0f
                var highTarget = 0f
                var kickTarget = 0f
                var accentTarget = 0f
                var direction = 1f
                var lastAudio = 0L
                launch {
                    PlaybackBeatLights.frames.collect { frame ->
                        lastAudio = System.nanoTime()
                        bassTarget = frame.bass
                        highTarget = frame.highs
                        if (frame.kick > 0f) kickTarget = .48f + .52f * frame.kick
                        if (frame.accent > 0f) {
                            accentTarget = .36f + .64f * frame.accent
                            direction = -direction
                        }
                    }
                }
                var previous = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val dt = if (previous == 0L) 1f / 60f else ((now - previous) / 1e9f).coerceIn(0f, .05f)
                        previous = now
                        if (System.nanoTime() - lastAudio > 150_000_000L) {
                            bassTarget = 0f
                            highTarget = 0f
                        }

                        fun approach(value: Float, target: Float, speed: Float): Float {
                            val next = target + (value - target) * exp(-dt * speed)
                            return if (next < .001f && target == 0f) 0f else next
                        }
                        bass.floatValue = approach(bass.floatValue, bassTarget, if (bassTarget > bass.floatValue) 23f else 8f)
                        highs.floatValue = approach(highs.floatValue, highTarget, if (highTarget > highs.floatValue) 20f else 7f)
                        kick.floatValue = approach(kick.floatValue, kickTarget, 46f)
                        accent.floatValue = approach(accent.floatValue, accentTarget, 31f)
                        sweep.floatValue += (direction - sweep.floatValue) * (1f - exp(-dt * 6f))
                        kickTarget *= exp(-dt * 6f)
                        accentTarget *= exp(-dt * 7.5f)
                    }
                }
            }
        }
    }
    Box(modifier.drawWithCache {
        val warm = lerp(colors.primary, Color.White, .43f)
        val cool = lerp(colors.accents.getOrNull(1) ?: colors.primary, Color.White, .31f)
        val floorLight = Brush.radialGradient(
            0f to warm.copy(alpha = .36f), .48f to warm.copy(alpha = .14f), 1f to warm.copy(alpha = 0f),
            center = Offset(size.width * .52f, size.height * .77f),
            radius = (size.width * .77f).coerceAtLeast(1f),
        )
        val sweepLight = Brush.radialGradient(
            0f to cool.copy(alpha = .24f), .53f to cool.copy(alpha = .10f), 1f to cool.copy(alpha = 0f),
            center = Offset(size.width * .51f, size.height * .69f),
            radius = (size.width * .62f).coerceAtLeast(1f),
        )
        onDrawBehind {
            val impact = kick.floatValue
            val wash = (bass.floatValue * .32f + impact * .82f).coerceAtMost(1f)
            val edge = (highs.floatValue * .24f + accent.floatValue * .79f).coerceAtMost(1f)
            if (wash > .001f) {
                drawShiftedPlayerLight(floorLight, Offset(0f, -size.height * .045f * impact), wash)
            }
            if (edge > .001f) {
                drawShiftedPlayerLight(sweepLight, Offset(size.width * .26f * sweep.floatValue, 0f), edge)
            }
        }
    })
}

/** Shift the light's sampling origin while leaving its paint area on the viewport. */
internal fun DrawScope.drawShiftedPlayerLight(brush: Brush, offset: Offset, alpha: Float) {
    translate(left = offset.x, top = offset.y) {
        drawRect(brush, topLeft = -offset, size = size, alpha = alpha)
    }
}
