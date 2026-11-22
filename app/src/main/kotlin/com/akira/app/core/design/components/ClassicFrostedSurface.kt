package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.glass.LocalReduceTransparency
import com.akira.app.core.design.glass.isGlassSupported

/** Diffuse backdrop and a quiet tint. Classic has no refraction, light rim or travelling lens. */
@Composable internal fun Modifier.classicFrostedSurface(backdrop: Backdrop, shape: RoundedCornerShape): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val base = if (dark) Color(0xFF202226) else Color(0xFFF0F0F5)
    val result = shadow(20.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .12f),
        spotColor = Color.Black.copy(alpha = .20f))
    if (!isGlassSupported() || LocalReduceTransparency.current) return result.clip(shape).background(base, shape)
    // The broad Gaussian blur masks the reduced capture grid; text stays full resolution.
    val captureScale = .65f
    val tint = base.copy(alpha = if (dark) .76f else .79f)
    return result.drawBackdrop(backdrop = backdrop, shape = { shape },
        effects = {
            val radius = 28.dp.toPx() * captureScale
            padding = radius * 2
            blur(radius)
        }, highlight = null, shadow = null, backdropScale = captureScale,
        onDrawSurface = {
            drawRect(tint)
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .035f else .12f), Color.Transparent)))
        })
}

internal fun classicMenuPivot(anchor: androidx.compose.ui.geometry.Rect?, bounds: androidx.compose.ui.geometry.Rect): TransformOrigin {
    if (anchor == null || bounds.width <= 0f || bounds.height <= 0f) return TransformOrigin.Center
    return TransformOrigin(((anchor.center.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
        ((anchor.center.y - bounds.top) / bounds.height).coerceIn(0f, 1f))
}
