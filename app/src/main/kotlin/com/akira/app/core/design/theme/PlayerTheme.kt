package com.akira.app.core.design.theme

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

/**
 * Modo de tema resuelto, legible desde cualquier pantalla.
 *
 * Antes `themeMode` se guardaba en DataStore y no lo leía nadie: el tema estaba
 * fijado a DarkColorScheme. Con este CompositionLocal las pantallas que pintan
 * su propio fondo (Ajustes, biblioteca) pueden respetar el modo sin tener que
 * recibir el parámetro a mano.
 */
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
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val resolved = resolveThemeMode(themeMode, dark)
    // Classic appearance replaces the old theme families. Existing stored selections
    // remain readable but no longer tint backgrounds or controls.
    val scheme = if (resolved == "LIGHT") LightColorScheme else AmoledColorScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = PlayerTypography
    ) {
        val base = LocalTextStyle.current
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
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

/**
 * Clickable limpio y directo sin indicador visual ripple.
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
