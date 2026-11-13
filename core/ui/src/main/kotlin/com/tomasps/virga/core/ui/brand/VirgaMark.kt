package com.tomasps.virga.core.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.R
import com.tomasps.virga.core.ui.theme.isDarkTone

/** Default on-screen size of the mark. */
val VirgaMarkDefaultSize = 64.dp

/**
 * The Virga mark: three slanted streaks of uneven length that fade toward their lower end.
 *
 * [streakProgress] holds one draw fraction per streak (0 = not drawn, 1 = full length), in streak order.
 * Missing entries count as fully drawn. A streak with progress 0 is not drawn at all, so a round cap never
 * shows as a dot. The gradient belongs to the whole streak, so a partly drawn streak does not shift its colours.
 */
@Composable
fun VirgaMark(
    modifier: Modifier = Modifier.size(VirgaMarkDefaultSize),
    streakProgress: List<Float> = FullStreakProgress,
    contentDescription: String? = stringResource(R.string.core_ui_logo_description),
) {
    val isDark = MaterialTheme.colorScheme.background.isDarkTone()
    val stops = VirgaMarkPalette.stops(isDark)
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(semanticsModifier)) {
        val canvasSize = size.minDimension
        val scale = VirgaMarkGeometry.gridScale(canvasSize)
        val strokeWidth = VirgaMarkGeometry.strokeWidthPx(canvasSize)
        VirgaMarkGeometry.STREAKS.forEachIndexed { index, streak ->
            val progress = VirgaMarkGeometry.clampProgress(streakProgress.getOrElse(index) { 1f })
            if (progress <= 0f) return@forEachIndexed
            val brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to stops.top,
                    0.5f to stops.middle,
                    1f to Color.Transparent,
                ),
                startY = streak.startY * scale,
                endY = streak.endY * scale,
            )
            val (endX, endY) = VirgaMarkGeometry.partialEnd(streak, progress)
            drawLine(
                brush = brush,
                start = Offset(streak.startX * scale, streak.startY * scale),
                end = Offset(endX * scale, endY * scale),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Progress list for a mark that is fully drawn. */
val FullStreakProgress: List<Float> = List(VirgaMarkGeometry.STREAK_COUNT) { 1f }
