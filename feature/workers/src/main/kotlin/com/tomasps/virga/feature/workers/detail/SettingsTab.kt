package com.tomasps.virga.feature.workers.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.BindingTarget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val SECTION_SPACING = 8.dp
private const val DASHBOARD_SETTINGS_URL = "https://dash.cloudflare.com/?to=/:account/workers-and-pages"

/**
 * Settings tab. Bindings and plain-text variables are read-only: a change to them needs a new version that carries
 * the whole binding list, and that write is not confirmed safe yet. Secrets are write-only and never shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTab(
    snapshot: WorkerSnapshot,
    scriptName: String,
    isBusy: Boolean,
    onOpenBinding: (BindingTarget) -> Unit,
    onSaveSecret: (String, String) -> Unit,
    onDeleteSecret: (String) -> Unit,
    onDeleteWorker: () -> Unit,
) {
    val navigator = LocalNavigator.current
    var secretToDelete by remember { mutableStateOf<String?>(null) }
    var addingSecret by remember { mutableStateOf(false) }
    var deleteWorkerOpen by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(SECTION_SPACING), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item { SectionHeader(title = stringResource(R.string.workers_settings_compatibility)) }
        item {
            SectionCard(
                rows = listOf {
                    ListRow(
                        headline = snapshot.settings.compatibilityDate ?: stringResource(R.string.workers_settings_unknown),
                        supporting = snapshot.settings.compatibilityFlags.joinToString(", ").ifEmpty { null },
                    )
                },
            )
        }
        item { SectionHeader(title = stringResource(R.string.workers_settings_variables)) }
        item { SectionCard(rows = variableRows(snapshot)) }
        item {
            SectionCard(
                rows = listOf {
                    OpenInDashboardRow(
                        description = stringResource(R.string.workers_settings_variables_readonly),
                        onOpen = { navigator.openInBrowser(DASHBOARD_SETTINGS_URL) },
                    )
                },
            )
        }
        item { SectionHeader(title = stringResource(R.string.workers_settings_secrets)) }
        item { SectionCard(rows = secretRows(snapshot, isBusy, onDelete = { secretToDelete = it })) }
        item {
            OutlinedButton(enabled = !isBusy, onClick = { addingSecret = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workers_settings_add_secret))
            }
        }
        item { SectionHeader(title = stringResource(R.string.workers_settings_bindings)) }
        item { SectionCard(rows = bindingRows(snapshot, onOpenBinding)) }
        item { SectionHeader(title = stringResource(R.string.workers_settings_danger)) }
        item {
            OutlinedButton(enabled = !isBusy, onClick = { deleteWorkerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workers_settings_delete_worker))
            }
        }
    }

    secretToDelete?.let { name ->
        ConfirmDialog(
            title = stringResource(R.string.workers_confirm_delete_secret_title),
            message = stringResource(R.string.workers_confirm_delete_secret_message, name),
            onConfirm = {
                onDeleteSecret(name)
                secretToDelete = null
            },
            onDismiss = { secretToDelete = null },
            destructive = true,
        )
    }
    if (addingSecret) {
        AddSecretSheet(
            onDismiss = { addingSecret = false },
            onSave = { name, value ->
                onSaveSecret(name, value)
                addingSecret = false
            },
        )
    }
    if (deleteWorkerOpen) {
        TypedNameConfirmDialog(
            title = stringResource(R.string.workers_confirm_delete_worker_title),
            message = stringResource(R.string.workers_confirm_delete_worker_message),
            expectedName = scriptName,
            onConfirm = {
                deleteWorkerOpen = false
                onDeleteWorker()
            },
            onDismiss = { deleteWorkerOpen = false },
        )
    }
}

@Composable
private fun variableRows(snapshot: WorkerSnapshot): List<@Composable () -> Unit> {
    if (snapshot.plainTextVariables.isEmpty()) {
        return listOf { ListRow(headline = stringResource(R.string.workers_settings_no_variables)) }
    }
    return snapshot.plainTextVariables.map { variable ->
        { ListRow(headline = variable.name, supporting = variable.text) }
    }
}

@Composable
private fun secretRows(snapshot: WorkerSnapshot, isBusy: Boolean, onDelete: (String) -> Unit): List<@Composable () -> Unit> {
    if (snapshot.secretNames.isEmpty()) return listOf { ListRow(headline = stringResource(R.string.workers_settings_no_secrets)) }
    return snapshot.secretNames.map { name ->
        {
            ListRow(
                headline = name,
                supporting = stringResource(R.string.workers_settings_secret_hidden),
                trailing = {
                    TextButton(enabled = !isBusy, onClick = { onDelete(name) }) {
                        Text(stringResource(R.string.workers_settings_delete_secret))
                    }
                },
            )
        }
    }
}

@Composable
private fun bindingRows(snapshot: WorkerSnapshot, onOpen: (BindingTarget) -> Unit): List<@Composable () -> Unit> {
    val linked = snapshot.bindings.filter { it.target != null }
    if (linked.isEmpty()) return listOf { ListRow(headline = stringResource(R.string.workers_overview_no_bindings)) }
    return linked.map { binding ->
        {
            ListRow(
                headline = binding.name,
                supporting = bindingTypeLabelRes(binding.type)?.let { stringResource(it) } ?: binding.type,
                onClick = binding.target?.let { target -> { onOpen(target) } },
            )
        }
    }
}
