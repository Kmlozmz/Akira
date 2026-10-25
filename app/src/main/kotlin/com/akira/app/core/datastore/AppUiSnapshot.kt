package com.akira.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import com.akira.app.core.design.glass.AkiraGlassDefaults

/** Only initial UI state, never a replacement for DataStore or audio preferences. */
internal object AppUiSnapshot {
    fun read(context: Context): UserPreferences? =
        read(context.getSharedPreferences("akira_ui_snapshot", Context.MODE_PRIVATE))

    internal fun read(store: SharedPreferences): UserPreferences? {
        if (!store.getBoolean("ready", false)) return null
        return UserPreferences(
            themeMode = store.getString("mode", "SYSTEM") ?: "SYSTEM",
            // Wait for DataStore's actual online consent before starting a search.
            onlineLyrics = false,
            appearancePreset = com.akira.app.core.model.baseAppearanceId(store.getString("preset", "MIDNIGHT") ?: "MIDNIGHT"),
            spaceMode = store.getBoolean("space", com.akira.app.core.model.AppearancePreset.fromId(store.getString("preset", "MIDNIGHT") ?: "MIDNIGHT").space),
            spaceStyle = store.getString("space_style", "SPACE") ?: "SPACE",
            accentColor = store.getString("accent", "PRESET") ?: "PRESET",
            language = store.getString("language", "SYSTEM") ?: "SYSTEM",
            isOnboardingCompleted = store.getBoolean("onboarded", false),
            dynamicColor = store.getBoolean("artwork", true),
            fullscreenPlayerArtwork = store.getBoolean("fullscreen_artwork", false),
            animatedPlayerArtwork = store.getBoolean("animated_artwork", true),
            liquidGlassEnabled = store.getBoolean("liquid_glass_enabled", false),
            dockLiquidGlassEnabled = store.getBoolean("dock_liquid_glass_enabled", true),
            liquidGlassOpacity = store.getFloat("liquid_glass_opacity", AkiraGlassDefaults.Opacity)
                .takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: AkiraGlassDefaults.Opacity,
            liquidGlassDiffusion = store.getFloat("liquid_glass_diffusion", AkiraGlassDefaults.Diffusion)
                .takeIf { it.isFinite() }?.coerceIn(0f, 24f) ?: AkiraGlassDefaults.Diffusion,
            backgroundMotion = store.getBoolean("motion", true),
            backgroundIntensity = store.getFloat("intensity", .7f),
        )
    }

    fun write(context: Context, prefs: UserPreferences) {
        write(context.getSharedPreferences("akira_ui_snapshot", Context.MODE_PRIVATE), prefs)
    }

    internal fun write(store: SharedPreferences, prefs: UserPreferences) {
        store.edit()
            .putBoolean("ready", true)
            .putString("mode", prefs.themeMode)
            .putBoolean("space", prefs.spaceMode)
            .putString("space_style", prefs.spaceStyle)
            .putString("accent", prefs.accentColor)
            .putString("preset", prefs.appearancePreset)
            .putString("language", prefs.language)
            .putBoolean("onboarded", prefs.isOnboardingCompleted)
            .putBoolean("artwork", prefs.dynamicColor)
            .putBoolean("fullscreen_artwork", prefs.fullscreenPlayerArtwork)
            .putBoolean("animated_artwork", prefs.animatedPlayerArtwork)
            .putBoolean("liquid_glass_enabled", prefs.liquidGlassEnabled)
            .putBoolean("dock_liquid_glass_enabled", prefs.dockLiquidGlassEnabled)
            .putFloat("liquid_glass_opacity", prefs.liquidGlassOpacity)
            .putFloat("liquid_glass_diffusion", prefs.liquidGlassDiffusion)
            .putBoolean("motion", prefs.backgroundMotion)
            .putFloat("intensity", prefs.backgroundIntensity)
            .apply()
    }
}
