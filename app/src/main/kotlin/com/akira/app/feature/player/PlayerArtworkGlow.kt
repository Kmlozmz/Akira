package com.akira.app.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin

/** Fluid Mesh Warp overlay reactive to audio beats and live animated artwork motion. */
@Composable
internal fun PlayerArtworkGlow(
    colors: PlayerArtworkColors,
    beatState: State<PlaybackBeatSnapshot>,
    liveArtwork: LiveArtworkSample?,
    moving: Boolean,
    intensity: Float,
    opacity: () -> Float,
    modifier: Modifier,
) {
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val context = LocalContext.current.applicationContext
    val shader = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
            AmbientArtworkShader(context.assets.open("shaders/player_ambient.agsl").bufferedReader().use { it.readText() })
        }.getOrNull() else null
    }
    val phase = rememberAmbientPhase(moving, radiansPerSecond = .58f)
    val (base, palette) = rememberFluidPalette(colors, liveArtwork)
    val currentBeat by rememberUpdatedState(beatState.value)
    val artMotion = animateFloatAsState(
        if (!reduced && moving) liveArtwork?.motionEnergy ?: 0f else 0f,
        tween(if (reduced) 0 else 220),
        label = "glowArtMotion",
    )
    val shiftX = animateFloatAsState(
        if (!reduced && moving) liveArtwork?.shiftX ?: 0f else 0f,
        tween(if (reduced) 0 else 260),
        label = "glowArtShiftX",
    )
    val shiftY = animateFloatAsState(
        if (!reduced && moving) liveArtwork?.shiftY ?: 0f else 0f,
        tween(if (reduced) 0 else 260),
        label = "glowArtShiftY",
    )
    val scaledIntensity = (.55f + .65f * intensity.coerceIn(.2f, 1f))
    val overlayOpacity = (.36f + .26f * intensity.coerceIn(.2f, 1f))

    Box(modifier.graphicsLayer().drawWithCache {
        onDrawBehind {
            val reveal = opacity().coerceIn(0f, 1f)
            if (reveal <= 0f || size.isEmpty()) return@onDrawBehind
            val beat = if (reduced) PlaybackBeatSnapshot.Zero else currentBeat
            val t = phase.value + beat.phaseBoost
            if (shader != null && android.os.Build.VERSION.SDK_INT >= 33) {
                shader.update(
                    size = size,
                    phase = t,
                    base = base.value,
                    colors = palette,
                    beat = beat,
                    artMotion = artMotion.value,
                    artShiftX = shiftX.value,
                    artShiftY = shiftY.value,
                    fadeStartY = .16f,
                    fadeEndY = .56f,
                    intensity = scaledIntensity,
                    overlayAlpha = overlayOpacity * reveal,
                )
                drawRect(shader.brush)
            } else {
                val layerAlpha = overlayOpacity * reveal
                palette.forEachIndexed { i, color ->
                    val angle = t * (.68f + i * .14f) + i * 1.27f
                    val energy = if (i % 2 == 0) beat.bass + beat.kick * .7f else beat.highs + beat.accent * .7f
                    val center = Offset(
                        size.width * (.48f + .36f * cos(angle) + shiftX.value * .14f),
                        size.height * (.62f + .24f * sin(angle * .91f + i) + shiftY.value * .10f),
                    )
                    val radius = size.maxDimension * (.28f + (i % 2) * .09f + energy * .15f * scaledIntensity)
                    val blobAlpha = (.26f + energy * .24f * scaledIntensity).coerceIn(0f, .56f) * layerAlpha
                    drawRect(
                        Brush.radialGradient(listOf(color.value.copy(alpha = blobAlpha), Color.Transparent), center, radius),
                    )
                }
            }
        }
    })
}
