// Copyright 2026 zarzet (MIT). See THIRD_PARTY_NOTICES.md.
package com.akira.app.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import org.json.JSONObject
import kotlin.math.min

/** One pointer owns selection and release; accessibility clicks remain on each row. */
internal class AkiraMenuContact {
    var coordinates: LayoutCoordinates? = null
    val targets = linkedMapOf<Any, Pair<() -> Rect?, () -> Unit>>()
    var active by mutableStateOf(false)
    var point by mutableStateOf(Offset.Zero)
    var selected by mutableStateOf<Any?>(null)
    var bounds by mutableStateOf(Rect.Zero)
    var viewport: LayoutCoordinates? = null
    var scroll: ScrollState? = null
    var origin = Offset.Zero
    var travelled by mutableStateOf(false)

    fun hit(point: Offset): Any? = targets.entries.firstOrNull { (_, target) ->
        target.first()?.let { it.width > 0 && it.height > 0 && it.contains(point) } == true
    }?.key
    fun move(position: Offset) {
        point = position
        if ((position - origin).getDistance() > 50 * density) travelled = true
        selected = hit(position)
        selected?.let { targets[it]?.first?.invoke()?.let { rect -> bounds = rect } }
    }
    var density = 1f
    fun release(cancelled: Boolean): (() -> Unit)? {
        val action = if (cancelled) null else selected?.let { targets[it]?.second }
        active = false
        selected = null
        return action
    }
}

internal val LocalAkiraMenuContact = staticCompositionLocalOf<AkiraMenuContact?> { null }

@Composable
internal fun AkiraMenuContactSurface(enabled: Boolean, content: @Composable (AkiraMenuContact) -> Unit) {
    val contact = remember { AkiraMenuContact() }
    val currentEnabled by rememberUpdatedState(enabled)
    contact.density = LocalDensity.current.density
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(contact.active) {
        var previous = 0L
        while (isActive && contact.active) {
            withFrameNanos { time ->
                val dt = if (previous == 0L) 0f else ((time - previous) / 1e9f).coerceAtMost(.032f)
                previous = time
                val root = contact.coordinates
                val viewport = contact.viewport
                if (root?.isAttached == true && viewport?.isAttached == true) {
                    val rect = root.localBoundingBoxOf(viewport, clipBounds = true)
                    val y = contact.point.y
                    val factor = when {
                        y < rect.top + edge -> ((y - rect.top - edge) / edge).coerceIn(-1f, 0f)
                        y > rect.bottom - edge -> ((y - rect.bottom + edge) / edge).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    contact.scroll?.let { scroll ->
                        if (factor != 0f && dt > 0) {
                            // Keep scrubbing available in tall menus without losing their lower actions.
                            scroll.dispatchRawDelta(factor * 360f * contact.density * dt)
                            contact.move(contact.point)
                        }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalAkiraMenuContact provides contact) {
        Box(Modifier.onGloballyPositioned { contact.coordinates = it }.pointerInput(contact) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!currentEnabled || down.isConsumed || contact.hit(down.position) == null) return@awaitEachGesture
                contact.origin = down.position
                contact.travelled = false
                contact.move(down.position)
                contact.active = true
                down.consume()
                var completed = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        contact.move(change.position)
                        change.consume()
                        if (!change.pressed) {
                            completed = true
                            contact.release(cancelled = !currentEnabled)?.invoke()
                            break
                        }
                    }
                } finally { if (!completed) contact.release(cancelled = true) }
            }
        }) {
            AkiraMenuContactLight(contact, Modifier.matchParentSize())
            content(contact)
        }
    }
}

@Composable
internal fun Modifier.menuContactTarget(onClick: () -> Unit): Modifier {
    val contact = LocalAkiraMenuContact.current ?: return this
    val key = remember { Any() }
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    val click by rememberUpdatedState(onClick)
    DisposableEffect(contact, key) {
        contact.targets[key] = ({
            val root = contact.coordinates
            val row = coordinates
            if (root?.isAttached == true && row?.isAttached == true) root.localBoundingBoxOf(row, clipBounds = true) else null
        }) to { click() }
        onDispose { contact.targets.remove(key) }
    }
    return onGloballyPositioned { coordinates = it }
}

private data class MenuLightProfile(val stops: Array<Pair<Float, Color>>, val selection: Color)

@Composable
private fun AkiraMenuContactLight(contact: AkiraMenuContact, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val profile = remember(context) {
        runCatching {
            val data = JSONObject(context.assets.open("visual-material/menu-contact.json").bufferedReader().use { it.readText() })
            val stops = data.getJSONArray("stops")
            val alpha = data.getJSONArray("profile_alpha")
            val rgb = data.getJSONArray("highlight_rgb")
            MenuLightProfile(Array(stops.length()) { i -> stops.getDouble(i).toFloat() to Color.White.copy(alpha = alpha.getDouble(i).toFloat()) },
                Color(rgb.getDouble(0).toFloat(), rgb.getDouble(1).toFloat(), rgb.getDouble(2).toFloat()))
        }.getOrNull()
    }
    val reduced = LocalContentMotionReduced.current
    val target = contact.bounds
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(target) {
        if (rect.value == Rect.Zero || reduced) rect.snapTo(target)
        else rect.animateTo(target, spring(dampingRatio = .82f, stiffness = 2741f))
    }
    val visible by animateFloatAsState(if (contact.active && contact.selected != null) 1f else 0f,
        if (reduced) snap() else spring(.85f, 439f), label = "menuSelection")
    val intensity by animateFloatAsState(if (contact.active) if (contact.travelled) .25f else .5f else 0f,
        if (reduced) snap() else spring(1f, 158f), label = "menuContactLight")
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = onSurface.luminance() > .5f
    val selection = if (dark) profile?.selection ?: onSurface.copy(alpha = .1f) else onSurface.copy(alpha = .09f)
    val inset = with(LocalDensity.current) { 8.dp.toPx() }
    Canvas(modifier) {
        val r = rect.value
        if (visible > .001f && r.width > inset * 2) {
            val highlight = Rect(r.left + inset, r.top + 2, r.right - inset, r.bottom - 2)
            drawRoundRect(selection, highlight.topLeft, highlight.size, CornerRadius(min(highlight.height / 2, 21.dp.toPx())), alpha = visible)
        }
        if (intensity > .001f && profile != null) {
            val radius = 2 * min(160.dp.toPx(), size.minDimension * 1.5f) * if (contact.travelled) 2 else 1
            val gradient = Brush.radialGradient(*profile.stops, center = contact.point, radius = radius)
            drawRect(gradient, alpha = intensity)
        }
    }
}
