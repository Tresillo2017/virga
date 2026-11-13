package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.files.rememberOpenFileLauncher
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.kv.KvAddKey
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.addkey.KvAddKeyEvent
import com.tomasps.virga.feature.kv.addkey.KvAddKeySource
import com.tomasps.virga.feature.kv.addkey.KvAddKeyViewModel
import com.tomasps.virga.feature.kv.value.KV_MAX_VALUE_BYTES
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

private val FORM_PADDING = 16.dp
private val FORM_GAP = 12.dp
private const val VALUE_MIN_LINES = 4
private const val METADATA_MIN_LINES = 3

/** Creates one key from typed text or a picked file, with metadata and an expiration. */
@Composable
fun KvAddKeyRoute(key: KvAddKey) {
    val navigator = LocalNavigator.current
    val viewModel: KvAddKeyViewModel = koinViewModel(parameters = { parametersOf(key.namespaceId) })
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val filePicker = rememberOpenFileLauncher { uri -> viewModel.pickFile(uri?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KvAddKeyEvent.Message -> launch { snackbar.showSnackbar(event.text.resolve(context)) }
                is KvAddKeyEvent.Saved -> navigator.back()
            }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.kv_add_key_title),
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(FORM_PADDING),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FORM_GAP),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.kv_key_name_label)) },
                supportingText = { Text(stringResource(R.string.kv_key_name_hint)) },
            )
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FORM_GAP)) {
                FilterChip(
                    selected = state.source == KvAddKeySource.TEXT,
                    onClick = { viewModel.setSource(KvAddKeySource.TEXT) },
                    label = { Text(stringResource(R.string.kv_source_text)) },
                )
                FilterChip(
                    selected = state.source == KvAddKeySource.FILE,
                    onClick = { viewModel.setSource(KvAddKeySource.FILE) },
                    label = { Text(stringResource(R.string.kv_source_file)) },
                )
            }
            if (state.source == KvAddKeySource.TEXT) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = VALUE_MIN_LINES,
                    label = { Text(stringResource(R.string.kv_value_label)) },
                    supportingText = { Text(stringResource(R.string.kv_value_size_hint, formatBytes(state.text.encodeToByteArray().size.toLong()), formatBytes(KV_MAX_VALUE_BYTES))) },
                )
            } else {
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.kv_action_choose_file))
                }
                state.fileUri?.let {
                    val size = state.fileSize?.let { formatBytes(it) } ?: stringResource(R.string.kv_size_unknown)
                    Text(stringResource(R.string.kv_file_summary, state.fileName.ifBlank { stringResource(R.string.kv_file_unnamed) }, size))
                }
                Text(stringResource(R.string.kv_value_size_limit, formatBytes(KV_MAX_VALUE_BYTES)))
            }
            OutlinedTextField(
                value = state.metadataText,
                onValueChange = viewModel::setMetadata,
                modifier = Modifier.fillMaxWidth(),
                minLines = METADATA_MIN_LINES,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                label = { Text(stringResource(R.string.kv_metadata_label)) },
                supportingText = { Text(stringResource(R.string.kv_metadata_optional_hint)) },
            )
            SectionHeader(title = stringResource(R.string.kv_section_expiration))
            KvExpirationFields(draft = state.expirationDraft, onChange = viewModel::setExpiration)
            Button(
                onClick = viewModel::save,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.kv_action_save_key))
            }
        }
    }
    if (state.showOverwrite) {
        ConfirmDialog(
            title = stringResource(R.string.kv_overwrite_title),
            message = stringResource(R.string.kv_overwrite_message, state.name),
            confirmLabel = stringResource(R.string.kv_action_overwrite),
            destructive = true,
            onConfirm = viewModel::confirmOverwrite,
            onDismiss = viewModel::dismissOverwrite,
        )
    }
}
