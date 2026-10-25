package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.glass.liquidGlass

internal object AkiraNavigationMetrics {
    val Gutter = 20.dp
    val Target = 48.dp
    val BackSurface = 40.dp
    val AvatarSurface = 44.dp
    val HeaderTop = 16.dp
    val GroupGap = 12.dp
}

/** Navigation remains distinct from a white page even with clear glass selected. */
@Composable internal fun Modifier.akiraNavigationSurface(shape: CornerBasedShape, backdrop: Backdrop? = null): Modifier {
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    return this
        .then(if (light) Modifier.shadow(10.dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .22f)) else Modifier)
        .liquidGlass(shape, backdropOverride = backdrop, shadowStrength = if (light) .35f else 1f)
        .then(if (light) Modifier.background(Color.Black.copy(alpha = .018f), shape) else Modifier)
}
