package com.tomasps.virga.feature.d1.detail.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.json.JsonElement
import com.tomasps.virga.feature.d1.detail.tables.cellText

/** Width of every grid column. Cells are truncated, and the grid scrolls sideways instead of wrapping. */
val GRID_CELL_WIDTH: Dp = 140.dp

/**
 * Rows of a result grid as lazy items. All rows share one [androidx.compose.foundation.ScrollState], so the header
 * and every row scroll sideways together. [onRowClick] receives the row index, or null for a read-only grid.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.resultGridItems(
    columns: List<String>,
    rows: List<List<JsonElement>>,
    scrollState: androidx.compose.foundation.ScrollState,
    onRowClick: ((Int) -> Unit)? = null,
    rowKey: ((Int) -> Any)? = null,
) {
    if (columns.isEmpty()) return
    item(key = "grid-header") {
        GridLine(cells = columns, scrollState = scrollState, header = true, onClick = null)
        HorizontalDivider()
    }
    itemsIndexed(rows, key = { index, _ -> rowKey?.invoke(index) ?: index }) { index, row ->
        GridLine(
            cells = List(columns.size) { cellText(row.getOrNull(it)) ?: NULL_DISPLAY },
            scrollState = scrollState,
            header = false,
            onClick = onRowClick?.let { { it(index) } },
        )
        HorizontalDivider()
    }
}

@Composable
private fun GridLine(
    cells: List<String>,
    scrollState: androidx.compose.foundation.ScrollState,
    header: Boolean,
    onClick: (() -> Unit)?,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(scrollState)
        .let { if (header) it.background(MaterialTheme.colorScheme.surfaceVariant) else it }
        .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
    Row(modifier = rowModifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        cells.forEachIndexed { index, text ->
            Text(
                text = text,
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(GRID_CELL_WIDTH).padding(end = 8.dp),
            )
        }
    }
}

/** How SQL NULL is shown in a grid. Kept distinct from an empty string. */
const val NULL_DISPLAY = "NULL"

@Composable
fun rememberGridScrollState() = rememberScrollState()
