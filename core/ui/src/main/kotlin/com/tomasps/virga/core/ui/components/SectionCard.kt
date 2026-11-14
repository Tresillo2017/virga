package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.tomasps.virga.core.ui.theme.SegmentedGroupGap
import com.tomasps.virga.core.ui.theme.segmentedShape

/**
 * A grouped card as an expressive segmented list: each row is its own segment on a surface container, with a small
 * gap between segments. The first and last segment round their outer corners; the joins stay small.
 * Use it for a short fixed group (a status card, a few facts). Long lists belong in a LazyColumn, with one card per
 * visual group. Empty [rows] draws nothing.
 */
@Composable
fun SectionCard(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val outerCorner = MaterialTheme.shapes.large.topStart
    val lastIndex = rows.lastIndex
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SegmentedGroupGap),
    ) {
        rows.forEachIndexed { index, row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(segmentedShape(isFirst = index == 0, isLast = index == lastIndex, outer = outerCorner))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                row()
            }
        }
    }
}
