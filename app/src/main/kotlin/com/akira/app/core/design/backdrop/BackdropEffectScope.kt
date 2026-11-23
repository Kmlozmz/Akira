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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.akira.app.core.design.backdrop.internal.chain

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    val size: Size

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    var renderEffect: RenderEffect?
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()
    private var lastBlur: BlurEffect? = null
    private var blurInput: RenderEffect? = null
    private var blurRadius = Float.NaN
    private var blurTileMode = TileMode.Clamp

    private var lastChain: RenderEffect? = null
    private var chainInput: RenderEffect? = null
    private var chainEffect: RenderEffect? = null

    fun applyCachedBlur(radius: Float, tileMode: TileMode) {
        val input = renderEffect
        if (lastBlur == null || input !== blurInput || radius != blurRadius || tileMode != blurTileMode) {
            lastBlur = BlurEffect(input, radius, radius, tileMode)
            blurInput = input
            blurRadius = radius
            blurTileMode = tileMode
        }
        renderEffect = lastBlur
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)
    fun chainEffect(effect: RenderEffect) {
        val input = renderEffect
        if (input == null) {
            renderEffect = effect
            return
        }
        if (lastChain == null || input !== chainInput || effect !== chainEffect) {
            lastChain = input.chain(effect)
            chainInput = input
            chainEffect = effect
        }
        renderEffect = lastChain
    }

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    // Vendored change: [contentScale] reports the size the effects actually operate
    // on when the backdrop layer is recorded at a reduced resolution, so shader
    // geometry (e.g. the lens size uniform) matches the layer's pixel grid.
    fun update(scope: DrawScope, contentScale: Float = 1f): Boolean {
        val newDensity = scope.density
        val newFontScale = scope.fontScale
        val newSize = if (contentScale != 1f) scope.size * contentScale else scope.size
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        effects()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        lastBlur = null
        blurInput = null
        lastChain = null
        chainInput = null
        chainEffect = null
        runtimeShaderCache.clear()
    }
}
