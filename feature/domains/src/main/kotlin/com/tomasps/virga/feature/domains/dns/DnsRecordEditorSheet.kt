package com.tomasps.virga.feature.domains.dns

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.feature.domains.R

/**
 * Create or edit form for one record. The fields shown depend on the record type: content, priority, or the
 * structured data fields. The type can only be picked when creating; an edited record keeps its type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsRecordEditorSheet(
    editor: DnsEditor,
    errors: List<DnsFieldError>,
    saving: Boolean,
    isPaidPlan: Boolean,
    onChange: (DnsRecordForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val form = editor.form
    val isNew = editor.recordId == null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(if (isNew) R.string.domains_record_new else R.string.domains_record_edit),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            if (isNew) TypePicker(form.type) { onChange(form.withType(it)) }
            else Text(stringResource(R.string.domains_record_type_fixed, form.type), modifier = Modifier.padding(top = 8.dp))
            FieldInput(label = stringResource(R.string.domains_field_name), value = form.name, error = errors.reasonFor("name")) {
                onChange(form.copy(name = it))
            }
            ValueFields(form, errors) { key, value -> onChange(form.withValue(key, value)) }
            if (form.recordType?.proxiable == true) {
                val proxiedLabel = stringResource(R.string.domains_field_proxied)
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(proxiedLabel, modifier = Modifier.weight(1f))
                    Switch(
                        checked = form.proxied,
                        onCheckedChange = { onChange(form.copy(proxied = it)) },
                        modifier = Modifier.semantics { contentDescription = proxiedLabel },
                    )
                }
            }
            if (!form.ttlIsForcedAuto) {
                FieldInput(label = stringResource(R.string.domains_field_ttl), value = form.ttl, error = errors.reasonFor("ttl")) {
                    onChange(form.copy(ttl = it))
                }
            } else {
                Text(stringResource(R.string.domains_ttl_proxied_auto), modifier = Modifier.padding(top = 8.dp))
            }
            FieldInput(label = stringResource(R.string.domains_field_comment), value = form.comment, error = errors.reasonFor("comment")) {
                onChange(form.copy(comment = it))
            }
            if (isPaidPlan) {
                FieldInput(label = stringResource(R.string.domains_field_tags), value = form.tags, error = errors.reasonFor("tags")) {
                    onChange(form.copy(tags = it))
                }
            } else {
                Text(stringResource(R.string.domains_tags_paid_only), modifier = Modifier.padding(top = 8.dp))
            }
            errors.forEach { error -> Text(errorText(error), modifier = Modifier.padding(top = 8.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (!isNew) {
                    TextButton(onClick = onDelete, enabled = !saving) {
                        Text(stringResource(R.string.domains_record_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.domains_cancel)) }
                TextButton(onClick = onSave, enabled = !saving) { Text(stringResource(R.string.domains_save)) }
            }
        }
    }
}

@Composable
private fun TypePicker(selected: String, onSelect: (DnsRecordType) -> Unit) {
    Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()).testTag(TYPE_PICKER_TAG)) {
        DnsRecordType.creatable.forEach { type ->
            FilterChip(
                selected = selected == type.name,
                onClick = { onSelect(type) },
                label = { Text(type.name) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/** Per-type value fields, keyed the same way [DnsRecordForm.values] and the request body codec use them. */
@Composable
private fun ValueFields(form: DnsRecordForm, errors: List<DnsFieldError>, onValue: (String, String) -> Unit) {
    val type = form.recordType
    when (type?.kind) {
        DnsValueKind.CONTENT, DnsValueKind.GENERIC, null -> ContentField(form, errors, onValue)
        DnsValueKind.CONTENT_WITH_PRIORITY -> {
            ContentField(form, errors, onValue)
            FieldInput(
                label = stringResource(R.string.domains_field_priority),
                value = form.values[F_PRIORITY].orEmpty(),
                error = errors.reasonFor(F_PRIORITY),
            ) { onValue(F_PRIORITY, it) }
        }
        DnsValueKind.STRUCTURED -> type.dataFields.forEach { field ->
            FieldInput(
                label = stringResource(fieldLabel(field)),
                value = form.values[field].orEmpty(),
                error = errors.reasonFor(field),
            ) { onValue(field, it) }
        }
    }
}

@Composable
private fun ContentField(form: DnsRecordForm, errors: List<DnsFieldError>, onValue: (String, String) -> Unit) {
    FieldInput(
        label = stringResource(R.string.domains_field_content),
        value = form.values[CONTENT].orEmpty(),
        error = errors.reasonFor(CONTENT),
    ) { onValue(CONTENT, it) }
}

@Composable
private fun FieldInput(label: String, value: String, error: DnsFieldError?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

private fun fieldLabel(field: String): Int = when (field) {
    F_PRIORITY -> R.string.domains_field_priority
    F_WEIGHT -> R.string.domains_field_weight
    F_PORT -> R.string.domains_field_port
    F_TARGET -> R.string.domains_field_target
    F_FLAGS -> R.string.domains_field_flags
    F_TAG -> R.string.domains_field_caa_tag
    else -> R.string.domains_field_value
}

@Composable
private fun errorText(error: DnsFieldError): String = stringResource(reasonText(error.reason))

private fun List<DnsFieldError>.reasonFor(field: String): DnsFieldError? = firstOrNull { it.field == field }

private fun reasonText(reason: DnsErrorReason): Int = when (reason) {
    DnsErrorReason.REQUIRED -> R.string.domains_err_required
    DnsErrorReason.INVALID_IPV4 -> R.string.domains_err_ipv4
    DnsErrorReason.INVALID_IPV6 -> R.string.domains_err_ipv6
    DnsErrorReason.INVALID_HOSTNAME -> R.string.domains_err_hostname
    DnsErrorReason.OUT_OF_RANGE -> R.string.domains_err_range
    DnsErrorReason.NOT_A_NUMBER -> R.string.domains_err_number
    DnsErrorReason.TOO_LONG -> R.string.domains_err_too_long
    DnsErrorReason.TTL_OUT_OF_RANGE -> R.string.domains_err_ttl
    DnsErrorReason.SELF_CNAME -> R.string.domains_err_self_cname
    DnsErrorReason.TAGS_NOT_ALLOWED -> R.string.domains_err_tags_paid
    DnsErrorReason.TOO_MANY_TAGS -> R.string.domains_err_too_many_tags
    DnsErrorReason.INVALID_FORMAT -> R.string.domains_err_format
    DnsErrorReason.NOT_PROXIABLE -> R.string.domains_err_not_proxiable
}

private const val CONTENT = "content"

/** Test tag of the record type picker in the editor, so a test picks a type there and not in the list filter. */
internal const val TYPE_PICKER_TAG = "record-type-picker"
