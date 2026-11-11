package com.akira.app.core.design.components

import androidx.compose.runtime.*
import com.akira.app.core.design.motion.AkiraThumbDynamics
import com.akira.app.core.design.motion.AkiraThumbPose

@Composable
internal fun rememberAkiraThumbResponse(held:Boolean,slider:Boolean,target:Float,
    position:()->Float,width:Float,height:Float,enabled:Boolean=true):State<AkiraThumbPose> {
    val reduced=LocalContentMotionReduced.current || !enabled
    val model=remember{AkiraThumbDynamics()}
    val pose=remember{mutableStateOf(AkiraThumbPose())}
    val point by rememberUpdatedState(position)
    val extent by rememberUpdatedState(width to height)
    LaunchedEffect(held,slider,target,reduced){
        if(reduced){pose.value=AkiraThumbPose();return@LaunchedEffect}
        var previous=0L;var lastPosition=Float.NaN
        do {
            val frame=withFrameNanos{it};val current=point()
            if(previous!=0L){
                model.advance(frame/1e9,current.toDouble(),extent.first.toDouble(),extent.second.toDouble(),
                    (frame-previous)/1e9,held,slider)
                pose.value=model.pose
            }
            val moving=lastPosition.isNaN() || kotlin.math.abs(current-lastPosition)>.0001f
            previous=frame;lastPosition=current
        }while(held || moving || !model.settled)
    }
    return pose
}
