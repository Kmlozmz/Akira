// liquid_glass_easy 5.0.0 liquidGlassSquirclePath, Copyright 2025 Ahmed Gamil (MIT).
package com.akira.app.core.design.components

import androidx.compose.foundation.shape.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

/** Continuous rounded outline shared by menu clipping and optical geometry. */
class ContinuousMenuShape(private val radius:Dp=28.dp, private val smoothing:Float=1f):CornerBasedShape(CornerSize(radius),CornerSize(radius),CornerSize(radius),CornerSize(radius)) {
    override fun createOutline(size:Size,topStart:Float,topEnd:Float,bottomEnd:Float,bottomStart:Float,layoutDirection:LayoutDirection):Outline {
        val w=size.width.toDouble();val h=size.height.toDouble()
        val r=min(topStart.toDouble(),min(w,h)/2)
        if(r<.5)return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f,0f,size.width,size.height))
        val zone=min(r*(1.0+.528*smoothing.coerceIn(0f,1f)),min(w,h)/2)
        val base=(1-.29289322*(r/zone)).coerceIn(.5,.999999)
        val n=-1/(ln(base)/ln(2.0));val exponent=2/n
        // Same polygon construction. A dense subdivision keeps the physical-pixel error small.
        val steps=ceil(sqrt(.103*(zone/topStart*radius.value)/.05)).toInt().coerceIn(3,40)*2
        val path=Path();var first=true
        fun corner(cx:Double,cy:Double,sx:Double,sy:Double,reverse:Boolean) {
            for(index in 0..steps) {
                val i=if(reverse)steps-index else index
                val t=PI/2*i/steps
                val x=(cx+sx*zone*cos(t).coerceIn(0.0,1.0).pow(exponent)).toFloat()
                val y=(cy+sy*zone*sin(t).coerceIn(0.0,1.0).pow(exponent)).toFloat()
                if(first){path.moveTo(x,y);first=false}else path.lineTo(x,y)
            }
        }
        corner(zone,zone,-1.0,-1.0,false)
        corner(w-zone,zone,1.0,-1.0,true)
        corner(w-zone,h-zone,1.0,1.0,false)
        corner(zone,h-zone,-1.0,1.0,true)
        path.close();return Outline.Generic(path)
    }
    override fun copy(topStart:CornerSize,topEnd:CornerSize,bottomEnd:CornerSize,bottomStart:CornerSize):CornerBasedShape=ContinuousMenuShape(radius,smoothing)
    override fun equals(other:Any?)=other is ContinuousMenuShape && other.radius==radius && other.smoothing==smoothing
    override fun hashCode()=31*radius.hashCode()+smoothing.hashCode()
}
