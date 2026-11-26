package com.akira.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.backdrop.effects.colorControls
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.glass.LocalReduceTransparency
import com.akira.app.core.design.glass.isGlassSupported

/** One diffuse layer for the player, tabs and system navigation inset. */
@Composable
internal fun Modifier.classicDockSurface(trackAccent: Color?): Modifier {
    val backdrop = LocalAppBackdrop.current
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > .5f
    val neutral = if (light) colors.background else lerp(colors.background, colors.onBackground, .10f)
    val base = trackAccent?.let { lerp(neutral, it, if (light) .13f else .24f) } ?: neutral
    if (!isGlassSupported() || LocalReduceTransparency.current) return background(base)
    val captureScale = .60f
    val tint = base.copy(alpha = if (light) .48f else .30f)
    val falloff = remember(base) { Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = .05f))) }
    return drawBackdrop(backdrop = backdrop, shape = { RectangleShape }, effects = {
        val radius = 22.dp.toPx() * captureScale
        padding = radius * 2
        blur(radius)
        colorControls(saturation = 1.32f)
    }, highlight = null, shadow = null, backdropScale = captureScale,
        // Fill outside the sampled pixels too, so sharp page content cannot leak at the edges.
        onDrawBehind = { drawRect(base) }, onDrawSurface = {
        drawRect(tint)
        drawRect(falloff)
    })
}
