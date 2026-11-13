package com.tomasps.virga.feature.d1.detail.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.detail.ReadReplicationMode
import com.tomasps.virga.feature.d1.ui.formatCreatedOn

/** Callbacks from the settings tab to its view model and the screen. */
data class SettingsCallbacks(
    val onRetry: () -> Unit,
    val onSetReplication: (ReadReplicationMode) -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissActionError: () -> Unit,
    val onOpenDashboard: () -> Unit,
)

@Composable
internal fun SettingsTab(state: DatabaseSettingsUiState, callbacks: SettingsCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        UiStateContent(state = state.database, onRetry = callbacks.onRetry) { database ->
            DatabaseFacts(database)
        }
        SectionHeader(title = stringResource(R.string.d1_settings_replication_title))
        Text(stringResource(R.string.d1_settings_replication_note), style = MaterialTheme.typography.bodySmall)
        ReplicationOption(ReadReplicationMode.AUTO, state.replicationMode, state.isSaving, callbacks.onSetReplication)
        ReplicationOption(ReadReplicationMode.DISABLED, state.replicationMode, state.isSaving, callbacks.onSetReplication)
        SectionHeader(title = stringResource(R.string.d1_settings_rename_title))
        OpenInDashboardRow(
            label = stringResource(R.string.d1_settings_rename_action),
            description = stringResource(R.string.d1_settings_rename_description),
            onOpen = callbacks.onOpenDashboard,
        )
        SectionHeader(title = stringResource(R.string.d1_settings_danger_title))
        OutlinedButton(onClick = callbacks.onRequestDelete, enabled = !state.isDeleting) {
            Text(stringResource(R.string.d1_settings_delete))
        }
    }
    if (state.isDeleteRequested) {
        val expected = (state.database as? UiState.Content)?.data?.name.orEmpty()
        TypedNameConfirmDialog(
            title = stringResource(R.string.d1_delete_title),
            message = stringResource(R.string.d1_delete_message, expected),
            expectedName = expected,
            onConfirm = callbacks.onConfirmDelete,
            onDismiss = callbacks.onCancelDelete,
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

@Composable
private fun DatabaseFacts(database: D1Database) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyValueRow(key = stringResource(R.string.d1_fact_name), value = database.name.orEmpty())
        KeyValueRow(key = stringResource(R.string.d1_fact_uuid), value = database.uuid.orEmpty())
        KeyValueRow(key = stringResource(R.string.d1_fact_created), value = formatCreatedOn(database.createdAt) ?: stringResource(R.string.d1_unknown))
        KeyValueRow(key = stringResource(R.string.d1_fact_size), value = database.fileSize?.let { formatBytes(it) } ?: stringResource(R.string.d1_unknown))
        KeyValueRow(key = stringResource(R.string.d1_fact_jurisdiction), value = database.jurisdiction ?: stringResource(R.string.d1_jurisdiction_default))
        KeyValueRow(key = stringResource(R.string.d1_fact_version), value = database.version ?: stringResource(R.string.d1_unknown))
    }
}

@Composable
private fun ReplicationOption(
    mode: ReadReplicationMode,
    selected: ReadReplicationMode,
    isSaving: Boolean,
    onSelect: (ReadReplicationMode) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = mode == selected,
                enabled = !isSaving,
                role = Role.RadioButton,
                onClick = { if (mode != selected) onSelect(mode) },
            ),
    ) {
        RadioButton(selected = mode == selected, onClick = null, enabled = !isSaving)
        Text(stringResource(mode.labelRes))
    }
}
