package com.tomasps.virga.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Virga's theme: Material 3 Expressive, with the expressive (spring) motion scheme. Dark is the primary design; light
 * is a full scheme, not an inversion. Colour roles beyond Material 3 are provided through [LocalVirgaSemanticColors].
 *
 * [dynamicColor] takes the palette from the wallpaper on Android 12 and newer, and is ignored below that.
 * [pureBlack] turns the dark theme's background and surfaces true black, for OLED screens; the light theme ignores it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VirgaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = remember(darkTheme, useDynamic, pureBlack, context) {
        val base = when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            darkTheme -> VirgaDarkColorScheme
            else -> VirgaLightColorScheme
        }
        if (darkTheme && pureBlack) base.toPureBlack() else base
    }
    val semantic = if (darkTheme) VirgaDarkSemanticColors else VirgaLightSemanticColors
    CompositionLocalProvider(LocalVirgaSemanticColors provides semantic) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = VirgaTypography,
            shapes = VirgaShapes,
            content = content,
        )
    }
}

/**
 * True black background and base surface. Containers stay a step above black, so cards and bars still separate from
 * the page without shadows.
 */
private fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)
