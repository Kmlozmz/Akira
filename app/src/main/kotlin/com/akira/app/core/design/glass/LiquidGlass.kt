/*
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.akira.app.core.design.backdrop] — see that package for the
 * upstream attribution. This file is the integration glue, adapted from
 * EchoMusicApp/Echo-Music's GlassEffectConfig/Modifier.liquidGlass
 * (GPL-3.0). Floating controls and presentations share their optics; the dock
 * has a separate enable flag and retains the global opacity and diffusion.
 */
package com.akira.app.core.design.glass


import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.LayerBackdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.backdrop.highlight.HighlightStyle as BackdropHighlightStyle
import com.akira.app.core.design.backdrop.effects.refractionLens
import com.akira.app.core.design.backdrop.effects.glassReadability
import com.akira.app.core.design.backdrop.highlight.Highlight
import com.akira.app.core.design.backdrop.highlight.HighlightElement
import com.akira.app.core.design.backdrop.internal.ShapeProvider
import com.akira.app.core.design.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset

/** Optional material flag retained for the upstream lightweight surface helper. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }
val LocalDockLiquidGlassEnabled = staticCompositionLocalOf { true }
internal val LocalIsDockGlass = staticCompositionLocalOf { false }

@Composable
internal fun isSurfaceGlassEnabled(): Boolean = LocalLiquidGlassEnabled.current &&
    (!LocalIsDockGlass.current || LocalDockLiquidGlassEnabled.current)
/** Draw-phase reader: the live global preview does not recompose the app. */
val LocalLiquidGlassDiffusion = staticCompositionLocalOf<() -> Float> { { AkiraGlassDefaults.Diffusion } }
internal object GlassDiffusionPreview { var value by mutableStateOf<Float?>(null) }
val LocalLiquidGlassOpacity = staticCompositionLocalOf<() -> Float> { { AkiraGlassDefaults.Opacity } }
/** Separate menu windows must invalidate their filtered source while the
 * activity animates. A RenderNode reference alone can retain a stale texture. */
internal val LocalGlassRedrawSignal = staticCompositionLocalOf<() -> Long> { { 0L } }
internal object GlassOpacityPreview {
    var value by mutableStateOf<Float?>(null)
}

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { com.akira.app.core.design.backdrop.backdrops.emptyBackdrop() }

/**
 * Where a [liquidGlass] surface composed under it also records the glass it
 * draws — the sampled, blurred and tinted backdrop, without its rim or shadow —
 * so a lens above it can refract that surface rather than the page behind it.
 * The tab bar's travelling selection pill is the one reader; see
 * [com.akira.app.feature.player.dock.navigation.GlassSelectionPill].
 */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/** Read in the effect draw phase, so moving glass updates optics with its bounds. */
internal val LocalGlassDeformation = staticCompositionLocalOf<() -> Float> { { 0f } }
internal val LocalGlassStrain = staticCompositionLocalOf<() -> androidx.compose.ui.geometry.Offset> { { androidx.compose.ui.geometry.Offset.Zero } }

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Shared optics; each surface owns its geometry and sampled layers. */
internal object AkiraGlassMaterialProfile {
    const val ResolutionScale = 1f
    const val LensDeformation = .35f
    val HighlightStyle = Highlight(width = .45.dp, blurRadius = .25.dp,
        style = BackdropHighlightStyle.Default(color = Color.White.copy(alpha = .36f), falloff = 3.2f))
    val ContactShadow = Shadow(radius = 12.dp, offset = DpOffset(0.dp, 2.dp),
        color = Color.Black.copy(alpha = .3f))
    fun rim(presence: Float): Float = ((presence - .45f) / .55f).coerceIn(0f, 1f)
}

private val FullGlass: () -> Float = { 1f }
private const val FALLBACK_RESOLUTION_SCALE = .4f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim
 * wherever the glass itself is not drawn.
 *
 * A surface filled with the theme's own `surface` colour has no edge of its own
 * against a dark page — it is the same near-black the page is. The glass gets
 * its edge from [Highlight], and this is that edge for everything that does not.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

/** Foreground ink paired with the source contrast protection in glassReadability. */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/** Match accent ink to the source bounds enforced by glassReadability. */
@Composable
fun glassAccentColor(requested: Color = MaterialTheme.colorScheme.primary): Color {
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > .5f
    return remember(requested, lightSurface) {
        val background = if (lightSurface) Color(.74f, .74f, .74f) else Color(.27f, .27f, .27f)
        val target = if (lightSurface) Color.Black else Color.White
        fun contrast(color: Color): Float {
            val ink = color.luminance()
            val source = background.luminance()
            return (maxOf(ink, source) + .05f) / (minOf(ink, source) + .05f)
        }
        // Leave a margin for the sampled surface's channel quantization.
        if (contrast(requested) >= 4.6f) requested else {
            var low = 0f
            var high = 1f
            repeat(16) {
                val middle = (low + high) * .5f
                if (contrast(lerp(requested, target, middle)) >= 4.6f) high = middle else low = middle
            }
            lerp(requested, target, high)
        }
    }
}

/**
 * Selected-tab indicator colour for a glass surface: the inverse of
 * [glassContentColor] rather than the same tint at lower alpha — white in
 * light theme, black in dark theme, so the pill reads as a shaded scrim
 * rather than as more of the same tint already on the glyph and label.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White else Color.Black

/** Compatibility entry point. All surfaces use the same blur/lens renderer. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Modifier.lightweightLiquidGlass(shape: CornerBasedShape, fallbackColor: Color): Modifier =
    liquidGlass(shape)

/** All controls and panels enter the same material renderer as the selection
 * lens. Each surface still owns its shader/shape cache and live coordinates. */
@Composable
fun Modifier.liquidGlass(shape: CornerBasedShape, backdropOverride: Backdrop? = null,
    shadowStrength: Float = 1f): Modifier =
    renderLiquidGlass(shape, backdropOverride ?: LocalAppBackdrop.current,
        exportedBackdrop = LocalGlassExport.current,shadowStrength=shadowStrength)

/** The sole optical implementation. Presence/material describe the lens's
 * hand-off into a flat selection fill, not a separate style for that component.
 */
@Composable
internal fun Modifier.renderLiquidGlass(shape: CornerBasedShape, backdrop: Backdrop,
    exportedBackdrop: LayerBackdrop? = null, presence: () -> Float = FullGlass,
    material: () -> Float = FullGlass, clearLens: Boolean = false, shadowStrength: Float = 1f): Modifier {
    if (!isGlassSupported()) return background(MaterialTheme.colorScheme.surfaceContainer, shape)
    val glass = isSurfaceGlassEnabled()
    val opacity = LocalLiquidGlassOpacity.current
    val diffusion = LocalLiquidGlassDiffusion.current
    val diffuse = !clearLens
    val redraw = LocalGlassRedrawSignal.current
    val density = LocalDensity.current
    val scale = if(glass)AkiraGlassMaterialProfile.ResolutionScale else FALLBACK_RESOLUTION_SCALE
    val surfaceTintColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val lightInk = glassContentColor().luminance() > .5f

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val diffusionRadius = if (glass && diffuse) diffusion().coerceIn(0f,24f) * .25f * density.density * scale * material() else 0f
            // Filter the source once, then refract that same continuous image.
            // A separately masked sharp rim makes the capsule look laminated.
            if (glass && diffuse) padding = maxOf(diffusionRadius * 2f, 26f * density.density * scale)
            if (!glass) blur(48f * scale)
            if (diffusionRadius > 0f) blur(diffusionRadius)
            if (glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                refractionLens(density.density * scale,
                    deformation = material() * AkiraGlassMaterialProfile.LensDeformation,
                    presence = presence())
            }
            if (glass) glassReadability(lightInk, presence())
        },
        highlight = if (glass) ({
            val alpha = material() * AkiraGlassMaterialProfile.rim(presence())
            if(alpha > 0f)AkiraGlassMaterialProfile.HighlightStyle.copy(alpha=alpha) else null
        }) else null,
        shadow = if (glass) ({
            val alpha = material() * AkiraGlassMaterialProfile.rim(presence())
            if(alpha > 0f && shadowStrength > 0f)AkiraGlassMaterialProfile.ContactShadow.copy(alpha=alpha*shadowStrength.coerceIn(0f,1f)) else null
        }) else null,
        onDrawBehind = {
            if(!glass)drawOutline(shape.createOutline(size, layoutDirection, this), surfaceTintColor)
        },
        onDrawSurface = {
            redraw()
            val tint = if(glass)opacity().coerceIn(0f,1f)*material().coerceIn(0f,1f) else .65f
            if(tint>0f)drawRect(color = surfaceTintColor.copy(alpha=tint), size=size)
        },
        exportedBackdrop = exportedBackdrop,
        backdropScale = scale,
    )
}
