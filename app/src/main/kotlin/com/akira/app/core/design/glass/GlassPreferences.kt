package com.akira.app.core.design.glass

import androidx.compose.runtime.*
import com.akira.app.core.datastore.UserPreferences

val LocalGlassStyleEnabled = staticCompositionLocalOf { false }
val LocalDockBackgroundDiffusion = staticCompositionLocalOf { true }
internal val LocalGlassIsDock = staticCompositionLocalOf { false }
val LocalGlassReadableSurface = staticCompositionLocalOf { false }
val LocalGlassAngle = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Sliders update draw-phase readers; saving a value does not rebuild their scene on every drag. */
@Composable
internal fun ProvideGlassPreferences(prefs: UserPreferences, content: @Composable () -> Unit) {
    val opacity = rememberUpdatedState(prefs.liquidGlassOpacity)
    val diffusion = rememberUpdatedState(prefs.liquidGlassDiffusion)
    CompositionLocalProvider(
        LocalGlassStyleEnabled provides prefs.liquidGlassEnabled,
        LocalLiquidGlassEnabled provides (prefs.liquidGlassEnabled && !prefs.appearanceReduceTransparency),
        LocalDockBackgroundDiffusion provides prefs.dockLiquidGlassEnabled,
        LocalLiquidGlassOpacity provides remember { { GlassOpacityPreview.value ?: opacity.value } },
        LocalLiquidGlassDiffusion provides remember { { GlassDiffusionPreview.value ?: diffusion.value } },
        content = content,
    )
}
