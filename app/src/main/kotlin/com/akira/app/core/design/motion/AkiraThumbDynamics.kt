package com.akira.app.core.design.motion

import com.akira.app.core.design.system.AkiraThumbMotionSpec as Spec
import kotlin.math.*

internal data class AkiraThumbPose(val x:Float=1f,val y:Float=1f,val offset:Float=0f)

/** The same acceleration filter and analytic spring used by the Appearance controls. */
internal class AkiraThumbDynamics {
    private class Axis(var position:Double) {
        var target=position;var velocity=0.0
        val settled get()=position==target && velocity==0.0
        fun step(seconds:Double,period:Double,damping:Double){
            if(settled)return
            val dt=min(seconds,.25);val omega=2*PI/period;val displacement=position-target
            val wd=omega*sqrt(1-damping*damping);val decay=exp(-damping*omega*dt)
            val c=cos(wd*dt);val s=sin(wd*dt);val k=(velocity+damping*omega*displacement)/wd
            val y=decay*(displacement*c+k*s)
            val v=decay*(velocity*c-(damping*omega*k+displacement*wd)*s)
            position=target+y;velocity=v
            if(abs(y)<.001 && abs(v)<.01){position=target;velocity=0.0}
        }
    }
    private val x=Axis(1.0);private val y=Axis(1.0);private val offset=Axis(0.0)
    private var historyTime:Double?=null
    private var historyPosition=0.0;private var velocity=0.0;private var acceleration=0.0
    val settled get()=x.settled && y.settled && offset.settled
    val pose get()=AkiraThumbPose(x.position.toFloat(),y.position.toFloat(),offset.position.toFloat())
    fun advance(time:Double,position:Double,width:Double,height:Double,seconds:Double,held:Boolean,slider:Boolean){
        val previous=historyTime
        if(previous==null || time-previous>.05){historyPosition=position;velocity=0.0;acceleration=0.0;historyTime=time}
        else if(time-previous>=.0001){
            val dt=time-previous;val next=historyPosition+(position-historyPosition)*.3
            val nextVelocity=velocity+((next-historyPosition)/dt-velocity)*.3
            acceleration+=((abs(nextVelocity)-abs(velocity))/dt-acceleration)*.3
            velocity=nextVelocity;historyPosition=next;historyTime=time
        }
        val period=if(!held)Spec.releasePeriod else if(slider)Spec.heldPeriod else Spec.switchPeriod
        val damping=if(!held)Spec.releaseDamping else if(slider)Spec.heldDamping else Spec.switchDamping
        val force=if(!held)Spec.releaseAccelerationScale else if(slider)Spec.heldAccelerationScale else Spec.switchAccelerationScale
        val extent=if(!held)Spec.releaseExtent else if(slider)Spec.heldExtent else Spec.switchExtent
        val minimum=if(!held)Spec.releaseMinimum else if(slider)Spec.heldMinimum else Spec.switchMinimum
        val maximum=if(!held)Spec.releaseMaximum else if(slider)Spec.heldMaximum else Spec.switchMaximum
        val lowerX=max(minimum.toDouble(),1-extent/width);val upperX=min(maximum.toDouble(),1+extent/width)
        val lowerY=max(minimum.toDouble(),1-extent/height);val upperY=min(maximum.toDouble(),1+extent/height)
        val a=acceleration/force
        x.target=softLimit(1+a*(upperX-1),lowerX,upperX)
        y.target=softLimit(1+a*(lowerY-1),lowerY,upperY)
        offset.target=(1-x.target)*.5*width*if(velocity<0)-1 else 1
        for(axis in listOf(x,y,offset))axis.step(seconds,period.toDouble(),damping.toDouble())
    }
    private fun softLimit(value:Double,lower:Double,upper:Double):Double {
        val band=(upper-lower)/3
        if(band<=0)return value
        return when {
            value<lower->lower-band+band*(tanh((value-(lower-band))*.55/band-.55)+1)
            value>upper->upper+band*tanh((value-upper)*.55/band)
            else->value
        }
    }
}
