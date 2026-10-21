package com.akira.app.core.design.motion

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Rect

/** Centre and extents have separate phases; retargets retain all four velocities. */
internal class ElasticBoundsSpec(opening: Boolean, audio: Boolean): FiniteAnimationSpec<Rect> {
    private val springs = listOf(
        FloatSpringSpec(.84f, if(opening)620f else 900f,.35f),
        FloatSpringSpec(.82f, if(audio)700f else if(opening)560f else 850f,.35f),
        FloatSpringSpec(.76f, if(opening)850f else 650f,.35f),
        FloatSpringSpec(.80f, if(opening)570f else 720f,.35f),
    )
    override fun <V: AnimationVector> vectorize(converter: TwoWayConverter<Rect,V>): VectorizedFiniteAnimationSpec<V> =
        object: VectorizedFiniteAnimationSpec<V> {
            override fun getDurationNanos(initialValue: V,targetValue: V,initialVelocity: V): Long {
                val a=parts(converter.convertFromVector(initialValue));val b=parts(converter.convertFromVector(targetValue));val v=parts(converter.convertFromVector(initialVelocity))
                return springs.indices.maxOf { springs[it].getDurationNanos(a[it],b[it],v[it]) }
            }
            override fun getValueFromNanos(playTimeNanos: Long,initialValue: V,targetValue: V,initialVelocity: V): V =
                sample(playTimeNanos,initialValue,targetValue,initialVelocity,false)
            override fun getVelocityFromNanos(playTimeNanos: Long,initialValue: V,targetValue: V,initialVelocity: V): V =
                sample(playTimeNanos,initialValue,targetValue,initialVelocity,true)
            private fun sample(t: Long,initial: V,target: V,velocity: V,speed: Boolean): V {
                if(t<=0)return if(speed)velocity else initial
                val a=parts(converter.convertFromVector(initial));val b=parts(converter.convertFromVector(target));val v=parts(converter.convertFromVector(velocity))
                val result=FloatArray(4) { i ->
                    val spring=springs[i]
                    if(t>=spring.getDurationNanos(a[i],b[i],v[i])) { if(speed)0f else b[i] }
                    else if(speed)spring.getVelocityFromNanos(t,a[i],b[i],v[i]) else spring.getValueFromNanos(t,a[i],b[i],v[i])
                }
                return converter.convertToVector(Rect(result[0]-result[2]/2,result[1]-result[3]/2,result[0]+result[2]/2,result[1]+result[3]/2))
            }
        }
    private fun parts(r: Rect)=floatArrayOf((r.left+r.right)/2,(r.top+r.bottom)/2,r.right-r.left,r.bottom-r.top)
}
