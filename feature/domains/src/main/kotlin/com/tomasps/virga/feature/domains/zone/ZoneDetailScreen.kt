package com.tomasps.virga.feature.domains.zone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.ui.components.DestructiveMenuItem
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.domains.DASHBOARD_URL
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.zones.PurgeRequest
import com.tomasps.virga.feature.domains.zone.CARD_GUTTER
import com.tomasps.virga.feature.domains.zones.Zone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Zone overview route. The ViewModel is scoped to the navigation entry and keyed by the zone id. */
@Composable
fun ZoneDetailRoute(zoneId: String) {
    val viewModel: ZoneDetailViewModel = koinViewModel(parameters = { parametersOf(zoneId) })
    val state by viewModel.state.collectAsState()
    ZoneDetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onRange = viewModel::setTimeRange,
        onDevMode = viewModel::setDevelopmentMode,
        onRequestPause = { paused -> viewModel.requestAction(ZoneAction.SetPaused(paused)) },
        onRequestPurge = viewModel::requestPurge,
        onRequestDelete = { viewModel.requestAction(ZoneAction.Delete) },
        onConfirm = viewModel::confirmAction,
        onDismiss = viewModel::dismissAction,
        onMessageShown = viewModel::messageShown,
    )
}

@Composable
internal fun ZoneDetailScreen(
    state: ZoneDetailUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRange: (com.tomasps.virga.core.ui.components.TimeRange) -> Unit,
    onDevMode: (Boolean) -> Unit,
    onRequestPause: (Boolean) -> Unit,
    onRequestPurge: (PurgeRequest) -> Unit,
    onRequestDelete: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPurge by remember { mutableStateOf(false) }
    val zone = (state.zone as? UiState.Content)?.data
    LaunchedEffect(state.message) {
        state.message?.let { text ->
            snackbar.showSnackbar(text.resolve(context))
            onMessageShown()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) navigator.back()
    }
    ScreenScaffold(
        title = zone?.name ?: stringResource(R.string.domains_title_zone),
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        actions = {
            ZoneOverflowMenu(enabled = zone != null, onDelete = onRequestDelete)
        },
    ) { padding ->
        UiStateContent(state = state.zone, onRetry = onRetry, modifier = Modifier.padding(padding)) { loaded ->
            ZoneOverviewBody(
                zone = loaded,
                state = state,
                onRange = onRange,
                onDevMode = onDevMode,
                onRequestPause = onRequestPause,
                onShowPurge = { showPurge = true },
                onRetry = onRetry,
                onOpenWorker = { name -> navigator.navigate(WorkerDetail(name)) },
                onOpenDns = { navigator.navigate(DnsRecords(loaded.id)) },
                onOpenDashboard = { navigator.openInBrowser(DASHBOARD_URL) },
            )
        }
    }
    if (showPurge) {
        PurgeSheet(
            onDismiss = { showPurge = false },
            onSubmit = { request ->
                showPurge = false
                onRequestPurge(request)
            },
        )
    }
    ZoneActionDialogs(state.pendingAction, zone, onConfirm, onDismiss)
}

@Composable
private fun ZoneOverviewBody(
    zone: Zone,
    state: ZoneDetailUiState,
    onRange: (com.tomasps.virga.core.ui.components.TimeRange) -> Unit,
    onDevMode: (Boolean) -> Unit,
    onRequestPause: (Boolean) -> Unit,
    onShowPurge: () -> Unit,
    onRetry: () -> Unit,
    onOpenWorker: (String) -> Unit,
    onOpenDns: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item { ZoneStatusSection(zone) }
        item { QuickActions(zone, state.busy, onRequestPause, onShowPurge) }
        item { SectionHeader(title = stringResource(R.string.domains_section_dns)) }
        item { DnsLinkCard(onOpenDns) }
        item { SectionHeader(title = stringResource(R.string.domains_section_settings)) }
        item { SettingsCard(state, onDevMode, onOpenDashboard) }
        item { ZoneTrafficSection(state.traffic, state.timeRange, onRange, onRetry) }
        item { ZoneRoutesSection(state.routes, onOpenWorker, onRetry) }
        item { ZoneRegistrarSection(state.registrar, onRetry) }
    }
}

/** The link from the overview to the zone's DNS records, as a one-row card. */
@Composable
private fun DnsLinkCard(onOpenDns: () -> Unit) {
    val rows: List<@Composable () -> Unit> = listOf(
        {
            ListRow(
                headline = stringResource(R.string.domains_open_dns_records),
                supporting = stringResource(R.string.domains_open_dns_supporting),
                onClick = onOpenDns,
            )
        },
    )
    SectionCard(rows = rows, modifier = Modifier.padding(horizontal = CARD_GUTTER))
}

/** Development mode and the two settings that only the dashboard changes, grouped under one header. */
@Composable
private fun SettingsCard(state: ZoneDetailUiState, onDevMode: (Boolean) -> Unit, onOpenDashboard: () -> Unit) {
    val rows: List<@Composable () -> Unit> = listOf(
        { DevelopmentModeRow(state.devMode, state.busy, onDevMode) },
        {
            OpenInDashboardRow(
                label = stringResource(R.string.domains_more_zone_settings),
                description = stringResource(R.string.domains_more_zone_settings_desc),
                onOpen = onOpenDashboard,
            )
        },
        {
            OpenInDashboardRow(
                label = stringResource(R.string.domains_under_attack),
                description = stringResource(R.string.domains_under_attack_desc),
                onOpen = onOpenDashboard,
            )
        },
    )
    SectionCard(rows = rows, modifier = Modifier.padding(horizontal = CARD_GUTTER))
}

@Composable
private fun QuickActions(zone: Zone, busy: Boolean, onRequestPause: (Boolean) -> Unit, onShowPurge: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { onRequestPause(!zone.paused) }, enabled = !busy) {
            Text(stringResource(if (zone.paused) R.string.domains_action_resume else R.string.domains_action_pause))
        }
        OutlinedButton(onClick = onShowPurge, enabled = !busy, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.domains_action_purge))
        }
    }
}

@Composable
private fun ZoneOverflowMenu(enabled: Boolean, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = enabled) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.domains_menu_cd))
    }
    androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DestructiveMenuItem(
            text = stringResource(R.string.domains_action_delete_zone),
            onClick = {
                open = false
                onDelete()
            },
        )
    }
}

/** Confirmation for the pending zone action: a plain confirm for pause and purge, typed zone name for delete. */
@Composable
private fun ZoneActionDialogs(action: ZoneAction?, zone: Zone?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (action) {
        null -> Unit
        is ZoneAction.SetPaused -> ConfirmDialog(
            title = stringResource(if (action.paused) R.string.domains_confirm_pause_title else R.string.domains_confirm_resume_title),
            message = stringResource(if (action.paused) R.string.domains_confirm_pause_message else R.string.domains_confirm_resume_message),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is ZoneAction.Purge -> ConfirmDialog(
            title = stringResource(R.string.domains_confirm_purge_title),
            message = stringResource(R.string.domains_confirm_purge_message),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        ZoneAction.Delete -> TypedNameConfirmDialog(
            title = stringResource(R.string.domains_confirm_delete_zone_title),
            message = stringResource(R.string.domains_confirm_delete_zone_message),
            expectedName = zone?.name.orEmpty(),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

