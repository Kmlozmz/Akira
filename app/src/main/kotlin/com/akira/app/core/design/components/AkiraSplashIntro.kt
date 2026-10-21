package com.akira.app.core.design.components

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.brand.AkiraBrand
import kotlinx.coroutines.delay

fun isReduceMotionActive(context: Context): Boolean = try {
    val resolver = context.contentResolver
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}

/** One clock drives the signature; destination composition starts after the text settles. */
@Composable
fun AkiraSplashIntro(
    isReadyToTransition: Boolean = true,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onRevealFinished: () -> Unit = {}
) {
    key(isReadyToTransition) {
        BackHandler { /* Retain priority when the destination registers its own back handler. */ }
    }
    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionActive(context) }
    val finished by rememberUpdatedState(onAnimationFinished)
    val revealed by rememberUpdatedState(onRevealFinished)
    val timeline = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val exit = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    val easeOut = remember { CubicBezierEasing(0f, 0f, 0.58f, 1f) }
    val signatureWidth = 52f + AkiraBrand.wordmarkWidth
    val initialShift = signatureWidth / 2f - 18.75f

    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) timeline.animateTo(1f, tween(720, easing = LinearEasing))
        revealed()
        if (!reduceMotion) delay(200)
        settled = true
    }
    LaunchedEffect(settled, isReadyToTransition) {
        if (settled && isReadyToTransition) {
            if (!reduceMotion) exit.animateTo(0f, tween(180))
            finished()
        }
    }

    Box(
        modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .graphicsLayer { alpha = exit.value }
            .background(AkiraBrand.accent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.offset(x = (-3.25).dp).size(signatureWidth.dp, 50.dp).graphicsLayer {
            val ms = timeline.value * 720f
            alpha = easeOut.transform((ms / 120f).coerceIn(0f, 1f))
            translationX = initialShift.dp.toPx() *
                (1f - easeOut.transform(((ms - 160f) / 560f).coerceIn(0f, 1f)))
        }) {
            val reveal = easeOut.transform(((timeline.value * 720f - 200f) / 520f).coerceIn(0f, 1f))
            // Both paths use dp coordinates; neither font scaling nor text layout can clip the name.
            scale(1.dp.toPx(), pivot = Offset.Zero) {
                translate(top = 3f) {
                    scale(44f / AkiraBrand.symbolViewport, pivot = Offset.Zero) {
                        drawPath(AkiraBrand.symbol, Color.White)
                    }
                }
                translate(left = 52f) {
                    clipRect(left = 0f, top = 0f, right = AkiraBrand.wordmarkWidth * reveal, bottom = 50f) {
                        drawPath(AkiraBrand.wordmark, Color.White)
                    }
                }
            }
        }
    }
}
