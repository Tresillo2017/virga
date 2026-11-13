package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.data.KvKeyInfo
import com.tomasps.virga.feature.kv.namespace.KvNamespaceUiState

private val LIST_PADDING = 16.dp
private val BAR_PADDING = 8.dp

/**
 * The key list of a namespace: prefix search, a selection bar for bulk delete, and paging at the bottom. A tap opens a
 * key, or toggles it while something is selected. A long press starts a selection.
 */
@Composable
internal fun KvKeysTab(
    state: KvNamespaceUiState,
    onPrefixChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onOpenKey: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRequestBulkDelete: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SearchField(
                query = state.prefix,
                onQueryChange = onPrefixChange,
                modifier = Modifier.padding(LIST_PADDING),
                placeholder = stringResource(R.string.kv_prefix_placeholder),
            )
        }
        if (state.selected.isNotEmpty()) {
            item {
                SelectionBar(
                    count = state.selected.size,
                    busy = state.isBusy,
                    onClear = onClearSelection,
                    onDelete = onRequestBulkDelete,
                )
            }
        }
        when (val keys = state.keys) {
            null -> Unit
            is UiState.Loading -> item { LoadingState() }
            is UiState.Failure -> item { ErrorState(error = keys.error, onRetry = onRetry) }
            is UiState.Content -> keyItems(
                keys = keys.data,
                prefix = state.prefix,
                selected = state.selected,
                loadMore = state.loadMore,
                onLoadMore = onLoadMore,
                onToggleSelected = onToggleSelected,
                onOpenKey = onOpenKey,
            )
        }
        item {
            OpenInDashboardRow(
                description = stringResource(R.string.kv_dashboard_search_note),
                onOpen = onOpenDashboard,
                modifier = Modifier.padding(horizontal = LIST_PADDING),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.keyItems(
    keys: List<KvKeyInfo>,
    prefix: String,
    selected: Set<String>,
    loadMore: com.tomasps.virga.core.ui.components.LoadMoreState,
    onLoadMore: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onOpenKey: (String) -> Unit,
) {
    if (keys.isEmpty()) {
        item {
            EmptyState(
                title = stringResource(
                    if (prefix.isEmpty()) R.string.kv_keys_empty_title else R.string.kv_keys_prefix_empty_title,
                ),
                message = stringResource(
                    if (prefix.isEmpty()) R.string.kv_keys_empty_message else R.string.kv_keys_prefix_empty_message,
                ),
            )
        }
    } else {
        items(keys, key = { it.name }) { key ->
            KeyRow(
                key = key,
                selecting = selected.isNotEmpty(),
                isSelected = key.name in selected,
                onToggle = { onToggleSelected(key.name) },
                onOpen = { onOpenKey(key.name) },
            )
        }
    }
    item { LoadMoreFooter(state = loadMore, onLoadMore = onLoadMore) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyRow(
    key: KvKeyInfo,
    selecting: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val supporting = keyRowSupporting(
        expiresAt = key.expirationEpochSeconds,
        hasMetadata = key.metadata != null,
        expiresLabel = stringResource(R.string.kv_key_row_expires),
        noExpiryLabel = stringResource(R.string.kv_key_row_no_expiry),
        metadataLabel = stringResource(R.string.kv_key_row_has_metadata),
    )
    ListRow(
        headline = key.name,
        supporting = supporting,
        leading = if (selecting) {
            { Checkbox(checked = isSelected, onCheckedChange = null) }
        } else {
            null
        },
        modifier = Modifier.combinedClickable(
            onClick = { if (selecting) onToggle() else onOpen() },
            onLongClick = onToggle,
        ),
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    busy: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LIST_PADDING, vertical = BAR_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(R.plurals.kv_selected_keys, count, count),
            style = MaterialTheme.typography.titleSmall,
        )
        Row {
            TextButton(onClick = onClear) { Text(stringResource(R.string.kv_action_clear_selection)) }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(
                    text = stringResource(R.string.kv_action_delete_selected),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
