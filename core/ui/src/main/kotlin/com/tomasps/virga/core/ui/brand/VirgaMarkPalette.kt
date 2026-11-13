package com.tomasps.virga.core.ui.brand

import androidx.compose.ui.graphics.Color

/**
 * Gradient stops for one streak, top to bottom. The gradient spans the streak's full extent,
 * so a partly drawn streak shows the same colours as a finished one.
 */
internal data class GradientStops(val top: Color, val middle: Color)

internal object VirgaMarkPalette {
    private val DARK_TOP = Color(0xFF9DB8FF)
    private val DARK_MIDDLE = Color(0xFF8E9BFF)
    private val LIGHT_TOP = Color(0xFF3B55D9)
    private val LIGHT_MIDDLE = Color(0xFF4A45C9)

    fun stops(isDark: Boolean): GradientStops =
        if (isDark) GradientStops(DARK_TOP, DARK_MIDDLE) else GradientStops(LIGHT_TOP, LIGHT_MIDDLE)
}
