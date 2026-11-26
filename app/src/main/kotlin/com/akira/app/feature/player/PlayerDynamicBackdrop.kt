package com.akira.app.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.akira.app.core.media.PlaybackBeatLights
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

@Immutable
internal data class PlaybackBeatSnapshot(
    val bass: Float = 0f,
    val highs: Float = 0f,
    val kick: Float = 0f,
    val accent: Float = 0f,
    val phaseBoost: Float = 0f,
) {
    companion object {
        val Zero = PlaybackBeatSnapshot()
    }
}

internal class BeatEnvelopeTracker {
    var bass: Float = 0f
        private set
    var highs: Float = 0f
        private set
    var kick: Float = 0f
        private set
    var accent: Float = 0f
        private set
    var phaseBoost: Float = 0f
        private set

    private var bassTarget: Float = 0f
    private var highTarget: Float = 0f
    private var kickTarget: Float = 0f
    private var accentTarget: Float = 0f
    private var lastAudioNanos: Long = 0L

    fun onFrame(frameBass: Float, frameHighs: Float, frameKick: Float, frameAccent: Float, nowNanos: Long) {
        lastAudioNanos = nowNanos
        bassTarget = frameBass.coerceIn(0f, 1f)
        highTarget = frameHighs.coerceIn(0f, 1f)
        if (frameKick > 0f) kickTarget = maxOf(kickTarget, .42f + .58f * frameKick.coerceIn(0f, 1f))
        if (frameAccent > 0f) accentTarget = maxOf(accentTarget, .34f + .66f * frameAccent.coerceIn(0f, 1f))
    }

    fun step(dtSeconds: Float, nowNanos: Long, audioTimeoutNanos: Long = 180_000_000L): PlaybackBeatSnapshot {
        val dt = dtSeconds.coerceIn(0f, .05f)
        if (lastAudioNanos != 0L && nowNanos - lastAudioNanos > audioTimeoutNanos) {
            bassTarget = 0f
            highTarget = 0f
        }
        bass = approach(bass, bassTarget, if (bassTarget > bass) 22f else 7f, dt)
        highs = approach(highs, highTarget, if (highTarget > highs) 19f else 6.5f, dt)
        kick = approach(kick, kickTarget, 44f, dt)
        accent = approach(accent, accentTarget, 30f, dt)
        kickTarget *= exp(-dt * 6.5f)
        accentTarget *= exp(-dt * 7.5f)
        if (kickTarget < .002f) kickTarget = 0f
        if (accentTarget < .002f) accentTarget = 0f
        phaseBoost += dt * (bass * .75f + kick * 1.35f + highs * .35f)
        return PlaybackBeatSnapshot(bass, highs, kick, accent, phaseBoost)
    }

    fun reset() {
        bass = 0f; highs = 0f; kick = 0f; accent = 0f; phaseBoost = 0f
        bassTarget = 0f; highTarget = 0f; kickTarget = 0f; accentTarget = 0f
        lastAudioNanos = 0L
    }

    private fun approach(value: Float, target: Float, speed: Float, dt: Float): Float {
        val next = target + (value - target) * exp(-dt * speed)
        return if (next < .001f && target == 0f) 0f else next
    }
}

@Composable
internal fun rememberPlaybackBeatState(active: Boolean): State<PlaybackBeatSnapshot> {
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val owner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(PlaybackBeatSnapshot.Zero) }
    LaunchedEffect(owner, active, reduced) {
        if (!active || reduced) {
            state.value = PlaybackBeatSnapshot.Zero
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val tracker = BeatEnvelopeTracker()
            coroutineScope {
                launch {
                    PlaybackBeatLights.frames.collect { frame ->
                        tracker.onFrame(frame.bass, frame.highs, frame.kick, frame.accent, System.nanoTime())
                    }
                }
                var previous = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val dt = if (previous == 0L) 1f / 60f else (now - previous) / 1_000_000_000f
                        previous = now
                        state.value = tracker.step(dt, System.nanoTime())
                    }
                }
            }
        }
    }
    return state
}

/** Draws the player's ambient background from the current artwork palette. */
@Composable
internal fun rememberAmbientPhase(moving: Boolean, radiansPerSecond: Float = .52f): State<Float> {
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val owner = LocalLifecycleOwner.current
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(owner, moving, reduced, radiansPerSecond) {
        if (reduced) phase.floatValue = 0f
        if (!moving || reduced) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) withFrameNanos { now ->
                if (last != 0L) phase.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(.1f) * radiansPerSecond
                last = now
            }
        }
    }
    return phase
}

internal fun fluidRichColor(color: Color): Color {
    val base = playerArtworkBase(color)
    val maxChannel = maxOf(base.red, base.green, base.blue)
    val minChannel = minOf(base.red, base.green, base.blue)
    val cap = if (maxChannel > .56f) .56f / maxChannel else 1f
    val r = base.red * cap
    val g = base.green * cap
    val b = base.blue * cap
    val luma = .2126f * r + .7152f * g + .0722f * b
    val satBoost = if (maxChannel - minChannel > .08f) 1.20f else 1f
    return Color(
        (luma + (r - luma) * satBoost).coerceIn(0f, .68f),
        (luma + (g - luma) * satBoost).coerceIn(0f, .68f),
        (luma + (b - luma) * satBoost).coerceIn(0f, .68f),
        color.alpha,
    )
}

@Composable
internal fun rememberFluidPalette(
    colors: PlayerArtworkColors,
    liveSample: LiveArtworkSample? = null,
    durationMs: Int = 750,
    liveDurationMs: Int = 380,
): Pair<State<Color>, List<State<Color>>> {
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val effectiveDuration = if (reduced) 0 else if (liveSample != null) liveDurationMs else durationMs
    val sourcePrimary = liveSample?.colors?.firstOrNull() ?: colors.primary
    val richPrimary = fluidRichColor(sourcePrimary)
    val base = animateColorAsState(richPrimary, tween(effectiveDuration), label = "ambientBase")
    val palette = (0..4).map { i ->
        val staticList = colors.accents.ifEmpty { listOf(colors.primary) }
        val liveList = liveSample?.colors
        val source = liveList?.getOrNull(i % liveList.size.coerceAtLeast(1))
            ?: staticList[i % staticList.size]
        val target = lerp(richPrimary, fluidRichColor(source), .80f)
        animateColorAsState(target, tween(effectiveDuration), label = "ambient$i")
    }
    return base to palette
}

@Composable
internal fun Modifier.playerDynamicBackdrop(
    colors: PlayerArtworkColors,
    moving: Boolean,
    opacity: () -> Float = { 1f },
    subdued: Boolean = false,
    beatState: State<PlaybackBeatSnapshot>? = null,
    liveArtwork: LiveArtworkSample? = null,
    intensity: Float = 1f,
): Modifier {
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val shader = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
            AmbientArtworkShader(context.assets.open("shaders/player_ambient.agsl").bufferedReader().use { it.readText() })
        }.getOrNull() else null
    }
    val phase = rememberAmbientPhase(moving)
    val subduedMix = androidx.compose.animation.core.animateFloatAsState(
        if (subdued) 1f else 0f,
        tween(if (reduced) 0 else 270),
        label = "playerBackdropDepth",
    )
    val (base, palette) = rememberFluidPalette(colors, liveArtwork)
    val currentBeat by rememberUpdatedState(beatState?.value ?: PlaybackBeatSnapshot.Zero)
    val currentArtMotion = androidx.compose.animation.core.animateFloatAsState(
        if (!reduced && moving) liveArtwork?.motionEnergy ?: 0f else 0f,
        tween(if (reduced) 0 else 220),
        label = "artMotionEnergy",
    )
    val currentShiftX = androidx.compose.animation.core.animateFloatAsState(
        if (!reduced && moving) liveArtwork?.shiftX ?: 0f else 0f,
        tween(if (reduced) 0 else 260),
        label = "artShiftX",
    )
    val currentShiftY = androidx.compose.animation.core.animateFloatAsState(
        if (!reduced && moving) liveArtwork?.shiftY ?: 0f else 0f,
        tween(if (reduced) 0 else 260),
        label = "artShiftY",
    )
    return drawWithCache {
        val shade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = .03f), Color.Black.copy(alpha = .12f)))
        val depth = Brush.verticalGradient(
            0f to Color(0xFF17181E).copy(alpha = .11f),
            .45f to Color(0xFF191B24).copy(alpha = .37f),
            1f to Color(0xFF172028).copy(alpha = .82f),
        )
        onDrawBehind {
            val alpha = opacity().coerceIn(0f, 1f)
            val beat = if (reduced) PlaybackBeatSnapshot.Zero else currentBeat
            val t = phase.value + beat.phaseBoost
            if (shader != null && android.os.Build.VERSION.SDK_INT >= 33) {
                shader.update(
                    size = size,
                    phase = t,
                    base = base.value,
                    colors = palette,
                    beat = beat,
                    artMotion = currentArtMotion.value,
                    artShiftX = currentShiftX.value,
                    artShiftY = currentShiftY.value,
                    intensity = intensity,
                )
                drawRect(shader.brush, alpha = alpha)
            } else {
                drawRect(base.value, alpha = alpha)
                val boost = intensity.coerceIn(0.2f, 1.4f)
                palette.forEachIndexed { i, color ->
                    val angle = t * (.65f + i * .13f) + i * 1.27f
                    val energy = if (i % 2 == 0) beat.bass + beat.kick * .65f else beat.highs + beat.accent * .65f
                    val center = Offset(
                        size.width * (.46f + .36f * cos(angle) + currentShiftX.value * .12f),
                        size.height * (.48f + .34f * sin(angle * .91f + i) + currentShiftY.value * .08f),
                    )
                    val radius = size.maxDimension * (.40f + (i % 2) * .1f + energy * .14f * boost)
                    val blobAlpha = (.64f + energy * .24f * boost).coerceIn(0f, .92f)
                    drawRect(
                        Brush.radialGradient(listOf(color.value.copy(alpha = blobAlpha), color.value.copy(alpha = 0f)), center, radius),
                        alpha = alpha,
                    )
                }
                drawRect(shade, alpha = alpha)
            }
            if (subduedMix.value > 0f) drawRect(depth, alpha = alpha * subduedMix.value)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun useMotionArtwork(fullscreen: Boolean, lyrics: Boolean, queue: Boolean, landscape: Boolean,
    enabled: Boolean): Boolean = fullscreen && enabled && !landscape
