package com.tomasps.virga.feature.domains.zones.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.zones.Zone
import com.tomasps.virga.feature.domains.zones.ZoneStatus
import com.tomasps.virga.feature.domains.zones.zoneStatusLabel
import com.tomasps.virga.feature.domains.zones.zoneStatusTone

/** The Domains tab's entry point. Owns the ViewModel so the screen can be reused by the navigation entry. */
@Composable
fun ZoneListRoute() {
    val viewModel: ZoneListViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    ZoneListScreen(
        state = state,
        onQueryChange = viewModel::setQuery,
        onStatusChange = viewModel::setStatusFilter,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onAddZone = { viewModel.showAddZone(true) },
        onDismissAdd = { viewModel.showAddZone(false) },
        onSubmitAdd = viewModel::addZone,
        onMessageShown = viewModel::messageShown,
        onCreatedOpened = viewModel::createdZoneOpened,
    )
}

@Composable
internal fun ZoneListScreen(
    state: ZoneListUiState,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAddZone: () -> Unit,
    onDismissAdd: () -> Unit,
    onSubmitAdd: (String) -> Unit,
    onMessageShown: () -> Unit,
    onCreatedOpened: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let { text ->
            snackbar.showSnackbar(text.resolve(context))
            onMessageShown()
        }
    }
    LaunchedEffect(state.createdZoneId) {
        state.createdZoneId?.let { id ->
            onCreatedOpened()
            navigator.navigate(ZoneDetail(id))
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.domains_title_domainsroot),
        snackbarHostState = snackbar,
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        actions = {
            IconButton(onClick = onAddZone) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.domains_add_zone_cd))
            }
        },
    ) { padding ->
        ZoneListBody(state, padding, onQueryChange, onStatusChange, onRetry) { id ->
            navigator.navigate(ZoneDetail(id))
        }
    }
    if (state.addingZone) {
        AddZoneSheet(
            creating = state.creating,
            error = state.addError,
            onDismiss = onDismissAdd,
            onSubmit = onSubmitAdd,
        )
    }
}

@Composable
private fun ZoneListBody(
    state: ZoneListUiState,
    padding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val zones = state.zones
    if (zones == null) return
    UiStateContent(state = zones, onRetry = onRetry, modifier = Modifier.padding(padding)) { all ->
        val visible = state.visibleZones()
        Column(Modifier.fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = stringResource(R.string.domains_search_zones),
            )
            StatusFilterRow(selected = state.statusFilter, onSelect = onStatusChange)
            when {
                all.isEmpty() -> EmptyState(
                    title = stringResource(R.string.domains_empty_title),
                    message = stringResource(R.string.domains_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )
                visible.isEmpty() -> EmptyState(
                    title = stringResource(R.string.domains_empty_filtered_title),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { zone -> ZoneRow(zone) { onOpen(zone.id) } }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    // Scrolls sideways instead of squeezing the chips, so no label wraps on a narrow screen.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.domains_status_all)) },
        )
        ZoneStatus.filterable.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(zoneStatusLabel(status)) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ZoneRow(zone: Zone, onClick: () -> Unit) {
    val plan = zone.plan?.name
    val supporting = listOfNotNull(
        zoneStatusLabel(zone.status),
        plan,
        if (zone.paused) stringResource(R.string.domains_paused_label) else null,
    ).joinToString(" · ")
    ListRow(
        headline = zone.name,
        supporting = supporting,
        leading = { StatusDot(tone = zoneStatusTone(zone.status)) },
        onClick = onClick,
    )
}


/**
 * Form to add a domain. A sheet rather than a dialog: a dialog that holds a text field keeps the phone's keyboard and
 * the field's error in a cramped window. The name is kept only while the sheet is open, so a closed or finished form
 * starts empty the next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddZoneSheet(creating: Boolean, error: UiText?, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.domains_add_zone), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.domains_add_zone_body), modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.domains_add_zone_hint)) },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message.asString()) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.domains_cancel)) }
                TextButton(onClick = { onSubmit(name) }, enabled = !creating) {
                    Text(stringResource(R.string.domains_add_zone_confirm))
                }
            }
        }
    }
}
