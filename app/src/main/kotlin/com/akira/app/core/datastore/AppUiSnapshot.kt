package com.akira.app.core.datastore

import android.content.Context

/** Only initial UI state, never a replacement for DataStore or audio preferences. */
internal object AppUiSnapshot {
    fun read(context: Context): UserPreferences? {
        val store = context.getSharedPreferences("akira_ui_snapshot", Context.MODE_PRIVATE)
        if (!store.getBoolean("ready", false)) return null
        return UserPreferences(themeMode = store.getString("mode", "SYSTEM") ?: "SYSTEM",
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
            backgroundMotion = store.getBoolean("motion", true),
            backgroundIntensity = store.getFloat("intensity", .7f))
    }
    fun write(context: Context, prefs: UserPreferences) {
        context.getSharedPreferences("akira_ui_snapshot", Context.MODE_PRIVATE).edit()
            .putBoolean("ready", true).putString("mode", prefs.themeMode)
            .putBoolean("space", prefs.spaceMode).putString("space_style", prefs.spaceStyle).putString("accent", prefs.accentColor).putString("preset", prefs.appearancePreset).putString("language", prefs.language)
            .putBoolean("onboarded", prefs.isOnboardingCompleted).putBoolean("artwork", prefs.dynamicColor)
            .putBoolean("fullscreen_artwork", prefs.fullscreenPlayerArtwork)
            .putBoolean("animated_artwork", prefs.animatedPlayerArtwork)
            .putBoolean("motion", prefs.backgroundMotion).putFloat("intensity", prefs.backgroundIntensity).apply()
    }
}
