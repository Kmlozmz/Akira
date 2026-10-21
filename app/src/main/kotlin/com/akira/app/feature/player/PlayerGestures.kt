package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.akira.app.core.design.components.LocalAkiraAccent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal object PlayerGesturePolicy {
    fun confirmedDirection(distance: Float, width: Float, velocity: Float, fraction: Float,
        flingThreshold: Float, minimumFlingDistance: Float): Int = when {
        width <= 0f -> 0
        abs(distance) >= width * fraction -> distance.sign.toInt()
        abs(distance) >= minimumFlingDistance && abs(velocity) >= flingThreshold && distance * velocity > 0 -> velocity.sign.toInt()
        else -> 0
    }
}

/** Foundation's horizontal touch slop competes by direction with the existing vertical handler. */
@Composable
internal fun HorizontalPlayerGesture(
    fraction: Float,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    revealActions: Boolean = false,
    content: @Composable (Modifier) -> Unit
) {
    val haptics = rememberAkiraHaptics()
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val flingThreshold = with(density) { 1000.dp.toPx() }
    val minimumFlingDistance = with(density) { 24.dp.toPx() }
    val latestConfirm by rememberUpdatedState(onConfirm)
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val visualOffset = offset.value
    val nextColor = Color(0xFF234F79)
    val queueColor = Color(0xFF594177)
    Box(modifier
        .onSizeChanged { width = it.width }
        .pointerInput(width) {
            val velocity = VelocityTracker()
            var distance = 0f
            var ticked = false
            fun returnHome() {
                settle = scope.launch { offset.animateTo(0f, spring(dampingRatio = .8f, stiffness = 500f)) }
            }
            detectHorizontalDragGestures(
                onDragStart = {
                    settle?.cancel()
                    distance = 0f
                    ticked = false
                    velocity.resetTracking()
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    distance += amount
                    velocity.addPosition(change.uptimeMillis, Offset(distance, 0f))
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(distance.coerceIn(-width.toFloat(), width.toFloat())) }
                    if (revealActions && !ticked && abs(distance) >= width * fraction) { ticked = true; haptics.tick() }
                },
                onDragEnd = {
                    val speed = runCatching { velocity.calculateVelocity().x }.getOrDefault(0f)
                    val direction = PlayerGesturePolicy.confirmedDirection(distance, width.toFloat(), speed,
                        fraction, flingThreshold, minimumFlingDistance)
                    if (direction != 0) { if (!ticked) haptics.tick(); latestConfirm(direction) }
                    returnHome()
                },
                onDragCancel = { returnHome() }
            )
        }) {
        if (revealActions && abs(visualOffset) > 1f) {
            val right = visualOffset > 0
            // Draw only in the revealed strip: a transparent row must not show the panel through its text.
            Box(Modifier.align(if (right) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight().width(with(density) { abs(visualOffset).toDp() })
                .clip(RoundedCornerShape(12.dp)).background(if (right) nextColor else queueColor).clip(androidx.compose.ui.graphics.RectangleShape), contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd) {
                Column(Modifier.requiredWidth(112.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(if (right) AkiraIcons.FastForward else AkiraIcons.Queue, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(if (right) strings.playNext else strings.addToQueue, color = Color.White,
                        fontFamily = AkiraFontFamily, fontSize = 11.sp, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        // Translation preserves every child's measured size throughout the gesture.
        content(Modifier.graphicsLayer { translationX = visualOffset })
    }
}
