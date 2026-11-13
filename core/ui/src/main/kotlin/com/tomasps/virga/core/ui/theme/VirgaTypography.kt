package com.tomasps.virga.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Typography with Material 3 defaults, tuned so numeric stats read as aligned columns. */
val VirgaTypography: Typography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular (fixed-width) digits, so values in a column align and do not jitter as they change. */
const val TABULAR_NUMBERS = "tnum"

/** Style for a stat value such as a request count or a percentage. */
fun statValueStyle(base: TextStyle): TextStyle =
    base.copy(fontFeatureSettings = TABULAR_NUMBERS, fontWeight = FontWeight.SemiBold)
