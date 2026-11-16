package com.akira.app.feature.setup

import androidx.compose.runtime.Composable
import com.akira.app.core.model.Song

/** Retain the original full player preview, including volume and secondary controls. */
@Composable internal fun OnboardingPlayerPreview(song: Song?, fullscreen: Boolean) {
    SetupPlayerPreview(song, fullscreen)
}
