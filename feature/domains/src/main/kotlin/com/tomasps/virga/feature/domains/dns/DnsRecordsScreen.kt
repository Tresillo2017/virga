package com.tomasps.virga.feature.domains.dns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.files.rememberOpenFileLauncher
import com.tomasps.virga.core.ui.files.rememberSaveFileLauncher
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.domains.R
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route for the records and DNS settings of one zone. Both tabs share the zone id, so the route carries only that. */
@Composable
fun DnsRecordsScreenRoute(zoneId: String) {
    val records: DnsRecordsViewModel = koinViewModel(parameters = { parametersOf(zoneId) })
    val dnssec: DnssecViewModel = koinViewModel(parameters = { parametersOf(zoneId) })
    val dnsSettings: ZoneDnsSettingsViewModel = koinViewModel(parameters = { parametersOf(zoneId) })
    val state by records.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val fileIo = remember { BindFileIo() }
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberSaveFileLauncher(mimeType = BIND_MIME) { uri ->
        if (uri == null) {
            records.exportSaved(saved = false)
        } else {
            val text = state.exportText.orEmpty()
            scope.launch { records.exportSaved(saved = fileIo.write(context, uri, text)) }
        }
    }
    val openLauncher = rememberOpenFileLauncher { uri ->
        if (uri != null) scope.launch { records.importBind(fileName = BIND_FILE_NAME, read = fileIo.read(context, uri)) }
    }
    LaunchedEffect(state.exportText) {
        state.exportText?.let { saveLauncher.launch("zone-$zoneId.txt") }
    }
    LaunchedEffect(state.message) {
        state.message?.let { text ->
            snackbar.showSnackbar(text.resolve(context))
            records.messageShown()
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.domains_title_dns),
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
        isRefreshing = state.refreshing,
        onRefresh = if (tab == 0) records::refresh else null,
        actions = {
            if (tab == 0) {
                RecordActions(
                    selectedCount = state.selectedIds.size,
                    onAdd = records::openCreate,
                    onDeleteSelected = { records.requestDelete(state.selectedIds.toList()) },
                    onImport = { openLauncher.launch(BIND_OPEN_TYPES) },
                    onExport = records::requestExport,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VirgaTabRow(
                tabs = listOf(stringResource(R.string.domains_tab_records), stringResource(R.string.domains_tab_dns_settings)),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            if (tab == 0) {
                RecordsTab(state, records)
            } else {
                DnsSettingsTab(dnssec, dnsSettings)
            }
        }
    }
}

@Composable
private fun RecordActions(
    selectedCount: Int,
    onAdd: () -> Unit,
    onDeleteSelected: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    if (selectedCount > 0) {
        IconButton(onClick = onDeleteSelected) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.domains_delete_selected_cd))
        }
    }
    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.domains_add_record_cd)) }
    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.domains_menu_cd)) }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.domains_import_bind)) },
            onClick = { menuOpen = false; onImport() },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.domains_export_bind)) },
            onClick = { menuOpen = false; onExport() },
        )
    }
}

@Composable
private fun RecordsTab(state: DnsRecordsUiState, viewModel: DnsRecordsViewModel) {
    val deleteIds = state.pendingDeleteIds
    Column(Modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = stringResource(R.string.domains_search_records),
        )
        TypeFilterRow(selected = state.typeFilter, onSelect = viewModel::setTypeFilter)
        state.typeFilter?.let { type ->
            ActiveTypeFilter(type = type, onClear = { viewModel.setTypeFilter(null) })
        }
        UiStateContent(state = state.records, onRetry = viewModel::retry, modifier = Modifier.fillMaxSize()) { _ ->
            RecordList(state, viewModel)
        }
    }
    val editor = state.editor
    if (editor != null) {
        DnsRecordEditorSheet(
            editor = editor,
            errors = state.formErrors,
            saving = state.saving,
            isPaidPlan = state.isPaidPlan,
            onChange = viewModel::updateForm,
            onSave = viewModel::save,
            onDelete = viewModel::requestDeleteEditing,
            onDismiss = viewModel::closeEditor,
        )
    }
    if (deleteIds != null) {
        ConfirmDialog(
            title = stringResource(R.string.domains_confirm_delete_records_title),
            message = stringResource(R.string.domains_confirm_delete_records_message, deleteIds.size),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

/** Shows which type the list is filtered to, with a way back to every type. */
@Composable
private fun ActiveTypeFilter(type: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.domains_type_filter_active, type),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.domains_type_filter_clear)) }
    }
}

@Composable
private fun RecordList(state: DnsRecordsUiState, viewModel: DnsRecordsViewModel) {
    val visible = remember(state) { state.visibleRecords() }
    if (visible.isEmpty()) {
        RecordsEmptyState(state, onClear = { viewModel.setTypeFilter(null) })
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(visible, key = { it.id }) { record ->
            RecordRow(
                record = record,
                selected = record.id in state.selectedIds,
                onOpen = { viewModel.openEdit(record) },
                onToggle = { viewModel.toggleSelected(record.id) },
                onDelete = { viewModel.requestDelete(listOf(record.id)) },
            )
        }
        item {
            LoadMoreFooter(
                state = loadMoreStateOf(state.hasMore, state.loadingMore, state.loadMoreFailed),
                onLoadMore = viewModel::loadMore,
            )
        }
    }
}

/** Nothing to show: either the zone has no records, or the search or the type filter hides them all. */
@Composable
private fun RecordsEmptyState(state: DnsRecordsUiState, onClear: () -> Unit) {
    val filteredByType = state.typeFilter != null && state.query.isBlank()
    val title = when {
        state.query.isBlank() && state.typeFilter == null -> stringResource(R.string.domains_records_empty)
        filteredByType -> stringResource(R.string.domains_empty_filtered_type, state.typeFilter.orEmpty())
        else -> stringResource(R.string.domains_records_empty_filtered)
    }
    if (filteredByType) {
        EmptyState(
            title = title,
            modifier = Modifier.fillMaxSize(),
            action = { TextButton(onClick = onClear) { Text(stringResource(R.string.domains_show_all_records)) } },
        )
    } else {
        EmptyState(title = title, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun RecordRow(
    record: com.tomasps.virga.feature.domains.dns.DnsRecord,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val proxy = when {
        !record.proxiable -> null
        record.proxied -> stringResource(R.string.domains_proxied)
        else -> stringResource(R.string.domains_dns_only)
    }
    val ttl = if (record.ttl == AUTO_TTL) stringResource(R.string.domains_ttl_auto) else record.ttl.toString()
    val value = record.displayValue()
    val supporting = listOfNotNull("${record.type} · $value", "TTL $ttl", proxy).joinToString(" · ")
    val moreName = stringResource(R.string.domains_record_more_cd, record.type, record.name, value)
    val selectName = stringResource(R.string.domains_record_select_cd, record.type, record.name, value)
    ListRow(
        headline = record.name,
        supporting = supporting,
        onClick = onOpen,
        modifier = Modifier.testTag(recordRowTag(record.id)),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = selectName },
                )
                RecordOptions(rowName = moreName, onDelete = onDelete)
            }
        },
    )
}

/** Per-record actions. Delete asks first, through the same confirmation as a bulk delete. */
@Composable
private fun RecordOptions(rowName: String, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = rowName)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.domains_record_delete_one)) },
            onClick = {
                open = false
                onDelete()
            },
        )
    }
}

/** Test tag of a record row, so a test can tap one record among several with the same name. */
internal fun recordRowTag(recordId: String): String = "record-row-$recordId"

@Composable
private fun TypeFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.domains_status_all)) })
        DnsRecordType.entries.forEach { type ->
            FilterChip(
                selected = selected == type.name,
                onClick = { onSelect(type.name) },
                label = { Text(type.name) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private const val BIND_MIME = "text/plain"
private const val BIND_FILE_NAME = "zone.bind"
private val BIND_OPEN_TYPES = arrayOf("text/plain", "application/octet-stream")
