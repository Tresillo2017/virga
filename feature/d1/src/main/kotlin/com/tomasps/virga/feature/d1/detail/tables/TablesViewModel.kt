package com.tomasps.virga.feature.d1.detail.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.data.RowPage
import com.tomasps.virga.feature.d1.data.TableCatalogRepository
import com.tomasps.virga.feature.d1.data.TableRowsRepository
import com.tomasps.virga.feature.d1.data.TableSchema
import com.tomasps.virga.feature.d1.sql.ColumnValue
import com.tomasps.virga.feature.d1.sql.deleteStatement
import com.tomasps.virga.feature.d1.sql.insertStatement
import com.tomasps.virga.feature.d1.sql.updateStatement
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Table list, schema, a paged row grid, and row insert, edit and delete. Writes are built here as parameterised
 * statements, shown to the user, and only run after [confirmWrite]. Tables without a primary key are browse-only.
 *
 * Every table or row load carries a generation number. Switching tables, closing one, or reloading bumps it, so a
 * late page from an earlier request is dropped instead of being appended to the table now on screen.
 */
class TablesViewModel(
    session: SessionRepository,
    private val databaseId: String,
    private val catalog: TableCatalogRepository,
    private val rowsRepository: TableRowsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(TablesUiState())
    val state: StateFlow<TablesUiState> = mutableState.asStateFlow()

    private var accountId: String? = null
    private var rowsGeneration = 0
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                invalidateRows()
                mutableState.update { TablesUiState() }
                loadTables(id)
            }
        }
    }

    fun refreshTables() {
        accountId?.let { id -> viewModelScope.launch { loadTables(id) } }
    }

    fun openTable(table: String) {
        val id = accountId ?: return
        invalidateRows()
        val generation = rowsGeneration
        mutableState.update { TablesUiState(tables = it.tables, selectedTable = table, schema = UiState.Loading, rows = UiState.Loading) }
        viewModelScope.launch { loadTable(id, table, generation) }
    }

    fun closeTable() {
        invalidateRows()
        mutableState.update { it.copy(selectedTable = null, schema = null, rows = null, editor = null, pendingWrite = null, selectedRowIndex = null) }
    }

    fun reloadRows() {
        val id = accountId ?: return
        val table = mutableState.value.selectedTable ?: return
        invalidateRows()
        val generation = rowsGeneration
        viewModelScope.launch {
            mutableState.update { it.copy(rows = UiState.Loading) }
            loadFirstRows(id, table, generation)
        }
    }

    fun loadMoreRows() {
        val id = accountId ?: return
        val table = mutableState.value.selectedTable ?: return
        val content = (mutableState.value.rows as? UiState.Content)?.data ?: return
        if (!content.hasMore || content.isLoadingMore) return
        setRows { it.copy(isLoadingMore = true, moreFailed = false) }
        val generation = rowsGeneration
        val offset = content.rows.size
        val orderBy = orderKeys()
        loadMoreJob = viewModelScope.launch {
            val result = rowsRepository.page(id, databaseId, table, offset = offset, pageRows = D1Limits.TABLE_PAGE_ROWS, orderBy = orderBy)
            if (generation != rowsGeneration) return@launch
            when (result) {
                is ApiResult.Success -> setRows { current ->
                    current.copy(rows = current.rows + result.value.rows, hasMore = result.value.hasMore, isLoadingMore = false)
                }
                is ApiResult.Failure -> setRows { it.copy(isLoadingMore = false, moreFailed = true) }
            }
        }
    }

    fun selectRow(index: Int?) = mutableState.update { it.copy(selectedRowIndex = index) }

    fun beginInsert() {
        val schema = schemaOrNull()?.takeIf { it.canEditRows } ?: return
        val cells = schema.columns.map { CellDraft(column = it.name, isKey = it.pkPosition > 0) }
        mutableState.update { it.copy(editor = RowEditorState(cells = cells), selectedRowIndex = null) }
    }

    fun beginEdit(index: Int) {
        val schema = schemaOrNull()?.takeIf { it.canEditRows } ?: return
        val content = (mutableState.value.rows as? UiState.Content)?.data ?: return
        val row = content.rows.getOrNull(index) ?: return
        if (row.any { it !is JsonPrimitive }) {
            mutableState.update { it.copy(actionError = rowNotEditableError, selectedRowIndex = null) }
            return
        }
        val cells = rowCells(schema, content.columns, row)
        val keys = schema.primaryKey.associateWith { key -> cellValue(content.columns, row, key) }
        mutableState.update { it.copy(editor = RowEditorState(cells = cells, keyValues = keys), selectedRowIndex = null) }
    }

    fun onCellText(column: String, text: String) = updateCell(column) { it.copy(text = text, isNull = false) }

    fun onCellNull(column: String, isNull: Boolean) = updateCell(column) { it.copy(isNull = isNull) }

    fun dismissEditor() = mutableState.update { it.copy(editor = null) }

    /**
     * Builds the statement for the editor and asks for confirmation. Nothing runs yet. An edit that changes nothing
     * closes the editor without a statement.
     */
    fun reviewEditor() {
        val editor = mutableState.value.editor ?: return
        val table = mutableState.value.selectedTable ?: return
        if (!editor.isInsert && editor.changedCells.isEmpty()) {
            mutableState.update { it.copy(editor = null) }
            return
        }
        val pending = if (editor.isInsert) buildInsert(table, editor) else buildUpdate(table, editor)
        if (pending == null) {
            mutableState.update { it.copy(actionError = rowKeyMissingError) }
            return
        }
        mutableState.update { it.copy(editor = null, pendingWrite = pending) }
    }

    fun requestDelete(index: Int) {
        val schema = schemaOrNull() ?: return
        if (!schema.canEditRows) {
            mutableState.update { it.copy(actionError = rowKeyMissingError, selectedRowIndex = null) }
            return
        }
        val table = mutableState.value.selectedTable ?: return
        val content = (mutableState.value.rows as? UiState.Content)?.data ?: return
        val row = content.rows.getOrNull(index) ?: return
        val keys = schema.primaryKey.map { key -> ColumnValue(key, cellValue(content.columns, row, key)) }
        if (keys.any { it.value == null }) {
            mutableState.update { it.copy(actionError = rowKeyMissingError, selectedRowIndex = null) }
            return
        }
        mutableState.update {
            it.copy(pendingWrite = PendingWrite(WriteKind.DELETE, deleteStatement(table, keys)), selectedRowIndex = null)
        }
    }

    fun cancelWrite() = mutableState.update { it.copy(pendingWrite = null) }

    /**
     * Runs the confirmed write once. The pending write is cleared in the same update that marks the write as running,
     * so a second tap on the confirm button finds nothing to run.
     */
    fun confirmWrite() {
        val id = accountId ?: return
        val state = mutableState.value
        val pending = state.pendingWrite ?: return
        if (state.isWriting) return
        mutableState.update { it.copy(isWriting = true, pendingWrite = null) }
        viewModelScope.launch {
            val result = rowsRepository.execute(id, databaseId, pending.statement)
            mutableState.update { it.copy(isWriting = false) }
            when (result) {
                is ApiResult.Success -> reloadRows()
                is ApiResult.Failure -> mutableState.update {
                    it.copy(actionError = D1ErrorMapper.toStatementErrorUi(result.error, mayWrite = true))
                }
            }
        }
    }

    fun dismissActionError() = mutableState.update { it.copy(actionError = null) }

    private suspend fun loadTables(id: String) {
        mutableState.update { it.copy(tables = UiState.Loading) }
        val outcome = when (val result = catalog.tables(id, databaseId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(tables = outcome) }
    }

    private suspend fun loadTable(id: String, table: String, generation: Int) {
        val schemaOutcome = when (val result = catalog.schema(id, databaseId, table)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { if (generation == rowsGeneration && it.selectedTable == table) it.copy(schema = schemaOutcome) else it }
        loadFirstRows(id, table, generation)
    }

    private suspend fun loadFirstRows(id: String, table: String, generation: Int) {
        val orderBy = orderKeys()
        val outcome = when (val result = rowsRepository.page(id, databaseId, table, offset = 0, pageRows = D1Limits.TABLE_PAGE_ROWS, orderBy = orderBy)) {
            is ApiResult.Success -> UiState.Content(result.value.toContent())
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { if (generation == rowsGeneration && it.selectedTable == table) it.copy(rows = outcome) else it }
    }

    /** Cancels any page still loading and makes its late result (if it arrives anyway) stale. */
    private fun invalidateRows() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        rowsGeneration++
    }

    private fun RowPage.toContent() = RowsContent(columns = columns, rows = rows, hasMore = hasMore)

    private fun setRows(transform: (RowsContent) -> RowsContent) = mutableState.update { state ->
        val content = (state.rows as? UiState.Content)?.data ?: return@update state
        state.copy(rows = UiState.Content(transform(content)))
    }

    private fun updateCell(column: String, transform: (CellDraft) -> CellDraft) = mutableState.update { state ->
        val editor = state.editor ?: return@update state
        val cells = editor.cells.map { if (it.column == column) transform(it) else it }
        state.copy(editor = editor.copy(cells = cells))
    }

    private fun schemaOrNull(): TableSchema? = (mutableState.value.schema as? UiState.Content)?.data

    /** The primary key in key order, or empty when the table has none (ordering then falls back to rowid). */
    private fun orderKeys(): List<String> = schemaOrNull()?.primaryKey.orEmpty()

    private fun buildInsert(table: String, editor: RowEditorState) = PendingWrite(
        kind = WriteKind.INSERT,
        statement = insertStatement(
            table = table,
            values = editor.cells.mapNotNull { cell ->
                when {
                    cell.isNull -> ColumnValue(cell.column, null)
                    cell.text.isEmpty() -> null
                    else -> ColumnValue(cell.column, cell.text)
                }
            },
        ),
    )

    /** Only the columns the user changed are written, so another writer's edits to other columns survive. */
    private fun buildUpdate(table: String, editor: RowEditorState): PendingWrite? {
        val keys = editor.keyValues ?: return null
        if (keys.values.any { it == null } || keys.isEmpty()) return null
        val where = keys.map { (column, value) -> ColumnValue(column, value) }
        val sets = editor.changedCells.map { cell ->
            ColumnValue(cell.column, if (cell.isNull) null else cell.text)
        }
        if (sets.isEmpty()) return null
        return PendingWrite(WriteKind.UPDATE, updateStatement(table, sets, where))
    }
}
