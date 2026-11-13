package com.tomasps.virga.feature.r2.bucket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.DestructiveMenuItem
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2BucketInfo
import com.tomasps.virga.feature.r2.common.r2DateLabel
import com.tomasps.virga.feature.r2.common.r2StorageClassLabel
import com.tomasps.virga.feature.r2.metrics.R2MetricsTab
import com.tomasps.virga.feature.r2.objects.R2ObjectsTab
import com.tomasps.virga.feature.r2.settings.R2SettingsTab
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One bucket: header with settings, then the Objects, Settings and Metrics tabs. Deleting asks for the name. */
@Composable
internal fun R2BucketRoute(bucketName: String) {
    val navigator = LocalNavigator.current
    val viewModel: R2BucketViewModel = koinViewModel(parameters = { parametersOf(bucketName) })
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val deleteError = state.deleteError?.message?.asString()
    LaunchedEffect(state.deleted) { if (state.deleted) navigator.back() }
    LaunchedEffect(deleteError) { deleteError?.let { snackbar.showSnackbar(it) } }
    val tabs = listOf(
        stringResource(R.string.r2_tab_objects),
        stringResource(R.string.r2_tab_settings),
        stringResource(R.string.r2_tab_metrics),
    )
    ScreenScaffold(
        title = bucketName,
        onBack = { navigator.back() },
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.r2_action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DestructiveMenuItem(
                    text = stringResource(R.string.r2_action_delete_bucket),
                    onClick = {
                        menuOpen = false
                        viewModel.requestDelete()
                    },
                )
            }
        },
        snackbarHostState = snackbar,
        onRefresh = { refreshTick++ },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.bucket?.let { bucket ->
                UiStateContent(state = bucket, onRetry = viewModel::retry) { info ->
                    BucketHeader(info)
                }
            }
            VirgaTabRow(tabs = tabs, selectedIndex = state.selectedTab, onSelect = viewModel::selectTab)
            when (state.selectedTab) {
                R2BucketUiState.TAB_OBJECTS -> R2ObjectsTab(bucketName, refreshTick)
                R2BucketUiState.TAB_SETTINGS -> R2SettingsTab(bucketName, refreshTick)
                else -> R2MetricsTab(bucketName, refreshTick)
            }
        }
    }
    if (state.confirmDelete) {
        TypedNameConfirmDialog(
            title = stringResource(R.string.r2_delete_bucket_title),
            message = stringResource(R.string.r2_delete_bucket_message, bucketName),
            expectedName = bucketName,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
            confirmLabel = stringResource(R.string.r2_action_delete_bucket),
        )
    }
}

@Composable
private fun BucketHeader(info: R2BucketInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        KeyValueRow(key = stringResource(R.string.r2_header_location), value = info.location?.takeIf { it.isNotBlank() }?.let { r2LocationLabel(it) } ?: "-")
        KeyValueRow(key = stringResource(R.string.r2_header_storage_class), value = r2StorageClassLabel(info.storageClass, "-"))
        info.jurisdiction?.takeIf { it.isNotBlank() }?.let {
            KeyValueRow(key = stringResource(R.string.r2_header_jurisdiction), value = it)
        }
        KeyValueRow(key = stringResource(R.string.r2_header_created), value = r2DateLabel(info.creationDate, "-"))
    }
}

