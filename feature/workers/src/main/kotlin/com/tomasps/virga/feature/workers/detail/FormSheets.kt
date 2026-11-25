package com.tomasps.virga.feature.workers.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tomasps.virga.feature.workers.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val SHEET_GUTTER = 16.dp
private val SHEET_SPACING = 8.dp

/**
 * Asks for one custom domain hostname. The shape is checked as the user types, so a bad value cannot reach the
 * confirmation step. Cloudflare still decides whether the zone can take it.
 *
 * A bottom sheet rather than a dialog, like the deployment editor: it scrolls and moves above the keyboard, so the
 * field and both buttons stay reachable on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddDomainSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var hostname by rememberSaveable { mutableStateOf("") }
    val isBlank = hostname.isBlank()
    val isInvalid = !isBlank && !isValidHostname(hostname)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(SHEET_GUTTER),
            verticalArrangement = Arrangement.spacedBy(SHEET_SPACING),
        ) {
            Text(stringResource(R.string.workers_domains_add))
            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text(stringResource(R.string.workers_domains_add_hostname)) },
                supportingText = if (isInvalid) {
                    { Text(stringResource(R.string.workers_domain_invalid)) }
                } else {
                    null
                },
                isError = isInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !isBlank && !isInvalid,
                onClick = { onAdd(normaliseHostname(hostname)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.workers_domains_add_confirm)) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workers_cancel))
            }
        }
    }
}

/**
 * Name and value for a new or replaced secret. The value is held only in this sheet and cleared on save. The sheet
 * shows the value masked, and nothing reads it back afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSecretSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(SHEET_GUTTER),
            verticalArrangement = Arrangement.spacedBy(SHEET_SPACING),
        ) {
            Text(stringResource(R.string.workers_settings_add_secret))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.workers_secret_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.workers_secret_value_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = name.isNotBlank() && value.isNotEmpty(),
                onClick = {
                    onSave(name.trim(), value)
                    value = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.workers_secret_save)) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workers_cancel))
            }
        }
    }
}
