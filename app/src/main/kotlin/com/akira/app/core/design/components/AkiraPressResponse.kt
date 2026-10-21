package com.akira.app.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.LocalContentMotionReduced

internal class AkiraPressResponse(val modifier: Modifier,val amount: () -> Float,val strain: () -> Offset)

/** Observe contact without taking clicks, keyboard focus or scroll from the control.
 * Geometry and optical strain share the same interruptible recovery.
 */
@Composable
internal fun rememberAkiraPressResponse(pressed: Boolean, compression: Float = 1f): AkiraPressResponse {
    val reduced=LocalContentMotionReduced.current
    val x=remember { mutableFloatStateOf(0f) };val y=remember { mutableFloatStateOf(0f) }
    val pressure=animateFloatAsState(if(pressed && !reduced)1f else 0f,
        if(reduced)snap() else spring(if(pressed).88f else .66f,if(pressed)1400f else 1000f,.001f),label="contactPressure")
    val followX=animateFloatAsState(x.floatValue,if(reduced)snap() else spring(.9f,1400f),label="contactX")
    val followY=animateFloatAsState(y.floatValue,if(reduced)snap() else spring(.9f,1400f),label="contactY")
    return remember(pressure, followX, followY, x, y, compression) {
    val modifier=Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(false,PointerEventPass.Initial)
            try {
                var point=down
                do {
                    x.floatValue=(point.position.x/size.width.coerceAtLeast(1)*2f-1f).coerceIn(-1f,1f)
                    y.floatValue=(point.position.y/size.height.coerceAtLeast(1)*2f-1f).coerceIn(-1f,1f)
                    point=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id==down.id } ?: break
                } while(point.pressed)
            } finally { x.floatValue=0f;y.floatValue=0f }
        }
    }.graphicsLayer {
        val p=pressure.value
        scaleX=1f-.035f*p*compression;scaleY=1f-.055f*p*compression
        translationX=followX.value*p*2.dp.toPx()
        translationY=followY.value*p*2.dp.toPx()
    }
    AkiraPressResponse(modifier,{pressure.value},{Offset(followX.value,followY.value)*pressure.value*.4f})
    }
}
