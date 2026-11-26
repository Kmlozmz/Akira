package com.akira.app.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AkiraPlayerSlider(value:Float,onValueChange:(Float)->Unit,label:String,
    modifier:Modifier=Modifier,valueRange:ClosedFloatingPointRange<Float> = 0f..1f,
    onFinished:()->Unit={},tint:Color=Color.White,volume:Boolean=false,onActiveChanged:(Boolean)->Unit={},
    restingThickness:Dp=6.dp,externalActive:Boolean=false,enabled:Boolean=true,
    steps:Int=0,animatedThumb:Boolean=false,frostColors:List<Color>?=null)=
    com.akira.app.core.design.components.AkiraSlider(value,onValueChange,label,modifier,valueRange,
        onFinished,tint,volume,onActiveChanged,restingThickness,externalActive,enabled,steps,animatedThumb,frostColors)
