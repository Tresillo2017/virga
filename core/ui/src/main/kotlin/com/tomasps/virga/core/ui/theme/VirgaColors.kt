package com.tomasps.virga.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkBackground = Color(0xFF0B0E14)
private val DarkSurfaceContainer = Color(0xFF141923)
private val DarkAccent = Color(0xFF8E9BFF)
private val LightAccent = Color(0xFF3B55D9)

val VirgaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBackground,
    primaryContainer = Color(0xFF2A3366),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFB6BDD0),
    onSecondary = Color(0xFF1E2430),
    secondaryContainer = Color(0xFF262D3D),
    onSecondaryContainer = Color(0xFFDCE2F5),
    tertiary = Color(0xFF7FD6C6),
    onTertiary = Color(0xFF00201B),
    tertiaryContainer = Color(0xFF004D42),
    onTertiaryContainer = Color(0xFFA9F0E1),
    error = Color(0xFFFF8C94),
    onError = Color(0xFF3C0A0F),
    errorContainer = Color(0xFF5C1A21),
    onErrorContainer = Color(0xFFFFDADD),
    background = DarkBackground,
    onBackground = Color(0xFFE4E8F2),
    surface = DarkBackground,
    onSurface = Color(0xFFE4E8F2),
    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFFA9B1C3),
    surfaceContainerLowest = Color(0xFF080A0F),
    surfaceContainerLow = Color(0xFF10141C),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = Color(0xFF1B2130),
    surfaceContainerHighest = Color(0xFF232A3A),
    outline = Color(0xFF5C6478),
    outlineVariant = Color(0xFF2F3648),
)

val VirgaLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0B1A6B),
    secondary = Color(0xFF545C70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE1F0),
    onSecondaryContainer = Color(0xFF151B2B),
    tertiary = Color(0xFF00695C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA9F0E1),
    onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF14171F),
    surface = Color(0xFFF7F8FB),
    onSurface = Color(0xFF14171F),
    surfaceVariant = Color(0xFFE0E4EE),
    onSurfaceVariant = Color(0xFF444B5C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFEBEEF5),
    surfaceContainerHigh = Color(0xFFE4E8F1),
    surfaceContainerHighest = Color(0xFFDDE2EC),
    outline = Color(0xFF747B8C),
    outlineVariant = Color(0xFFC4C9D6),
)

/** Colours that Material 3 has no slot for: status tones and chart series. */
@Immutable
data class VirgaSemanticColors(
    val success: Color,
    val warning: Color,
    val neutral: Color,
    val chartSeries: List<Color>,
    /** Brand accent used by the mark gradient (kept here so the mark and the theme agree). */
    val accent: Color,
)

val VirgaDarkSemanticColors = VirgaSemanticColors(
    success = Color(0xFF7EDC9A),
    warning = Color(0xFFE6D27A),
    neutral = Color(0xFF8A93A6),
    chartSeries = listOf(
        Color(0xFF8E9BFF),
        Color(0xFF6FD3C2),
        Color(0xFFC49BFF),
        Color(0xFF7FB8FF),
        Color(0xFFD5DAE8),
    ),
    accent = DarkAccent,
)

val VirgaLightSemanticColors = VirgaSemanticColors(
    success = Color(0xFF1E7A46),
    warning = Color(0xFF6B5A00),
    neutral = Color(0xFF5C6476),
    chartSeries = listOf(
        Color(0xFF3B55D9),
        Color(0xFF00796B),
        Color(0xFF7B4FD0),
        Color(0xFF1F6FB8),
        Color(0xFF4A5468),
    ),
    accent = LightAccent,
)

val LocalVirgaSemanticColors = staticCompositionLocalOf { VirgaDarkSemanticColors }
