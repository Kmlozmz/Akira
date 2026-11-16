package com.akira.app.feature.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import com.akira.app.core.design.brand.AkiraBrand
import kotlin.math.hypot

/** The circle starts near the action and covers every corner at completion. */
internal fun onboardingRevealRadius(width: Float, height: Float): Float = hypot(width / 2, height * .82f)

internal class OnboardingRevealShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (progress >= 1f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val radius = onboardingRevealRadius(size.width, size.height) * progress.coerceIn(0f, 1f)
        val center = Offset(size.width / 2, size.height * .82f)
        return Outline.Generic(Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) })
    }
}

/** Read the clock only while drawing: the library must not recompose for every reveal frame. */
internal fun Modifier.onboardingReveal(progress: () -> Float): Modifier = drawWithCache {
    val path = Path()
    val radius = onboardingRevealRadius(size.width, size.height)
    val origin = Offset(size.width / 2, size.height * .82f)
    onDrawWithContent {
        val p = progress().coerceIn(0f, 1f)
        if (p >= 1f) drawContent() else {
            val r = radius * p
            path.reset()
            path.addOval(Rect(origin.x - r, origin.y - r, origin.x + r, origin.y + r))
            clipPath(path) { this@onDrawWithContent.drawContent() }
        }
    }
}

internal fun onboardingEntryTransform(reduced: Boolean): ContentTransform = if (reduced) {
    EnterTransition.None togetherWith ExitTransition.None
} else {
    (fadeIn(tween(600, delayMillis = 60)) + scaleIn(tween(700, easing = FastOutSlowInEasing), initialScale = .98f)) togetherWith
        (fadeOut(tween(350)) + scaleOut(tween(500, easing = FastOutSlowInEasing), targetScale = 1.035f))
}

/** A visible accent wipe carries the signature into the destination reveal. */
@Composable internal fun OnboardingDeparture(progress: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val p = progress().coerceIn(0f, 1f)
        val origin = Offset(center.x, size.height * .82f)
        drawCircle(AkiraBrand.accent, onboardingRevealRadius(size.width, size.height) * p, origin)
        val presence = ((p - .28f) / .5f).coerceIn(0f, 1f)
        if (presence > 0f) {
            val mark = size.minDimension * (.30f + .14f * presence)
            translate(center.x - mark / 2, center.y - mark / 2) {
                scale(mark / 128f, pivot = Offset.Zero) { drawPath(AkiraBrand.symbol, Color.White, alpha = presence) }
            }
        }
    }
}

/** Matches the departure's last frame while the actual app expands over it. */
@Composable internal fun OnboardingArrivalBackdrop(progress: () -> Float = { 0f }) {
    Canvas(Modifier.fillMaxSize()) {
        if (progress() >= 1f) return@Canvas
        drawRect(AkiraBrand.accent)
        val mark = size.minDimension * .44f
        translate(center.x - mark / 2, center.y - mark / 2) {
            scale(mark / 128f, pivot = Offset.Zero) { drawPath(AkiraBrand.symbol, Color.White) }
        }
    }
}
