package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.glass.isGlassSupported

/** Frosted reading surface with a neutral veil and no refraction over metadata. */
@Composable internal fun Modifier.akiraDetailsMaterial(shape: CornerBasedShape, backdrop: Backdrop): Modifier {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val base = if (dark) Color(0xFF303030) else Color(0xFFF5F5F7)
    if (!isGlassSupported()) return background(base, shape)
    val density = LocalDensity.current.density
    return drawBackdrop(backdrop = backdrop, shape = { shape }, backdropScale = .5f,
        effects = { blur(16f * density * .5f) }, highlight = null, shadow = null,
        onDrawSurface = { drawRect(base.copy(alpha = if (dark) .70f else .82f)) })
}
