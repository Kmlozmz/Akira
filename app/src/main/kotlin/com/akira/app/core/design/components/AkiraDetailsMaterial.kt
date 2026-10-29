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

/** Metadata uses the same opaque reading surface as other panels. */
@Composable internal fun Modifier.akiraDetailsMaterial(shape: CornerBasedShape, backdrop: Backdrop): Modifier =
    background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
