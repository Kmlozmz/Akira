package com.akira.app.feature.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.brand.AkiraBrand
import kotlin.math.*

/** Akira is the subject: a spring signature, musical notes and a moving staff, without a mock player. */
@Composable internal fun OnboardingBrandStage(finale: Boolean = false) {
    val phase = onboardingPhase(if (finale) 4_800 else 6_000)
    val reduced = setupReducedMotion()
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) entrance.snapTo(1f) else entrance.animateTo(1f, spring(.64f, 135f))
    }
    val accent = AkiraBrand.accent
    val ink = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth().height(if (finale) 294.dp else 310.dp)
        .clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = phase.value
            val glow = Offset(center.x, center.y * .95f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .16f), Color.Transparent), glow, size.minDimension * .48f),
                size.minDimension * .48f, glow)
            repeat(5) { line ->
                val path = Path()
                repeat(81) { i ->
                    val x = i / 80f
                    val y = size.height * (.33f + line * .045f + sin(x * 6.283185f + t) * .065f)
                    if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
                }
                drawPath(path, accent.copy(alpha = .12f), style = Stroke(1.2.dp.toPx()))
            }
            repeat(29) { i ->
                val x = size.width * (i + .5f) / 29
                val pulse = .25f + .75f * abs(sin(t * 2 + i * .47f))
                val h = size.height * (.035f + .09f * pulse) * sin((i + 1) * PI / 30).toFloat()
                drawLine(accent.copy(alpha = .3f + pulse * .35f), Offset(x, size.height * .88f - h / 2),
                    Offset(x, size.height * .88f + h / 2), 3.dp.toPx(), StrokeCap.Round)
            }
            if (finale) repeat(14) { i ->
                val f = i / 14f
                val drift = (t / 6.283185f + f) % 1f
                val x = size.width * (.08f + .84f * ((i * .618f) % 1f))
                val y = size.height * (.82f - drift * .72f)
                val a = sin(drift * PI).toFloat() * .65f
                drawLine(if (i % 2 == 0) accent.copy(alpha = a) else ink.copy(alpha = a * .5f),
                    Offset(x - 2.dp.toPx(), y + 3.dp.toPx()), Offset(x + 2.dp.toPx(), y - 3.dp.toPx()),
                    2.dp.toPx(), StrokeCap.Round)
            }
        }
        val positions = listOf(.08f to .16f, .80f to .08f, .13f to .59f, .83f to .60f)
        positions.forEachIndexed { i, (x, y) ->
            Icon(if (i % 2 == 0) Icons.Rounded.MusicNote else Icons.Rounded.QueueMusic, null,
                Modifier.align(Alignment.TopStart).offset(maxWidth * x, maxHeight * y).size(if (i % 2 == 0) 34.dp else 28.dp)
                    .graphicsLayer {
                        translationY = sin(phase.value + i * 1.6f) * 12.dp.toPx()
                        translationX = cos(phase.value + i) * 5.dp.toPx()
                        rotationZ = sin(phase.value + i) * 12f
                        alpha = entrance.value.coerceIn(0f, 1f) * .8f
                    }, tint = if (i % 2 == 0) accent else ink.copy(alpha = .6f))
        }
        SetupEclipse(Modifier.size(218.dp).offset(y = (-14).dp).graphicsLayer {
            val pop = .55f + .45f * entrance.value
            scaleX = pop * (1f + sin(phase.value * 2) * .055f)
            scaleY = pop * (1f + sin(phase.value * 2) * .055f)
            rotationZ = (1 - entrance.value) * -18f + sin(phase.value) * 4f
            translationY = sin(phase.value) * 7.dp.toPx()
            alpha = entrance.value.coerceIn(0f, 1f)
        }, accent)
    }
}
