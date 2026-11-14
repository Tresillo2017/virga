package com.tomasps.virga.feature.d1.detail.export

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.d1.R

/** Callbacks from the export tab to its view model. */
data class ExportCallbacks(
    val onStructureOnly: (Boolean) -> Unit,
    val onDataOnly: (Boolean) -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun ExportTab(state: ExportUiState, callbacks: ExportCallbacks) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionHeader(title = stringResource(R.string.d1_export_title))
        Text(stringResource(R.string.d1_export_intro), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.d1_export_unavailable_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OptionRow(stringResource(R.string.d1_export_structure_only), state.options.structureOnly, callbacks.onStructureOnly)
        OptionRow(stringResource(R.string.d1_export_data_only), state.options.dataOnly, callbacks.onDataOnly)
        when (val phase = state.phase) {
            is ExportPhase.Idle -> Button(onClick = callbacks.onStart) { Text(stringResource(R.string.d1_export_start)) }
            is ExportPhase.Running -> RunningView(phase.status, callbacks.onStop)
            is ExportPhase.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.d1_export_ready, phase.download.filename), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { enqueueDownload(context, phase.download) }) { Text(stringResource(R.string.d1_export_download)) }
                OutlinedButton(onClick = callbacks.onDismiss) { Text(stringResource(R.string.d1_export_done)) }
            }
            is ExportPhase.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(phase.error.title.asString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text(phase.error.message.asString(), style = MaterialTheme.typography.bodySmall)
                Button(onClick = callbacks.onStart) { Text(stringResource(R.string.d1_retry)) }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RunningView(status: String?, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIndicator(modifier = Modifier.padding(4.dp))
            Text(stringResource(R.string.d1_export_running, stringResource(exportProgressLabelRes(status))))
        }
        OutlinedButton(onClick = onStop) { Text(stringResource(R.string.d1_export_stop)) }
    }
}

/**
 * The wording for an export that is still running. The API's status id is never shown: a poll that has not reported
 * yet reads as starting, and any running status reads as in progress.
 */
@StringRes
internal fun exportProgressLabelRes(status: String?): Int = when (status) {
    null -> R.string.d1_export_status_starting
    else -> R.string.d1_export_status_in_progress
}

/** Hands the signed link to the download manager. The link itself is never shown or logged. */
private fun enqueueDownload(context: Context, download: ExportDownload) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
    val request = DownloadManager.Request(Uri.parse(download.signedUrl))
        .setTitle(download.filename)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, download.filename)
    manager.enqueue(request)
}
