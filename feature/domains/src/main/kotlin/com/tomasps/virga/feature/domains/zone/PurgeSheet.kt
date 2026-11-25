package com.tomasps.virga.feature.domains.zone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.zones.PurgeProblem
import com.tomasps.virga.feature.domains.zones.PurgeRequest
import com.tomasps.virga.feature.domains.zones.parsePurgeItems
import com.tomasps.virga.feature.domains.zones.validatePurge

/** The purge selectors the owner can pick. Hosts, prefixes and tags take one item per line or comma separated. */
enum class PurgeMode { EVERYTHING, FILES, HOSTS, PREFIXES, TAGS }

/** Builds the request for a mode from the typed items. Everything ignores the text. */
fun PurgeMode.toRequest(text: String): PurgeRequest {
    val items = parsePurgeItems(text)
    return when (this) {
        PurgeMode.EVERYTHING -> PurgeRequest.Everything
        PurgeMode.FILES -> PurgeRequest.Files(items)
        PurgeMode.HOSTS -> PurgeRequest.Hosts(items)
        PurgeMode.PREFIXES -> PurgeRequest.Prefixes(items)
        PurgeMode.TAGS -> PurgeRequest.Tags(items)
    }
}

/**
 * Asks which cache to purge. A sheet, so the typed items stay on screen with their problem: a selection that cannot be
 * sent keeps the sheet open, shows why next to the field, and keeps what the owner typed. Only a valid selection goes on
 * to the confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeSheet(onDismiss: () -> Unit, onSubmit: (PurgeRequest) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(PurgeMode.EVERYTHING) }
    var text by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<PurgeProblem?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.domains_purge_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.domains_purge_help), modifier = Modifier.padding(top = 8.dp))
            PurgeModeRow(selected = mode, onSelect = {
                mode = it
                problem = null
            })
            if (mode != PurgeMode.EVERYTHING) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        problem = null
                    },
                    label = { Text(stringResource(modeHint(mode))) },
                    minLines = 2,
                    isError = problem != null,
                    supportingText = problem?.let { found -> { Text(found.toText().asString()) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.domains_cancel)) }
                TextButton(onClick = {
                    val request = mode.toRequest(text)
                    val found = validatePurge(request)
                    if (found == null) onSubmit(request) else problem = found
                }) {
                    Text(stringResource(R.string.domains_purge_submit))
                }
            }
        }
    }
}

@Composable
private fun PurgeModeRow(selected: PurgeMode, onSelect: (PurgeMode) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        PurgeMode.entries.chunked(3).forEach { row ->
            Row {
                row.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = { Text(stringResource(modeLabel(mode))) },
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

private fun modeLabel(mode: PurgeMode): Int = when (mode) {
    PurgeMode.EVERYTHING -> R.string.domains_purge_mode_everything
    PurgeMode.FILES -> R.string.domains_purge_mode_files
    PurgeMode.HOSTS -> R.string.domains_purge_mode_hosts
    PurgeMode.PREFIXES -> R.string.domains_purge_mode_prefixes
    PurgeMode.TAGS -> R.string.domains_purge_mode_tags
}

private fun modeHint(mode: PurgeMode): Int = when (mode) {
    PurgeMode.EVERYTHING -> R.string.domains_purge_mode_everything
    PurgeMode.FILES -> R.string.domains_purge_hint_files
    PurgeMode.HOSTS -> R.string.domains_purge_hint_hosts
    PurgeMode.PREFIXES -> R.string.domains_purge_hint_prefixes
    PurgeMode.TAGS -> R.string.domains_purge_hint_tags
}
