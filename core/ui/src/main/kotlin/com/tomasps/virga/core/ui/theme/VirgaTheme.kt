package com.tomasps.virga.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Virga's theme. Dark is the primary design; light is a full scheme, not an inversion.
 * Colour roles beyond Material 3 are provided through [LocalVirgaSemanticColors].
 */
@Composable
fun VirgaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VirgaDarkColorScheme else VirgaLightColorScheme
    val semantic = if (darkTheme) VirgaDarkSemanticColors else VirgaLightSemanticColors
    CompositionLocalProvider(LocalVirgaSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VirgaTypography,
            shapes = VirgaShapes,
            content = content,
        )
    }
}
