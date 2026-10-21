package com.akira.app.core.design.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.akira.app.core.model.AppearancePreset
import com.akira.app.core.design.background.CosmicStarfieldBackground

/** Used unchanged by the library, theme picker thumbnails and library preview. */
@Composable
fun AkiraThemeBackground(preset: String, animated: Boolean, intensity: Float, modifier: Modifier = Modifier) {
    val effects = LocalSpaceMode.current
    val style = LocalSpaceStyle.current
    Crossfade(Triple(preset, effects, style), modifier.fillMaxSize(), animationSpec = tween(420), label = "appearance_background") { selection ->
        val id = selection.first
        val theme = AppearancePreset.fromId(id)
        Box(Modifier.fillMaxSize().background(appearanceBackground(theme, LocalThemeMode.current == "LIGHT"))) {
            ThemeBackdrop(id, animated, intensity)
            if (selection.second && !theme.space) ThemeBackdrop(selection.third, animated, intensity)
        }
    }
}

@Composable
fun ThemeBackdrop(preset: String, animated: Boolean, intensity: Float, modifier: Modifier = Modifier) {
    val theme = AppearancePreset.fromId(preset)
    if (!theme.atmosphere) return
    val light = LocalThemeMode.current == "LIGHT"
    val strength = intensity.coerceIn(.2f, 1f) * if (light) .28f else 1f
    val drift = if (animated && preset != "SPACE_OLED") {
        val transition = rememberInfiniteTransition(label = "atmosphere")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse), label = "cloud_drift").value
    } else 0f
    Box(modifier.fillMaxSize()) {
        if (preset != "SPACE_OLED") Canvas(Modifier.fillMaxSize()) {
            val colors = when (preset) {
                "SPACE_NEBULA" -> listOf(Color(0xFF67319C), Color(0xFF194C9F))
                "SPACE_DUSK" -> listOf(Color(0xFF123F73), Color(0xFF965738))
                "FOREST" -> listOf(Color(0xFF17694D), Color(0xFF183F3C))
                "ROSE" -> listOf(Color(0xFF843A63), Color(0xFF4D325D))
                "SUNSET" -> listOf(Color(0xFFA76028), Color(0xFF883D49))
                "SPACE_ICE" -> listOf(Color(0xFF186F87), Color(0xFF294675))
                "SPACE_ORBIT" -> listOf(Color(0xFF352577), Color(0xFF70366B))
                "AURORA" -> listOf(Color(0xFF185D61), Color(0xFF273F78))
                else -> listOf(Color(0xFF192D63), Color(0xFF362056))
            }
            val cloud = if (preset == "SPACE_NEBULA") .9f else if (preset == "SPACE_DUSK") .6f else .35f
            drawRect(Brush.radialGradient(listOf(colors[0].copy(alpha = strength * cloud), Color.Transparent),
                Offset(size.width * (.12f + drift * .16f), size.height * .25f), size.width * 1.2f))
            drawRect(Brush.radialGradient(listOf(colors[1].copy(alpha = strength * cloud * .8f), Color.Transparent),
                Offset(size.width * (.88f - drift * .16f), size.height * .76f), size.width * 1.3f))
            if (preset == "SPACE_NEBULA") drawRect(Brush.radialGradient(
                listOf(Color(0xFF943D75).copy(alpha = strength * .4f), Color.Transparent),
                Offset(size.width * .62f, size.height * (.43f + drift * .06f)), size.width * .9f))
        }
        if (theme.space) CosmicStarfieldBackground(Modifier.graphicsLayer { alpha = if (light) intensity.coerceIn(.2f, 1f) * .6f else strength }, animated,
            ink = if (light) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else null)
    }
}

fun appearanceBackground(theme: AppearancePreset, light: Boolean): Color =
    themePalette(theme, light)?.background ?: if (!light) Color(theme.background) else when (theme) {
    AppearancePreset.MIDNIGHT -> Color(0xFFF8F8FA)
    AppearancePreset.GRAPHITE -> Color(0xFFF0F1F3)
    AppearancePreset.FOREST -> Color(0xFFEDF6F0)
    AppearancePreset.ROSE -> Color(0xFFFAEFF5)
    AppearancePreset.SUNSET -> Color(0xFFFCF3EA)
    else -> Color(0xFFF0F4FA)
}
