package com.akira.app.core.design.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import com.akira.app.core.design.motion.AkiraMotion
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.spring
import com.akira.app.core.design.system.AkiraContactSpec as ContactSpec
import com.akira.app.core.design.system.AkiraNavigationSpec as NavigationSpec
import kotlin.math.PI

internal val LocalGlassPressHandled = staticCompositionLocalOf { false }

internal class AkiraPressResponse(val modifier: Modifier, val visualModifier: Modifier,
    val amount: () -> Float, val strain: () -> Offset)

@Composable
internal fun rememberAkiraPressResponse(pressed: Boolean, compression: Float = 1f, referenceContact: Boolean = false, contactLighting:Boolean=true): AkiraPressResponse {
    val enabled = !LocalContentMotionReduced.current && !LocalGlassPressHandled.current
    var contact by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset.Zero) }
    var downPoint by remember { mutableStateOf(Offset.Zero) }
    var faceSize by remember { mutableStateOf(IntSize(1,1)) }
    val density=LocalDensity.current.density
    val shared=com.akira.app.core.design.glass.LocalSharedGlassMaterial.current!=null
    val glass=com.akira.app.core.design.glass.LocalLiquidGlassEnabled.current
    val stretch=animateOffsetAsState(if(enabled && glass && contact)point else Offset.Zero,
        if(!enabled)snap() else AkiraMotion.release(),label="glassContactStrain")
    val touching = enabled && (pressed || contact)
    val inset=if(referenceContact)(NavigationSpec.target-NavigationSpec.face)/2 else 0f
    val target=com.akira.app.core.design.motion.glassContactTarget(
        androidx.compose.ui.geometry.Size((faceSize.width/density-2*inset).coerceAtLeast(1f),
            (faceSize.height/density-2*inset).coerceAtLeast(1f)),
        Offset((point.x+1)*faceSize.width/2f,(point.y+1)*faceSize.height/2f)/density-Offset(inset,inset),
        downPoint/density-Offset(inset,inset),touching,referenceContact)
    val period=if(referenceContact){if(touching)ContactSpec.heldPeriod else ContactSpec.releasePeriod}
        else if(touching)ContactSpec.paneHeldPeriod else ContactSpec.paneReleasePeriod
    val contactSpring=spring<Offset>(dampingRatio=if(referenceContact){if(touching)ContactSpec.heldDamping else ContactSpec.releaseDamping}
            else if(touching)ContactSpec.paneHeldDamping else ContactSpec.paneReleaseDamping,
        stiffness=(2*PI/period).toFloat().let{it*it})
    val wholeScale=animateOffsetAsState(if(enabled && glass && shared)target.scale else Offset(1f,1f),
        if(!enabled)snap()else contactSpring,label="wholeGlassContactScale")
    val wholeOffset=animateOffsetAsState(if(enabled && glass && shared)target.offset*density else Offset.Zero,
        if(!enabled)snap()else contactSpring,label="wholeGlassContactOffset")
    val progress = animateFloatAsState(if (touching) 1f else 0f,
        if(!enabled)snap()else if(shared)spring<Float>(dampingRatio=ContactSpec.glowDamping,
            stiffness=(2*PI/(if(touching)ContactSpec.glowPeriod else ContactSpec.glowReleasePeriod)).toFloat().let{it*it})
        else if(touching)AkiraMotion.press()else AkiraMotion.release(), label="controlContact")
    val light=if(contactLighting)com.akira.app.core.design.glass.rememberAkiraContactLighting({progress.value},{point})else Modifier
    return remember(enabled, glass, shared, compression, progress, stretch, wholeScale, wholeOffset, light) {
        val visual = if (!enabled) Modifier else Modifier.graphicsLayer {
            val scale = 1f + (if(glass).025f else -.02f) * compression.coerceIn(0f, 1f) * progress.value
            translationX=if(glass)stretch.value.x*size.height*.045f else 0f
            translationY=if(glass)stretch.value.y*size.height*.045f else 0f
            if(shared && glass) {
                translationX=wholeOffset.value.x;translationY=wholeOffset.value.y
                scaleX=wholeScale.value.x;scaleY=wholeScale.value.y
            } else { scaleX = scale; scaleY = scale }
        }
        val illuminated=visual.then(light)
        val observer = if (!enabled) Modifier else Modifier.onSizeChanged { faceSize=it }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fun update(position:Offset) {
                    point=Offset((position.x/size.width.coerceAtLeast(1)*2-1),
                        (position.y/size.height.coerceAtLeast(1)*2-1))
                }
                downPoint=down.position
                update(down.position)
                contact = true
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if(change!=null){
                            update(change.position)
                            if(referenceContact && change.pressed && change.positionChangedIgnoreConsumed())change.consume()
                        }
                    } while (change?.pressed == true)
                } finally { contact = false }
            }
        }
        AkiraPressResponse(illuminated.then(observer), illuminated,
            { if (enabled) progress.value else 0f }, { if(enabled && glass && !shared)Offset(stretch.value.x.coerceIn(-1f,1f),stretch.value.y.coerceIn(-1f,1f))*.8f else Offset.Zero })
    }
}
