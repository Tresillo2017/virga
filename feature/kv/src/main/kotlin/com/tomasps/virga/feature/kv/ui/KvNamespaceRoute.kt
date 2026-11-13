package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.DestructiveMenuItem
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.kv.namespace.KvNamespaceUiState
import com.tomasps.virga.feature.kv.KvAddKey
import com.tomasps.virga.feature.kv.KvImport
import com.tomasps.virga.feature.kv.KvKeyDetail
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.metrics.KvMetricsViewModel
import com.tomasps.virga.feature.kv.namespace.KvNamespaceEvent
import com.tomasps.virga.feature.kv.namespace.KvNamespaceViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

/** One namespace: its keys (search, paging, bulk delete) and its analytics, with rename and delete in the menu. */
@Composable
fun KvNamespaceRoute(key: KvNamespace) {
    val navigator = LocalNavigator.current
    val viewModel: KvNamespaceViewModel = koinViewModel(parameters = { parametersOf(key.namespaceId) })
    val metrics: KvMetricsViewModel = koinViewModel(parameters = { parametersOf(key.namespaceId) })
    val state by viewModel.state.collectAsState()
    val metricsState by metrics.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(KEYS_TAB) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KvNamespaceEvent.Message -> launch { snackbar.showSnackbar(event.text.resolve(context)) }
                KvNamespaceEvent.Closed -> navigator.back()
            }
        }
    }
    val title = state.title.ifBlank { key.namespaceId }
    ScreenScaffold(
        title = title,
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
        isRefreshing = state.isRefreshing || metricsState.isRefreshing,
        onRefresh = {
            viewModel.refresh()
            metrics.refresh()
        },
        actions = {
            if (tab == KEYS_TAB) {
                IconButton(onClick = { navigator.navigate(KvAddKey(key.namespaceId)) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.kv_action_add_key))
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.kv_action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.kv_action_import)) },
                    leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        navigator.navigate(KvImport(key.namespaceId))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.kv_action_rename)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.requestRename()
                    },
                )
                DestructiveMenuItem(
                    text = stringResource(R.string.kv_action_delete_namespace),
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.requestDelete()
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            VirgaTabRow(
                tabs = listOf(stringResource(R.string.kv_tab_keys), stringResource(R.string.kv_tab_metrics)),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            if (tab == KEYS_TAB) {
                KvKeysTab(
                    state = state,
                    onPrefixChange = viewModel::setPrefix,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::refresh,
                    onToggleSelected = viewModel::toggleSelected,
                    onOpenKey = { name -> navigator.navigate(KvKeyDetail(key.namespaceId, name)) },
                    onClearSelection = viewModel::clearSelection,
                    onRequestBulkDelete = viewModel::requestBulkDelete,
                    onOpenDashboard = { navigator.openInBrowser(DASHBOARD_URL) },
                )
            } else {
                KvMetricsTab(state = metricsState, onRetry = metrics::refresh)
            }
        }
    }
    NamespaceDialogs(state = state, viewModel = viewModel, title = title, namespaceId = key.namespaceId)
}

@Composable
private fun NamespaceDialogs(
    state: KvNamespaceUiState,
    viewModel: KvNamespaceViewModel,
    title: String,
    namespaceId: String,
) {
    if (state.showRename) {
        KvTextInputDialog(
            title = stringResource(R.string.kv_rename_namespace_title),
            label = stringResource(R.string.kv_namespace_title_label),
            initial = state.title,
            confirmLabel = stringResource(R.string.kv_action_save),
            supporting = stringResource(R.string.kv_namespace_title_hint),
            onConfirm = viewModel::rename,
            onDismiss = viewModel::dismissDialogs,
        )
    }
    if (state.showDelete) {
        TypedNameConfirmDialog(
            title = stringResource(R.string.kv_delete_namespace_title),
            message = stringResource(R.string.kv_delete_namespace_message),
            expectedName = title.ifBlank { namespaceId },
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDialogs,
            confirmLabel = stringResource(R.string.kv_action_delete),
        )
    }
    if (state.showBulkDelete) {
        ConfirmDialog(
            title = stringResource(R.string.kv_bulk_delete_title),
            message = stringResource(R.string.kv_bulk_delete_message, state.selected.size),
            confirmLabel = stringResource(R.string.kv_action_delete),
            destructive = true,
            onConfirm = viewModel::confirmBulkDelete,
            onDismiss = viewModel::dismissDialogs,
        )
    }
}

private const val KEYS_TAB = 0
private const val DASHBOARD_URL = "https://dash.cloudflare.com/"
