// SPDX-License-Identifier: GPL-3.0-only
// Adapted from realidkroo/page, commit bb28993b9f7abbac984d3eed8a8f4b62af83f256.
// Akira adaptation: local Song data, Coil 2, local playback callbacks and light mode.
// Original geometry, shaders, wipe, reel and motion are retained. See THIRD_PARTY_NOTICES.md.
package com.akira.app.core.design.rendering

import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
internal fun glassDownscale(effectiveRadius: Float): Int = when {
    effectiveRadius >= 64f -> 4
    effectiveRadius >= 16f -> 2
    else -> 1
}

/** The size a downscaled layout was given, before it was divided down. */
private class FullSize {
    var width = 0
    var height = 0
}

/** Lays its content out at 1/[downscale] of the space it takes, from the top left. */
private fun Modifier.downscaledLayout(downscale: () -> Int, full: FullSize): Modifier = layout { measurable, constraints ->
    val k = downscale().coerceAtLeast(1)
    val fullWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val fullHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    full.width = fullWidth
    full.height = fullHeight
    val placeable = measurable.measure(
        Constraints.fixed((fullWidth + k - 1) / k, (fullHeight + k - 1) / k)
    )
    layout(fullWidth, fullHeight) { placeable.place(0, 0) }
}

/** The last masked glass effect, reused while its radius, size and mask stay the same. */
private class MaskedEffectCache {
    var radius = -1f
    var width = -1f
    var height = -1f
    var tileOrdinal = -1
    var mask: Brush? = null
    var effect: RenderEffect? = null
}

/**
 * The blur and the mask in one effect: the blurred content kept only where [mask] is opaque
 * (DstIn) - what drawing the glass into an offscreen layer and DstIn-ing the mask over it did,
 * without that extra layer.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun maskedBlurEffect(radius: Float, tileMode: android.graphics.Shader.TileMode, mask: android.graphics.Shader): RenderEffect {
    val content = if (radius > 0.5f) {
        android.graphics.RenderEffect.createBlurEffect(radius, radius, tileMode)
    } else {
        android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
    }
    return android.graphics.RenderEffect.createBlendModeEffect(
        content,
        android.graphics.RenderEffect.createShaderEffect(mask),
        android.graphics.BlendMode.DST_IN,
    ).asComposeRenderEffect()
}

/**
 * Whatever [draw] draws, blurred by [radius] (the radius actually applied - see
 * [BlurEffectCache.effectiveRadius]) and optionally faded by [mask] (DstIn).
 *
 * Drawn at the resolution [glassDownscale] picks for the radius: into a layer 1/k the size, blurred
 * by radius/k and scaled back up - see [glassDownscale] for why that's the same picture for less
 * work. [draw] works in full-size coordinates and gets the full size; the scaling is done here.
 */
@Composable
internal fun DownscaledBlur(
    radius: () -> Float,
    tileMode: android.graphics.Shader.TileMode?,
    mask: Brush?,
    modifier: Modifier = Modifier,
    /** Draw into a buffer of its own even without a blur, for a [draw] that erases with blend modes. */
    offscreen: (() -> Boolean)? = null,
    draw: DrawScope.(fullSize: Size) -> Unit,
) {
    val currentRadius by rememberUpdatedState(radius)
    val currentOffscreen by rememberUpdatedState(offscreen)
    // A radius that animates only re-lays this out when it crosses into another resolution step.
    val downscale by remember { derivedStateOf { glassDownscale(currentRadius()) } }
    val maskCache = remember { MaskedEffectCache() }
    val full = remember { FullSize() }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                // Without RenderEffect (below API 31) the mask is applied the old way.
                if (mask != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = mask, blendMode = BlendMode.DstIn)
                        }
                } else Modifier
            )
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .downscaledLayout({ downscale }, full)
                .graphicsLayer {
                    val k = downscale.toFloat()
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                    val r = currentRadius() / k
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val mode = tileMode ?: android.graphics.Shader.TileMode.DECAL
                        if (mask is ShaderBrush) {
                            val quantized = if (r > 0.5f) BlurEffectCache.quantize(r) else 0f
                            val cache = maskCache
                            if (cache.effect == null || cache.radius != quantized || cache.width != size.width ||
                                cache.height != size.height || cache.tileOrdinal != mode.ordinal || cache.mask != mask
                            ) {
                                cache.effect = maskedBlurEffect(quantized, mode, mask.createShader(size))
                                cache.radius = quantized
                                cache.width = size.width
                                cache.height = size.height
                                cache.tileOrdinal = mode.ordinal
                                cache.mask = mask
                            }
                            cache.effect
                        } else {
                            BlurEffectCache.get(r, mode)
                        }
                    } else {
                        null
                    }
                    compositingStrategy = if (currentOffscreen?.invoke() == true) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    clip = true
                }
        ) {
            val k = downscale.toFloat()
            val fullSize = Size(full.width.toFloat(), full.height.toFloat())
            scale(1f / k, 1f / k, pivot = Offset.Zero) { draw(fullSize) }
        }
    }
}
