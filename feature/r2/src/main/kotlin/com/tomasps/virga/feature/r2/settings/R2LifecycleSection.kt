package com.tomasps.virga.feature.r2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.R
import kotlinx.serialization.json.JsonObject

/** Lifecycle rules. Each rule opens in a small editor; saving writes the whole list back with the raw fields kept. */
@Composable
internal fun LifecycleSection(state: R2LifecycleUiState, viewModel: R2LifecycleViewModel) {
    SectionHeader(
        title = stringResource(R.string.r2_section_lifecycle),
        action = {
            IconButton(onClick = viewModel::openNewRule, enabled = state.rules is UiState.Content) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.r2_action_add_lifecycle_rule))
            }
        },
    )
    state.rules?.let { section ->
        UiStateContent(state = section, onRetry = viewModel::retry) { rules ->
            if (rules.isEmpty()) {
                Text(text = stringResource(R.string.r2_lifecycle_empty), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            rules.forEach { raw -> LifecycleRuleRow(raw, onOpen = { viewModel.openRule(raw.toLifecycleEntry().id) }, onRemove = { viewModel.requestRemove(raw.toLifecycleEntry().id) }) }
        }
    }
    state.ruleToRemove?.let {
        ConfirmDialog(
            title = stringResource(R.string.r2_lifecycle_remove_title),
            message = stringResource(R.string.r2_lifecycle_remove_message),
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::dismissRemove,
            confirmLabel = stringResource(R.string.r2_action_remove),
            destructive = true,
        )
    }
    state.form?.let { form -> LifecycleFormDialog(form, viewModel) }
}

@Composable
private fun LifecycleRuleRow(raw: JsonObject, onOpen: () -> Unit, onRemove: () -> Unit) {
    val entry = raw.toLifecycleEntry()
    val scope = entry.prefix.ifEmpty { stringResource(R.string.r2_lock_all_objects) }
    val expiry = entry.expireAfterDays?.let { stringResource(R.string.r2_lifecycle_expire_days, it) }
        ?: stringResource(R.string.r2_lifecycle_no_expiry)
    ListRow(
        headline = entry.id,
        supporting = stringResource(
            R.string.r2_lifecycle_rule_supporting,
            scope,
            expiry,
            if (entry.enabled) stringResource(R.string.r2_lifecycle_enabled) else stringResource(R.string.r2_lifecycle_disabled),
        ),
        onClick = onOpen,
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.r2_action_remove_lifecycle_cd, entry.id),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun LifecycleFormDialog(form: R2LifecycleForm, viewModel: R2LifecycleViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissForm,
        title = { Text(stringResource(if (form.original == null) R.string.r2_lifecycle_form_new else R.string.r2_lifecycle_form_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = form.id,
                    onValueChange = viewModel::onIdChanged,
                    label = { Text(stringResource(R.string.r2_lifecycle_id_label)) },
                    supportingText = { Text(form.error?.asString() ?: stringResource(R.string.r2_lifecycle_id_help)) },
                    isError = form.error != null,
                    singleLine = true,
                    enabled = form.original == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.prefix,
                    onValueChange = viewModel::onPrefixChanged,
                    label = { Text(stringResource(R.string.r2_lock_prefix_label)) },
                    supportingText = { Text(stringResource(R.string.r2_lifecycle_prefix_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = form.expireDays,
                    onValueChange = viewModel::onExpireDaysChanged,
                    label = { Text(stringResource(R.string.r2_lifecycle_days_label)) },
                    supportingText = { Text(stringResource(R.string.r2_lifecycle_days_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(text = stringResource(R.string.r2_lifecycle_enabled_label), modifier = Modifier.weight(1f))
                    Switch(checked = form.enabled, onCheckedChange = viewModel::onEnabledChanged)
                }
                Text(
                    text = stringResource(R.string.r2_lifecycle_preserved_note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                form.submitError?.let { Text(it.message.asString(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::submit, enabled = !form.isSaving) { Text(stringResource(R.string.r2_action_save)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissForm) { Text(stringResource(R.string.r2_action_cancel)) } },
    )
}
