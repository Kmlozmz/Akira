package com.akira.app.core.design.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.akira.app.core.design.motion.AkiraMotion

internal val LocalGlassPressHandled = staticCompositionLocalOf { false }

internal class AkiraPressResponse(val modifier: Modifier, val visualModifier: Modifier,
    val amount: () -> Float, val strain: () -> Offset)

@Composable
internal fun rememberAkiraPressResponse(pressed: Boolean, compression: Float = 1f): AkiraPressResponse {
    val enabled = !LocalContentMotionReduced.current && !LocalGlassPressHandled.current
    var contact by remember { mutableStateOf(false) }
    val touching = enabled && (pressed || contact)
    val progress = animateFloatAsState(if (touching) 1f else 0f,
        if (!enabled) snap() else if (touching) AkiraMotion.press() else AkiraMotion.release(), label = "controlContact")
    return remember(enabled, compression, progress) {
        val visual = if (!enabled) Modifier else Modifier.graphicsLayer {
            val scale = 1f - .02f * compression.coerceIn(0f, 1f) * progress.value
            scaleX = scale; scaleY = scale
        }
        val observer = if (!enabled) Modifier else Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                contact = true
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                    } while (change?.pressed == true)
                } finally { contact = false }
            }
        }
        AkiraPressResponse(visual.then(observer), visual,
            { if (enabled) progress.value else 0f }, { Offset.Zero })
    }
}
