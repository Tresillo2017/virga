package com.tomasps.virga.feature.d1.detail.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.detail.grid.resultGridItems
import com.tomasps.virga.feature.d1.detail.grid.rememberGridScrollState
import com.tomasps.virga.feature.d1.sql.QueryHistory
import com.tomasps.virga.feature.d1.sql.RiskKind
import com.tomasps.virga.feature.d1.sql.StatementRisk

/** Callbacks from the console tab to its view model. */
data class ConsoleCallbacks(
    val onSqlChanged: (String) -> Unit,
    val onRun: () -> Unit,
    val onConfirmRun: () -> Unit,
    val onCancelRun: () -> Unit,
    val onUseHistory: (String) -> Unit,
    val onClearHistory: () -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
internal fun ConsoleTab(state: ConsoleUiState, callbacks: ConsoleCallbacks) {
    val gridScroll = rememberGridScrollState()
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item(key = "console-editor") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.sql,
                    onValueChange = callbacks.onSqlChanged,
                    label = { Text(stringResource(R.string.d1_console_input_label)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = callbacks.onRun,
                    enabled = state.sql.isNotBlank() && !state.isRunning,
                ) { Text(stringResource(R.string.d1_console_run)) }
            }
        }
        state.error?.let { error ->
            item(key = "console-error") {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(error.title.asString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text(error.message.asString(), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = callbacks.onDismissError) { Text(stringResource(R.string.d1_ok)) }
                }
            }
        }
        state.result?.let { result ->
            item(key = "console-meta") {
                Text(resultSummary(result), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (result.columns.isNotEmpty()) {
                resultGridItems(columns = result.columns, rows = result.rows, scrollState = gridScroll)
            }
            if (result.hasMoreStatements) {
                item(key = "console-batch") {
                    Text(stringResource(R.string.d1_console_batch_note, result.statementCount), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (result.isTruncated) {
                item(key = "console-truncated") {
                    Text(stringResource(R.string.d1_console_truncated, result.rows.size, result.totalRows), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "console-history-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title = stringResource(R.string.d1_console_history_title), modifier = Modifier.weight(1f))
                if (state.history.isNotEmpty()) {
                    OutlinedButton(onClick = callbacks.onClearHistory) { Text(stringResource(R.string.d1_console_history_clear)) }
                }
            }
        }
        if (state.history.isEmpty()) {
            item(key = "console-history-empty") {
                Text(stringResource(R.string.d1_console_history_empty), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(state.history, key = { "history-$it" }) { entry ->
            ListRow(
                headline = QueryHistory.headline(entry),
                onClick = { callbacks.onUseHistory(entry) },
            )
        }
    }
    state.pendingRun?.let { run ->
        RiskConfirmDialog(risks = run.risks, onConfirm = callbacks.onConfirmRun, onDismiss = callbacks.onCancelRun)
    }
}

@Composable
private fun resultSummary(result: ConsoleResult): String {
    val duration = result.durationMs?.let { stringResource(R.string.d1_console_duration, it.toLong()) }
    val base = stringResource(R.string.d1_console_summary, result.rowsRead, result.rowsWritten)
    return listOfNotNull(base, duration).joinToString(" · ")
}

@Composable
private fun RiskConfirmDialog(risks: List<StatementRisk>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.d1_risk_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.d1_risk_message))
                risks.forEach { risk ->
                    Text(risk.statement, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    val labels = risk.kinds.map { riskLabel(it) }
                    Text(labels.joinToString(", "), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.d1_risk_run)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.d1_cancel)) } },
    )
}

@Composable
private fun riskLabel(kind: RiskKind): String = stringResource(
    when (kind) {
        RiskKind.DROP -> R.string.d1_risk_drop
        RiskKind.ALTER -> R.string.d1_risk_alter
        RiskKind.DELETE_WITHOUT_WHERE -> R.string.d1_risk_delete_no_where
        RiskKind.UPDATE_WITHOUT_WHERE -> R.string.d1_risk_update_no_where
    },
)

