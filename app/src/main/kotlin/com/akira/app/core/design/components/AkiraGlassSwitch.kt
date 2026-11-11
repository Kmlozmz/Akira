package com.akira.app.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.*
import com.akira.app.core.design.system.AkiraSwitchSpec as Spec
import com.akira.app.core.design.system.AkiraControlMotionSpec as Motion
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.glass.LocalSharedGlassMaterial
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.roundToInt

/** One measured lens: growth changes its capture footprint, never scales a tiny texture. */
@Composable
internal fun AkiraGlassSwitch(checked:Boolean,onChange:(Boolean)->Unit,modifier:Modifier=Modifier) {
    val material=requireNotNull(LocalSharedGlassMaterial.current)
    val background=LocalAppBackdrop.current
    val track=rememberLayerBackdrop()
    val density=LocalDensity.current
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val reduced=LocalContentMotionReduced.current
    val current by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onChange)
    var held by remember{mutableStateOf(false)}
    var tracking by remember{mutableStateOf(false)}
    var positionTarget by remember{mutableFloatStateOf(if(checked)1f else 0f)}
    LaunchedEffect(checked,held){if(!held)positionTarget=if(checked)1f else 0f}
    fun response(period:Float,damping:Float)=if(reduced)snap<Float>()else spring<Float>(
        dampingRatio=damping,stiffness=(2*PI/period).toFloat().let{it*it},visibilityThreshold=.0001f)
    val pressure by animateFloatAsState(if(held)1f else 0f,
        response(if(held)Motion.contactPeriod else Motion.releasePeriod,
            if(held)Motion.contactDamping else Motion.releaseDamping),label="switchPressure")
    val position by animateFloatAsState(positionTarget,
        response(if(tracking)Motion.switchTrackingPeriod else Motion.switchReleasePeriod,
            if(tracking)Motion.switchTrackingDamping else Motion.switchReleaseDamping),label="switchPosition")
    val reveal by animateFloatAsState(if(checked)0f else Spec.trackGradientExtent,
        response(if(checked)Spec.revealOnPeriod else Spec.revealOffPeriod,1f),label="switchTrackReveal")
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val inactive=Color(if(dark)Spec.inactiveDarkArgb else Spec.inactiveLightArgb)
    val active=MaterialTheme.colorScheme.primary
    val p=if(reduced)0f else pressure.coerceIn(-.12f,1.16f)
    val w=Spec.thumbWidth+(Spec.heldWidth-Spec.thumbWidth)*p
    val h=Spec.thumbHeight+(Spec.heldHeight-Spec.thumbHeight)*p
    val fraction=if(rtl)1-position else position
    val center=Spec.leadingInset+Spec.thumbWidth/2+Spec.travel*fraction
    val deformation by rememberAkiraThumbResponse(held,false,positionTarget,
        {Spec.leadingInset+Spec.thumbWidth/2+Spec.travel*position},
        if(held)Spec.heldWidth else Spec.thumbWidth,if(held)Spec.heldHeight else Spec.thumbHeight)
    Box(modifier.size(Spec.trackWidth.dp,Spec.targetHeight.dp)
        .semantics{role=Role.Switch;toggleableState=ToggleableState(checked);onClick{change(!current);true}}
        .pointerInput(rtl,density.density){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                down.consume();held=true;tracking=false
                val start=down.position
                val initial=if(current)1f else 0f
                var cancelled=false
                try {
                    do {
                        val event=awaitPointerEvent()
                        val move=event.changes.firstOrNull{it.id==down.id}
                        if(move==null){cancelled=true;break}
                        val delta=move.position-start
                        if(move.isConsumed && move.positionChangedIgnoreConsumed()){cancelled=true;break}
                        if(!tracking && abs(delta.y)>viewConfiguration.touchSlop && abs(delta.y)>abs(delta.x)){
                            cancelled=true;break
                        }
                        if(abs(delta.x)>Spec.dragSlop*density.density && abs(delta.x)>abs(delta.y))tracking=true
                        if(tracking){
                            positionTarget=(initial+delta.x/(Spec.travel*density.density)*(if(rtl)-1 else 1)).coerceIn(0f,1f)
                            move.consume()
                        }
                    } while(move.pressed)
                    if(!cancelled){
                        val next=if(tracking)positionTarget>=.5f else !current
                        positionTarget=if(next)1f else 0f
                        if(next!=current)change(next)
                    }
                } finally {
                    if(cancelled)positionTarget=if(current)1f else 0f
                    held=false;tracking=false
                }
            }
        }) {
        Canvas(Modifier.align(Alignment.Center).size(Spec.trackWidth.dp,Spec.trackHeight.dp).layerBackdrop(track)) {
            val radius=CornerRadius(size.height/2)
            drawRoundRect(inactive,cornerRadius=radius)
            val path=Path().apply{addRoundRect(RoundRect(0f,0f,size.width,size.height,radius.x,radius.y))}
            clipPath(path){
                drawRect(Brush.horizontalGradient(listOf(active,active.copy(alpha=0f)),
                    startX=(Spec.trackGradientEnd-reveal)*density.density,
                    endX=(Spec.trackGradientExtent-reveal)*density.density))
            }
        }
        AkiraThumbLens(center+deformation.offset,Spec.targetHeight/2,w*deformation.x,h*deformation.y,pressure,
            rememberCombinedBackdrop(background,track),requireNotNull(material.switchMaterial))
    }
}
