package com.tomasps.virga.feature.pages.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesDeploymentConfigValues
import com.tomasps.virga.feature.pages.data.PagesDeploymentConfigs
import com.tomasps.virga.feature.pages.logic.EnvVarEntry
import com.tomasps.virga.feature.pages.logic.EnvVarRules
import com.tomasps.virga.feature.pages.logic.EnvVarType
import com.tomasps.virga.feature.pages.logic.PagesRules

/** Which environment's variables an edit targets. Preview and production each have their own set. */
private val ENVIRONMENTS = listOf(PagesRules.ENVIRONMENT_PRODUCTION, PagesRules.ENVIRONMENT_PREVIEW)

/** The environment picker and the variable list. Secret values never reach the screen, so they are shown as hidden. */
internal fun LazyListScope.envVarItems(
    configs: PagesDeploymentConfigs?,
    environment: String,
    onEnvironment: (String) -> Unit,
    busy: Boolean,
    onSave: (String, String, EnvVarType, String, EnvVarEntry?) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    item { SectionHeader(stringResource(R.string.pages_section_env)) }
    item {
        VirgaTabRow(
            tabs = listOf(
                stringResource(R.string.pages_env_production),
                stringResource(R.string.pages_env_preview),
            ),
            selectedIndex = ENVIRONMENTS.indexOf(environment).coerceAtLeast(0),
            onSelect = { onEnvironment(ENVIRONMENTS[it]) },
        )
    }
    val values = if (environment == PagesRules.ENVIRONMENT_PREVIEW) configs?.preview else configs?.production
    val entries = EnvVarRules.entriesOf(values)
    item { EnvVarAddRow(busy = busy, onSave = { name, type, value -> onSave(environment, name, type, value, null) }) }
    if (entries.isEmpty()) {
        item {
            ListRow(headline = stringResource(R.string.pages_env_none), supporting = null)
        }
    }
    entries.forEach { entry ->
        item(key = "$environment/${entry.name}") {
            EnvVarRow(
                entry = entry,
                busy = busy,
                onSave = { type, value -> onSave(environment, entry.name, type, value, entry) },
                onDelete = { onDelete(environment, entry.name) },
            )
        }
    }
}

/** Read-only compatibility settings and bindings for the selected environment. Bindings cannot be edited here. */
internal fun LazyListScope.compatibilityItems(configs: PagesDeploymentConfigs?, environment: String) {
    val values = if (environment == PagesRules.ENVIRONMENT_PREVIEW) configs?.preview else configs?.production
    item { SectionHeader(stringResource(R.string.pages_section_compatibility)) }
    item {
        KeyValueRow(
            key = stringResource(R.string.pages_compat_date),
            value = values?.compatibilityDate ?: stringResource(R.string.pages_value_none),
            monospaceValue = true,
        )
    }
    item {
        KeyValueRow(
            key = stringResource(R.string.pages_compat_flags),
            value = values?.compatibilityFlags?.joinToString(", ").orEmpty()
                .ifBlank { stringResource(R.string.pages_value_none) },
            monospaceValue = true,
        )
    }
    bindingsRows(values)
}

private fun LazyListScope.bindingsRows(values: PagesDeploymentConfigValues?) {
    val bindings = listOf(
        R.string.pages_binding_kv to values?.kvNamespaces?.keys,
        R.string.pages_binding_d1 to values?.d1Databases?.keys,
        R.string.pages_binding_r2 to values?.r2Buckets?.keys,
    )
    item { SectionHeader(stringResource(R.string.pages_section_bindings)) }
    bindings.forEach { (labelRes, names) ->
        item(key = "binding-$labelRes") {
            KeyValueRow(
                key = stringResource(labelRes),
                value = names?.sorted()?.joinToString(", ")?.ifBlank { null }
                    ?: stringResource(R.string.pages_value_none),
                monospaceValue = true,
            )
        }
    }
}

@Composable
private fun EnvVarRow(
    entry: EnvVarEntry,
    busy: Boolean,
    onSave: (EnvVarType, String) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by rememberSaveable(entry.name) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(entry.name) { mutableStateOf(false) }
    val supporting = if (entry.type == EnvVarType.SECRET) {
        stringResource(R.string.pages_env_secret_hidden)
    } else {
        entry.value.orEmpty()
    }
    ListRow(
        headline = entry.name,
        supporting = supporting,
        trailing = {
            Row {
                TextButton(onClick = { editing = true }, enabled = !busy) { Text(stringResource(R.string.pages_action_edit)) }
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text(stringResource(R.string.pages_action_remove))
                }
            }
        },
    )
    if (editing) {
        EnvVarDialog(
            existing = entry,
            onDismiss = { editing = false },
            onConfirm = { _, type, value ->
                editing = false
                onSave(type, value)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.pages_env_delete_title),
            message = stringResource(R.string.pages_env_delete_message, entry.name),
            confirmLabel = stringResource(R.string.pages_action_remove),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun EnvVarAddRow(busy: Boolean, onSave: (String, EnvVarType, String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    ListRow(
        headline = stringResource(R.string.pages_env_add),
        trailing = {
            TextButton(onClick = { adding = true }, enabled = !busy) { Text(stringResource(R.string.pages_action_add)) }
        },
    )
    if (adding) {
        EnvVarDialog(
            existing = null,
            onDismiss = { adding = false },
            onConfirm = { name, type, value ->
                adding = false
                onSave(name, type, value)
            },
        )
    }
}

/**
 * Add or edit one variable. For an existing secret the value field may stay blank: the stored value is kept and
 * nothing is sent. The name cannot change on edit.
 */
@Composable
private fun EnvVarDialog(
    existing: EnvVarEntry?,
    onDismiss: () -> Unit,
    onConfirm: (String, EnvVarType, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: EnvVarType.PLAIN) }
    // Not rememberSaveable: the typed value may be a secret and must never reach the saved-state Bundle.
    var value by remember { mutableStateOf(existing?.value.orEmpty()) }
    // The dialog stays open until the input is valid, so a typo never throws the typed values away.
    val nameInvalid = existing == null && name.isNotBlank() && !EnvVarRules.isValidName(name.trim())
    val needsValue = EnvVarRules.needsSecretValue(type, value, existing)
    val canSave = name.isNotBlank() && !nameInvalid && !needsValue
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.pages_env_add else R.string.pages_action_edit)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = existing == null,
                    isError = nameInvalid,
                    supportingText = if (nameInvalid) {
                        { Text(stringResource(R.string.pages_message_env_name_invalid)) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.pages_field_env_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(
                        selected = type == EnvVarType.PLAIN,
                        onClick = { type = EnvVarType.PLAIN },
                        label = { Text(stringResource(R.string.pages_env_type_plain)) },
                    )
                    FilterChip(
                        selected = type == EnvVarType.SECRET,
                        onClick = { type = EnvVarType.SECRET },
                        label = { Text(stringResource(R.string.pages_env_type_secret)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            stringResource(
                                if (existing?.type == EnvVarType.SECRET) R.string.pages_field_secret_keep
                                else R.string.pages_field_env_value,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim(), type, value) }, enabled = canSave) {
                Text(stringResource(R.string.pages_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pages_action_cancel)) }
        },
    )
}
