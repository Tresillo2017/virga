package com.tomasps.virga.feature.r2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2CustomDomain
import com.tomasps.virga.feature.r2.api.R2LockRule
import com.tomasps.virga.feature.r2.api.R2_MIN_TLS_VERSIONS

/** Public development URL (r2.dev) switch. Turning it on or off asks first. */
@Composable
internal fun PublicAccessSection(state: R2SettingsUiState, viewModel: R2SettingsViewModel) {
    SectionHeader(title = stringResource(R.string.r2_section_public_access))
    val switchLabel = stringResource(R.string.r2_public_access_switch)
    state.publicAccess?.let { section ->
        UiStateContent(state = section, onRetry = viewModel::retryAll) { managed ->
            ListRow(
                headline = stringResource(R.string.r2_public_access_switch),
                supporting = managed.domain?.takeIf { it.isNotBlank() } ?: stringResource(R.string.r2_public_access_no_url),
                trailing = {
                    Switch(
                        checked = managed.enabled,
                        onCheckedChange = { viewModel.requestPublicAccess(it) },
                        enabled = !state.isWorking,
                        modifier = Modifier.semantics { contentDescription = switchLabel },
                    )
                },
            )
        }
    }
    state.pendingPublicAccess?.let { enable ->
        ConfirmDialog(
            title = stringResource(
                if (enable) R.string.r2_public_access_enable_title else R.string.r2_public_access_disable_title,
            ),
            message = stringResource(R.string.r2_public_access_confirm_message),
            onConfirm = viewModel::confirmPublicAccess,
            onDismiss = viewModel::dismissPublicAccess,
            confirmLabel = stringResource(
                if (enable) R.string.r2_public_access_enable_action else R.string.r2_public_access_disable_action,
            ),
            destructive = enable,
        )
    }
}

/** Custom domains: list with remove, plus an add dialog that needs the zone id. */
@Composable
internal fun DomainsSection(state: R2SettingsUiState, viewModel: R2SettingsViewModel) {
    SectionHeader(
        title = stringResource(R.string.r2_section_domains),
        action = {
            IconButton(onClick = viewModel::openDomainForm) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.r2_action_add_domain))
            }
        },
    )
    state.domains?.let { section ->
        UiStateContent(state = section, onRetry = viewModel::retryAll) { domains ->
            if (domains.isEmpty()) {
                Text(
                    text = stringResource(R.string.r2_domain_empty),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            domains.forEach { domain -> DomainRow(domain, onRemove = { viewModel.requestRemoveDomain(domain.domain) }) }
        }
    }
    state.domainToRemove?.let { domain ->
        ConfirmDialog(
            title = stringResource(R.string.r2_domain_remove_title, domain),
            message = stringResource(R.string.r2_domain_remove_message),
            onConfirm = viewModel::confirmRemoveDomain,
            onDismiss = viewModel::dismissRemoveDomain,
            confirmLabel = stringResource(R.string.r2_action_remove),
            destructive = true,
        )
    }
    state.domainForm?.let { form -> DomainFormDialog(form, viewModel) }
}

@Composable
private fun DomainRow(domain: R2CustomDomain, onRemove: () -> Unit) {
    val status = domain.status?.ownership ?: stringResource(R.string.r2_domain_status_unknown)
    ListRow(
        headline = domain.domain,
        supporting = stringResource(R.string.r2_domain_supporting, status, domain.minTls ?: stringResource(R.string.r2_domain_tls_default)),
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.r2_action_remove_domain_cd, domain.domain),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun DomainFormDialog(form: R2DomainForm, viewModel: R2SettingsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDomainForm,
        title = { Text(stringResource(R.string.r2_domain_form_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = form.domain,
                    onValueChange = viewModel::onDomainChanged,
                    label = { Text(stringResource(R.string.r2_domain_name_label)) },
                    supportingText = { Text(form.error?.asString() ?: stringResource(R.string.r2_domain_name_help)) },
                    isError = form.error != null,
                    singleLine = true,
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.zoneId,
                    onValueChange = viewModel::onDomainZoneChanged,
                    label = { Text(stringResource(R.string.r2_domain_zone_label)) },
                    supportingText = { Text(stringResource(R.string.r2_domain_zone_help)) },
                    singleLine = true,
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(text = stringResource(R.string.r2_domain_min_tls_label), modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = form.minTls == null,
                        onClick = { viewModel.onDomainMinTlsChanged(null) },
                        label = { Text(stringResource(R.string.r2_domain_tls_default)) },
                    )
                    R2_MIN_TLS_VERSIONS.forEach { version ->
                        FilterChip(
                            selected = form.minTls == version,
                            onClick = { viewModel.onDomainMinTlsChanged(version) },
                            label = { Text(version) },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                form.submitError?.let { Text(it.message.asString(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::submitDomain, enabled = !form.isSaving) {
                Text(stringResource(R.string.r2_action_save))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDomainForm) { Text(stringResource(R.string.r2_action_cancel)) } },
    )
}

/** Bucket lock rules. A rule keeps matching objects for a number of days, or indefinitely. */
@Composable
internal fun LockSection(state: R2SettingsUiState, viewModel: R2SettingsViewModel) {
    SectionHeader(
        title = stringResource(R.string.r2_section_lock),
        action = {
            IconButton(onClick = viewModel::openLockForm, enabled = state.lock is UiState.Content) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.r2_action_add_lock_rule))
            }
        },
    )
    state.lock?.let { section ->
        UiStateContent(state = section, onRetry = viewModel::retryAll) { rules ->
            if (rules.isEmpty()) {
                Text(text = stringResource(R.string.r2_lock_empty), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            rules.forEach { rule -> LockRuleRow(rule, onRemove = { viewModel.requestRemoveLockRule(rule.id) }) }
        }
    }
    state.lockRuleToRemove?.let { ruleId ->
        ConfirmDialog(
            title = stringResource(R.string.r2_lock_remove_title, ruleId),
            message = stringResource(R.string.r2_lock_remove_message),
            onConfirm = viewModel::confirmRemoveLockRule,
            onDismiss = viewModel::dismissRemoveLockRule,
            confirmLabel = stringResource(R.string.r2_action_remove),
            destructive = true,
        )
    }
    state.lockForm?.let { form -> LockFormDialog(form, viewModel) }
}

@Composable
private fun LockRuleRow(rule: R2LockRule, onRemove: () -> Unit) {
    val scope = rule.prefix?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.r2_lock_all_objects)
    val retention = if (rule.condition.type == CONDITION_AGE && rule.condition.maxAgeSeconds != null) {
        stringResource(R.string.r2_lock_condition_days, rule.condition.maxAgeSeconds / SECONDS_PER_DAY)
    } else {
        stringResource(R.string.r2_lock_condition_indefinite)
    }
    ListRow(
        headline = rule.id,
        supporting = stringResource(R.string.r2_lock_rule_supporting, scope, retention),
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.r2_action_remove_lock_cd, rule.id),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun LockFormDialog(form: R2LockForm, viewModel: R2SettingsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissLockForm,
        title = { Text(stringResource(R.string.r2_lock_form_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = form.id,
                    onValueChange = viewModel::onLockIdChanged,
                    label = { Text(stringResource(R.string.r2_lock_id_label)) },
                    supportingText = { Text(form.error?.asString() ?: stringResource(R.string.r2_lock_id_help)) },
                    isError = form.error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.prefix,
                    onValueChange = viewModel::onLockPrefixChanged,
                    label = { Text(stringResource(R.string.r2_lock_prefix_label)) },
                    supportingText = { Text(stringResource(R.string.r2_lock_prefix_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = form.indefinite,
                        onClick = { viewModel.onLockIndefiniteChanged(true) },
                        label = { Text(stringResource(R.string.r2_lock_condition_indefinite)) },
                    )
                    FilterChip(
                        selected = !form.indefinite,
                        onClick = { viewModel.onLockIndefiniteChanged(false) },
                        label = { Text(stringResource(R.string.r2_lock_days_chip)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (!form.indefinite) {
                    OutlinedTextField(
                        value = form.days,
                        onValueChange = viewModel::onLockDaysChanged,
                        label = { Text(stringResource(R.string.r2_lock_days_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                form.submitError?.let { Text(it.message.asString(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::submitLockRule, enabled = !form.isSaving) { Text(stringResource(R.string.r2_action_save)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissLockForm) { Text(stringResource(R.string.r2_action_cancel)) } },
    )
}

private const val CONDITION_AGE = "Age"
private const val SECONDS_PER_DAY = 86_400L
