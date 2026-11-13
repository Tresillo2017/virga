package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.CodeBlock
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.files.rememberSaveFileLauncher
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.kv.KvKeyDetail
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.key.KvKeyDetailViewModel
import com.tomasps.virga.feature.kv.key.KvKeyEvent
import com.tomasps.virga.feature.kv.key.KvKeyDetails
import com.tomasps.virga.feature.kv.value.KvValueView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

private val SECTION_GAP = 16.dp
private val ROW_GAP = 8.dp
private val EDITOR_MIN_LINES = 6

/** One key: its expiration and metadata, its value on request, and the edit, download and delete actions. */
@Composable
fun KvKeyDetailRoute(key: KvKeyDetail) {
    val navigator = LocalNavigator.current
    val viewModel: KvKeyDetailViewModel = koinViewModel(parameters = { parametersOf(key.namespaceId, key.keyName) })
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val saveLauncher = rememberSaveFileLauncher(mimeType = DOWNLOAD_MIME_TYPE) { uri ->
        viewModel.saveDownload(uri?.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KvKeyEvent.Message -> launch { snackbar.showSnackbar(event.text.resolve(context)) }
                KvKeyEvent.Closed -> navigator.back()
                is KvKeyEvent.DownloadReady -> saveLauncher.launch(event.suggestedName)
            }
        }
    }
    ScreenScaffold(
        title = key.keyName,
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
        onRefresh = viewModel::retry,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(SECTION_GAP),
        ) {
            if (state.isMissing) {
                EmptyState(
                    title = stringResource(R.string.kv_key_missing_title),
                    message = stringResource(R.string.kv_key_missing_message),
                )
            } else {
                UiStateContent(state = state.details ?: UiState.Loading, onRetry = viewModel::retry) { details ->
                    KeyDetailsSection(details = details, onEditMetadata = viewModel::openMetadataEditor, onEditExpiration = viewModel::openExpirationEditor)
                }
                Spacer(Modifier.height(SECTION_GAP))
                ValueSection(
                    state = state,
                    onShowValue = viewModel::showValue,
                    onStartEdit = viewModel::startEdit,
                    onEditChange = viewModel::updateEditText,
                    onSaveEdit = viewModel::saveEdit,
                    onCancelEdit = viewModel::cancelEditors,
                    onDownload = viewModel::prepareDownload,
                )
                Spacer(Modifier.height(SECTION_GAP))
                OutlinedButton(
                    onClick = viewModel::requestDelete,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                ) {
                    Text(stringResource(R.string.kv_action_delete_key), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    KeyDialogs(state = state, viewModel = viewModel, keyName = key.keyName)
}

@Composable
private fun KeyDetailsSection(
    details: KvKeyDetails,
    onEditMetadata: () -> Unit,
    onEditExpiration: () -> Unit,
) {
    val expires = details.expiresAtEpochSeconds?.let { formatEpochSeconds(it) }
        ?: stringResource(R.string.kv_key_row_no_expiry)
    Column {
        SectionHeader(title = stringResource(R.string.kv_section_details))
        SectionCard(rows = listOf({ KeyValueRow(key = stringResource(R.string.kv_detail_expires), value = expires) }))
        SectionHeader(title = stringResource(R.string.kv_section_metadata))
        val metadata = details.metadata
        if (metadata == null) {
            Text(stringResource(R.string.kv_metadata_none))
        } else {
            CodeBlock(code = prettyJson(metadata), modifier = Modifier.fillMaxWidth())
        }
        Row(modifier = Modifier.padding(top = ROW_GAP)) {
            OutlinedButton(onClick = onEditMetadata) { Text(stringResource(R.string.kv_action_edit_metadata)) }
            Spacer(Modifier.width(ROW_GAP))
            OutlinedButton(onClick = onEditExpiration) { Text(stringResource(R.string.kv_action_edit_expiration)) }
        }
    }
}

@Composable
private fun ValueSection(
    state: com.tomasps.virga.feature.kv.key.KvKeyUiState,
    onShowValue: () -> Unit,
    onStartEdit: () -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDownload: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.kv_section_value))
    val editText = state.editText
    when {
        editText != null -> ValueEditor(editText, state.isBusy, onEditChange, onSaveEdit, onCancelEdit)
        else -> when (val value = state.value) {
            null -> Button(onClick = onShowValue) { Text(stringResource(R.string.kv_action_show_value)) }
            is UiState.Loading -> LoadingState()
            is UiState.Failure -> ErrorState(error = value.error, onRetry = onShowValue)
            is UiState.Content -> ValueView(value.data, state.canEditText, onStartEdit)
        }
    }
    OutlinedButton(
        onClick = onDownload,
        modifier = Modifier.padding(top = ROW_GAP),
        enabled = !state.isBusy,
    ) {
        Icon(Icons.Filled.Download, contentDescription = null)
        Spacer(Modifier.width(ROW_GAP))
        Text(stringResource(R.string.kv_action_download))
    }
}

@Composable
private fun ValueView(view: KvValueView, canEdit: Boolean, onStartEdit: () -> Unit) {
    when (view) {
        is KvValueView.Text -> {
            CodeBlock(code = view.text, modifier = Modifier.fillMaxWidth())
            if (canEdit) {
                TextButton(onClick = onStartEdit) { Text(stringResource(R.string.kv_action_edit_text)) }
            }
        }
        is KvValueView.Binary -> {
            KeyValueRow(key = stringResource(R.string.kv_value_size), value = formatBytes(view.size))
            Text(stringResource(R.string.kv_value_binary_note), modifier = Modifier.padding(vertical = ROW_GAP))
            CodeBlock(code = view.hexPreview, modifier = Modifier.fillMaxWidth())
            if (view.truncated) {
                Text(stringResource(R.string.kv_value_hex_truncated, view.previewBytes))
            }
        }
    }
}

@Composable
private fun ValueEditor(
    text: String,
    busy: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = EDITOR_MIN_LINES,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        label = { Text(stringResource(R.string.kv_value_editor_label)) },
        supportingText = { Text(stringResource(R.string.kv_value_editor_hint)) },
    )
    Row(modifier = Modifier.padding(top = ROW_GAP)) {
        Button(onClick = onSave, enabled = !busy) { Text(stringResource(R.string.kv_action_save)) }
        Spacer(Modifier.width(ROW_GAP))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.kv_action_cancel)) }
    }
}

@Composable
private fun KeyDialogs(
    state: com.tomasps.virga.feature.kv.key.KvKeyUiState,
    viewModel: KvKeyDetailViewModel,
    keyName: String,
) {
    state.metadataText?.let { text ->
        KvMetadataDialog(
            text = text,
            onTextChange = viewModel::updateMetadataText,
            onSave = viewModel::saveMetadata,
            onDismiss = viewModel::cancelEditors,
        )
    }
    state.expirationDraft?.let { draft ->
        KvExpirationDialog(
            draft = draft,
            onDraftChange = viewModel::updateExpirationDraft,
            onSave = viewModel::saveExpiration,
            onDismiss = viewModel::cancelEditors,
        )
    }
    if (state.showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.kv_delete_key_title),
            message = stringResource(R.string.kv_delete_key_message, keyName),
            confirmLabel = stringResource(R.string.kv_action_delete),
            destructive = true,
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

private val PRETTY = Json { prettyPrint = true }

private fun prettyJson(element: JsonElement): String = PRETTY.encodeToString(JsonElement.serializer(), element)
private const val DOWNLOAD_MIME_TYPE = "application/octet-stream"
