package com.akira.app.core.design.motion

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.sqrt

internal object PresentationMotionProfile {
    const val SourceHandoffEnd = .14f
}

internal fun presentationBoundsSpec(opening: Boolean, anchored: Boolean = false): FiniteAnimationSpec<Rect> =
    if (opening) UnfoldBoundsSpec.Open else UnfoldBoundsSpec.Close

/** Width leads height; each axis carries its velocity into an interrupted return. */
private class UnfoldBoundsSpec(opening: Boolean) : FiniteAnimationSpec<Rect> {
    companion object {
        val Open = UnfoldBoundsSpec(true)
        val Close = UnfoldBoundsSpec(false)
    }
    private val springs = (if (opening) listOf(650f, 560f, 760f, 480f)
        else listOf(780f, 680f, 820f, 640f)).map { FloatSpringSpec(1f, it, .1f) }
    private fun components(rect: Rect) = floatArrayOf(rect.center.x, rect.center.y, rect.width, rect.height)
    private fun rectangle(v: FloatArray) = Rect(v[0] - v[2] / 2f, v[1] - v[3] / 2f,
        v[0] + v[2] / 2f, v[1] + v[3] / 2f)

    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Rect, V>): VectorizedFiniteAnimationSpec<V> =
        object : VectorizedFiniteAnimationSpec<V> {
            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long {
                val a = components(converter.convertFromVector(initialValue))
                val b = components(converter.convertFromVector(targetValue))
                val v = components(converter.convertFromVector(initialVelocity))
                return springs.indices.maxOf { springs[it].getDurationNanos(a[it], b[it], v[it]) }
            }
            private fun sample(time: Long, initialValue: V, targetValue: V, initialVelocity: V, velocity: Boolean): V {
                val a = components(converter.convertFromVector(initialValue))
                val b = components(converter.convertFromVector(targetValue))
                val v = components(converter.convertFromVector(initialVelocity))
                val values = FloatArray(4) { i ->
                    val spec = springs[i]
                    if (time >= spec.getDurationNanos(a[i], b[i], v[i])) { if (velocity) 0f else b[i] }
                    else if (velocity) spec.getVelocityFromNanos(time, a[i], b[i], v[i])
                    else spec.getValueFromNanos(time, a[i], b[i], v[i])
                }
                return converter.convertToVector(rectangle(values))
            }
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                if (playTimeNanos <= 0L) initialValue else sample(playTimeNanos, initialValue, targetValue, initialVelocity, false)
            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                if (playTimeNanos <= 0L) initialVelocity else sample(playTimeNanos, initialValue, targetValue, initialVelocity, true)
        }
}

internal fun presentationProgress(current: Rect, source: Rect, destination: Rect): Float {
    fun distance(a: Rect, b: Rect): Float {
        val center = a.center - b.center
        val width = (a.width - b.width) * .5f
        val height = (a.height - b.height) * .5f
        return sqrt(center.x * center.x + center.y * center.y + width * width + height * height)
    }
    val departure = distance(current, source)
    val arrival = distance(current, destination)
    return if (departure + arrival < .001f) 1f else departure / (departure + arrival)
}

internal data class PresentationContentTransform(val scale: Float, val offset: Offset)

internal fun presentationContentTransform(viewport: Size, content: Size,
    progress: Float): PresentationContentTransform {
    val scale = minOf(viewport.width / content.width.coerceAtLeast(1f),
        viewport.height / content.height.coerceAtLeast(1f)).coerceIn(.01f, 1f)
    return PresentationContentTransform(scale, Offset(
        (viewport.width - content.width * scale) * .5f,
        (viewport.height - content.height * scale) * .5f,
    ))
}
