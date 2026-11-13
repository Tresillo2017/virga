package com.tomasps.virga.feature.workers.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.DeploymentVersion
import com.tomasps.virga.feature.workers.data.WorkerVersion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val SECTION_SPACING = 8.dp
private val EDITOR_GUTTER = 16.dp

/** Deployments tab: the active split, rollback to older versions, and the gradual deployment editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeploymentsTab(
    snapshot: WorkerSnapshot,
    isBusy: Boolean,
    onDeploy: (List<DeploymentVersion>, String?) -> Unit,
    onRollback: (String, String?) -> Unit,
) {
    val active = snapshot.activeDeployment
    val names = rememberVersionNames(snapshot)
    val activeIds = active?.versions?.map { it.versionId }.orEmpty().toSet()
    val history = remember(snapshot.versions, activeIds) { snapshot.versions.filterNot { it.id in activeIds } }
    var rollbackTarget by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(SECTION_SPACING), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item { SectionHeader(title = stringResource(R.string.workers_deploy_active)) }
        item {
            SectionCard(
                rows = if (active == null) {
                    listOf { ListRow(headline = stringResource(R.string.workers_overview_no_deployment)) }
                } else {
                    active.versions.map { share ->
                        {
                            ListRow(
                                headline = names.version(share.versionId),
                                supporting = stringResource(R.string.workers_deploy_percent, DeploymentSplit.formatPercent(share.percentage)),
                            )
                        }
                    }
                },
            )
        }
        item {
            Button(
                onClick = { editorOpen = true },
                enabled = !isBusy && active != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.workers_deploy_gradual_open)) }
        }
        item { SectionHeader(title = stringResource(R.string.workers_deploy_history)) }
        item { SectionCard(rows = historyRows(history, names, isBusy, onRollback = { rollbackTarget = it })) }
    }

    rollbackTarget?.let { versionId ->
        ConfirmDialog(
            title = stringResource(R.string.workers_confirm_rollback_title),
            message = stringResource(R.string.workers_confirm_rollback_message, names.version(versionId)),
            onConfirm = {
                onRollback(versionId, active?.id)
                rollbackTarget = null
            },
            onDismiss = { rollbackTarget = null },
            destructive = true,
        )
    }

    if (editorOpen) {
        GradualDeploymentEditor(
            candidates = (active?.versions?.map { it.versionId }.orEmpty() + history.map { it.id }).distinct(),
            names = names,
            onDismiss = { editorOpen = false },
            onSave = { split ->
                onDeploy(split, active?.id)
                editorOpen = false
            },
        )
    }
}

/**
 * Percentage editor. Save stays disabled until every share is above zero and the shares total exactly 100.
 * The active deployment is re-read before the POST (see the view model), so a change made elsewhere is caught.
 * The sheet is shown in full and scrolls, and it moves up above the keyboard, so every field and button stays reachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradualDeploymentEditor(
    candidates: List<String>,
    names: VersionNames,
    onDismiss: () -> Unit,
    onSave: (List<DeploymentVersion>) -> Unit,
) {
    val shares = remember(candidates) { mutableStateMapOf<String, String>().apply { candidates.forEach { put(it, "") } } }
    val parsed = candidates.map { id -> DeploymentVersion(id, shares[id]?.toDoubleOrNull() ?: 0.0) }
    val total = parsed.sumOf { it.percentage }
    val chosen = parsed.filter { it.percentage > 0.0 }
    val valid = DeploymentSplit.isValid(chosen.map { it.percentage })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(EDITOR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        ) {
            Text(stringResource(R.string.workers_deploy_gradual_title))
            candidates.forEach { id ->
                OutlinedTextField(
                    value = shares[id].orEmpty(),
                    onValueChange = { shares[id] = it },
                    label = { Text(stringResource(R.string.workers_deploy_percent_label, names.version(id))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(R.string.workers_deploy_total, DeploymentSplit.formatPercent(total)))
            Button(
                onClick = { onSave(chosen) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.workers_deploy_gradual_save)) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workers_cancel))
            }
        }
    }
}

@Composable
private fun historyRows(
    history: List<WorkerVersion>,
    names: VersionNames,
    isBusy: Boolean,
    onRollback: (String) -> Unit,
): List<@Composable () -> Unit> {
    if (history.isEmpty()) return listOf { ListRow(headline = stringResource(R.string.workers_deploy_history_empty)) }
    return history.map { version ->
        {
            ListRow(
                headline = names.version(version.id),
                supporting = formatIsoDate(version.metadata?.createdOn),
                trailing = {
                    TextButton(enabled = !isBusy, onClick = { onRollback(version.id) }) {
                        Text(stringResource(R.string.workers_deploy_rollback))
                    }
                },
            )
        }
    }
}
