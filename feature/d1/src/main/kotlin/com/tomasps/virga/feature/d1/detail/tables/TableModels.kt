package com.tomasps.virga.feature.d1.detail.tables

import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.feature.d1.data.TableSchema
import com.tomasps.virga.feature.d1.sql.BoundStatement
import kotlinx.serialization.json.JsonElement

/** One page of rows shown in the grid. Pages append; the offset is the number of rows already loaded. */
data class RowsContent(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val hasMore: Boolean,
    val isLoadingMore: Boolean = false,
    val moreFailed: Boolean = false,
) {
    val loadMore: LoadMoreState
        get() = loadMoreStateOf(hasMore = hasMore, isLoading = isLoadingMore, hasFailed = moreFailed)
}

/**
 * One column in the row editor. [isNull] writes SQL NULL; a blank text on insert leaves the column to its default.
 * [original] is the value the row had when the editor opened (null for SQL NULL), so an edit writes only real changes.
 */
data class CellDraft(
    val column: String,
    val text: String = "",
    val isNull: Boolean = false,
    val isKey: Boolean = false,
    val original: String? = null,
) {
    val isChanged: Boolean
        get() = if (isNull) original != null else original == null || text != original
}

/** The editor for one row. [keyValues] is the original key of an existing row; null for an insert. */
data class RowEditorState(
    val cells: List<CellDraft>,
    val keyValues: Map<String, String?>? = null,
) {
    val isInsert: Boolean get() = keyValues == null

    /** Non-key columns whose value differs from the original row. */
    val changedCells: List<CellDraft> get() = cells.filter { !it.isKey && it.isChanged }
}

enum class WriteKind { INSERT, UPDATE, DELETE }

/** A write waiting for confirmation. The statement is shown to the user exactly as it will run. */
data class PendingWrite(val kind: WriteKind, val statement: BoundStatement)

data class TablesUiState(
    val tables: UiState<List<String>> = UiState.Loading,
    val selectedTable: String? = null,
    val schema: UiState<TableSchema>? = null,
    val rows: UiState<RowsContent>? = null,
    val selectedRowIndex: Int? = null,
    val editor: RowEditorState? = null,
    val pendingWrite: PendingWrite? = null,
    val isWriting: Boolean = false,
    val actionError: ErrorUi? = null,
)
