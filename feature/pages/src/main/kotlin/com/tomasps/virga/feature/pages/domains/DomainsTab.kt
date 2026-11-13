package com.tomasps.virga.feature.pages.domains

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesDomain
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Custom domains: add, retry validation, remove, and the validation record the user must publish. A domain whose
 * zone is on Cloudflare links to its DNS records. Creating the DNS record itself is left to the DNS screens.
 */
@Composable
fun DomainsTab(projectName: String) {
    val viewModel: DomainsViewModel = koinViewModel(parameters = { parametersOf(projectName) })
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    var hostname by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.addedCount) {
        if (state.addedCount > 0) hostname = ""
    }
    val message = state.message?.asString()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionHeader(stringResource(R.string.pages_section_domains)) }
            item {
                AddDomainForm(
                    hostname = hostname,
                    onHostnameChange = { hostname = it },
                    adding = state.adding,
                    error = state.addError?.asString(),
                    onAdd = { viewModel.add(hostname) },
                )
            }
            message?.let { text ->
                item { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            }
            when (val domains = state.domains) {
                is UiState.Loading -> item { LoadingState() }
                is UiState.Failure -> item {
                    ErrorState(error = domains.error, onRetry = viewModel::refresh)
                }
                is UiState.Content -> domainItems(
                    domains = domains.data,
                    busyDomain = state.busyDomain,
                    onRetry = viewModel::retryValidation,
                    onRemove = viewModel::remove,
                    onDnsRecords = { zoneId -> navigator.navigate(DnsRecords(zoneId)) },
                )
            }
        }
    }
}

private fun LazyListScope.domainItems(
    domains: List<PagesDomain>,
    busyDomain: String?,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDnsRecords: (String) -> Unit,
) {
    if (domains.isEmpty()) {
        item {
            EmptyState(
                title = stringResource(R.string.pages_domains_empty_title),
                message = stringResource(R.string.pages_domains_empty_message),
            )
        }
    }
    items(domains, key = { it.name }) { domain ->
        DomainCard(
            domain = domain,
            busy = busyDomain == domain.name,
            anyBusy = busyDomain != null,
            onRetry = { onRetry(domain.name) },
            onRemove = { onRemove(domain.name) },
            onDnsRecords = domain.zoneTag?.takeIf { it.isNotBlank() }?.let { zone -> { onDnsRecords(zone) } },
        )
    }
}

@Composable
private fun AddDomainForm(
    hostname: String,
    onHostnameChange: (String) -> Unit,
    adding: Boolean,
    error: String?,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = hostname,
            onValueChange = onHostnameChange,
            label = { Text(stringResource(R.string.pages_field_hostname)) },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = onAdd, enabled = !adding && hostname.isNotBlank()) {
            Text(stringResource(R.string.pages_action_add_domain))
        }
    }
}

@Composable
private fun DomainCard(
    domain: PagesDomain,
    busy: Boolean,
    anyBusy: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDnsRecords: (() -> Unit)?,
) {
    var confirmRemove by rememberSaveable(domain.name) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(domain.name, style = MaterialTheme.typography.titleSmall)
        KeyValueRow(
            key = stringResource(R.string.pages_domain_status),
            value = stringResource(domainStatusLabelRes(domain.status)),
        )
        domain.validation?.let { validation ->
            KeyValueRow(
                key = stringResource(R.string.pages_domain_validation_method),
                value = validation.method.orEmpty().ifBlank { stringResource(R.string.pages_value_none) },
            )
            validation.txtName?.let { KeyValueRow(stringResource(R.string.pages_domain_txt_name), it, monospaceValue = true) }
            validation.txtValue?.let { KeyValueRow(stringResource(R.string.pages_domain_txt_value), it, monospaceValue = true) }
            validation.errorMessage?.let { KeyValueRow(stringResource(R.string.pages_domain_error), it) }
        }
        Row {
            TextButton(onClick = onRetry, enabled = !busy && !anyBusy) {
                Text(stringResource(R.string.pages_action_retry_validation))
            }
            if (onDnsRecords != null) {
                TextButton(onClick = onDnsRecords) { Text(stringResource(R.string.pages_action_dns_records)) }
            }
            TextButton(onClick = { confirmRemove = true }, enabled = !busy && !anyBusy) {
                Text(stringResource(R.string.pages_action_remove))
            }
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.pages_domain_remove_title),
            message = stringResource(R.string.pages_domain_remove_message, domain.name),
            confirmLabel = stringResource(R.string.pages_action_remove),
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
            destructive = true,
        )
    }
}

@StringRes
private fun domainStatusLabelRes(status: String?): Int = when (status) {
    "active" -> R.string.pages_domain_status_active
    "pending" -> R.string.pages_domain_status_pending
    "initializing" -> R.string.pages_domain_status_initializing
    "deactivated" -> R.string.pages_domain_status_deactivated
    "blocked" -> R.string.pages_domain_status_blocked
    "error" -> R.string.pages_domain_status_error
    else -> R.string.pages_status_unknown
}
