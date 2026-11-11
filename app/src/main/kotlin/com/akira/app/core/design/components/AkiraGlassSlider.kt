package com.akira.app.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.system.AkiraSliderSpec as Spec
import com.akira.app.core.design.system.AkiraControlMotionSpec as Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun AkiraGlassSlider(value:Float,onChange:(Float)->Unit,label:String,modifier:Modifier=Modifier,
    range:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,onFinished:()->Unit={},onActive:(Boolean)->Unit={},enabled:Boolean=true) {
    val material=requireNotNull(LocalSharedGlassMaterial.current)
    val backdrop=LocalAppBackdrop.current
    val track=rememberLayerBackdrop()
    val density=LocalDensity.current.density
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val glass=LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduced=LocalContentMotionReduced.current || !glass
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val active by rememberUpdatedState(onActive)
    val span=(range.endInclusive-range.start).coerceAtLeast(.0001f)
    fun fraction(v:Float)=((v-range.start)/span).coerceIn(0f,1f)
    var width by remember{mutableFloatStateOf(1f)}
    var held by remember{mutableStateOf(false)}
    var positionTarget by remember{mutableFloatStateOf(fraction(value))}
    LaunchedEffect(value,held){if(!held)positionTarget=fraction(value)}
    fun response(period:Float,damping:Float)=if(reduced)snap<Float>()else spring<Float>(
        dampingRatio=damping,stiffness=(2*PI/period).toFloat().let{it*it},visibilityThreshold=.0001f)
    val pressure by animateFloatAsState(if(held)1f else 0f,
        response(if(held)Motion.contactPeriod else Motion.releasePeriod,
            if(held)Motion.contactDamping else Motion.releaseDamping),label="sliderPressure")
    val settledPosition by animateFloatAsState(positionTarget,
        if(held || reduced)snap()else response(Motion.sliderTrackingPeriod,Motion.sliderTrackingDamping),label="sliderPosition")
    val position=if(held)positionTarget else settledPosition
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val inactive=Color(if(dark)Spec.inactiveDarkArgb else Spec.inactiveLightArgb)
    val accent=MaterialTheme.colorScheme.primary
    val p=if(reduced)0f else pressure.coerceIn(-.12f,1.16f)
    val w=Spec.thumbWidth*(1+(Spec.heldScale-1)*p)
    val h=Spec.thumbHeight*(1+(Spec.heldScale-1)*p)
    val center=Spec.thumbWidth/2+(width-Spec.thumbWidth).coerceAtLeast(1f)*(if(rtl)1-position else position)
    val deformation by rememberAkiraThumbResponse(held,true,positionTarget,
        {position*(width-Spec.thumbWidth)},
        if(held)Spec.thumbWidth*Spec.heldScale else Spec.thumbWidth,
        if(held)Spec.thumbHeight*Spec.heldScale else Spec.thumbHeight,enabled=glass)
    fun setFraction(raw:Float){
        val divisions=if(steps>0)steps+1 else 100
        val next=(raw.coerceIn(0f,1f)*divisions).roundToInt().toFloat()/divisions
        if(next!=positionTarget){positionTarget=next;change(range.start+span*next)}
    }
    Box(modifier.fillMaxWidth().heightIn(min=Spec.targetHeight.dp).onSizeChanged{width=it.width/density}
        .semantics{contentDescription=label;progressBarRangeInfo=ProgressBarRangeInfo(value.coerceIn(range),range,steps)
            if(!enabled)disabled()
            setProgress{if(enabled){change(it.coerceIn(range));finished();true}else false}}
        .onKeyEvent{event->
            if(!enabled || event.type!=KeyEventType.KeyDown)false else {
                val delta=when(event.key){Key.DirectionRight,Key.DirectionUp->1;Key.DirectionLeft,Key.DirectionDown->-1;else->0}
                if(delta==0)false else {setFraction(fraction(current)+delta.toFloat()/(if(steps>0)steps+1 else 100));finished();true}
            }
        }.focusable(enabled).pointerInput(density,rtl,range,steps,enabled){
            awaitEachGesture{
                val down=awaitFirstDown(requireUnconsumed=false);down.consume()
                if(!enabled)return@awaitEachGesture
                held=true;active(true)
                var dragged=false;var cancelled=false
                val start=down.position;val initial=fraction(current)
                val travel=(size.width-Spec.thumbWidth*density).coerceAtLeast(1f)
                try{
                    do{
                        val event=awaitPointerEvent();val move=event.changes.firstOrNull{it.id==down.id}?:break
                        val delta=move.position-start
                        if(move.isConsumed && move.positionChangedIgnoreConsumed()){cancelled=true;break}
                        if(!dragged && abs(delta.y)>viewConfiguration.touchSlop && abs(delta.y)>abs(delta.x)){cancelled=true;break}
                        if(abs(delta.x)>3*density && abs(delta.x)>abs(delta.y))dragged=true
                        if(dragged){setFraction(initial+delta.x/travel*(if(rtl)-1 else 1));move.consume()}
                        if(!move.pressed){
                            if(!dragged){val f=(move.position.x-Spec.thumbWidth*density/2)/travel;setFraction(if(rtl)1-f else f)}
                            break
                        }
                    }while(true)
                }finally{
                    held=false;active(false)
                    if(!cancelled || dragged)finished()
                }
            }
        }){
        Canvas(Modifier.fillMaxWidth().height(Spec.targetHeight.dp).then(if(glass)Modifier.layerBackdrop(track)else Modifier)){
            val thickness=Spec.trackHeight*density;val y=(size.height-thickness)/2
            drawRoundRect(inactive,Offset(0f,y),Size(size.width,thickness),CornerRadius(thickness/2))
            val fill=(center*density).coerceIn(0f,size.width)
            drawRoundRect(accent,Offset(if(rtl)fill else 0f,y),
                Size(if(rtl)size.width-fill else fill,thickness),CornerRadius(thickness/2))
        }
        AkiraThumbLens(center+deformation.offset,Spec.targetHeight/2,w*deformation.x,h*deformation.y,pressure,if(glass)rememberCombinedBackdrop(backdrop,track)else backdrop,
            requireNotNull(material.sliderMaterial),glass=glass)
    }
}
