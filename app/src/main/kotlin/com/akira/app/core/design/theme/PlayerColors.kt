package com.akira.app.core.design.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Neutral dark palette used by player surfaces and controls. */
object AkiraColors {
    // Background and Surface Containers (Pure AMOLED Hierarchy)
    val Background = Color(0xFF000000)
    val SurfaceLowest = Color(0xFF000000)
    val SurfaceLow = Color(0xFF0A0A0E)
    val SurfaceContainer = Color(0xFF0C0C10)
    val SurfaceHigh = Color(0xFF16161C)
    val SurfaceHighest = Color(0xFF22222A)

    // Contrast & Typography
    val OnSurface = Color(0xFFEDEDED)
    val OnSurfaceVariant = Color(0xFFC2C3CB)
    val Outline = Color(0xFF27272A)
    val OutlineVariant = Color(0xFF3F3F46)

    // Primary: Crisp High-Contrast Pure White
    val Primary = Color(0xFFFFFFFF)
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFF1E1E24)
    val OnPrimaryContainer = Color(0xFFFFFFFF)

    // Secondary: Platinum Zinc Slate
    val Secondary = Color(0xFFD4D4D8)
    val OnSecondary = Color(0xFF18181B)
    val SecondaryContainer = Color(0xFF27272A)
    val OnSecondaryContainer = Color(0xFFF4F4F5)

    // Tertiary: Fine Chrome Highlight
    val Tertiary = Color(0xFFE4E4E7)
    val OnTertiary = Color(0xFF09090B)
    val TertiaryContainer = Color(0xFF27272A)
    val OnTertiaryContainer = Color(0xFFFAFAFA)

    // Errors
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)

    // Categorical Artist Accents
    val ArtistPalette = listOf(
        Color(0xFF7CD5FF), Color(0xFFA6C8FF), Color(0xFF3FD17A), Color(0xFF2FD1BE),
        Color(0xFFFF9A4D), Color(0xFFF2B632), Color(0xFFB18CFF), Color(0xFFF27CC0),
        Color(0xFF58A6FF), Color(0xFF10B8AC), Color(0xFFFF6B5E), Color(0xFF8A93FF)
    )
    fun artistColor(index: Int): Color = ArtistPalette[Math.floorMod(index, ArtistPalette.size)]
}

/** Categorical artwork and genre tones. */
enum class PlayerTone(val hex: Long) {
    CIAN(0xFF7CD5FF),
    AZUL(0xFFA6C8FF),
    TURQUESA(0xFF2FD1BE),
    VERDE(0xFF3FD17A),
    AMBAR(0xFFF2B632),
    NARANJA(0xFFFF9A4D),
    ROJO(0xFFFF6B5E),
    INDIGO(0xFF8A93FF),
    VIOLETA(0xFFB18CFF),
    ROSA(0xFFF27CC0),
    GRIS(0xFFB8C8D4);

    val color: Color
        @Composable @ReadOnlyComposable get() = Color(hex)
}

/** Blend sRGB color with a tonal base. */
fun mix(c: Color, p: Float, base: Color): Color = c.copy(alpha = p).compositeOver(base)

val DarkColorScheme = darkColorScheme(
    primary = AkiraColors.Primary,
    onPrimary = AkiraColors.OnPrimary,
    primaryContainer = AkiraColors.PrimaryContainer,
    onPrimaryContainer = AkiraColors.OnPrimaryContainer,
    secondary = AkiraColors.Secondary,
    onSecondary = AkiraColors.OnSecondary,
    secondaryContainer = AkiraColors.SecondaryContainer,
    onSecondaryContainer = AkiraColors.OnSecondaryContainer,
    tertiary = AkiraColors.Tertiary,
    onTertiary = AkiraColors.OnTertiary,
    tertiaryContainer = AkiraColors.TertiaryContainer,
    onTertiaryContainer = AkiraColors.OnTertiaryContainer,
    background = AkiraColors.Background,
    onBackground = AkiraColors.OnSurface,
    surface = AkiraColors.Background,
    onSurface = AkiraColors.OnSurface,
    surfaceVariant = AkiraColors.SurfaceContainer,
    onSurfaceVariant = AkiraColors.OnSurfaceVariant,
    surfaceContainerLowest = AkiraColors.SurfaceLowest,
    surfaceContainerLow = AkiraColors.SurfaceLow,
    surfaceContainer = AkiraColors.SurfaceContainer,
    surfaceContainerHigh = AkiraColors.SurfaceHigh,
    surfaceContainerHighest = AkiraColors.SurfaceHighest,
    outline = AkiraColors.Outline,
    outlineVariant = AkiraColors.OutlineVariant,
    inverseSurface = Color(0xFFF2F2F5), inverseOnSurface = Color(0xFF25262D), inversePrimary = Color(0xFF202127),
    surfaceTint = Color.Transparent,
    error = AkiraColors.Error, onError = Color(0xFF5F110B),
    errorContainer = AkiraColors.ErrorContainer,
    onErrorContainer = AkiraColors.OnErrorContainer
)

/** Elevate the dark background while preserving surface tokens and their tonal hierarchy; AMOLED retains pure black. */
private val DarkBackground = Color(0xFF0B0B10)

/** Esquema AMOLED: negro absoluto en fondo y superficies (comportamiento original). */
val AmoledColorScheme = DarkColorScheme

/** Esquema Oscuro: fondo ligeramente elevado, superficies intactas. */
val ElevatedDarkColorScheme = DarkColorScheme.copy(
    background = DarkBackground,
    surface = DarkBackground
)

val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF202127), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7EC), onPrimaryContainer = Color(0xFF191B21),
    tertiary = Color(0xFFC91432), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1E6), onTertiaryContainer = Color(0xFF790B21),
    inverseSurface = Color(0xFF292A30), inverseOnSurface = Color(0xFFF5F5F7), inversePrimary = Color(0xFFF2F2F5),
    surfaceTint = Color.Transparent,
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color.White, onBackground = Color(0xFF191B21),
    surface = Color.White, onSurface = Color(0xFF191B21),
    surfaceVariant = Color(0xFFE8EAF0), onSurfaceVariant = Color(0xFF51545F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F1F5),
    surfaceContainer = Color(0xFFECEEF3), surfaceContainerHigh = Color(0xFFE4E7EF),
    surfaceContainerHighest = Color(0xFFDDE0E9),
    outline = Color(0xFF898D99), outlineVariant = Color(0xFFD3D6DF),
    secondary = Color(0xFF555862), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7EC), onSecondaryContainer = Color(0xFF303139)
)
