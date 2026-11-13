package com.tomasps.virga.core.ui.brand

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.tomasps.virga.core.ui.R

/**
 * The word "Virga" in the display style. [color] defaults to the theme's on-background colour.
 * [alpha] lets the splash fade the wordmark in without changing its layout.
 */
@Composable
fun VirgaWordmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    alpha: Float = 1f,
) {
    Text(
        text = stringResource(R.string.core_ui_brand_name),
        modifier = modifier.alpha(alpha),
        color = color,
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.02.em,
        ),
    )
}

/** Mark and wordmark side by side, for app bars and the About screen. */
@Composable
fun VirgaLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 32.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        VirgaMark(modifier = Modifier.size(markSize))
        Spacer(Modifier.width(8.dp))
        VirgaWordmark()
    }
}
