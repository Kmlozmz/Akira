/* Derived from ClearPDF, Copyright 2026 Chethan616, MIT License.
 * Akira adaptations: namespace, integration and accessibility. See assets/licenses/glass-controls/NOTICE. */
package com.akira.app.core.design.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance

import com.akira.app.core.design.controls.utils.DampedDragAnimation
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberCombinedBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.backdrop.effects.lens
import com.akira.app.core.design.backdrop.highlight.Highlight
import com.akira.app.core.design.backdrop.shadow.InnerShadow
import com.akira.app.core.design.backdrop.shadow.Shadow
import androidx.compose.foundation.shape.CircleShape as Capsule
import kotlin.math.abs

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onFinished: () -> Unit = {},
    onActiveChanged: (Boolean) -> Unit = {},
    accent: Color = Color(0xFFFA243C)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(context) { MotionSettings.load(context) }
    val isDarkMode = (MaterialTheme.colorScheme.surface.luminance() < .5f)
    val isLightTheme = !isDarkMode
    val accentColor = accent
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val updatedFinished by rememberUpdatedState(onFinished)
    val updatedActive by rememberUpdatedState(onActiveChanged)
    val updatedChange by rememberUpdatedState(onValueChange)
    val updatedValue by rememberUpdatedState(value)
    fun change(raw: Float) {
        val span = valueRange.endInclusive - valueRange.start
        val clamped = raw.coerceIn(valueRange)
        val snapped = if (steps > 0 && span > 0f)
            valueRange.start + (((clamped - valueRange.start) / span) * (steps + 1)).roundToInt() * span / (steps + 1)
            else clamped
        updatedChange(snapped.coerceIn(valueRange))
    }
    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value().coerceIn(valueRange), valueRange, steps)
                setProgress { requested -> change(requested); updatedFinished(); true }
            }
            .onKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionRight, Key.DirectionUp -> 1f
                    Key.DirectionLeft, Key.DirectionDown -> -1f
                    else -> 0f
                }
                if (event.type == KeyEventType.KeyDown && direction != 0f) {
                    val increment = (valueRange.endInclusive - valueRange.start) / (if (steps > 0) steps + 1 else 100)
                    change(updatedValue() + increment * direction); updatedFinished(); true
                } else false
            }.focusable(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(1)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val currentValue = value().coerceIn(valueRange.start, valueRange.endInclusive)
        val onValueChangeState = rememberUpdatedState<(Float) -> Unit>(::change)
        var didDrag by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }

        val dampedDragAnimation = remember(
            animationScope,
            isLtr,
            trackWidth,
            valueRange.start,
            valueRange.endInclusive,
            visibilityThreshold
        ) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentValue,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {
                    didDrag = false
                    isDragging = true
                    updatedActive(true)
                },
                onDragStopped = {
                    isDragging = false
                    updatedActive(false)
                    if (didDrag) {
                        onValueChangeState.value(targetValue.coerceIn(valueRange.start, valueRange.endInclusive))
                    }
                    didDrag = false
                    updatedFinished()
                },
                onDrag = { _, dragAmount ->
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth.toFloat())
                    if (delta != 0f) {
                        didDrag = true
                        val nextValue =
                            if (isLtr) (targetValue + delta).coerceIn(valueRange.start, valueRange.endInclusive)
                            else (targetValue - delta).coerceIn(valueRange.start, valueRange.endInclusive)
                        updateValue(nextValue)
                        onValueChangeState.value(nextValue)
                    }
                }
            )
        }

        // Squash-and-stretch of the thumb along its travel (LiquidTracker technique).
        val stretch = rememberLiquidStretchState(stretchFactor = 0.4f, minScale = 0.85f, maxScale = 1.2f)
        LaunchedEffect(dampedDragAnimation, stretch, trackWidth) {
            snapshotFlow { dampedDragAnimation.progress }
                .collect { p -> stretch.onPositionSample(p * trackWidth) }
        }

        LaunchedEffect(currentValue, isDragging, dampedDragAnimation) {
            if (!isDragging && abs(dampedDragAnimation.targetValue - currentValue) > visibilityThreshold) {
                dampedDragAnimation.updateValue(currentValue)
            }
        }

        Box(Modifier.fillMaxWidth().height(48.dp)
            .pointerInput(trackWidth, isLtr, valueRange.start, valueRange.endInclusive) {
                detectTapGestures { position ->
                    val fraction = (position.x / trackWidth).coerceIn(0f, 1f)
                    val target = valueRange.start + (valueRange.endInclusive - valueRange.start) * (if (isLtr) fraction else 1f - fraction)
                    dampedDragAnimation.animateToValue(target)
                    onValueChangeState.value(target)
                    updatedFinished()
                }
            }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule)
                    .background(trackColor)
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule)
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .liquidStretch(stretch)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { Capsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // At rest the thumb is an opaque white capsule (see onDrawSurface), so a blur
                        // of what is under it would be invisible work. Only refract once pressed.
                        if (progress > 0f) blur(8f.dp.toPx() * (1f - progress) * GlassSettings.style.blur)
                        lens(
                            10f.dp.toPx() * progress * GlassSettings.style.refractionHeight,
                            14f.dp.toPx() * progress * GlassSettings.style.refractionAmount,
                            chromaticAberration = GlassSettings.style.chromaticAberration, depthEffect = GlassSettings.style.depthEffect
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        // Alpha 0 at rest: skip it outright, so an idle thumb never builds the
                        // highlight shader at all.
                        if (progress <= 0f) null else Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(radius = 2.dp, color = Color.Black.copy(alpha = if (isLightTheme) .25f else .10f))
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        if (progress <= 0f) null else InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    // Fully covered by the opaque white surface at rest â€” see LiquidToggle.
                    onDrawBackdrop = { drawBackdrop ->
                        if (dampedDragAnimation.pressProgress > 0f) drawBackdrop()
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                        if (isLightTheme && progress < 1f) {
                            drawRoundRect(Color(0xFF767983).copy(alpha = .8f * (1f - progress)),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        }
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
