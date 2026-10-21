package com.akira.app.core.design.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The returned release action belongs to this press, including cancellation
 * by a drag, screen change or composition disposal. A hold never emits a tap. */
@Composable
internal fun Modifier.akiraTransportPress(onClick: () -> Unit,
    onHoldStart: (() -> (() -> Unit))? = null,
    onPressedChanged: (Boolean) -> Unit = {},
    onBoostChanged: (Boolean) -> Unit = {}): Modifier {
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHoldStart)
    val pressed by rememberUpdatedState(onPressedChanged)
    val boost by rememberUpdatedState(onBoostChanged)
    return semantics(mergeDescendants=true) {
        role=Role.Button
        onClick {click();true}
    }.pointerInput(Unit) {
        var held=false
        detectTapGestures(onPress={
            coroutineScope {
                held=false
                pressed(true)
                var release: (() -> Unit)?=null
                val activation=launch {
                    delay(400)
                    hold?.let {start ->
                        held=true
                        release=start()
                        boost(true)
                    }
                }
                try {tryAwaitRelease()} finally {
                    activation.cancel()
                    release?.invoke()
                    boost(false)
                    pressed(false)
                }
            }
        },onTap={if(!held)click()})
    }
}
