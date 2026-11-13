package com.tomasps.virga.feature.d1.detail.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.TableSchema
import com.tomasps.virga.feature.d1.sql.statementValueLines
import com.tomasps.virga.feature.d1.detail.grid.resultGridItems
import com.tomasps.virga.feature.d1.detail.grid.rememberGridScrollState

/** Callbacks from the tables tab to its view model. */
data class TablesCallbacks(
    val onOpenTable: (String) -> Unit,
    val onCloseTable: () -> Unit,
    val onRefreshTables: () -> Unit,
    val onReloadRows: () -> Unit,
    val onLoadMoreRows: () -> Unit,
    val onSelectRow: (Int?) -> Unit,
    val onBeginInsert: () -> Unit,
    val onBeginEdit: (Int) -> Unit,
    val onRequestDelete: (Int) -> Unit,
    val onCellText: (String, String) -> Unit,
    val onCellNull: (String, Boolean) -> Unit,
    val onDismissEditor: () -> Unit,
    val onReviewEditor: () -> Unit,
    val onCancelWrite: () -> Unit,
    val onConfirmWrite: () -> Unit,
    val onDismissActionError: () -> Unit,
)

@Composable
internal fun TablesTab(state: TablesUiState, callbacks: TablesCallbacks) {
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val table = state.selectedTable
        if (table == null) {
            tableListItems(state.tables, callbacks)
        } else {
            tableDetailItems(table, state, callbacks, scrollState)
        }
    }
    TablesDialogs(state = state, callbacks = callbacks)
}

private fun LazyListScope.tableListItems(tables: UiState<List<String>>, callbacks: TablesCallbacks) {
    item(key = "tables-header") {
        SectionHeader(title = stringResource(R.string.d1_tables_title))
    }
    when (tables) {
        is UiState.Loading -> item(key = "tables-loading") {
            UiStateContent(state = tables) {}
        }
        is UiState.Failure -> item(key = "tables-failure") {
            UiStateContent(state = tables, onRetry = callbacks.onRefreshTables) {}
        }
        is UiState.Content -> {
            if (tables.data.isEmpty()) {
                item(key = "tables-empty") {
                    EmptyState(title = stringResource(R.string.d1_tables_empty_title), message = stringResource(R.string.d1_tables_empty_message))
                }
            }
            items(tables.data, key = { "table-$it" }) { name ->
                ListRow(headline = name, onClick = { callbacks.onOpenTable(name) })
            }
        }
    }
}

private fun LazyListScope.tableDetailItems(
    table: String,
    state: TablesUiState,
    callbacks: TablesCallbacks,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    item(key = "table-header") {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(table, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = callbacks.onCloseTable) { Text(stringResource(R.string.d1_all_tables)) }
        }
    }
    val schemaState = state.schema ?: UiState.Loading
    item(key = "table-schema") {
        UiStateContent(state = schemaState, onRetry = callbacks.onReloadRows) { schema -> SchemaSummary(schema) }
    }
    val canEdit = (schemaState as? UiState.Content)?.data?.canEditRows == true
    item(key = "table-actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            if (canEdit) {
                Button(onClick = callbacks.onBeginInsert) { Text(stringResource(R.string.d1_row_insert)) }
            }
            OutlinedButton(onClick = callbacks.onReloadRows) { Text(stringResource(R.string.d1_refresh)) }
        }
        if (!canEdit && schemaState is UiState.Content) {
            Text(stringResource(R.string.d1_browse_only_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    val rows = state.rows ?: UiState.Loading
    when (rows) {
        is UiState.Loading -> item(key = "rows-loading") { UiStateContent(state = rows) {} }
        is UiState.Failure -> item(key = "rows-failure") { UiStateContent(state = rows, onRetry = callbacks.onReloadRows) {} }
        is UiState.Content -> {
            if (rows.data.rows.isEmpty()) {
                item(key = "rows-empty") {
                    EmptyState(title = stringResource(R.string.d1_rows_empty_title), message = stringResource(R.string.d1_rows_empty_message))
                }
            }
            val onRowClick: ((Int) -> Unit)? = if (canEdit) ({ index: Int -> callbacks.onSelectRow(index) }) else null
            resultGridItems(
                columns = rows.data.columns,
                rows = rows.data.rows,
                scrollState = scrollState,
                onRowClick = onRowClick,
            )
            item(key = "rows-footer") {
                LoadMoreFooter(state = rows.data.loadMore, onLoadMore = callbacks.onLoadMoreRows)
            }
        }
    }
}

@Composable
private fun SchemaSummary(schema: TableSchema) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SectionHeader(title = stringResource(R.string.d1_columns_title))
        val columnRows = buildList<@Composable () -> Unit> {
            schema.columns.forEach { column ->
                add {
                    val flags = listOfNotNull(
                        if (column.pkPosition > 0) stringResource(R.string.d1_flag_primary_key) else null,
                        if (column.notNull) stringResource(R.string.d1_flag_not_null) else null,
                        column.defaultValue?.let { "${stringResource(R.string.d1_flag_default)} $it" },
                    ).joinToString(", ")
                    KeyValueRow(key = column.name, value = listOfNotNull(column.type.ifBlank { null }, flags.ifBlank { null }).joinToString(" · ").ifBlank { "-" })
                }
            }
        }
        SectionCard(rows = columnRows)
        if (schema.indexes.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.d1_indexes_title))
            schema.indexes.forEach { index ->
                KeyValueRow(
                    key = index.name,
                    value = (if (index.unique) stringResource(R.string.d1_flag_unique) + " · " else "") + index.columns.joinToString(", "),
                )
            }
        }
        if (schema.foreignKeys.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.d1_foreign_keys_title))
            schema.foreignKeys.forEach { fk ->
                KeyValueRow(key = fk.fromColumn, value = "${fk.table}.${fk.toColumn.orEmpty()}")
            }
        }
    }
}

@Composable
private fun TablesDialogs(state: TablesUiState, callbacks: TablesCallbacks) {
    val rowIndex = state.selectedRowIndex
    if (rowIndex != null) {
        AlertDialog(
            onDismissRequest = { callbacks.onSelectRow(null) },
            title = { Text(stringResource(R.string.d1_row_actions_title)) },
            text = { Text(stringResource(R.string.d1_row_actions_message)) },
            confirmButton = { TextButton(onClick = { callbacks.onBeginEdit(rowIndex) }) { Text(stringResource(R.string.d1_row_edit)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { callbacks.onRequestDelete(rowIndex) }) { Text(stringResource(R.string.d1_row_delete)) }
                    TextButton(onClick = { callbacks.onSelectRow(null) }) { Text(stringResource(R.string.d1_cancel)) }
                }
            },
        )
    }
    state.editor?.let { editor ->
        RowEditorDialog(
            editor = editor,
            onCellText = callbacks.onCellText,
            onCellNull = callbacks.onCellNull,
            onSave = callbacks.onReviewEditor,
            onDismiss = callbacks.onDismissEditor,
        )
    }
    state.pendingWrite?.let { pending ->
        val statement = pending.statement
        val values = statementValueLines(statement)
        val valuesBlock = if (values.isEmpty()) "" else "\n\n" + stringResource(R.string.d1_statement_values) + "\n" + values.joinToString("\n")
        ConfirmDialog(
            title = stringResource(writeTitle(pending.kind)),
            message = statement.sql + valuesBlock,
            destructive = pending.kind == WriteKind.DELETE,
            onConfirm = callbacks.onConfirmWrite,
            onDismiss = callbacks.onCancelWrite,
            confirmLabel = stringResource(R.string.d1_run_statement),
        )
    }
    state.actionError?.let { error ->
        AlertDialog(
            onDismissRequest = callbacks.onDismissActionError,
            title = { Text(error.title.asString()) },
            text = { Text(error.message.asString()) },
            confirmButton = { TextButton(onClick = callbacks.onDismissActionError) { Text(stringResource(R.string.d1_ok)) } },
        )
    }
}

private fun writeTitle(kind: WriteKind): Int = when (kind) {
    WriteKind.INSERT -> R.string.d1_write_insert_title
    WriteKind.UPDATE -> R.string.d1_write_update_title
    WriteKind.DELETE -> R.string.d1_write_delete_title
}
