package com.akira.app.core.design.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Cache the periodic brightness curve instead of computing sine for each star.
private val FastSinTable = FloatArray(256) { i ->
    ((sin(i * 2.0 * PI / 256.0) + 1.0) * 0.5).toFloat()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun fastSineWave(angle: Float): Float {
    val idx = (angle * (256f / 6.2831853f)).toInt() and 255
    return FastSinTable[idx]
}

// 1. Natural Star with depth, pulse, and color warmth
private data class CosmicStar(
    val xNorm: Float,
    val yNorm: Float,
    val radiusDp: Float,
    val color: Color,
    val minAlpha: Float,
    val maxAlpha: Float,
    val speed: Float,
    val phase: Float,
    val hasSpike: Boolean
)

// 2. Drifting Micro-motes / Cosmic Stardust
private data class CosmicDustMote(
    val startXRatio: Float,
    val startYRatio: Float,
    val radiusDp: Float,
    val speedY: Float,
    val swaySpeed: Float,
    val swayAmp: Float,
    val alpha: Float
)

// 3. Elegant Shooting Star (Meteor)
private class ShootingStar {
    var active = false
    var startX = 0f
    var startY = 0f
    var dirX = 1f
    var dirY = 0.65f
    var lengthDp = 80f
    var progress = 0f
    var speed = 0.038f
}

/**
 * Animated starfield with cached geometry and draw-time brightness updates.
 */
@Composable
fun CosmicStarfieldBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    ink: Color? = null
) {
    // 1. Generate Starfield (100 stars with realistic depth tiers)
    val stars = remember(ink) {
        val rng = Random(101)
        List(100) {
            val isProminent = rng.nextFloat() < 0.14f
            val isMid = rng.nextFloat() < 0.38f

            val radiusDp = when {
                isProminent -> 1.4f + rng.nextFloat() * 0.9f
                isMid -> 0.85f + rng.nextFloat() * 0.5f
                else -> 0.45f + rng.nextFloat() * 0.4f
            }

            val starColor = when {
                rng.nextFloat() < 0.20f -> Color(0xFFE2F1FF) // Cool Ice Blue
                rng.nextFloat() < 0.15f -> Color(0xFFFFFBEB) // Warm Pearl
                else -> Color(0xFFFFFFFF)                    // Diamond White
            }

            CosmicStar(
                xNorm = rng.nextFloat(),
                yNorm = rng.nextFloat(),
                radiusDp = radiusDp,
                color = ink ?: starColor,
                minAlpha = if (isProminent) 0.30f else 0.08f + rng.nextFloat() * 0.15f,
                maxAlpha = if (isProminent) 0.95f else 0.55f + rng.nextFloat() * 0.35f,
                speed = 0.0012f + rng.nextFloat() * 0.0028f,
                phase = rng.nextFloat() * 6.28f,
                hasSpike = isProminent && rng.nextFloat() < 0.50f
            )
        }
    }

    // 2. Drifting Cosmic Motes
    val motes = remember {
        val rng = Random(404)
        List(16) {
            CosmicDustMote(
                startXRatio = rng.nextFloat(),
                startYRatio = rng.nextFloat(),
                radiusDp = 0.6f + rng.nextFloat() * 0.8f,
                speedY = 0.00003f + rng.nextFloat() * 0.00006f,
                swaySpeed = 0.0008f + rng.nextFloat() * 0.0014f,
                swayAmp = 8f + rng.nextFloat() * 14f,
                alpha = 0.18f + rng.nextFloat() * 0.25f
            )
        }
    }

    // 3. Dynamic Shooting Stars
    val meteor1 = remember { ShootingStar() }
    val meteor2 = remember { ShootingStar() }

    val timeMillis = remember { mutableLongStateOf(0L) }

    LaunchedEffect(animated) {
        if (!animated) { timeMillis.longValue = 0L; meteor1.active = false; meteor2.active = false; return@LaunchedEffect }
        val startNanos = System.nanoTime()
        var lastElapsed = 0L
        var nextMeteor1 = 2000L
        var nextMeteor2 = 6000L

        while (isActive) {
            androidx.compose.runtime.withFrameMillis {
                val elapsed = (System.nanoTime() - startNanos) / 1_000_000L
                val frameScale = ((elapsed - lastElapsed).coerceIn(0L, 40L) / 16.666f)
                lastElapsed = elapsed
                timeMillis.longValue = elapsed

                // Update Meteor 1
                if (meteor1.active) {
                    meteor1.progress += meteor1.speed * frameScale
                    if (meteor1.progress >= 1f) {
                        meteor1.active = false
                        nextMeteor1 = elapsed + Random.nextLong(3500, 6500)
                    }
                } else if (elapsed >= nextMeteor1) {
                    meteor1.active = true
                    meteor1.progress = 0f
                    meteor1.startX = Random.nextFloat() * 0.60f
                    meteor1.startY = Random.nextFloat() * 0.30f
                    meteor1.dirX = 1f + Random.nextFloat() * 0.30f
                    meteor1.dirY = 0.55f + Random.nextFloat() * 0.35f
                    meteor1.lengthDp = 90f + Random.nextFloat() * 50f
                    meteor1.speed = 0.034f + Random.nextFloat() * 0.012f
                }

                // Update Meteor 2
                if (meteor2.active) {
                    meteor2.progress += meteor2.speed * frameScale
                    if (meteor2.progress >= 1f) {
                        meteor2.active = false
                        nextMeteor2 = elapsed + Random.nextLong(5000, 9000)
                    }
                } else if (elapsed >= nextMeteor2) {
                    meteor2.active = true
                    meteor2.progress = 0f
                    meteor2.startX = 0.40f + Random.nextFloat() * 0.55f
                    meteor2.startY = Random.nextFloat() * 0.35f
                    meteor2.dirX = -(0.9f + Random.nextFloat() * 0.30f)
                    meteor2.dirY = 0.60f + Random.nextFloat() * 0.35f
                    meteor2.lengthDp = 80f + Random.nextFloat() * 45f
                    meteor2.speed = 0.030f + Random.nextFloat() * 0.010f
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
    ) {
        val w = size.width
        val h = size.height
        val t = timeMillis.longValue

        // A. Draw Twinkling Stars (Zero trigonometry: uses FastSinTable)
        val visibleStars = (w * h / (density * density) * 100f / 350000f).toInt().coerceIn(8, stars.size)
        for (i in 0 until visibleStars) {
            val star = stars[i]
            val cx = star.xNorm * w
            val cy = star.yNorm * h
            val wave = fastSineWave(t * star.speed + star.phase)
            val currentAlpha = star.minAlpha + (star.maxAlpha - star.minAlpha) * wave
            val rPx = star.radiusDp * density

            if (currentAlpha > 0.05f) {
                // Star Core
                drawCircle(
                    color = star.color.copy(alpha = currentAlpha),
                    radius = rPx,
                    center = Offset(cx, cy)
                )

                // Soft Lens Flare Cross Spikes on Prominent Stars
                if (star.hasSpike && wave > 0.50f) {
                    val spikeLen = rPx * 4.0f * wave
                    val spikeAlpha = currentAlpha * 0.35f
                    val spikeColor = star.color.copy(alpha = spikeAlpha)

                    drawLine(
                        color = spikeColor,
                        start = Offset(cx - spikeLen, cy),
                        end = Offset(cx + spikeLen, cy),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = spikeColor,
                        start = Offset(cx, cy - spikeLen),
                        end = Offset(cx, cy + spikeLen),
                        strokeWidth = 1f
                    )
                }
            }
        }

        // B. Draw Drifting Cosmic Motes
        for (i in motes.indices) {
            val mote = motes[i]
            val progress = ((t * mote.speedY) % 1.0f).toFloat()
            val cy = (mote.startYRatio * h - progress * h + h) % h
            val cx = (mote.startXRatio * w + fastSineWave(t * mote.swaySpeed) * mote.swayAmp)

            drawCircle(
                color = (ink ?: Color.White).copy(alpha = mote.alpha),
                radius = mote.radiusDp * density,
                center = Offset(cx, cy)
            )
        }

        // C. Draw Elegant Swift Shooting Stars
        if (meteor1.active) {
            drawElegantMeteor(meteor1, w, h)
        }
        if (meteor2.active) {
            drawElegantMeteor(meteor2, w, h)
        }
    }
}

/**
 * Pre-allocated color list to eliminate per-frame allocations during meteor drawing.
 */
private val MeteorColors = listOf(
    Color.Transparent,
    Color(0xFFE2F1FF),
    Color.White
)

/**
 * Renders a natural, fine, swift shooting star streak with zero object allocations.
 */
private fun DrawScope.drawElegantMeteor(
    meteor: ShootingStar,
    w: Float,
    h: Float
) {
    val travelDist = 340f * density
    val headX = (meteor.startX * w) + (meteor.dirX * travelDist * meteor.progress)
    val headY = (meteor.startY * h) + (meteor.dirY * travelDist * meteor.progress)

    val trailLen = meteor.lengthDp * density
    val tailX = headX - (meteor.dirX * trailLen)
    val tailY = headY - (meteor.dirY * trailLen)

    val lifeAlpha = fastSineWave(meteor.progress * 3.14159f)

    if (lifeAlpha > 0.05f) {
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    MeteorColors[1].copy(alpha = 0.40f * lifeAlpha),
                    MeteorColors[2].copy(alpha = 0.90f * lifeAlpha)
                ),
                start = Offset(tailX, tailY),
                end = Offset(headX, headY)
            ),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = 1.3f * density,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.95f * lifeAlpha),
            radius = 1.6f * density,
            center = Offset(headX, headY)
        )
    }
}
