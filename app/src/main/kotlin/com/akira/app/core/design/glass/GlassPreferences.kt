package com.akira.app.core.design.glass

import androidx.compose.runtime.*
import com.akira.app.core.datastore.UserPreferences

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
        LocalLiquidGlassEnabled provides prefs.liquidGlassEnabled,
        LocalDockBackgroundDiffusion provides prefs.dockLiquidGlassEnabled,
        LocalLiquidGlassOpacity provides remember { { GlassOpacityPreview.value ?: opacity.value } },
        LocalLiquidGlassDiffusion provides remember { { GlassDiffusionPreview.value ?: diffusion.value } },
        content = content,
    )
}
