package com.akira.app.core.design.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One owner per press, so a second input cannot release another input's hold. */
internal class TransportPressSession(
    private val scope: CoroutineScope,
    private val click: () -> Unit,
    private val hold: () -> (() -> Unit)?,
    private val pressed: (Boolean) -> Unit,
    private val boost: (Boolean) -> Unit,
) {
    private var owner: Any? = null
    private var activation: Job? = null
    private var releaseHold: (() -> Unit)? = null

    fun start(input: Any): Boolean {
        if (owner != null) return false
        owner = input
        pressed(true)
        activation = scope.launch {
            delay(400)
            releaseHold = hold()
            if (releaseHold != null) boost(true)
        }
        return true
    }

    fun finish(input: Any, cancelled: Boolean) {
        if (owner != input) return
        val release = releaseHold
        owner = null
        releaseHold = null
        activation?.cancel()
        activation = null
        try { release?.invoke() } finally {
            boost(false)
            pressed(false)
        }
        if (!cancelled && release == null) click()
    }

    fun cancel() { owner?.let { finish(it, cancelled = true) } }
}

/** Two contrasting strokes keep keyboard focus legible over changing artwork. */
internal fun Modifier.akiraFocusOutline(focused: Boolean): Modifier = drawWithContent {
    drawContent()
    if (focused) {
        val inset = 2.dp.toPx()
        val outlineSize = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
        val radius = CornerRadius(outlineSize.minDimension / 2)
        drawRoundRect(Color.Black, Offset(inset, inset), outlineSize, radius, style = Stroke(3.dp.toPx()))
        drawRoundRect(Color.White, Offset(inset, inset), outlineSize, radius, style = Stroke(1.dp.toPx()))
    }
}

private fun Key.activatesTransport() = this == Key.Enter || this == Key.NumPadEnter ||
    this == Key.DirectionCenter || this == Key.Spacebar

@Composable
internal fun Modifier.akiraTransportPress(onClick: () -> Unit,
    onHoldStart: (() -> (() -> Unit))? = null,
    onPressedChanged: (Boolean) -> Unit = {},
    onBoostChanged: (Boolean) -> Unit = {}): Modifier {
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHoldStart)
    val pressed by rememberUpdatedState(onPressedChanged)
    val boost by rememberUpdatedState(onBoostChanged)
    val scope = rememberCoroutineScope()
    val session = remember(scope) { TransportPressSession(scope, { click() }, { hold?.invoke() }, { pressed(it) }, { boost(it) }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) session.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.cancel() }
    }
    return semantics(mergeDescendants=true) {
        role=Role.Button
        onClick { session.cancel(); click(); true }
    }.akiraFocusOutline(focused)
        .onFocusChanged { focused = it.isFocused; if (!it.isFocused) session.cancel() }
        .onKeyEvent { event ->
            if (!event.key.activatesTransport()) false
            else {
                when (event.type) {
                    KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) session.start(event.key)
                    KeyEventType.KeyUp -> session.finish(event.key, cancelled = false)
                }
                true
            }
        }.focusable()
        .pointerInput(session) {
            detectTapGestures(onPress = {
                val input = Any()
                if (session.start(input)) {
                    var released = false
                    try { released = tryAwaitRelease() }
                    finally { session.finish(input, cancelled = !released) }
                }
            })
        }
}
