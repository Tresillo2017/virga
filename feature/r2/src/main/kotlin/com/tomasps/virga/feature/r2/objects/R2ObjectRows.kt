package com.tomasps.virga.feature.r2.objects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2ObjectItem
import com.tomasps.virga.feature.r2.transfer.R2Transfer
import com.tomasps.virga.feature.r2.transfer.R2TransferDirection
import com.tomasps.virga.feature.r2.transfer.R2TransferStatus

/** Folders first, then objects. Folder names and object names are shown relative to [prefix]. */
@Composable
internal fun ObjectList(
    listing: R2ObjectListing,
    prefix: String,
    selected: Set<String>,
    loadMore: LoadMoreState,
    onOpenFolder: (String) -> Unit,
    onToggle: (String) -> Unit,
    onDetails: (R2ObjectItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val unknownType = stringResource(R.string.r2_unknown_content_type)
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(listing.folders, key = { "folder:$it" }) { folder ->
            ListRow(
                headline = folder.removePrefix(prefix).trimEnd('/'),
                leading = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenFolder(folder) },
            )
        }
        items(listing.objects, key = { "object:${it.key}" }) { item ->
            ListRow(
                headline = item.key.removePrefix(prefix),
                supporting = stringResource(
                    R.string.r2_object_supporting,
                    formatBytes(item.size),
                    item.contentType?.takeIf { it.isNotBlank() } ?: unknownType,
                ),
                leading = {
                    val label = stringResource(R.string.r2_cd_select_object, item.key.removePrefix(prefix))
                    Checkbox(
                        checked = item.key in selected,
                        onCheckedChange = { onToggle(item.key) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                },
                onClick = { onDetails(item) },
            )
        }
        item { LoadMoreFooter(state = loadMore, onLoadMore = onLoadMore) }
    }
}

/** Downloads and uploads in flight, and recently finished ones until the user dismisses them. */
@Composable
internal fun TransfersPanel(
    transfers: List<R2Transfer>,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.r2_transfers_title),
            style = MaterialTheme.typography.titleSmall,
        )
        transfers.forEach { transfer ->
            TransferRow(transfer = transfer, onCancel = { onCancel(transfer.id) }, onDismiss = { onDismiss(transfer.id) })
        }
    }
}

@Composable
private fun TransferRow(transfer: R2Transfer, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val name = transfer.objectKey.substringAfterLast('/')
    val directionLabel = when (transfer.direction) {
        R2TransferDirection.DOWNLOAD -> stringResource(R.string.r2_transfer_download, name)
        R2TransferDirection.UPLOAD -> stringResource(R.string.r2_transfer_upload, name)
    }
    val total = transfer.totalBytes
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = directionLabel, style = MaterialTheme.typography.bodyMedium)
        if (transfer.status == R2TransferStatus.RUNNING) {
            if (total != null && total > 0) {
                val fraction = (transfer.doneBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusText(transfer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (transfer.status == R2TransferStatus.RUNNING) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.r2_action_cancel)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.r2_action_dismiss)) }
            }
        }
    }
}

@Composable
private fun statusText(transfer: R2Transfer): String = when (transfer.status) {
    R2TransferStatus.RUNNING -> transfer.totalBytes?.let {
        stringResource(R.string.r2_transfer_progress, formatBytes(transfer.doneBytes), formatBytes(it))
    } ?: stringResource(R.string.r2_transfer_running)
    R2TransferStatus.COMPLETED -> stringResource(R.string.r2_transfer_completed)
    R2TransferStatus.CANCELLED -> stringResource(R.string.r2_transfer_cancelled)
    R2TransferStatus.FAILED -> transfer.failure?.message?.asString() ?: stringResource(R.string.r2_transfer_failed)
}
