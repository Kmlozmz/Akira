package com.akira.app.feature.player.dock.navigation

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.ui.geometry.Rect

/**
 * A dock changes its width before its vertical travel settles. Using one spring
 * for the four rectangle edges makes it look like a rigid diagonal translation.
 * The nearer outer edge stays anchored while width folds into the circle.
 * Separate edge/size springs retain velocity when the user reverses a fold.
 * These are fits to the reference recording, not Apple's private constants.
 */
internal object DockBoundsSpec : FiniteAnimationSpec<Rect> {
    private val springs = listOf(
        FloatSpringSpec(dampingRatio = .84f, stiffness = 850f, visibilityThreshold = .1f),
        FloatSpringSpec(dampingRatio = .82f, stiffness = 650f, visibilityThreshold = .1f),
        FloatSpringSpec(dampingRatio = .80f, stiffness = 850f, visibilityThreshold = .1f),
        FloatSpringSpec(dampingRatio = .86f, stiffness = 650f, visibilityThreshold = .1f),
    )

    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Rect, V>): VectorizedFiniteAnimationSpec<V> =
        object : VectorizedFiniteAnimationSpec<V> {
            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long {
                val a=converter.convertFromVector(initialValue)
                val b=converter.convertFromVector(targetValue)
                val leftAnchor=kotlin.math.abs(b.left-a.left)<=kotlin.math.abs(b.right-a.right)
                val initial = components(a,leftAnchor)
                val target = components(b,leftAnchor)
                val velocity = components(converter.convertFromVector(initialVelocity),leftAnchor)
                return springs.indices.maxOf { springs[it].getDurationNanos(initial[it], target[it], velocity[it]) }
            }

            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
                if (playTimeNanos <= 0L) return initialValue
                val a=converter.convertFromVector(initialValue)
                val b=converter.convertFromVector(targetValue)
                val leftAnchor=kotlin.math.abs(b.left-a.left)<=kotlin.math.abs(b.right-a.right)
                val initial = components(a,leftAnchor)
                val target = components(b,leftAnchor)
                val velocity = components(converter.convertFromVector(initialVelocity),leftAnchor)
                val values = FloatArray(4) { i ->
                    val spec = springs[i]
                    if (playTimeNanos >= spec.getDurationNanos(initial[i], target[i], velocity[i])) target[i]
                    else spec.getValueFromNanos(playTimeNanos, initial[i], target[i], velocity[i])
                }
                return converter.convertToVector(rectangle(values,leftAnchor))
            }

            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
                if (playTimeNanos <= 0L) return initialVelocity
                val a=converter.convertFromVector(initialValue)
                val b=converter.convertFromVector(targetValue)
                val leftAnchor=kotlin.math.abs(b.left-a.left)<=kotlin.math.abs(b.right-a.right)
                val initial = components(a,leftAnchor)
                val target = components(b,leftAnchor)
                val velocity = components(converter.convertFromVector(initialVelocity),leftAnchor)
                val values = FloatArray(4) { i ->
                    val spec = springs[i]
                    if (playTimeNanos >= spec.getDurationNanos(initial[i], target[i], velocity[i])) 0f
                    else spec.getVelocityFromNanos(playTimeNanos, initial[i], target[i], velocity[i])
                }
                return converter.convertToVector(rectangle(values,leftAnchor))
            }
        }

    private fun components(rect: Rect,leftAnchor: Boolean) = floatArrayOf(
        if(leftAnchor)rect.left else rect.right, (rect.top + rect.bottom) / 2f,
        rect.right - rect.left, rect.bottom - rect.top,
    )
    private fun rectangle(values: FloatArray,leftAnchor: Boolean): Rect {
        val left=if(leftAnchor)values[0] else values[0]-values[2]
        return Rect(left,values[1]-values[3]/2f,left+values[2],values[1]+values[3]/2f)
    }
}
