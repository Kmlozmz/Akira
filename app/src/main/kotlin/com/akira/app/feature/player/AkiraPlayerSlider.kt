package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.akira.app.core.design.components.LocalContentMotionReduced

/** Continuous, thumb-less transport slider. Keeps native keyboard/TalkBack seeking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AkiraPlayerSlider(value: Float, onValueChange: (Float)->Unit, label: String,
    modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onFinished: ()->Unit = {}, tint: Color = Color.White, volume: Boolean = false,
    onActiveChanged: (Boolean)->Unit = {},
    restingThickness: Dp = 6.dp, externalActive: Boolean = false, enabled: Boolean = true,
    steps: Int = 0) {
    val interactions=remember { MutableInteractionSource() }
    val dragged by interactions.collectIsDraggedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val active=dragged || pressed || externalActive
    val currentActiveChanged by rememberUpdatedState(onActiveChanged)
    LaunchedEffect(active) { currentActiveChanged(active) }
    DisposableEffect(Unit) { onDispose { currentActiveChanged(false) } }
    val reduced=LocalContentMotionReduced.current
    var edgePull by remember { mutableFloatStateOf(0f) }
    val pull by animateFloatAsState(if(reduced)0f else edgePull,
        if(reduced)snap() else spring(.72f,1100f),label="transportEdgeResistance")
    val thickness by animateDpAsState(if(active) (if(volume)14.dp else 18.dp) else restingThickness,
        if(reduced)snap() else spring(.78f,1100f),label="transportThickness")
    val scale by animateFloatAsState(if(active)1.035f else 1f,
        if(reduced)snap() else spring(.72f,1100f),label="transportStretch")
    val fillAlpha by animateFloatAsState(if(active).96f else .65f,
        if(reduced)snap() else tween(100),label="transportFill")
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val colors=SliderDefaults.colors(activeTrackColor=tint.copy(alpha=if(active)1f else .72f),
        inactiveTrackColor=tint.copy(alpha=.22f))
    Slider(value.coerceIn(valueRange.start,valueRange.endInclusive),onValueChange,
        modifier=modifier.semantics { contentDescription=label }
            .pointerInput(Unit) {
                // Observe without consuming: Material Slider retains seeking,
                // cancellation, keyboard input and progress semantics.
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    try {
                        do {
                            val pointer=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id==down.id }
                            if(pointer==null || !pointer.pressed)break
                            val x=pointer.position.x
                            val overflow=x-x.coerceIn(0f,size.width.toFloat())
                            edgePull=(overflow*.16f).coerceIn(-size.width*.04f,size.width*.04f)
                        } while(true)
                    } finally { edgePull=0f }
                }
            }.graphicsLayer {
                scaleX=scale+kotlin.math.abs(pull)/size.width.coerceAtLeast(1f)
                translationX=pull
            },
        valueRange=valueRange,onValueChangeFinished=onFinished,interactionSource=interactions,enabled=enabled,steps=steps,
        colors=colors,thumb={},track={ sliderState ->
            Canvas(Modifier.fillMaxWidth().height(thickness)) {
                val outline=Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero,size),CornerRadius(size.height/2))) }
                val fraction=((sliderState.value-valueRange.start)/(valueRange.endInclusive-valueRange.start).coerceAtLeast(.001f)).coerceIn(0f,1f)
                clipPath(outline) {
                    drawRect(tint.copy(alpha=.25f))
                    val width=size.width*fraction
                    drawRect(tint.copy(alpha=fillAlpha),Offset(if(rtl)size.width-width else 0f,0f),Size(width,size.height))
                }
            }
        })
}
