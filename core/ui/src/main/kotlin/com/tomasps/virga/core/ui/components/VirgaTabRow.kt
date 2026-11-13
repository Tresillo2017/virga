package com.tomasps.virga.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val TAB_EDGE_PADDING = 16.dp

/**
 * Scrollable tab strip for screens with more sections than fit the width (Worker detail: overview, metrics,
 * deployments, logs, domains, settings). Renders nothing for an empty list. A [selectedIndex] outside the list
 * is clamped to the nearest tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirgaTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(tabs.indices),
        modifier = modifier,
        edgePadding = TAB_EDGE_PADDING,
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(title, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
