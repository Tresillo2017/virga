package com.tomasps.virga.feature.d1.detail.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.data.SqlOutcome
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.sql.QueryHistoryStore
import com.tomasps.virga.feature.d1.sql.SqlSafety
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SQL console. Statements are sent as typed, without parameters. Destructive statements wait for confirmation.
 * History is kept on this device only, per database, and never sent anywhere except the statement itself.
 */
class ConsoleViewModel(
    session: SessionRepository,
    private val sqlRepository: SqlRepository,
    private val historyStore: QueryHistoryStore,
    private val databaseId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            historyStore.observe(databaseId).collectLatest { entries ->
                mutableState.update { it.copy(history = entries) }
            }
        }
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id -> accountId = id }
        }
    }

    fun onSqlChanged(sql: String) = mutableState.update { it.copy(sql = sql, error = null) }

    fun useHistory(entry: String) = mutableState.update { it.copy(sql = entry, error = null) }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear(databaseId) }
    }

    fun run() {
        val sql = mutableState.value.sql.trim()
        if (sql.isEmpty() || mutableState.value.isRunning) return
        val risks = SqlSafety.risks(sql)
        if (risks.isNotEmpty()) {
            mutableState.update { it.copy(pendingRun = PendingRun(sql = sql, risks = risks)) }
            return
        }
        execute(sql)
    }

    /** Runs the statement that was checked and shown in the dialog. Editor text typed since then does not change it. */
    fun confirmRun() {
        val pending = mutableState.value.pendingRun ?: return
        mutableState.update { it.copy(pendingRun = null) }
        execute(pending.sql)
    }

    fun cancelRun() = mutableState.update { it.copy(pendingRun = null) }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    private fun execute(sql: String) {
        val id = accountId ?: return
        mutableState.update { it.copy(isRunning = true, error = null, result = null) }
        val mayWrite = SqlSafety.mayWrite(sql)
        viewModelScope.launch {
            historyStore.record(databaseId, sql)
            val outcome = when (val result = sqlRepository.run(id, databaseId, sql)) {
                is ApiResult.Success -> ApiResult.Success(result.value.toConsoleResult())
                is ApiResult.Failure -> result
            }
            mutableState.update {
                when (outcome) {
                    is ApiResult.Success -> it.copy(isRunning = false, result = outcome.value)
                    is ApiResult.Failure -> it.copy(isRunning = false, error = D1ErrorMapper.toStatementErrorUi(outcome.error, mayWrite))
                }
            }
        }
    }

    private fun SqlOutcome.toConsoleResult() = ConsoleResult(
        columns = columns,
        rows = rows.take(D1Limits.CONSOLE_MAX_ROWS_SHOWN),
        totalRows = rows.size,
        rowsRead = rowsRead,
        rowsWritten = rowsWritten,
        durationMs = durationMs,
        statementCount = statementCount,
    )
}
