package com.akira.app.core.design.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** One material for floating panels and inline glass. Never blur foreground content. */
object AkiraGlass {
    const val Opacity = .80f
    const val FallbackOpacity = .92f
    const val AccentMix = .16f
    val BlurRadius = 22.dp
    fun color(accent: Color, blurred: Boolean, light: Boolean = false) =
        lerp(if (light) Color(0xFFF5F6FA) else Color(0xFF101012), accent, if (light) .035f else AccentMix).copy(alpha = if (blurred) Opacity else FallbackOpacity)
}

class AkiraGlassBackdrop(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? = null
}

val LocalAkiraGlassBackdrop = staticCompositionLocalOf<AkiraGlassBackdrop?> { null }

@Composable
fun rememberAkiraGlassBackdrop(): AkiraGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { AkiraGlassBackdrop(layer) }
}

/** Record only the content behind floating controls, preventing recursive capture. */
fun Modifier.recordAkiraGlassBackdrop(backdrop: AkiraGlassBackdrop) =
    onGloballyPositioned { backdrop.coordinates = it }.drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Composable
fun AkiraGlassSurface(modifier: Modifier = Modifier, shape: Shape, chrome: Boolean = false,
    content: @Composable BoxScope.() -> Unit) {
    val rounded = shape as? androidx.compose.foundation.shape.CornerBasedShape
        ?: androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    Box(modifier.akiraMaterial(rounded, if(chrome) AkiraMaterialRole.Control else AkiraMaterialRole.Panel), content=content)
}
