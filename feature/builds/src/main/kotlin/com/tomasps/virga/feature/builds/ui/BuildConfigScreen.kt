package com.tomasps.virga.feature.builds.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.builds.BuildConfigScreen
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildConfig
import com.tomasps.virga.feature.builds.api.BuildConfigEdit
import com.tomasps.virga.feature.builds.api.PreviewSummary
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val DASHBOARD_URL = "https://dash.cloudflare.com/"
private val PAGE_GUTTER = 16.dp
private val SECTION_GAP = 10.dp
private val EDIT_SHEET_GUTTER = 16.dp

/** Repository connection and build settings, read and edited with PATCH. Previews are listed read-only. */
@Composable
fun BuildConfigRoute(key: BuildConfigScreen) {
    val viewModel: BuildConfigViewModel = koinViewModel(parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.builds_config_saved)
    var editing by remember { mutableStateOf(false) }
    // The save flag is a one-shot event: consume it here, so reopening Edit later is not closed at once.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.savedShown()
            editing = false
            scope.launch { snackbar.showSnackbar(savedMessage) }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.builds_config_title, key.scriptName),
        onBack = { navigator.back() },
        onRefresh = viewModel::retry,
        isRefreshing = state.content is UiState.Loading,
        snackbarHostState = snackbar,
    ) { padding ->
        UiStateContent(state = state.content, modifier = Modifier.fillMaxSize().padding(padding), onRetry = viewModel::retry) { content ->
            ConfigBody(
                config = content.config,
                previews = content.previews,
                previewsError = content.previewsError?.title?.asString(),
                onEdit = { editing = true },
                onOpenDashboard = { navigator.openInBrowser(DASHBOARD_URL) },
            )
        }
    }
    if (editing) {
        val current = (state.content as? UiState.Content)?.data?.config
        if (current != null) {
            EditConfigSheet(
                config = current,
                saving = state.isSaving,
                saveError = state.saveError?.message?.asString(),
                onSave = { edit -> viewModel.save(edit) },
                onDismiss = { editing = false },
            )
        }
    }
}

@Composable
private fun ConfigBody(
    config: BuildConfig,
    previews: List<PreviewSummary>,
    previewsError: String?,
    onEdit: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val none = stringResource(R.string.builds_value_none)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 24.dp),
    ) {
        item { SectionHeader(title = stringResource(R.string.builds_config_repository)) }
        item { SectionCard(rows = repositoryRows(config, none, onOpenDashboard), modifier = Modifier.padding(bottom = SECTION_GAP)) }
        item {
            SectionHeader(
                title = stringResource(R.string.builds_config_build_settings),
                action = { TextButton(onClick = onEdit) { Text(stringResource(R.string.builds_action_edit)) } },
            )
        }
        item { SectionCard(rows = buildSettingRows(config, none), modifier = Modifier.padding(bottom = SECTION_GAP)) }
        item { SectionHeader(title = stringResource(R.string.builds_config_previews)) }
        item { PreviewsSection(previews, previewsError) }
    }
}

private fun repositoryRows(
    config: BuildConfig,
    none: String,
    onOpenDashboard: () -> Unit,
): List<@Composable () -> Unit> {
    val connected = if (config.isConnected) {
        listOf<@Composable () -> Unit>(
            { KeyValueRow(key = stringResource(R.string.builds_field_repo), value = config.repoName ?: none) },
            { KeyValueRow(key = stringResource(R.string.builds_field_owner), value = config.providerAccountName ?: none) },
            { KeyValueRow(key = stringResource(R.string.builds_field_production_branch), value = config.productionBranch ?: none) },
        )
    } else {
        listOf<@Composable () -> Unit>(
            { ListRow(headline = stringResource(R.string.builds_config_not_connected)) },
        )
    }
    return connected + listOf<@Composable () -> Unit>({
        OpenInDashboardRow(
            label = stringResource(R.string.builds_dashboard_connect_label),
            description = stringResource(R.string.builds_dashboard_connect_description),
            onOpen = onOpenDashboard,
        )
    })
}

private fun buildSettingRows(config: BuildConfig, none: String): List<@Composable () -> Unit> = listOf(
    { KeyValueRow(key = stringResource(R.string.builds_field_build_command), value = config.buildCommand ?: none, monospaceValue = true) },
    { KeyValueRow(key = stringResource(R.string.builds_field_deploy_command), value = config.deployCommand ?: none, monospaceValue = true) },
    { KeyValueRow(key = stringResource(R.string.builds_field_root_directory), value = config.rootDirectory ?: none, monospaceValue = true) },
    {
        KeyValueRow(
            key = stringResource(R.string.builds_field_previews),
            value = stringResource(if (config.previewsEnabled == true) R.string.builds_value_on else R.string.builds_value_off),
        )
    },
)

@Composable
private fun PreviewsSection(previews: List<PreviewSummary>, previewsError: String?) {
    when {
        previewsError != null -> SectionCard(rows = listOf({ ListRow(headline = previewsError) }))
        previews.isEmpty() -> SectionCard(rows = listOf({ ListRow(headline = stringResource(R.string.builds_previews_empty)) }))
        else -> SectionCard(rows = previews.map<PreviewSummary, @Composable () -> Unit> { preview -> { PreviewRow(preview) } })
    }
}

@Composable
private fun PreviewRow(preview: PreviewSummary) {
    ListRow(
        headline = preview.branch.ifBlank { stringResource(R.string.builds_preview_untitled) },
        supporting = stringResource(if (preview.autoBuild == true) R.string.builds_preview_auto_on else R.string.builds_preview_auto_off),
    )
}

/**
 * Edits the repository's production branch and build settings. A bottom sheet, like the start-build sheet: the
 * text fields sit in a sheet rather than in an alert dialog. Blank production branch is refused before anything is sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditConfigSheet(
    config: BuildConfig,
    saving: Boolean,
    saveError: String?,
    onSave: (BuildConfigEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    var branch by rememberSaveable { mutableStateOf(config.productionBranch.orEmpty()) }
    var buildCommand by rememberSaveable { mutableStateOf(config.buildCommand.orEmpty()) }
    var deployCommand by rememberSaveable { mutableStateOf(config.deployCommand.orEmpty()) }
    var rootDirectory by rememberSaveable { mutableStateOf(config.rootDirectory.orEmpty()) }
    var previews by rememberSaveable { mutableStateOf(config.previewsEnabled == true) }
    var branchMissing by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (branch.isBlank()) {
            branchMissing = true
            return
        }
        onSave(
            BuildConfigEdit(
                productionBranch = branch.trim().takeIf { it != config.productionBranch.orEmpty() },
                buildCommand = buildCommand.takeIf { it != config.buildCommand.orEmpty() },
                deployCommand = deployCommand.takeIf { it != config.deployCommand.orEmpty() },
                rootDirectory = rootDirectory.takeIf { it != config.rootDirectory.orEmpty() },
                previewsEnabled = previews.takeIf { it != (config.previewsEnabled == true) },
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = EDIT_SHEET_GUTTER, vertical = EDIT_SHEET_GUTTER),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.builds_config_edit_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = branch,
                onValueChange = {
                    branch = it
                    branchMissing = false
                },
                label = { Text(stringResource(R.string.builds_field_production_branch)) },
                singleLine = true,
                isError = branchMissing,
            )
            if (branchMissing) {
                Text(
                    text = stringResource(R.string.builds_config_branch_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(value = buildCommand, onValueChange = { buildCommand = it }, label = { Text(stringResource(R.string.builds_field_build_command)) }, singleLine = true)
            OutlinedTextField(value = deployCommand, onValueChange = { deployCommand = it }, label = { Text(stringResource(R.string.builds_field_deploy_command)) }, singleLine = true)
            OutlinedTextField(value = rootDirectory, onValueChange = { rootDirectory = it }, label = { Text(stringResource(R.string.builds_field_root_directory)) }, singleLine = true)
            SwitchRow(label = stringResource(R.string.builds_field_previews), checked = previews, onChange = { previews = it })
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.builds_cancel)) }
                Button(enabled = !saving, onClick = ::submit) { Text(stringResource(R.string.builds_action_save)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Intent that opens this app's Live Update settings. Used by the detail screen when promotion is off. */
fun liveUpdateSettingsIntent(context: Context): Intent =
    Intent(LIVE_UPDATE_SETTINGS_ACTION).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS, written out so the build does not depend on the constant. */
private const val LIVE_UPDATE_SETTINGS_ACTION = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

@Composable
internal fun rememberLiveUpdateSettingsOpener(): () -> Unit {
    val context = LocalContext.current
    return { context.startActivity(liveUpdateSettingsIntent(context)) }
}
