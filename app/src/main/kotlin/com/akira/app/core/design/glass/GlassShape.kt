package com.akira.app.core.design.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private class TunedCorner(private val original: CornerSize, private val multiplier: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density) = original.toPx(shapeSize, density) * multiplier
}

/** Keep the original outline algorithm when applying the user's corner multiplier. */
internal fun tunedGlassShape(shape: CornerBasedShape, multiplier: Float): CornerBasedShape =
    TunedGlassShape(shape, TunedCorner(shape.topStart, multiplier), TunedCorner(shape.topEnd, multiplier),
        TunedCorner(shape.bottomEnd, multiplier), TunedCorner(shape.bottomStart, multiplier))

private class TunedGlassShape(private val original: CornerBasedShape, topStart: CornerSize,
    topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) :
    CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float,
        bottomStart: Float, layoutDirection: LayoutDirection): Outline =
        original.createOutline(size, topStart, topEnd, bottomEnd, bottomStart, layoutDirection)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        TunedGlassShape(original, topStart, topEnd, bottomEnd, bottomStart)
}
