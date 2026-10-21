package com.akira.app.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Legends, controls and plot strokes use the same readable pair in both themes. */
internal data class AudioCurvePalette(val outgoing: Color, val incoming: Color, val power: Color)
internal fun audioCurvePalette(background: Color): AudioCurvePalette = if (background.luminance() > .5f)
    AudioCurvePalette(Color(0xFFA63E00), Color(0xFF005AA8), Color(0xFF795300))
else AudioCurvePalette(Color(0xFFFFAC70), Color(0xFF75BDFF), Color(0xFFFFD480))
