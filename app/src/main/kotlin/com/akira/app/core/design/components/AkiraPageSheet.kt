// SPDX-License-Identifier: GPL-3.0-only
// Presentation geometry adapted from Page bb28993, ui/component/PopupSheet.kt.
// Akira shares its panel surfaces and anchored menu motion.
package com.akira.app.core.design.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal object PageSheetMotion {
    const val Recede = .08f
    const val CoveredScale = .9f
    const val Scrim = .5f
    val spring get() = spring<Float>(1f, 200f, .0005f)
    fun presence(offset: Float) = (1f - offset).coerceIn(0f, 1f)
    fun scale(presence: Float) = 1f - Recede * presence.coerceIn(0f, 1f)
    fun shouldClose(offset: Float, velocity: Float) = when {
        velocity > 1200f -> true
        velocity < -1200f -> false
        else -> offset > .35f
    }
}

internal class AkiraPageEntry(val content: @Composable ColumnScope.() -> Unit, val dismiss: () -> Unit,
    val details: Boolean = false, val immersive: Boolean = false) {
    var offset by mutableFloatStateOf(1f)
    val presence get() = PageSheetMotion.presence(offset)
    var height by mutableFloatStateOf(0f)
    var closing by mutableStateOf(false)
    var detached = false
    var expansion by mutableFloatStateOf(.75f)
    var minimumHeight = 0f
    var colors by mutableStateOf<ColorScheme?>(null)
    var typography: Typography? = null
    var reduced by mutableStateOf(false)
    var ink by mutableStateOf(Color.White)
    var containerColor by mutableStateOf<Color?>(null)
    var title by mutableStateOf<String?>(null)
    var footer by mutableStateOf<(@Composable () -> Unit)?>(null)
}

internal class AkiraPageSheetState(private val onOpen: () -> Unit) {
    val entries = mutableStateListOf<AkiraPageEntry>()
    val presence get() = entries.maxOfOrNull { it.presence } ?: 0f
    fun add(entry: AkiraPageEntry) {
        onOpen()
        var index = entries.size
        while (index > 0 && entries[index - 1].closing) index--
        entry.minimumHeight = entries.getOrNull(index - 1)?.height ?: 0f
        entries.add(index, entry)
    }
    fun close() { entries.lastOrNull()?.let { if (!it.closing) it.closing = true } }
    fun remove(entry: AkiraPageEntry) {
        if (entries.remove(entry) && !entry.detached) entry.dismiss()
    }
}

internal val LocalAkiraPageSheetState = staticCompositionLocalOf<AkiraPageSheetState?> { null }

@Composable
internal fun AkiraPageSheet(onDismiss: () -> Unit, contentColor: Color, title: String?,
    footer: (@Composable () -> Unit)?, containerColor: Color?, details: Boolean, immersive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val state = LocalAkiraPageSheetState.current
    if (state == null) {
        AkiraPresentationHost { AkiraPageSheet(onDismiss, contentColor, title, footer, containerColor, details, immersive, content) }
        return
    }
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestFooter by rememberUpdatedState(footer)
    val entry = remember(details, immersive) { AkiraPageEntry({ latestContent() }, { latestDismiss() }, details, immersive) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val reduced = LocalContentMotionReduced.current
    val footerBody: (@Composable () -> Unit)? = remember(footer != null) {
        if (footer != null) ({ latestFooter?.invoke() }) else null
    }
    SideEffect {
        entry.colors = colors; entry.typography = typography; entry.reduced = reduced
        entry.ink = contentColor; entry.title = title; entry.footer = footerBody
        entry.containerColor = containerColor
    }
    DisposableEffect(entry) {
        state.add(entry)
        onDispose { entry.detached = true; entry.closing = true }
    }
}

/** Read by the root graphics layer: no bitmap snapshot, no second animation clock. */
internal fun Modifier.pageSheetScene(state: AkiraPageSheetState): Modifier = graphicsLayer {
    val p = state.entries.filterNot { it.details }.maxOfOrNull { it.presence } ?: 0f
    val reduced = state.entries.firstOrNull()?.reduced == true
    scaleX = if (reduced) 1f else PageSheetMotion.scale(p)
    scaleY = scaleX
    shape = RoundedCornerShape((if (reduced) 0f else 28f * p).dp)
    clip = p > 0f
}

@Composable
internal fun AkiraPageSheetOverlay(state: AkiraPageSheetState, backdrop: Backdrop) {
    val entries = state.entries.toList()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 600.dp
        val density = LocalDensity.current
        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
        val cap = minOf(maxHeight * .86f, maxHeight - status - 30.dp - keyboard).coerceAtLeast(1.dp)
        val parentHeight = with(density) { (maxHeight - keyboard).toPx() }
        val peek = with(density) { 24.dp.toPx() }
        fun topOf(entry: AkiraPageEntry) = if (tablet) (parentHeight - entry.height) / 2f else parentHeight - entry.height
        fun restingTop(index: Int): Float {
            val own = topOf(entries[index])
            if (index == entries.lastIndex) return own
            val front = entries[index + 1]
            val further = entries.drop(index + 2).sumOf { it.presence.toDouble() }.toFloat().coerceAtMost(1f)
            return own + (restingTop(index + 1) - peek * (1f - further) - own) * front.presence
        }
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Color.Black.copy(alpha = entries.maxOfOrNull { (if (it.details) .20f else PageSheetMotion.Scrim) * it.presence } ?: 0f))
        }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.close() })
        entries.forEachIndexed { index, entry -> key(entry) {
            val front = index == entries.lastIndex && !entry.closing
            val scope = rememberCoroutineScope()
            var job by remember { mutableStateOf<Job?>(null) }
            var velocity by remember { mutableFloatStateOf(0f) }
            val latestFront by rememberUpdatedState(front)
            fun travel() = (parentHeight - topOf(entry)).coerceAtLeast(1f)
            fun run(target: Float, initialVelocity: Float = velocity) {
                job?.cancel()
                job = scope.launch {
                    if (entry.reduced) entry.offset = target else
                        animate(entry.offset, target, initialVelocity, PageSheetMotion.spring) { value, speed ->
                            entry.offset = value.coerceIn(0f, 1f); velocity = speed
                        }
                    velocity = 0f
                    if (target == 1f) state.remove(entry)
                }
            }
            fun move(delta: Float): Float {
                if (!latestFront) return 0f
                job?.cancel()
                var remaining = delta
                var used = 0f
                if (entry.details && entry.offset == 0f) {
                    val previousHeight = entry.expansion
                    entry.expansion = (previousHeight - remaining / parentHeight).coerceIn(.4f, .9f)
                    used = (previousHeight - entry.expansion) * parentHeight
                    remaining -= used
                }
                val previous = entry.offset
                entry.offset = (previous + remaining / travel()).coerceIn(0f, 1f)
                return used + (entry.offset - previous) * travel()
            }
            fun settle(speed: Float) {
                if (!latestFront) return
                if (entry.details && entry.offset == 0f) {
                    val target = when {
                        speed < -1200f -> .9f
                        speed > 1200f -> .4f
                        else -> listOf(.4f, .75f, .9f).minBy { kotlin.math.abs(it - entry.expansion) }
                    }
                    job?.cancel()
                    job = scope.launch {
                        if (entry.reduced) entry.expansion = target else
                            animate(entry.expansion, target, animationSpec = spring(1f, 300f)) { value, _ ->
                                entry.expansion = value.coerceIn(.4f, .9f)
                            }
                    }
                    return
                }
                velocity = speed / travel()
                if (PageSheetMotion.shouldClose(entry.offset, speed)) entry.closing = true
                else run(0f)
            }
            LaunchedEffect(entry, entry.reduced) {
                snapshotFlow { entry.height }.first { it > 0f }
                withFrameNanos { }
                if (!entry.closing) run(0f)
            }
            LaunchedEffect(entry.closing) { if (entry.closing) run(1f) }
            DisposableEffect(entry) { onDispose { job?.cancel() } }
            // A new sheet can cover one that was being dragged. Restore it in place.
            LaunchedEffect(front) { if (!front && !entry.closing && job?.isActive != true) run(0f) }
            val scroll = remember(entry, parentHeight, tablet) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                        if (source == NestedScrollSource.UserInput && available.y < 0f && (entry.offset > 0f || (entry.details && entry.expansion < .9f)))
                            Offset(0f, move(available.y)) else Offset.Zero
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                        if (source == NestedScrollSource.UserInput && available.y > 0f)
                            Offset(0f, move(available.y)) else Offset.Zero
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (!latestFront) return Velocity.Zero
                        if (entry.offset <= 0f && !(entry.details && entry.expansion > .4f && entry.expansion < .9f)) return Velocity.Zero
                        settle(available.y); return available
                    }
                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (!latestFront || (!entry.details && entry.offset <= 0f) || job?.isActive == true) return Velocity.Zero
                        settle(available.y); return available
                    }
                }
            }
            val shape = remember(tablet, entry.details) {
                if (tablet || entry.details) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            }
            val size = if (tablet) Modifier.widthIn(max = 460.dp).fillMaxHeight(.84f)
                else if (entry.details) Modifier.fillMaxWidth().height(minOf((maxHeight - keyboard) * entry.expansion,
                    maxHeight - status - keyboard - 20.dp).coerceAtLeast(1.dp))
                else Modifier.fillMaxWidth().heightIn(max = cap)
            MaterialTheme(colorScheme = entry.colors ?: MaterialTheme.colorScheme,
                typography = entry.typography ?: MaterialTheme.typography) {
                CompositionLocalProvider(LocalAppBackdrop provides backdrop, LocalContentColor provides entry.ink,
                    LocalContentMotionReduced provides entry.reduced) {
                    Box(Modifier.align(if (tablet) Alignment.Center else Alignment.BottomCenter)
                        .padding(bottom = keyboard).then(size).onSizeChanged { entry.height = it.height.toFloat() }
                        .graphicsLayer {
                            val depth = entries.drop(index + 1).sumOf { it.presence.toDouble() }.toFloat()
                            translationY = restingTop(index) - topOf(entry) + entry.offset * travel()
                            scaleX = if (entry.reduced) 1f else 1f - (1f - PageSheetMotion.CoveredScale) * depth.coerceAtMost(2f)
                            scaleY = scaleX
                            alpha = 1f - (depth - 1f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(.5f, 0f)
                        }.then(if (entry.details) Modifier.akiraDetailsMaterial(shape, backdrop) else entry.containerColor?.let { Modifier.background(it, shape) }
                            ?: Modifier.akiraMaterial(shape, AkiraMaterialRole.Panel, backdrop)).clip(shape)
                        // A normal parent click target shields the scrim without cancelling
                        // a child's drag in the Final pointer pass (which prevented scrolling).
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .semantics { isTraversalGroup = true; entry.title?.let { paneTitle = it } }
                        .then(if (!front) Modifier.clearAndSetSemantics { } else Modifier)) {
                        Column(Modifier.fillMaxWidth().then(if (entry.details) Modifier.fillMaxHeight() else Modifier)
                            .heightIn(min = with(density) { entry.minimumHeight.toDp() }.coerceAtMost(cap))
                            .then(if (entry.immersive) Modifier else Modifier.navigationBarsPadding())) {
                            if (!entry.immersive) Box(Modifier.fillMaxWidth().height(32.dp).draggable(
                                state = rememberDraggableState { move(it) }, orientation = Orientation.Vertical,
                                enabled = front, onDragStarted = { job?.cancel() }, onDragStopped = { settle(it) }
                            ), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(entry.ink.copy(alpha = .4f)))
                            }
                            Column(Modifier.fillMaxWidth().weight(1f, fill = entry.details).nestedScroll(scroll)
                                .padding(top = if (entry.details || entry.immersive) 0.dp else 4.dp,
                                    bottom = if (entry.details || entry.immersive) 0.dp else 20.dp), content = entry.content)
                            entry.footer?.let { Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) { it() } }
                        }
                        if (entry.immersive) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(32.dp)
                            .draggable(state=rememberDraggableState { move(it) }, orientation=Orientation.Vertical,
                                enabled=front,onDragStarted={job?.cancel()},onDragStopped={settle(it)}),
                            contentAlignment=Alignment.Center) {
                            Box(Modifier.size(36.dp,4.dp).clip(CircleShape).background(entry.ink.copy(alpha=.7f)))
                        }
                        // Empty sheet areas and covered sheets never leak gestures to the scene.
                        if (!front) Box(Modifier.matchParentSize().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { state.close() })
                    }
                }
            }
        } }
    }
}
