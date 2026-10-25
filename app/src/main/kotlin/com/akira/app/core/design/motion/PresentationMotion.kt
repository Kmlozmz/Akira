package com.akira.app.core.design.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.sqrt

internal object GlassMotionProfile {
    const val SourceHandoffEnd = .14f
}

internal fun presentationBoundsSpec(opening: Boolean, anchored: Boolean = false): FiniteAnimationSpec<Rect> =
    if(anchored)SurfaceBoundsSpec(opening) else spring(
    dampingRatio = 1f,
    stiffness = if (opening) 550f else 700f,
    visibilityThreshold = Rect(.5f, .5f, .5f, .5f),
)

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
    val p = progress.coerceIn(0f, 1f)
    val scale = .96f + .04f * p * p * (3f - 2f * p)
    return PresentationContentTransform(scale, Offset(
        (viewport.width - content.width * scale) * .5f,
        (viewport.height - content.height * scale) * .5f,
    ))
}
