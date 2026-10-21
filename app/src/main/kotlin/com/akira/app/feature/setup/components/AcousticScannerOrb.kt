package com.akira.app.feature.setup.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Expressive Acoustic Scanner Orb Component (Option 1).
 *
 * Implements radiating acoustic pulse waves on pure OLED black
 * with a high-contrast central soundwave icon.
 */
@Composable
fun AcousticScannerOrb(
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified
) {
    val resolvedTint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.onBackground else iconTint
    val infiniteTransition = rememberInfiniteTransition(label = "acoustic_pulse")

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_1"
    )

    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_2"
    )

    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_3"
    )

    Box(
        modifier = modifier
            .sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.16f
            val maxRadius = size.minDimension * 0.48f

            // Radiating acoustic pulse waves
            drawAcousticWave(pulse1, baseRadius, maxRadius, centerOffset, resolvedTint)
            drawAcousticWave(pulse2, baseRadius, maxRadius, centerOffset, resolvedTint)
            drawAcousticWave(pulse3, baseRadius, maxRadius, centerOffset, resolvedTint)
        }

        // Central soundwave icon
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = "Audio Scanner",
            tint = resolvedTint,
            modifier = Modifier.size(48.dp)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAcousticWave(
    progress: Float,
    baseRadius: Float,
    maxRadius: Float,
    center: Offset,
    color: Color
) {
    if (progress <= 0f || progress >= 1f) return
    val currentRadius = baseRadius + (maxRadius - baseRadius) * progress
    val alpha = (1f - progress) * 0.40f

    drawCircle(
        color = color.copy(alpha = alpha),
        radius = currentRadius,
        center = center,
        style = Stroke(width = (1.5f - 0.5f * progress) * density)
    )
}
