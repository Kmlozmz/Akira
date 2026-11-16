package com.akira.app.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import kotlin.math.roundToInt

/** Classic retains menu stacking and focus, using opaque surfaces and a short fade/slide only. */
@Composable internal fun AkiraClassicPresentation(state: AkiraPresentationState) {
    val displayed = state.closing ?: state.desiredEntry ?: state.returningEntry ?: return
    val closing = state.closing != null || displayed.removed
    val presence = remember(displayed) { Animatable(0f) }
    LaunchedEffect(displayed, closing, displayed.reduced) {
        if (closing) {
            if (state.closing == null) state.closing = displayed
            if (displayed.reduced) presence.snapTo(0f) else presence.animateTo(0f, tween(130))
            state.finishClose()
        } else {
            withFrameNanos { }
            if (displayed.reduced) presence.snapTo(1f) else presence.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
        }
    }
    DisposableEffect(state.rootSource) {
        state.anchors?.hiddenSource = null
        onDispose { }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Color.Black.copy(alpha = .32f * presence.value))
        }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (displayed.kind == AkiraPresentationKind.Menu) state.dismissAll() else state.closing = displayed
        })
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val density = LocalDensity.current
            val direction = LocalLayoutDirection.current
            var origin by remember { mutableStateOf(Offset.Zero) }
            val window = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
                // Keep parent content composed so a nested selector does not dispose its owning menu.
                state.entries.toList().forEach { entry -> key(entry) {
                    val front = entry === displayed
                    val menu = entry.kind == AkiraPresentationKind.Menu || entry.kind == AkiraPresentationKind.Popover
                    var measured by remember { mutableStateOf(IntSize.Zero) }
                    val width = minOf(availableWidth, if (menu) 304.dp else 480.dp)
                    val anchor = entry.anchor?.translate(-origin.x, -origin.y)
                    val position = if (menu) anchoredMenuPosition(anchor, window,
                        Size(measured.width.toFloat(), measured.height.toFloat()), 0f, with(density) { 8.dp.toPx() }, entry.preferAbove, direction)
                    else Offset((window.width - measured.width) / 2, (window.height - measured.height) / 2)
                    MaterialTheme(colorScheme = entry.colors ?: MaterialTheme.colorScheme,
                        typography = entry.typography ?: MaterialTheme.typography) {
                        CompositionLocalProvider(LocalAkiraPresentationEntry provides entry,
                            LocalLiquidGlassEnabled provides false, LocalContentMotionReduced provides entry.reduced,
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            Surface(Modifier.width(width).heightIn(max = availableHeight).onSizeChanged { measured = it }
                                .offset {
                                    if (front) IntOffset(position.x.roundToInt(), (position.y + entry.dragOffset).roundToInt())
                                    else IntOffset(-10000, -10000)
                                }.then(if (!front) Modifier.clearAndSetSemantics {} else Modifier)
                                .graphicsLayer {
                                    val p = if (front) presence.value else 0f
                                    alpha = p
                                    scaleX = if (menu && !entry.reduced) .96f + .04f * p else 1f
                                    scaleY = scaleX
                                    translationY = if (entry.reduced) 0f else (1 - p) * if (menu) 6.dp.toPx() else 18.dp.toPx()
                                }, shape = RoundedCornerShape(if (menu) 16.dp else 24.dp),
                                color = if (menu) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shadowElevation = 12.dp) {
                                Column {
                                    Box(Modifier.weight(1f, fill = false)) { entry.content() }
                                    entry.footer?.invoke()
                                }
                            }
                        }
                    }
                } }
            }
        }
    }
}
