package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val CARD_CORNER = 14.dp
private val DIVIDER_INSET = 16.dp

/**
 * A grouped card: rows on a rounded surface, with a hairline divider between them. Use it for a short fixed group
 * (a status card, a few facts). Long lists belong in a LazyColumn, with one card per visual group.
 * Empty [rows] draws nothing.
 */
@Composable
fun SectionCard(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = DIVIDER_INSET),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            row()
        }
    }
}
