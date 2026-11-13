package com.tomasps.virga.feature.r2.objects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2ObjectItem
import com.tomasps.virga.feature.r2.common.r2DateLabel
import com.tomasps.virga.feature.r2.common.r2StorageClassLabel

/** Details of one object with download and delete. Both actions are handed back to the tab, which confirms deletes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObjectDetailsSheet(
    item: R2ObjectItem,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unknown = stringResource(R.string.r2_detail_unknown)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(text = item.key, style = MaterialTheme.typography.titleMedium)
            KeyValueRow(key = stringResource(R.string.r2_detail_size), value = formatBytes(item.size))
            KeyValueRow(key = stringResource(R.string.r2_detail_etag), value = item.etag ?: unknown, monospaceValue = true)
            KeyValueRow(key = stringResource(R.string.r2_detail_content_type), value = item.contentType ?: unknown)
            KeyValueRow(key = stringResource(R.string.r2_detail_last_modified), value = r2DateLabel(item.lastModified, unknown))
            KeyValueRow(key = stringResource(R.string.r2_detail_storage_class), value = r2StorageClassLabel(item.storageClass, unknown))
            Text(
                text = stringResource(R.string.r2_detail_custom_metadata),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (item.customMetadata.isEmpty()) {
                Text(text = stringResource(R.string.r2_detail_no_metadata), style = MaterialTheme.typography.bodyMedium)
            } else {
                item.customMetadata.forEach { (name, value) -> KeyValueRow(key = name, value = value) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text(stringResource(R.string.r2_action_download), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.r2_action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
