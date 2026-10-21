package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.akira.app.core.design.components.isReduceMotionActive
import kotlin.math.sin

/** Animated only while the playing row is composed; respects system motion settings. */
@Composable
internal fun NowPlayingIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionActive(context) }
    val phase = if (reduceMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "playing bars")
        val animated by transition.animateFloat(0f, (Math.PI * 2).toFloat(),
            infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "bar phase")
        animated
    }
    Canvas(modifier) {
        val width = size.width / 7f
        repeat(4) { index ->
            val height = size.height * (.25f + .65f * ((sin(phase + index * 1.7f) + 1f) / 2f))
            drawRoundRect(Color.White, Offset(index * width * 2f, (size.height - height) / 2f),
                Size(width, height), CornerRadius(width / 2f))
        }
    }
}
