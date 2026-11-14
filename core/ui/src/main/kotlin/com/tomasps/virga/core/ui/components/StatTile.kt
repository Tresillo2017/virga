package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.chart.Sparkline
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors
import com.tomasps.virga.core.ui.theme.statValueStyle

/** Which colour a change is drawn in. The caller decides whether "up" is good for that metric. */
enum class DeltaTone { Positive, Negative, Neutral }

/** Change text under a stat, such as "+12.4% vs previous 24h". The text is formatted by the caller. */
data class StatDelta(
    val text: String,
    val tone: DeltaTone = DeltaTone.Neutral,
)

private val TILE_PADDING = 12.dp
private val TEXT_GAP = 4.dp
private val SPARKLINE_GAP = 6.dp

/**
 * A metric card: label, value (tabular digits), optional delta and optional trend line.
 * The whole card is one accessibility element, read as "label, value, delta".
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: StatDelta? = null,
    sparkline: List<Double>? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(TILE_PADDING)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(TEXT_GAP))
        Text(
            text = value,
            style = statValueStyle(MaterialTheme.typography.titleLarge),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (delta != null) {
            Spacer(Modifier.height(TEXT_GAP))
            Text(
                text = delta.text,
                style = MaterialTheme.typography.labelMedium,
                color = toneColor(delta.tone),
            )
        }
        if (!sparkline.isNullOrEmpty()) {
            Sparkline(
                values = sparkline,
                modifier = Modifier.padding(top = SPARKLINE_GAP),
            )
        }
    }
}

@Composable
private fun toneColor(tone: DeltaTone): Color = when (tone) {
    DeltaTone.Positive -> LocalVirgaSemanticColors.current.success
    DeltaTone.Negative -> MaterialTheme.colorScheme.error
    DeltaTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}
