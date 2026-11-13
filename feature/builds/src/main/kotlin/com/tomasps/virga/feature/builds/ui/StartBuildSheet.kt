package com.tomasps.virga.feature.builds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildTarget

/**
 * Bottom sheet that starts a build by branch or by commit, through the Worker's first trigger. Blank input is
 * refused here, before anything is sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartBuildSheet(
    state: WorkerBuildsUiState,
    onStart: (BuildTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SHEET_GUTTER, vertical = SHEET_GUTTER),
            verticalArrangement = Arrangement.spacedBy(SHEET_SPACING),
        ) {
            Text(text = stringResource(R.string.builds_trigger_title), style = MaterialTheme.typography.titleMedium)
            when {
                !state.triggersLoaded && state.startSheetError == null ->
                    Text(text = stringResource(R.string.builds_trigger_loading))
                state.triggers.isEmpty() -> Text(text = stringResource(R.string.builds_trigger_none))
                else -> StartBuildForm(state = state, onStart = onStart)
            }
            state.startSheetError?.let { error -> Text(text = error.asString()) }
        }
    }
}

@Composable
private fun StartBuildForm(state: WorkerBuildsUiState, onStart: (BuildTarget) -> Unit) {
    var byCommit by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf("") }
    var showInputError by rememberSaveable { mutableStateOf(false) }
    VirgaTabRow(
        tabs = listOf(
            stringResource(R.string.builds_trigger_by_branch),
            stringResource(R.string.builds_trigger_by_commit),
        ),
        selectedIndex = if (byCommit) 1 else 0,
        onSelect = { index ->
            byCommit = index == 1
            showInputError = false
        },
    )
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = {
            Text(
                stringResource(
                    if (byCommit) R.string.builds_trigger_commit_label else R.string.builds_trigger_branch_label,
                ),
            )
        },
        singleLine = true,
        isError = showInputError,
        modifier = Modifier.fillMaxWidth(),
    )
    if (showInputError) Text(text = stringResource(R.string.builds_trigger_needs_value))
    Button(
        onClick = {
            val target = buildTargetFor(byCommit, value)
            if (target == null) showInputError = true else onStart(target)
        },
        enabled = !state.isStarting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.builds_trigger_start))
    }
}

private val SHEET_GUTTER = 16.dp
private val SHEET_SPACING = 12.dp
