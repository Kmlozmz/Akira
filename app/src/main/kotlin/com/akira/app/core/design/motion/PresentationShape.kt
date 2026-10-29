package com.akira.app.core.design.motion

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.akira.app.core.design.components.ContinuousMenuShape

private class PresentationCorner(private val radius: () -> Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density) = radius().coerceIn(0f, shapeSize.minDimension / 2f)
}

/** The shadow, surface and clipping use the same live radius during a resize. */
internal class PresentationShape(private val radius: () -> Float, private val smoothing: () -> Float,
    private val density: Density) : CornerBasedShape(
    PresentationCorner(radius), PresentationCorner(radius), PresentationCorner(radius), PresentationCorner(radius)) {
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float,
        bottomStart: Float, layoutDirection: LayoutDirection): Outline =
        ContinuousMenuShape(with(density) { minOf(topStart, topEnd, bottomEnd, bottomStart).toDp() }, smoothing())
            .createOutline(size, layoutDirection, density)
    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize,
        bottomStart: CornerSize): CornerBasedShape = this
}
