package com.akira.app.feature.setup

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.brand.AkiraBrandFont
import com.akira.app.core.design.theme.LocalThemeMode

/** Onboarding inherits the app's palette, accent and control typography. */
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
    val inherited = MaterialTheme.typography
    MaterialTheme(typography = inherited.copy(
        displayLarge = SetupTypography.displayLarge, displayMedium = SetupTypography.displayMedium,
        displaySmall = SetupTypography.displaySmall, headlineLarge = SetupTypography.headlineLarge,
        headlineMedium = SetupTypography.headlineMedium, headlineSmall = SetupTypography.headlineSmall),
        content = content)
}
