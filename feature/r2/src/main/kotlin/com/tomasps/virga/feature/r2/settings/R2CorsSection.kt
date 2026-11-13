package com.tomasps.virga.feature.r2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
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

/** CORS rules. Each rule opens in an editor; saving writes the whole list back with the other rules untouched. */
@Composable
internal fun CorsSection(state: R2CorsUiState, viewModel: R2CorsViewModel) {
    SectionHeader(
        title = stringResource(R.string.r2_section_cors),
        action = {
            Row {
                IconButton(onClick = viewModel::openNewRule, enabled = state.rules is UiState.Content) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.r2_action_add_cors_rule))
                }
                if (state.hasRules) {
                    IconButton(onClick = viewModel::requestDeleteAll) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.r2_action_delete_all_cors),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
    state.rules?.let { section ->
        UiStateContent(state = section, onRetry = viewModel::retry) { rules ->
            if (rules.isEmpty()) {
                Text(text = stringResource(R.string.r2_cors_empty), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            rules.forEachIndexed { index, rule ->
                ListRow(
                    headline = rule.allowed.origins.joinToString(", ").ifEmpty { stringResource(R.string.r2_cors_no_origins) },
                    supporting = stringResource(
                        R.string.r2_cors_rule_supporting,
                        rule.allowed.methods.joinToString(", ").ifEmpty { "-" },
                        rule.maxAgeSeconds?.toString() ?: stringResource(R.string.r2_cors_max_age_default),
                    ),
                    onClick = { viewModel.openRule(index) },
                )
            }
        }
    }
    state.ruleToRemove?.let {
        ConfirmDialog(
            title = stringResource(R.string.r2_cors_remove_title),
            message = stringResource(R.string.r2_cors_remove_message),
            onConfirm = viewModel::confirmRemoveRule,
            onDismiss = viewModel::dismissRemoveRule,
            confirmLabel = stringResource(R.string.r2_action_remove),
            destructive = true,
        )
    }
    if (state.deleteAllRequested) {
        ConfirmDialog(
            title = stringResource(R.string.r2_cors_delete_all_title),
            message = stringResource(R.string.r2_cors_delete_all_message),
            onConfirm = viewModel::confirmDeleteAll,
            onDismiss = viewModel::dismissDeleteAll,
            confirmLabel = stringResource(R.string.r2_action_delete_all_cors),
            destructive = true,
        )
    }
    state.editor?.let { editor -> CorsEditorSheet(editor, state.methodOptions, viewModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorsEditorSheet(editor: R2CorsEditor, methods: List<String>, viewModel: R2CorsViewModel) {
    ModalBottomSheet(onDismissRequest = viewModel::dismissEditor) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(if (editor.index == null) R.string.r2_cors_form_new else R.string.r2_cors_form_edit),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = editor.origins,
                onValueChange = viewModel::onOriginsChanged,
                label = { Text(stringResource(R.string.r2_cors_origins_label)) },
                supportingText = { Text(editor.error?.asString() ?: stringResource(R.string.r2_cors_list_help)) },
                isError = editor.error != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(text = stringResource(R.string.r2_cors_methods_label), modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                methods.forEach { method ->
                    FilterChip(
                        selected = method in editor.methods,
                        onClick = { viewModel.onMethodToggled(method) },
                        label = { Text(method) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            OutlinedTextField(
                value = editor.headers,
                onValueChange = viewModel::onHeadersChanged,
                label = { Text(stringResource(R.string.r2_cors_headers_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = editor.exposeHeaders,
                onValueChange = viewModel::onExposeHeadersChanged,
                label = { Text(stringResource(R.string.r2_cors_expose_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = editor.maxAge,
                onValueChange = viewModel::onMaxAgeChanged,
                label = { Text(stringResource(R.string.r2_cors_max_age_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            editor.submitError?.let { Text(it.message.asString(), color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                TextButton(onClick = viewModel::dismissEditor) { Text(stringResource(R.string.r2_action_cancel)) }
                TextButton(onClick = viewModel::submit, enabled = !editor.isSaving) { Text(stringResource(R.string.r2_action_save)) }
            }
        }
    }
}
