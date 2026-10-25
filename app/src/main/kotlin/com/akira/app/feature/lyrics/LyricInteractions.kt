package com.akira.app.feature.lyrics

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.localizedText

@Composable
internal fun Modifier.lyricActions(onSeek: (() -> Unit)? = null, onShare: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    val seekLabel = localizedText("Play from this line", "Reproducir desde este verso")
    val shareLabel = localizedText("Share lyrics", "Compartir letras")
    return this
        .drawWithContent {
            drawContent()
            if (focused) drawRect(Color.White, style = Stroke(2.dp.toPx()))
        }
        .semantics(mergeDescendants = true) {
            onClick(label = if (onSeek != null) seekLabel else shareLabel) {
                (onSeek ?: onShare)()
                true
            }
            onLongClick(label = shareLabel) { onShare(); true }
        }
        .onKeyEvent { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
                    if (event.type == KeyEventType.KeyUp) (onSeek ?: onShare)()
                    true
                }
                Key.Menu -> {
                    if (event.type == KeyEventType.KeyUp) onShare()
                    true
                }
                Key.F10 -> {
                    if (event.isShiftPressed) {
                        if (event.type == KeyEventType.KeyUp) onShare()
                        true
                    } else false
                }
                else -> false
            }
        }
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .pointerInput(onSeek, onShare) {
            detectTapGestures(onTap = onSeek?.let { seek -> { seek() } }, onLongPress = { onShare() })
        }
}
