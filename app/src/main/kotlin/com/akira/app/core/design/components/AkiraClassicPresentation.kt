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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.*
import com.akira.app.core.design.backdrop.Backdrop
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import kotlin.math.roundToInt

/** Frosted, anchored Classic panels with reversible motion and continuous nested-menu returns. */
@Composable internal fun AkiraClassicPresentation(state: AkiraPresentationState, backdrop: Backdrop) {
    // A caller can dispose a child directly (for example after choosing a listening mode).
    // Retain that departing child until its return finishes, rather than jumping to its parent.
    val displayed = state.closing ?: state.entries.lastOrNull()?.takeIf { it.removed }
        ?: state.desiredEntry ?: state.returningEntry ?: return
    val closing = state.closing != null || displayed.removed
    val motions = remember(state) { mutableMapOf<AkiraPresentationEntry, Animatable<Float, AnimationVector1D>>() }
    val presence = remember(displayed) { motions.getOrPut(displayed) { Animatable(0f) } }
    val behind = if (state.closingAll) null else if (closing) state.desiredEntry?.takeIf { it !== displayed }
        else displayed.parent?.takeUnless { it.removed }
    SideEffect { motions.keys.retainAll(state.entries.toSet()) }
    LaunchedEffect(displayed, closing, displayed.reduced) {
        if (closing) {
            if (state.closing == null) state.closing = displayed
            if (displayed.reduced) presence.snapTo(0f) else presence.animateTo(0f, tween(180, easing = FastOutLinearInEasing))
            state.finishClose()
        } else {
            snapshotFlow { displayed.bounds != null }.first { it }
            withFrameNanos { }
            if (displayed.reduced) presence.snapTo(1f) else presence.animateTo(1f, spring(dampingRatio = .86f, stiffness = 480f))
        }
    }
    DisposableEffect(state.rootSource) {
        state.anchors?.hiddenSource = null
        onDispose { }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Color.Black.copy(alpha = .20f * if (behind != null) 1f else presence.value.coerceIn(0f, 1f)))
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
                    val backing = entry === behind
                    val menu = entry.kind == AkiraPresentationKind.Menu || entry.kind == AkiraPresentationKind.Popover
                    var measured by remember { mutableStateOf(IntSize.Zero) }
                    val width = minOf(availableWidth, if (entry.kind == AkiraPresentationKind.Menu) 296.dp else if (menu) 336.dp else 480.dp)
                    val anchor = entry.anchor?.translate(-origin.x, -origin.y)
                    val position = if (menu) anchoredMenuPosition(anchor, window,
                        Size(measured.width.toFloat(), measured.height.toFloat()), 0f, with(density) { 8.dp.toPx() }, entry.preferAbove, direction)
                    else Offset((window.width - measured.width) / 2, (window.height - measured.height) / 2)
                    val bounds = Rect(position, Size(measured.width.toFloat(), measured.height.toFloat()))
                    SideEffect { if (front && measured.width > 0 && measured.height > 0) entry.bounds = bounds }
                    MaterialTheme(colorScheme = entry.colors ?: MaterialTheme.colorScheme,
                        typography = entry.typography ?: MaterialTheme.typography) {
                        CompositionLocalProvider(LocalAkiraPresentationEntry provides entry,
                            LocalLiquidGlassEnabled provides false, LocalContentMotionReduced provides entry.reduced,
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            val shape = RoundedCornerShape(if (menu) 28.dp else 32.dp)
                            Column(Modifier.width(width).heightIn(max = availableHeight).onSizeChanged { measured = it }
                                .offset {
                                    if (front || backing) IntOffset(position.x.roundToInt(), (position.y + entry.dragOffset).roundToInt())
                                    else IntOffset(-10000, -10000)
                                }.then(if (!front || closing) Modifier.clearAndSetSemantics {} else Modifier)
                                .then(if (!front) Modifier.pointerInput(displayed) {
                                    awaitPointerEventScope { while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.changes.any { it.pressed && !it.previousPressed } && state.closing == null) {
                                            if (displayed.kind == AkiraPresentationKind.Menu) state.dismissAll() else state.closing = displayed
                                        }
                                        event.changes.forEach { it.consume() }
                                    } }
                                } else Modifier)
                                .graphicsLayer {
                                    val p = presence.value.coerceIn(0f, 1f)
                                    val amount = if (front) p else if (backing) 1f - p else 0f
                                    alpha = amount
                                    transformOrigin = if (menu) classicMenuPivot(anchor, bounds) else TransformOrigin(.5f, .65f)
                                    scaleX = if (entry.reduced) 1f else if (front) .92f + .08f * presence.value else .98f + .02f * amount
                                    scaleY = scaleX
                                    translationY = if (entry.reduced || !front) 0f else (1 - p) * if (menu) 8.dp.toPx() else 20.dp.toPx()
                                }.classicFrostedSurface(backdrop, shape)) {
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
        // Commit each action once while the departing panel is still visible.
        if (closing) Box(Modifier.fillMaxSize().pointerInput(displayed) {
            awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
        })
    }
}
