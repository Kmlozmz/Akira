package com.akira.app.feature.setup

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.brand.AkiraBrandFont
import com.akira.app.core.design.theme.LocalThemeMode

/** A neutral canvas and one functional accent keep every onboarding stage in the same identity. */
private val SetupLightColors = lightColorScheme(
    primary = Color(0xFFCB1731), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6EB), onPrimaryContainer = Color(0xFF8E1024),
    secondary = Color(0xFFCB1731), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8ED), onSecondaryContainer = Color(0xFF17171B),
    background = Color.White, onBackground = Color(0xFF111114),
    surface = Color.White, onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE8E8ED), onSurfaceVariant = Color(0xFF62626E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color(0xFFF2F2F5), surfaceContainerHigh = Color(0xFFE7E7EC),
    surfaceContainerHighest = Color(0xFFDADAE2), outline = Color.Transparent,
    outlineVariant = Color.Transparent
)
private val SetupDarkColors = darkColorScheme(
    primary = Color(0xFFFF687C), onPrimary = Color(0xFF1C080D),
    primaryContainer = Color(0xFF36151E), onPrimaryContainer = Color(0xFFFFB8C3),
    secondary = Color(0xFFFF687C), onSecondary = Color(0xFF1C080D),
    secondaryContainer = Color(0xFF28282F), onSecondaryContainer = Color.White,
    background = Color(0xFF08080A), onBackground = Color(0xFFF9F9FC),
    surface = Color(0xFF08080A), onSurface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFF292930), onSurfaceVariant = Color(0xFFB6B6C1),
    surfaceContainerLowest = Color(0xFF08080A), surfaceContainerLow = Color(0xFF111114),
    surfaceContainer = Color(0xFF18181D), surfaceContainerHigh = Color(0xFF28282F),
    surfaceContainerHighest = Color(0xFF393941), outline = Color.Transparent,
    outlineVariant = Color.Transparent
)
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontFamily = AkiraBrandFont, fontSize = size.sp, lineHeight = line.sp,
        fontWeight = weight, letterSpacing = spacing.sp)
private val SetupTypography = Typography(
    displayLarge = type(48, 54, FontWeight.Bold, -2f),
    displayMedium = type(42, 48, FontWeight.Bold, -1.6f),
    displaySmall = type(36, 42, FontWeight.Bold, -1.2f),
    headlineLarge = type(34, 39, FontWeight.Bold, -1.1f),
    headlineMedium = type(28, 34, FontWeight.Bold, -.8f),
    headlineSmall = type(24, 30, FontWeight.SemiBold, -.6f),
    titleLarge = type(21, 28, FontWeight.Bold, -.4f),
    titleMedium = type(17, 24, FontWeight.SemiBold, -.2f),
    titleSmall = type(15, 22, FontWeight.SemiBold),
    bodyLarge = type(16, 24), bodyMedium = type(14, 21), bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.SemiBold, .4f),
    labelSmall = type(11, 16, FontWeight.SemiBold, .8f)
)

@Composable internal fun SetupBrandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (LocalThemeMode.current == "LIGHT") SetupLightColors else SetupDarkColors,
        typography = SetupTypography, content = content)
}
