package com.tomasps.virga.feature.d1.detail.console

import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.sql.StatementRisk
import kotlinx.serialization.json.JsonElement

/**
 * A finished console run. Rows past [D1Limits.CONSOLE_MAX_ROWS_SHOWN] are counted in [totalRows] but not kept.
 * [rowsRead], [rowsWritten] and [durationMs] cover every statement in the batch; the grid shows the first one only.
 */
data class ConsoleResult(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val totalRows: Int,
    val rowsRead: Long,
    val rowsWritten: Long,
    val durationMs: Double?,
    val statementCount: Int = 1,
) {
    val isTruncated: Boolean get() = totalRows > rows.size
    val hasMoreStatements: Boolean get() = statementCount > 1
}

/**
 * A run that needs confirmation. [sql] is the exact text that was checked; confirming runs it, not whatever the editor
 * holds by then, and the dialog lists [risks] from that same text.
 */
data class PendingRun(val sql: String, val risks: List<StatementRisk>)

data class ConsoleUiState(
    val sql: String = "",
    val isRunning: Boolean = false,
    val result: ConsoleResult? = null,
    val error: ErrorUi? = null,
    val pendingRun: PendingRun? = null,
    val history: List<String> = emptyList(),
)
