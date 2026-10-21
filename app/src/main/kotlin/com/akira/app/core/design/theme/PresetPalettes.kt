package com.akira.app.core.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.akira.app.core.model.AppearancePreset

/** Published palettes adapted to Material roles. Attribution: docs/TEMAS.md. */
data class ThemePalette(val background: Color, val surface: Color, val raised: Color,
    val text: Color, val muted: Color, val accent: Color, val secondary: Color)

internal fun contrastRatio(first: Color, second: Color): Float =
    (maxOf(first.luminance(), second.luminance()) + .05f) /
        (minOf(first.luminance(), second.luminance()) + .05f)

/** Moves a semantic foreground only as far as needed towards black or white. */
internal fun ensureContrast(foreground: Color, backgrounds: List<Color>, minimum: Float): Color {
    val opaque = foreground.copy(alpha = 1f)
    fun score(color: Color) = backgrounds.minOf { contrastRatio(color, it) }
    if (score(opaque) >= minimum) return opaque
    val target = if (score(Color.Black) >= score(Color.White)) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(16) {
        val amount = (low + high) / 2f
        if (score(lerp(opaque, target, amount)) >= minimum) high = amount else low = amount
    }
    return lerp(opaque, target, high)
}

fun themePalette(preset: AppearancePreset, light: Boolean): ThemePalette? {
    fun p(bg: Long, surface: Long, raised: Long, text: Long, muted: Long, accent: Long, secondary: Long) =
        ThemePalette(Color(bg), Color(surface), Color(raised), Color(text), Color(muted), Color(accent), Color(secondary))
    return when (preset.family) {
        "CAT" -> if (light) p(0xFFEFF1F5,0xFFE6E9EF,0xFFDCE0E8,0xFF4C4F69,0xFF5C5F77,0xFF8839EF,0xFF179299)
        else when (preset) {
            AppearancePreset.CAT_FRAPPE -> p(0xFF303446,0xFF292C3C,0xFF414559,0xFFC6D0F5,0xFFA5ADCE,0xFFCA9EE6,0xFF81C8BE)
            AppearancePreset.CAT_MACCHIATO -> p(0xFF24273A,0xFF1E2030,0xFF363A4F,0xFFCAD3F5,0xFFA5ADCB,0xFFC6A0F6,0xFF8BD5CA)
            else -> p(0xFF1E1E2E,0xFF181825,0xFF313244,0xFFCDD6F4,0xFFBAC2DE,0xFFCBA6F7,0xFF94E2D5)
        }
        "TOKYO" -> if (light) p(0xFFE1E2E7,0xFFD5D6DB,0xFFC4C8DA,0xFF3760BF,0xFF485E93,0xFF5A4A78,0xFF006C86)
        else when (preset) {
            AppearancePreset.TOKYO_STORM -> p(0xFF24283B,0xFF1F2335,0xFF292E42,0xFFC0CAF5,0xFFA9B1D6,0xFF7AA2F7,0xFF7DCFFF)
            AppearancePreset.TOKYO_MOON -> p(0xFF222436,0xFF1E2030,0xFF2F334D,0xFFC8D3F5,0xFFA9B8E8,0xFF82AAFF,0xFFC099FF)
            else -> p(0xFF1A1B26,0xFF16161E,0xFF24283B,0xFFC0CAF5,0xFFA9B1D6,0xFF7AA2F7,0xFFBB9AF7)
        }
        "EVER" -> if (light) {
            val bg = when (preset) { AppearancePreset.EVER_HARD -> 0xFFFFFBEF; AppearancePreset.EVER_SOFT -> 0xFFF3EAD3; else -> 0xFFFDF6E3 }
            p(bg,0xFFF4F0D9,0xFFE6E2CC,0xFF5C6A72,0xFF5C6A72,0xFF526B12,0xFF2F7971)
        } else {
            val bg = when (preset) { AppearancePreset.EVER_HARD -> 0xFF272E33; AppearancePreset.EVER_SOFT -> 0xFF333C43; else -> 0xFF2D353B }
            p(bg,0xFF232A2E,0xFF3D484D,0xFFD3C6AA,0xFFB0BBB2,0xFFA7C080,0xFF7FBBB3)
        }
        else -> null
    }
}

fun presetColorScheme(preset: AppearancePreset, light: Boolean, accentHex: String = "PRESET",
    base: ColorScheme = if (light) LightColorScheme else DarkColorScheme): ColorScheme {
    val palette = themePalette(preset, light)
    val background = palette?.background ?: appearanceBackground(preset, light)
    val surface = palette?.surface ?: base.surface
    val raised = palette?.raised ?: base.surfaceVariant
    val contentSurfaces = listOf(background, surface, raised)
    val text = ensureContrast(palette?.text ?: base.onSurface, contentSurfaces, 4.5f)
    val muted = ensureContrast(palette?.muted ?: base.onSurfaceVariant, contentSurfaces, 4.5f)
    val custom = accentHex.takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) }
        ?.drop(1)?.toLongOrNull(16)?.let { Color(it or 0xFF000000) }
    var accent = custom ?: palette?.accent ?: base.primary
    // Keep accent text legible on its background, including custom pale colors in light mode.
    repeat(12) {
        val contrast = contrastRatio(accent, background)
        if (contrast < 4.5f) accent = lerp(accent, if (light) Color.Black else Color.White, .12f)
    }
    val secondary = ensureContrast(palette?.secondary ?: base.secondary, listOf(background), 4.5f)
    val primaryContainer = lerp(background, accent, .18f)
    val secondaryContainer = lerp(background, secondary, .16f)
    val outline = ensureContrast(palette?.muted ?: base.outline, contentSurfaces, 3f)
    val onAccent = ensureContrast(if (accent.luminance() > .179f) Color.Black else Color.White, listOf(accent), 4.5f)
    val onSecondary = ensureContrast(if (secondary.luminance() > .179f) Color.Black else Color.White, listOf(secondary), 4.5f)
    return base.copy(background = background, onBackground = text,
        surface = surface, onSurface = text,
        onSurfaceVariant = muted,
        surfaceVariant = raised,
        surfaceContainer = raised,
        surfaceContainerLow = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        primary = accent, onPrimary = onAccent,
        primaryContainer = primaryContainer,
        onPrimaryContainer = ensureContrast(text, listOf(primaryContainer), 4.5f),
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = ensureContrast(text, listOf(secondaryContainer), 4.5f),
        tertiary = secondary, onTertiary = onSecondary,
        outline = outline,
        outlineVariant = ensureContrast(outline, contentSurfaces, 3f))
}
