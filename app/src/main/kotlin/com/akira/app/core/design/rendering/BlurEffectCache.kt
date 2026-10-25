// SPDX-License-Identifier: GPL-3.0-only
// Adapted from realidkroo/page, commit bb28993b9f7abbac984d3eed8a8f4b62af83f256.
// Akira adaptation: local Song data, Coil 2, local playback callbacks and light mode.
// Original geometry, shaders, wipe, reel and motion are retained. See THIRD_PARTY_NOTICES.md.
package com.akira.app.core.design.rendering

import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*

import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Reuses quantized blur radii; older-Android surfaces use a bounded radius by default. */
object BlurEffectCache {
    private val composeCache = ConcurrentHashMap<Int, RenderEffect>()
    private val nativeCache = ConcurrentHashMap<Int, android.graphics.RenderEffect>()

    /** [allowLargeRadius] lets a surface opt out of the API 31-34 radius budget. */
    fun get(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
        allowLargeRadius: Boolean = false,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) {
            return null
        }

        val effectiveRadius = if (Build.VERSION.SDK_INT <= 34 && !allowLargeRadius) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }

        val quant = when {
            effectiveRadius <= 16f -> 0.5f
            effectiveRadius <= 32f -> 1.0f
            effectiveRadius <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effectiveRadius / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal

        return composeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect
                .createBlurEffect(r, r, tileMode)
                .asComposeRenderEffect()
        }
    }

    /** The radius [get] actually blurs by: clamped on API <= 34 unless [allowLargeRadius]. */
    fun effectiveRadius(radius: Float, allowLargeRadius: Boolean = false): Float =
        if (Build.VERSION.SDK_INT <= 34 && !allowLargeRadius) radius.coerceAtMost(54f) else radius

    /** [radius] rounded to the step [get] caches by, so a cache keyed on it matches [get]'s. */
    fun quantize(radius: Float): Float {
        val quant = when {
            radius <= 16f -> 0.5f
            radius <= 32f -> 1.0f
            radius <= 64f -> 2.0f
            else -> 4.0f
        }
        return (radius / quant).roundToInt().coerceAtLeast(1) * quant
    }

    /**
     * Retrieves or creates a cached android.graphics.RenderEffect for View/TextureView.
     */
    fun getNative(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP
    ): android.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) {
            return null
        }

        val effectiveRadius = if (Build.VERSION.SDK_INT <= 34) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }

        val quant = when {
            effectiveRadius <= 16f -> 0.5f
            effectiveRadius <= 32f -> 1.0f
            effectiveRadius <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effectiveRadius / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal

        return nativeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect.createBlurEffect(r, r, tileMode)
        }
    }
}
