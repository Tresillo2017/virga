package com.tomasps.virga.feature.r2.objects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.files.rememberOpenFileLauncher
import com.tomasps.virga.core.ui.files.rememberSaveFileLauncher
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.common.MessageStrip
import com.tomasps.virga.feature.r2.api.R2ObjectItem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Object browser for one bucket. Folders are navigated by prefix and delimiter; search matches keys under the
 * current folder. Downloads and uploads go through the app-scoped coordinator, so they keep running after the
 * user leaves this tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun R2ObjectsTab(bucketName: String, refreshKey: Int) {
    val viewModel: R2ObjectsViewModel = koinViewModel(parameters = { parametersOf(bucketName) })
    val state by viewModel.state.collectAsState()
    val transfers by viewModel.transfers.collectAsState()
    val saveLauncher = rememberSaveFileLauncher(mimeType = OCTET_STREAM) { uri ->
        viewModel.onDownloadDestination(uri?.toString())
    }
    val uploadLauncher = rememberOpenFileLauncher { uri -> viewModel.onUploadPicked(uri?.toString()) }
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.refresh() }
    val download: (R2ObjectItem) -> Unit = { item ->
        viewModel.prepareDownload(item)
        saveLauncher.launch(item.key.substringAfterLast('/'))
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(
                query = state.search,
                onQueryChange = viewModel::onSearchChanged,
                placeholder = stringResource(R.string.r2_objects_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FolderBar(state.folderPrefix, viewModel::goUp, bucketName)
            state.message?.let { message ->
                MessageStrip(text = message.asString(), onDismiss = viewModel::messageShown)
            }
            state.actionError?.let { error ->
                ErrorState(error = error, modifier = Modifier.fillMaxWidth())
            }
            if (transfers.isNotEmpty()) {
                TransfersPanel(
                    transfers = transfers,
                    onCancel = viewModel::cancelTransfer,
                    onDismiss = viewModel::dismissTransfer,
                )
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                state.listing?.let { listing ->
                    UiStateContent(state = listing, onRetry = viewModel::retry) { content ->
                        if (content.folders.isEmpty() && content.objects.isEmpty() && state.search.isNotBlank()) {
                            EmptyState(
                                title = stringResource(R.string.r2_empty_search_title),
                                message = stringResource(R.string.r2_empty_search_objects_message),
                            )
                        } else if (content.folders.isEmpty() && content.objects.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.r2_empty_objects_title),
                                message = stringResource(R.string.r2_empty_objects_message),
                            )
                        } else {
                            ObjectList(
                                listing = content,
                                prefix = state.folderPrefix,
                                selected = state.selectedKeys,
                                loadMore = state.loadMore,
                                onOpenFolder = viewModel::openFolder,
                                onToggle = viewModel::toggleSelected,
                                onDetails = viewModel::showDetails,
                                onLoadMore = viewModel::loadMore,
                            )
                        }
                    }
                }
            }
            if (state.selectedKeys.isNotEmpty()) {
                SelectionBar(
                    count = state.selectedKeys.size,
                    onDelete = { viewModel.requestDelete(state.selectedKeys.toList()) },
                    onClear = viewModel::clearSelection,
                )
            }
        }
        if (state.selectedKeys.isEmpty()) {
            FloatingActionButton(
                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.r2_action_upload))
            }
        }
    }
    state.details?.let { item ->
        ObjectDetailsSheet(
            item = item,
            onDownload = {
                viewModel.dismissDetails()
                download(item)
            },
            onDelete = {
                viewModel.dismissDetails()
                viewModel.requestDelete(listOf(item.key))
            },
            onDismiss = viewModel::dismissDetails,
        )
    }
    state.pendingDelete?.let { keys ->
        DeleteObjectsDialog(
            keys = keys,
            prefix = state.folderPrefix,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
    state.pendingOverwrite?.let { upload ->
        ConfirmDialog(
            title = stringResource(R.string.r2_upload_overwrite_title, upload.key.substringAfterLast('/')),
            message = stringResource(R.string.r2_upload_overwrite_message),
            onConfirm = viewModel::confirmOverwrite,
            onDismiss = viewModel::dismissOverwrite,
            confirmLabel = stringResource(R.string.r2_upload_overwrite_confirm),
            destructive = true,
        )
    }
}

@Composable
private fun FolderBar(prefix: String, onUp: () -> Unit, bucketName: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (prefix.isNotEmpty()) {
            IconButton(onClick = onUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.r2_action_up),
                )
            }
        }
        Text(
            text = if (prefix.isEmpty()) bucketName else prefix,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onDelete: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onClear) { Text(stringResource(R.string.r2_action_clear_selection)) }
        Text(text = stringResource(R.string.r2_selected_count, count))
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.r2_action_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeleteObjectsDialog(keys: List<String>, prefix: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = if (keys.size == 1) {
        stringResource(R.string.r2_delete_object_title, keys.first().removePrefix(prefix))
    } else {
        stringResource(R.string.r2_delete_objects_title, keys.size)
    }
    ConfirmDialog(
        title = title,
        message = stringResource(R.string.r2_delete_object_message),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.r2_action_delete),
        destructive = true,
    )
}

private const val OCTET_STREAM = "application/octet-stream"
