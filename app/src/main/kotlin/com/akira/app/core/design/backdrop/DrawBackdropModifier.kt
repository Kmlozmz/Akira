/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.akira.app.core.design.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.akira.app.core.design.backdrop.backdrops.LayerBackdrop
import com.akira.app.core.design.backdrop.highlight.Highlight
import com.akira.app.core.design.backdrop.highlight.HighlightElement
import com.akira.app.core.design.backdrop.internal.ShapeProvider
import com.akira.app.core.design.backdrop.internal.recordLayer
import com.akira.app.core.design.backdrop.internal.clipOutline
import com.akira.app.core.design.backdrop.shadow.InnerShadow
import com.akira.app.core.design.backdrop.shadow.InnerShadowElement
import com.akira.app.core.design.backdrop.shadow.Shadow
import com.akira.app.core.design.backdrop.shadow.ShadowElement
import kotlin.math.ceil

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                backdropScale = 1f
            )
        )
}

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    // Vendored addition: records the backdrop into a layer at this fraction of the
    // surface resolution and draws it scaled back up, so the RenderEffect chain
    // processes far fewer pixels. Pixel-sized effect parameters (blur radius, lens
    // refraction) must be pre-multiplied by the same factor by the caller; the blur
    // hides the upscaling. 1f keeps the original full resolution behavior.
    backdropScale: Float = 1f
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                backdropScale = backdropScale.coerceIn(0.05f, 1f)
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?,
    val backdropScale: Float
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront,
            backdropScale = backdropScale
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.backdropScale = backdropScale
        node.invalidateDrawCache()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false
        if (backdropScale != other.backdropScale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        result = 31 * result + backdropScale.hashCode()
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?,
    var backdropScale: Float
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null
    private var surfaceClipPath: Path? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        shape = shapeProvider.shape
        // The filtered backdrop already owns a low-resolution offscreen layer.
        // This clips the placed child. Our own filtered draw is clipped separately
        // below; a child's layer does not bound this modifier's draw commands.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
    }

    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val padding = padding
        val scale = backdropScale
        // A GraphicsLayer's size alone does not crop its display list. Bound
        // the INPUT before filtering too, including its blur margin, so a page
        // replay cannot expose unrelated rows outside this capture rectangle.
        clipRect {
            translate(padding, padding) {
                scale(scale, pivot = Offset.Zero) {
                    onDrawBackdrop {
                        with(backdrop) {
                            drawBackdrop(
                                density = effectScope,
                                coordinates = layoutCoordinates,
                                layerBlock = layerBlock
                            )
                        }
                    }
                }
            }
        }
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            val padding = padding
            val scale = backdropScale

            recordLayer(
                this@DrawBackdropNode,
                layer,
                size = IntSize(
                    ceil(size.width * scale + padding * 2f).toInt().coerceAtLeast(1),
                    ceil(size.height * scale + padding * 2f).toInt().coerceAtLeast(1)
                ),
                block = recordBackdropBlock
            )

            layer.topLeft =
                if (padding != 0f) IntOffset(-padding.toInt(), -padding.toInt())
                else IntOffset.Zero
            drawRecordedBackdrop()
        }
    }

    // Vendored change: the export below draws the layer this frame already
    // recorded, rather than recording the backdrop a second time. Upstream
    // re-runs the whole capture and effect chain for the export, which doubles
    // the surface's cost on every frame it is exported.
    private val drawRecordedBackdrop: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            val scale = backdropScale
            if (scale != 1f) {
                // Vendored addition: stretch the low resolution layer back over the
                // full surface; the blur in the effect chain masks the upscaling.
                scale(1f / scale, pivot = Offset.Zero) {
                    drawLayer(layer)
                }
            } else {
                drawLayer(layer)
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    override fun ContentDrawScope.draw() {
        if (effectScope.update(this, backdropScale)) {
            updateEffects()
        }

        withSurfaceClip {
            onDrawBehind?.invoke(this)
            drawBackdropLayer()
            onDrawSurface?.invoke(this)
        }
        drawContent()
        onDrawFront?.let { front -> withSurfaceClip { front() } }

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(this@DrawBackdropNode, layer) {
                withSurfaceClip {
                    onDrawBehind?.invoke(this)
                    drawRecordedBackdrop()
                    onDrawSurface?.invoke(this)
                    onDrawFront?.invoke(this)
                }
            }
        }
    }

    /** Clip the optical output, not its padded input or the outer shadow/rim. */
    private inline fun DrawScope.withSurfaceClip(draw: DrawScope.() -> Unit) {
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val path = if (outline is Outline.Rounded) {
            surfaceClipPath ?: Path().also { surfaceClipPath = it }
        } else null
        val canvas = drawContext.canvas
        canvas.save()
        try {
            canvas.clipOutline(outline, path)
            draw()
        } finally {
            canvas.restore()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            if (backdrop.isCoordinatesDependent) {
                layoutCoordinates = coordinates
            } else {
                if (layoutCoordinates != null) {
                    layoutCoordinates = null
                }
            }
            exportedBackdrop?.layerCoordinates = coordinates
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) return

        effectScope.apply(effects)
        graphicsLayer?.renderEffect = effectScope.renderEffect
        padding = effectScope.padding
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()

        observeEffects()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }

        effectScope.reset()
        surfaceClipPath = null
        layoutCoordinates = null
        exportedBackdrop?.layerCoordinates = null
    }
}
