package com.akira.app.core.design.motion

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

/** Reference-fitted departure, inflation and settlement, not Apple constants.
 * C1 Hermite segments carry the live velocity through an interrupted gesture.
 * Centre and extents travel separately: a round detached bubble precedes the
 * broad menu; closing first unloads downward before returning to the control. */
internal class MenuBoundsSpec(private val opening: Boolean, private val density: Float) : FiniteAnimationSpec<Rect> {
    private val duration = if(opening) .42f else .33f
    override fun <V: AnimationVector> vectorize(converter: TwoWayConverter<Rect,V>): VectorizedFiniteAnimationSpec<V> =
        object : VectorizedFiniteAnimationSpec<V> {
            override fun getDurationNanos(initialValue: V,targetValue: V,initialVelocity: V) =
                if(converter.convertFromVector(initialValue)==converter.convertFromVector(targetValue) &&
                    converter.convertFromVector(initialVelocity)==Rect.Zero)0L else (duration*1e9).toLong()
            override fun getValueFromNanos(playTimeNanos: Long,initialValue: V,targetValue: V,initialVelocity: V) =
                sample(playTimeNanos,initialValue,targetValue,initialVelocity,false)
            override fun getVelocityFromNanos(playTimeNanos: Long,initialValue: V,targetValue: V,initialVelocity: V) =
                sample(playTimeNanos,initialValue,targetValue,initialVelocity,true)
            private fun sample(t: Long,initial: V,target: V,velocity: V,speed: Boolean): V {
                if(t<=0) return if(speed)velocity else initial
                if(t>=getDurationNanos(initial,target,velocity))return if(speed)converter.convertToVector(Rect.Zero) else target
                val a=parts(converter.convertFromVector(initial));val b=parts(converter.convertFromVector(target))
                val v=parts(converter.convertFromVector(velocity))
                val source=if(opening)a else b;val menu=if(opening)b else a
                val direction=if(menu[1]>=source[1])1f else -1f
                val edge=min(min(menu[2],menu[3])*.85f,min(source[2],source[3])*2.5f)
                    .coerceAtLeast(min(source[2],source[3]))
                val bubble=floatArrayOf(source[0]+(menu[0]-source[0])*(if(opening).22f else .18f),
                    source[1]+(menu[1]-source[1])*(if(opening).4f else .25f)+
                        direction*(min(source[2],source[3])*(if(opening).65f else .25f)+8f*density),edge,edge)
                val near=menu.copyOf().apply {
                    this[0]+=(source[0]-menu[0])*.035f
                    this[1]+=direction*7f*density
                    this[2]*=.96f;this[3]*=.94f
                }
                val settle=menu.copyOf().apply { this[2]*=1.012f;this[3]*=1.012f }
                val unload=menu.copyOf().apply {
                    this[0]+=(source[0]-menu[0]).coerceIn(-14f*density,14f*density)
                    this[1]+=direction*12f*density
                    this[2]*=.97f;this[3]*=.97f
                }
                val knots=if(opening)listOf(a,bubble,near,settle,b) else listOf(a,unload,bubble,b,b)
                val times=if(opening)floatArrayOf(0f,.12f,.50f,.78f,1f) else floatArrayOf(0f,.12f,.58f,.86f,1f)
                val p=(t/1e9f/duration).coerceIn(0f,1f)
                val segment=(0..3).firstOrNull { p<times[it+1] } ?: 3
                val dt=times[segment+1]-times[segment];val u=(p-times[segment])/dt
                fun tangent(k: Int,i: Int): Float = when(k) {
                    0 -> v[i]*duration
                    4 -> 0f
                    else -> (knots[k+1][i]-knots[k-1][i])/(times[k+1]-times[k-1])
                }
                val values=FloatArray(4) { i ->
                    val x=knots[segment][i];val y=knots[segment+1][i]
                    val m=tangent(segment,i)*dt;val n=tangent(segment+1,i)*dt
                    if(speed) ((6*u*u-6*u)*x+(3*u*u-4*u+1)*m+(-6*u*u+6*u)*y+(3*u*u-2*u)*n)/(dt*duration)
                    else (2*u*u*u-3*u*u+1)*x+(u*u*u-2*u*u+u)*m+(-2*u*u*u+3*u*u)*y+(u*u*u-u*u)*n
                }
                if(!speed) { values[2]=values[2].coerceAtLeast(1f);values[3]=values[3].coerceAtLeast(1f) }
                return converter.convertToVector(Rect(values[0]-values[2]/2,values[1]-values[3]/2,
                    values[0]+values[2]/2,values[1]+values[3]/2))
            }
        }
    private fun parts(r: Rect)=floatArrayOf(r.center.x,r.center.y,r.width,r.height)
}
