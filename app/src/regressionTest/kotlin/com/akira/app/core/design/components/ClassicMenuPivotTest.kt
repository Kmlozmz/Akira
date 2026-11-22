package com.akira.app.core.design.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import org.junit.Assert.*
import org.junit.Test

class ClassicMenuPivotTest {
    @Test fun expansionStartsAtTheOpeningControlRelativeToThePanel() {
        val pivot = classicMenuPivot(Rect(240f, 20f, 280f, 60f), Rect(60f, 80f, 300f, 400f))
        assertEquals(200f / 240f, pivot.pivotFractionX, .0001f)
        assertEquals(0f, pivot.pivotFractionY, 0f)
    }
    @Test fun aboveMenusReturnTowardTheBottomRightControl() {
        assertEquals(TransformOrigin(1f, 1f), classicMenuPivot(Rect(300f, 500f, 340f, 540f), Rect(60f, 80f, 300f, 400f)))
    }
    @Test fun missingOrUnmeasuredAnchorsUseTheCenterWithoutInvalidTransforms() {
        assertEquals(TransformOrigin.Center, classicMenuPivot(null, Rect(0f, 0f, 300f, 400f)))
        assertEquals(TransformOrigin.Center, classicMenuPivot(Rect.Zero, Rect.Zero))
    }
}
