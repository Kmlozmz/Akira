package com.akira.app.core.design.motion

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

/** Measured departure, inflation and contact phases from the interaction reference. */
internal class SurfaceBoundsSpec(private val opening: Boolean): FiniteAnimationSpec<Rect> {
    private val seconds=if(opening).5f else .46f

    override fun <V: AnimationVector> vectorize(converter: TwoWayConverter<Rect,V>): VectorizedFiniteAnimationSpec<V> =
        object: VectorizedFiniteAnimationSpec<V> {
            private var cachedFrom: Rect? = null
            private var cachedTo: Rect? = null
            private var cachedVelocity: Rect? = null
            private var cachedTrajectory: Trajectory? = null

            private fun trajectory(from: Rect,to: Rect,velocity: Rect): Trajectory {
                cachedTrajectory?.let {
                    if(from==cachedFrom && to==cachedTo && velocity==cachedVelocity)return it
                }
                val source=if(opening)from else to
                val panel=if(opening)to else from
                val edge=min(panel.size.minDimension*.68f,
                    max(source.size.maxDimension,source.size.minDimension*2.4f))
                val above=panel.center.y<source.center.y
                val direction=if(above)-1f else 1f
                val sourceAnchor=if(above)source.bottom else source.top
                val panelAnchor=if(above)panel.bottom else panel.top
                fun blend(x: Float,y: Float,p: Float)=x+(y-x)*p
                fun point(x: Float,drift: Float,w: Float,h: Float): FloatArray {
                    val cx=blend(source.center.x,panel.center.x,if(opening)x else 1f-x)
                    val anchor=blend(sourceAnchor,panelAnchor,if(opening)x else 1f-x)+direction*edge*drift
                    val cy=anchor+direction*h*.5f
                    return floatArrayOf(cx,cy,w,h)
                }
                val times: FloatArray
                val knots: List<FloatArray>
                if(opening) {
                    times=floatArrayOf(0f,.07f,.14f,.27f,.4f,.54f,.72f,1f)
                    val earlyHeight=(.89f/(panel.height/panel.width).coerceAtLeast(1f)).coerceAtLeast(.62f)
                    knots=listOf(parts(from),
                        point(.234f,.20f,edge*1.06f,edge*.98f),
                        point(.324f,.23f,blend(edge*1.06f,panel.width,.11f),edge*1.07f),
                        point(.786f,.30f,blend(source.width,panel.width,.72f),blend(source.height,panel.height,earlyHeight)),
                        point(1.007f,.14f,blend(source.width,panel.width,.98f),panel.height*1.006f),
                        point(1.041f,.03f,panel.width*1.025f,panel.height*1.018f),
                        point(1.02f,-.01f,panel.width*1.012f,panel.height*1.008f),parts(to))
                } else {
                    times=floatArrayOf(0f,.045f,.116f,.188f,.26f,.333f,.405f,.478f,.55f,.62f,.7f,.85f,1f)
                    knots=listOf(parts(from),
                        point(.007f,.038f,blend(panel.width,source.width,.007f),panel.height),
                        point(.159f,.295f,blend(source.width,panel.width,.745f),blend(source.height,panel.height,.908f)),
                        point(.331f,.266f,blend(source.width,panel.width,.46f),blend(source.height,panel.height,.783f)),
                        point(.503f,.142f,blend(source.width,panel.width,.18f),blend(source.height,panel.height,.625f)),
                        point(.676f,.019f,min(source.width*.95f,edge*.92f),edge*.95f),
                        point(.91f,-.028f,source.width*.91f,blend(source.height,edge,.52f)),
                        point(1.004f,-.030f,source.width*.92f,source.height*1.20f),
                        point(1f,-.030f,source.width*.96f,source.height*1.08f),
                        point(1f,-.024f,source.width*.99f,source.height*.99f),
                        point(1f,-.024f,source.width*.995f,source.height*1.01f),
                        point(1f,-.012f,source.width*1.01f,source.height*1.01f),parts(to))
                }

                val initialSpeed=parts(velocity)
                val tangents=Array(times.size) { k -> FloatArray(4) { i ->
                    when(k) {
                        0 -> initialSpeed[i]*seconds
                        times.lastIndex -> 0f
                        else -> (knots[k+1][i]-knots[k-1][i])/(times[k+1]-times[k-1])
                    }
                } }
                cachedFrom=from;cachedTo=to;cachedVelocity=velocity
                return Trajectory(times,knots,tangents).also { cachedTrajectory=it }
            }

            override fun getDurationNanos(initialValue: V,targetValue: V,initialVelocity: V): Long =
                if(initialValue==targetValue && converter.convertFromVector(initialVelocity)==Rect.Zero)0L
                else (seconds*1e9).toLong()
            override fun getValueFromNanos(t: Long,a: V,b: V,v: V): V = sample(t,a,b,v,false)
            override fun getVelocityFromNanos(t: Long,a: V,b: V,v: V): V = sample(t,a,b,v,true)

            private fun sample(t: Long,a: V,b: V,v: V,speed: Boolean): V {
                if(t<=0)return if(speed)v else a
                if(t>=getDurationNanos(a,b,v))return if(speed)converter.convertToVector(Rect.Zero) else b
                val from=converter.convertFromVector(a);val to=converter.convertFromVector(b)
                val trajectory=trajectory(from,to,converter.convertFromVector(v))
                val times=trajectory.times
                val knots=trajectory.knots
                val phase=(t/1e9f/seconds).coerceIn(0f,1f)
                val segment=(0 until times.lastIndex).firstOrNull { phase<times[it+1] } ?: times.lastIndex-1
                val dt=times[segment+1]-times[segment]
                val u=(phase-times[segment])/dt
                fun component(i: Int): Float {
                    val x=knots[segment][i];val y=knots[segment+1][i]
                    val m=trajectory.tangents[segment][i]*dt;val n=trajectory.tangents[segment+1][i]*dt
                    return if(speed)((6*u*u-6*u)*x+(3*u*u-4*u+1)*m+(-6*u*u+6*u)*y+(3*u*u-2*u)*n)/(dt*seconds)
                    else (2*u*u*u-3*u*u+1)*x+(u*u*u-2*u*u+u)*m+(-2*u*u*u+3*u*u)*y+(u*u*u-u*u)*n
                }
                val cx=component(0);val cy=component(1)
                val width=component(2).let { if(speed)it else it.coerceAtLeast(1f) }
                val height=component(3).let { if(speed)it else it.coerceAtLeast(1f) }
                return converter.convertToVector(Rect(cx-width/2,cy-height/2,cx+width/2,cy+height/2))
            }
        }

    private class Trajectory(val times: FloatArray,val knots: List<FloatArray>,val tangents: Array<FloatArray>)

    private fun parts(rect: Rect)=floatArrayOf(rect.center.x,rect.center.y,rect.width,rect.height)
}
