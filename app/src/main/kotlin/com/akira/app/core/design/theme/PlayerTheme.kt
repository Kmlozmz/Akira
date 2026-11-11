package com.akira.app.core.design.theme

import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import com.akira.app.core.design.components.akiraFocusOutline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.io.File
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.akira.app.R

val AkiraFontFamily = FontFamily(
    Font(R.font.roboto_flex_regular, FontWeight.Normal),
    Font(R.font.roboto_flex_medium, FontWeight.Medium),
    Font(R.font.roboto_flex_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_flex_bold, FontWeight.Bold),
    Font(R.font.roboto_flex_extrabold, FontWeight.ExtraBold),
    Font(R.font.roboto_flex_black, FontWeight.Black)
)

val LocalAkiraFontFamily = staticCompositionLocalOf { AkiraFontFamily }
val CurrentAkiraFontFamily: FontFamily
    @Composable get() = LocalAkiraFontFamily.current

val PlayerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AkiraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    )
)

/** Expose the resolved theme to screens that draw their own backgrounds. */
val LocalThemeMode = staticCompositionLocalOf { "AMOLED" }
val LocalAppearancePreset = staticCompositionLocalOf { "MIDNIGHT" }
val LocalSpaceMode = staticCompositionLocalOf { false }
val LocalSpaceStyle = staticCompositionLocalOf { "SPACE" }

/** Theme selection resolves live when the phone switches between light and dark. */
internal fun resolveThemeMode(mode: String, systemDark: Boolean): String = when (mode) {
    "LIGHT" -> "LIGHT"
    "DARK", "AMOLED" -> mode
    else -> if (systemDark) "AMOLED" else "LIGHT"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkiraTheme(
    themeMode: String = "SYSTEM",
    appearancePreset: String = "MIDNIGHT",
    accentColor: String = "PRESET",
    spaceMode: Boolean = false,
    spaceStyle: String = "SPACE",
    appearanceFont: String = "system",
    appearanceFontId: String = "",
    appearanceSizeIndex: Int = 3,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val resolved = resolveThemeMode(themeMode, dark)
    // Classic appearance replaces the old theme families. Existing stored selections
    // remain readable but no longer tint backgrounds or controls.
    val context = LocalContext.current
    val sharedMaterialAvailable = remember(context.assets) {
        context.assets.list("visual-material")?.contains("glass.agsl") == true
    }
    val baseScheme = (if (resolved == "LIGHT") LightColorScheme else AmoledColorScheme).let { base ->
        if (!sharedMaterialAvailable) base else if (resolved == "LIGHT") base.copy(
            background = Color(0xFFF2F2F7), surface = Color.White,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2F7),
            surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE5E5EA),
            surfaceContainerHighest = Color(0xFFD1D1D6), surfaceVariant = Color(0xFFE5E5EA),
            onSurface = Color(0xFF000000), onBackground = Color(0xFF000000),
            onSurfaceVariant = Color(0xFF636366), outlineVariant = Color(0xFFD1D1D6)
        ) else base.copy(
            background = Color.Black, surface = Color(0xFF1C1C1E),
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF111113),
            surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF38383A), surfaceVariant = Color(0xFF2C2C2E),
            onSurface = Color.White, onBackground = Color.White,
            onSurfaceVariant = Color(0xFFAEAEB2), outlineVariant = Color(0xFF3A3A3C)
        )
    }
    val accent = remember(accentColor) {
        runCatching { Color(android.graphics.Color.parseColor(accentColor)) }
            .getOrDefault(Color(0xFFFA243C))
    }
    val onAccent = if (accent.luminance() > .179f) Color.Black else Color.White
    val scheme = baseScheme.copy(primary = accent, secondary = accent, tertiary = accent,
        onPrimary = onAccent, onSecondary = onAccent, onTertiary = onAccent,
        primaryContainer = androidx.compose.ui.graphics.lerp(baseScheme.surface, accent, if (resolved == "LIGHT") .13f else .23f),
        secondaryContainer = androidx.compose.ui.graphics.lerp(baseScheme.surface, accent, if (resolved == "LIGHT") .13f else .23f),
        tertiaryContainer = androidx.compose.ui.graphics.lerp(baseScheme.surface, accent, if (resolved == "LIGHT") .13f else .23f),
        onPrimaryContainer = baseScheme.onSurface, onSecondaryContainer = baseScheme.onSurface,
        onTertiaryContainer = baseScheme.onSurface)
    val defaultFamily = AkiraFontFamily
    val family = remember(appearanceFont, appearanceFontId, defaultFamily) {
        when (appearanceFont) {
            "serif" -> FontFamily.Serif
            "custom" -> if (Regex("[0-9a-f]{64}").matches(appearanceFontId)) runCatching {
                FontFamily(androidx.compose.ui.text.font.Font(File(context.filesDir, "appearance-fonts/$appearanceFontId.ttf")))
            }.getOrDefault(defaultFamily) else defaultFamily
            else -> defaultFamily
        }
    }
    val type = remember(family) { PlayerTypography.copy(
        displayLarge = PlayerTypography.displayLarge.copy(fontFamily = family),
        displayMedium = PlayerTypography.displayMedium.copy(fontFamily = family),
        displaySmall = PlayerTypography.displaySmall.copy(fontFamily = family),
        headlineMedium = PlayerTypography.headlineMedium.copy(fontFamily = family),
        headlineSmall = PlayerTypography.headlineSmall.copy(fontFamily = family),
        titleSmall = PlayerTypography.titleSmall.copy(fontFamily = family),
        bodySmall = PlayerTypography.bodySmall.copy(fontFamily = family),
        labelLarge = PlayerTypography.labelLarge.copy(fontFamily = family),
        labelMedium = PlayerTypography.labelMedium.copy(fontFamily = family),
        headlineLarge = PlayerTypography.headlineLarge.copy(fontFamily = family),
        titleLarge = PlayerTypography.titleLarge.copy(fontFamily = family),
        titleMedium = PlayerTypography.titleMedium.copy(fontFamily = family),
        bodyLarge = PlayerTypography.bodyLarge.copy(fontFamily = family),
        bodyMedium = PlayerTypography.bodyMedium.copy(fontFamily = family),
        labelSmall = PlayerTypography.labelSmall.copy(fontFamily = family)
    ) }
    val density = LocalDensity.current
    val textScale = listOf(14f, 15f, 16f, 17f, 19f, 21f, 23f)[appearanceSizeIndex.coerceIn(0, 6)] / 17f
    MaterialTheme(
        colorScheme = scheme,
        typography = type
    ) {
        val base = LocalTextStyle.current
        val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current || com.akira.app.core.design.motion.InteractionPreferences.reducedMotion
        val delegateHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val haptics = remember(delegateHaptics) { com.akira.app.core.design.util.PreferenceHaptics(delegateHaptics) }
        CompositionLocalProvider(
            LocalAkiraFontFamily provides family,
            LocalDensity provides Density(density.density, density.fontScale * textScale),
            LocalRippleConfiguration provides null,
            com.akira.app.core.design.components.LocalContentMotionReduced provides reduced,
            androidx.compose.ui.platform.LocalHapticFeedback provides haptics,
            androidx.compose.foundation.LocalOverscrollFactory provides com.akira.app.core.design.motion.rememberAkiraBouncingFactory(),
            LocalThemeMode provides resolved,
            LocalAppearancePreset provides "MIDNIGHT",
            LocalSpaceMode provides false,
            LocalSpaceStyle provides "SPACE",
            LocalTextStyle provides base.copy(
                letterSpacing = 0.sp,
                lineHeight = 1.45.em,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            )
        ) {
            content()
        }
    }
}

/** No touch ripple; keyboard focus retains its own visible outline. */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    ).akiraFocusOutline(focused)
}
