package com.akira.app.feature.settings

import com.akira.app.core.i18n.AkiraUiText

/** Stable identifiers also restore the currently open category after recreation. */
internal enum class SettingsSection(val title: AkiraUiText, val summary: AkiraUiText) {
    PLAYBACK(AkiraUiText.Playback, AkiraUiText.SettingsPlaybackSummary),
    SOUND(AkiraUiText.SettingsSound, AkiraUiText.SettingsSoundSummary),
    APPEARANCE(AkiraUiText.Appearance, AkiraUiText.SettingsAppearanceSummary),
    LYRICS(AkiraUiText.SettingsLyrics, AkiraUiText.SettingsLyricsSummary),
    LIBRARY(AkiraUiText.Library, AkiraUiText.SettingsLibrarySummary),
    APPLICATION(AkiraUiText.SettingsApplication, AkiraUiText.SettingsApplicationSummary),
}

internal enum class SettingsEditor {
    TRANSITIONS, EQUALIZER, OUTPUT_PROFILES, LYRICS_SOURCE, LANGUAGE,
    BLUETOOTH_WINDOW, NORMALIZATION, MINIMUM_DURATION, DEFAULT_TAB, LIBRARY_EXCEPTIONS, UPDATES,
}
