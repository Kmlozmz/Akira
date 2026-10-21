package com.akira.app.core.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.backdrop.Backdrop

/** Roles describe the surface's purpose, never a separate optical recipe. */
enum class AkiraMaterialRole { Control, Panel, AudioOutput }

@Composable
@Suppress("UNUSED_PARAMETER")
fun Modifier.akiraMaterial(shape: CornerBasedShape, role: AkiraMaterialRole = AkiraMaterialRole.Control,
    backdrop: Backdrop? = null): Modifier = liquidGlass(shape, backdrop)

@Composable
fun AkiraMaterialSurface(shape: CornerBasedShape, modifier: Modifier = Modifier,
    role: AkiraMaterialRole = AkiraMaterialRole.Panel, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.akiraMaterial(shape,role), content=content)
}
