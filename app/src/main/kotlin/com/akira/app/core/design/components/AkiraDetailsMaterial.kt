package com.akira.app.core.design.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akira.app.core.design.backdrop.Backdrop

/** Metadata keeps its detents and scroll behavior while sharing the panel material. */
@Composable internal fun Modifier.akiraDetailsMaterial(shape: CornerBasedShape, backdrop: Backdrop): Modifier =
    akiraMaterial(shape, AkiraMaterialRole.Panel, backdrop)
