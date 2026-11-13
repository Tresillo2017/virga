package com.tomasps.virga.feature.r2.buckets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2_LOCATION_HINTS
import com.tomasps.virga.feature.r2.api.R2_STORAGE_CLASSES

/**
 * Create-bucket form. Location and storage class are chips: "Automatic" (null) keeps the API default. The
 * jurisdiction option is not offered here because the API takes it in a request header Virga cannot send yet.
 */
@Composable
internal fun CreateBucketDialog(
    form: R2CreateBucketForm,
    onNameChanged: (String) -> Unit,
    onLocationChanged: (String?) -> Unit,
    onStorageClassChanged: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.r2_create_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.r2_create_name_label)) },
                    supportingText = {
                        Text(form.nameError?.asString() ?: stringResource(R.string.r2_create_name_help))
                    },
                    isError = form.nameError != null,
                    singleLine = true,
                    enabled = !form.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.r2_create_location_label),
                    modifier = Modifier.padding(top = 12.dp),
                )
                ChoiceRow(
                    options = R2_LOCATION_HINTS,
                    selected = form.location,
                    labelFor = { locationName(it) },
                    onSelected = onLocationChanged,
                )
                Text(
                    text = stringResource(R.string.r2_create_storage_label),
                    modifier = Modifier.padding(top = 12.dp),
                )
                ChoiceRow(
                    options = R2_STORAGE_CLASSES,
                    selected = form.storageClass,
                    labelFor = { storageName(it) },
                    onSelected = onStorageClassChanged,
                )
                form.submitError?.let { error ->
                    Text(
                        text = error.message.asString(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                OpenInDashboardRow(
                    description = stringResource(R.string.r2_create_jurisdiction_note),
                    onOpen = onOpenDashboard,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !form.isSubmitting) {
                Text(stringResource(R.string.r2_create_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.r2_action_cancel)) }
        },
    )
}

/** A row of single-choice chips with an "Automatic" chip that maps to null. */
@Composable
private fun ChoiceRow(
    options: List<String>,
    selected: String?,
    labelFor: @Composable (String) -> String,
    onSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.r2_choice_automatic)) },
        )
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(labelFor(option)) },
            )
        }
    }
}

@Composable
private fun locationName(hint: String): String = when (hint) {
    "enam" -> stringResource(R.string.r2_location_enam)
    "wnam" -> stringResource(R.string.r2_location_wnam)
    "weur" -> stringResource(R.string.r2_location_weur)
    "eeur" -> stringResource(R.string.r2_location_eeur)
    "apac" -> stringResource(R.string.r2_location_apac)
    else -> stringResource(R.string.r2_location_oc)
}

@Composable
private fun storageName(storageClass: String): String = when (storageClass) {
    "InfrequentAccess" -> stringResource(R.string.r2_storage_infrequent)
    else -> stringResource(R.string.r2_storage_standard)
}
