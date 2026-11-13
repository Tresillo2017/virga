package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.files.rememberOpenFileLauncher
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.KvImport
import com.tomasps.virga.feature.kv.importer.KvImportEvent
import com.tomasps.virga.feature.kv.importer.KvImportReport
import com.tomasps.virga.feature.kv.importer.KvImportViewModel
import com.tomasps.virga.feature.kv.importer.KvImportUiState
import com.tomasps.virga.feature.kv.value.BulkImportProblem
import com.tomasps.virga.feature.kv.value.PairProblem
import com.tomasps.virga.feature.kv.value.RejectedPair
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

private val SCREEN_PADDING = 16.dp
private val LIST_GAP = 8.dp
private const val LISTED_KEY_LIMIT = 50
private const val JSON_MIME_TYPE = "application/json"

/** Bulk import from a JSON file in the bulk-write format. The file is previewed before anything is sent. */
@Composable
fun KvImportRoute(key: KvImport) {
    val navigator = LocalNavigator.current
    val viewModel: KvImportViewModel = koinViewModel(parameters = { parametersOf(key.namespaceId) })
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val picker = rememberOpenFileLauncher { uri -> viewModel.pickFile(uri?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KvImportEvent.Message -> launch { snackbar.showSnackbar(event.text.resolve(context)) }
            }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.kv_import_title),
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(SCREEN_PADDING),
        ) {
            Text(stringResource(R.string.kv_import_intro))
            Button(onClick = { picker.launch(arrayOf(JSON_MIME_TYPE)) }, modifier = Modifier.padding(top = LIST_GAP)) {
                Text(stringResource(R.string.kv_action_choose_file))
            }
            if (state.hasFile) {
                KeyValueRow(
                    key = stringResource(R.string.kv_file_label),
                    value = state.fileName.ifBlank { stringResource(R.string.kv_file_unnamed) },
                )
            }
            ImportPreview(state = state, onImport = viewModel::startImport)
            state.report?.let { ImportReportSection(it) }
        }
    }
}

@Composable
private fun ImportPreview(state: KvImportUiState, onImport: () -> Unit) {
    state.refusal?.let { problem ->
        Text(stringResource(refusalText(problem)), modifier = Modifier.padding(top = LIST_GAP))
    }
    val preview = state.preview ?: return
    SectionHeader(title = stringResource(R.string.kv_import_preview_title))
    SectionCard(
        rows = listOf(
            { KeyValueRow(key = stringResource(R.string.kv_import_pairs), value = preview.pairCount.toString()) },
            { KeyValueRow(key = stringResource(R.string.kv_import_batches), value = preview.chunkCount.toString()) },
            { KeyValueRow(key = stringResource(R.string.kv_import_rejected), value = preview.rejected.size.toString()) },
        ),
    )
    Text(stringResource(R.string.kv_import_overwrite_notice), modifier = Modifier.padding(top = LIST_GAP))
    if (state.isImporting) {
        val progress = if (preview.chunkCount == 0) 0f else state.chunksDone.toFloat() / preview.chunkCount
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = LIST_GAP),
        )
        Text(stringResource(R.string.kv_import_progress, state.chunksDone, preview.chunkCount))
    } else {
        Button(
            onClick = onImport,
            enabled = preview.pairCount > 0,
            modifier = Modifier.fillMaxWidth().padding(top = LIST_GAP),
        ) {
            Text(stringResource(R.string.kv_action_import_pairs, preview.pairCount))
        }
    }
    if (preview.rejected.isNotEmpty()) {
        RejectedList(title = stringResource(R.string.kv_import_rejected_title), rejected = preview.rejected)
    }
}

@Composable
private fun ImportReportSection(report: KvImportReport) {
    SectionHeader(title = stringResource(R.string.kv_import_report_title))
    SectionCard(
        rows = listOf({ KeyValueRow(key = stringResource(R.string.kv_import_written), value = report.written.toString()) }),
    )
    if (report.failedChunks > 0) {
        Text(stringResource(R.string.kv_import_failed_chunks, report.failedChunks))
    }
    if (report.unsuccessfulKeys.isNotEmpty()) {
        KeyList(title = stringResource(R.string.kv_import_unsuccessful_title), keys = report.unsuccessfulKeys)
    }
    if (report.rejected.isNotEmpty()) {
        RejectedList(title = stringResource(R.string.kv_import_rejected_title), rejected = report.rejected)
    }
}

@Composable
private fun RejectedList(title: String, rejected: List<RejectedPair>) {
    Text(title, modifier = Modifier.padding(top = LIST_GAP))
    rejected.take(LISTED_KEY_LIMIT).forEach { pair ->
        val name = pair.key.ifBlank { stringResource(R.string.kv_import_no_key) }
        Text(stringResource(R.string.kv_import_pair_reason, name, stringResource(pairProblemText(pair.problem))))
    }
    if (rejected.size > LISTED_KEY_LIMIT) {
        Text(stringResource(R.string.kv_import_and_more, rejected.size - LISTED_KEY_LIMIT))
    }
}

@Composable
private fun KeyList(title: String, keys: List<String>) {
    Text(title, modifier = Modifier.padding(top = LIST_GAP))
    keys.take(LISTED_KEY_LIMIT).forEach { Text(it) }
    if (keys.size > LISTED_KEY_LIMIT) {
        Text(stringResource(R.string.kv_import_and_more, keys.size - LISTED_KEY_LIMIT))
    }
}

private fun refusalText(problem: BulkImportProblem): Int = when (problem) {
    BulkImportProblem.NOT_JSON -> R.string.kv_import_refused_not_json
    BulkImportProblem.NOT_AN_ARRAY -> R.string.kv_import_refused_not_array
    BulkImportProblem.TOO_MANY_PAIRS -> R.string.kv_import_refused_too_many
}

private fun pairProblemText(problem: PairProblem): Int = when (problem) {
    PairProblem.NOT_AN_OBJECT -> R.string.kv_import_problem_not_object
    PairProblem.MISSING_KEY -> R.string.kv_import_problem_missing_key
    PairProblem.MISSING_VALUE -> R.string.kv_import_problem_missing_value
    PairProblem.KEY_EMPTY -> R.string.kv_import_problem_key_empty
    PairProblem.KEY_DOT_SEGMENT -> R.string.kv_key_dot_segment
    PairProblem.KEY_TOO_LONG -> R.string.kv_key_too_long
    PairProblem.VALUE_TOO_LARGE -> R.string.kv_import_problem_value_large
    PairProblem.TTL_TOO_SHORT -> R.string.kv_error_expiration_ttl_short
}
