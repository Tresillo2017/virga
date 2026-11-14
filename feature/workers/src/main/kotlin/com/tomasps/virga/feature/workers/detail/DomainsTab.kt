package com.tomasps.virga.feature.workers.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
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
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.CustomDomain
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val SECTION_SPACING = 8.dp
private const val DASHBOARD_ROUTES_URL = "https://dash.cloudflare.com/?to=/:account/workers-and-pages"

/** A toggle the user asked for and has not confirmed yet. */
private sealed interface PendingDomainAction {
    data class SetWorkersDev(val enabled: Boolean) : PendingDomainAction
    data class SetPreviews(val enabled: Boolean) : PendingDomainAction
    data class RemoveDomain(val domain: CustomDomain) : PendingDomainAction
}

/** The custom domains, or one line saying there are none. Each row removes after a confirmation. */
@Composable
private fun customDomainRows(
    snapshot: WorkerSnapshot,
    isBusy: Boolean,
    onRemove: (CustomDomain) -> Unit,
): List<@Composable () -> Unit> {
    if (snapshot.domains.isEmpty()) return listOf { ListRow(headline = stringResource(R.string.workers_domains_none)) }
    return snapshot.domains.map { domain ->
        {
            ListRow(
                headline = domain.hostname,
                supporting = domain.zoneName,
                trailing = {
                    TextButton(enabled = !isBusy, onClick = { onRemove(domain) }) {
                        Text(stringResource(R.string.workers_domains_remove))
                    }
                },
            )
        }
    }
}

/** Domains tab: workers.dev and preview URLs (both confirmed), custom domains, and a link for routes. */
@Composable
internal fun DomainsTab(
    snapshot: WorkerSnapshot,
    isBusy: Boolean,
    onSetWorkersDevAndPreviews: (enabled: Boolean, previewsEnabled: Boolean) -> Unit,
    onDeleteDomain: (CustomDomain) -> Unit,
    onAddDomain: (String) -> Unit,
) {
    val navigator = LocalNavigator.current
    var pending by remember { mutableStateOf<PendingDomainAction?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingHostname by remember { mutableStateOf<String?>(null) }
    val devEnabled = snapshot.subdomain.enabled
    val previews = snapshot.subdomain.previewsEnabled

    LazyColumn(verticalArrangement = Arrangement.spacedBy(SECTION_SPACING), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item { SectionHeader(title = stringResource(R.string.workers_domains_workers_dev)) }
        item {
            SectionCard(
                rows = listOf(
                    {
                        ListRow(
                            headline = stringResource(R.string.workers_domains_workers_dev_toggle),
                            supporting = stringResource(R.string.workers_domains_workers_dev_supporting),
                            trailing = {
                                Switch(
                                    checked = devEnabled,
                                    enabled = !isBusy,
                                    onCheckedChange = { pending = PendingDomainAction.SetWorkersDev(it) },
                                )
                            },
                        )
                    },
                    {
                        ListRow(
                            headline = stringResource(R.string.workers_domains_previews_toggle),
                            supporting = stringResource(R.string.workers_domains_previews_supporting),
                            trailing = {
                                Switch(
                                    checked = previews,
                                    enabled = !isBusy,
                                    onCheckedChange = { pending = PendingDomainAction.SetPreviews(it) },
                                )
                            },
                        )
                    },
                ),
            )
        }
        item { SectionHeader(title = stringResource(R.string.workers_domains_custom)) }
        item {
            SectionCard(rows = customDomainRows(snapshot, isBusy, onRemove = { pending = PendingDomainAction.RemoveDomain(it) }))
        }
        item {
            TextButton(
                enabled = !isBusy,
                onClick = { adding = true },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) { Text(stringResource(R.string.workers_domains_add)) }
        }
        item { SectionHeader(title = stringResource(R.string.workers_domains_routes)) }
        item {
            SectionCard(
                rows = listOf {
                    OpenInDashboardRow(
                        description = stringResource(R.string.workers_domains_routes_description),
                        onOpen = { navigator.openInBrowser(DASHBOARD_ROUTES_URL) },
                    )
                },
            )
        }
    }

    if (adding) {
        AddDomainSheet(
            onDismiss = { adding = false },
            onAdd = { hostname ->
                pendingHostname = hostname
                adding = false
            },
        )
    }

    pendingHostname?.let { hostname ->
        ConfirmDialog(
            title = stringResource(R.string.workers_confirm_add_domain_title),
            message = stringResource(R.string.workers_confirm_add_domain_message, hostname),
            onConfirm = {
                onAddDomain(hostname)
                pendingHostname = null
            },
            onDismiss = { pendingHostname = null },
        )
    }

    pending?.let { action ->
        DomainConfirmation(
            action = action,
            onDismiss = { pending = null },
            onConfirm = {
                when (action) {
                    is PendingDomainAction.SetWorkersDev -> onSetWorkersDevAndPreviews(action.enabled, previews)
                    is PendingDomainAction.SetPreviews -> onSetWorkersDevAndPreviews(devEnabled, action.enabled)
                    is PendingDomainAction.RemoveDomain -> onDeleteDomain(action.domain)
                }
                pending = null
            },
        )
    }
}

@Composable
private fun DomainConfirmation(action: PendingDomainAction, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val (title, message) = when (action) {
        is PendingDomainAction.SetWorkersDev -> stringResource(
            if (action.enabled) R.string.workers_confirm_workers_dev_on_title else R.string.workers_confirm_workers_dev_off_title,
        ) to stringResource(
            if (action.enabled) R.string.workers_confirm_workers_dev_on_message else R.string.workers_confirm_workers_dev_off_message,
        )
        is PendingDomainAction.SetPreviews -> stringResource(
            if (action.enabled) R.string.workers_confirm_previews_on_title else R.string.workers_confirm_previews_off_title,
        ) to stringResource(
            if (action.enabled) R.string.workers_confirm_previews_on_message else R.string.workers_confirm_previews_off_message,
        )
        is PendingDomainAction.RemoveDomain -> stringResource(R.string.workers_confirm_remove_domain_title) to
            stringResource(R.string.workers_confirm_remove_domain_message, action.domain.hostname)
    }
    ConfirmDialog(
        title = title,
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = action is PendingDomainAction.RemoveDomain || (action is PendingDomainAction.SetPreviews && action.enabled),
    )
}