package com.tomasps.virga.feature.pages.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesBuildConfig
import com.tomasps.virga.feature.pages.data.PagesDeploymentConfigValues
import com.tomasps.virga.feature.pages.data.PagesProject
import com.tomasps.virga.feature.pages.logic.PagesRules
import com.tomasps.virga.feature.pages.ui.PAGES_DASHBOARD_URL
import com.tomasps.virga.feature.pages.ui.PagesNote

private val FIELD_GAP = 8.dp

/** Settings: production branch, build config, env vars and secrets, read-only bindings, and the destructive actions. */
@Composable
internal fun ProjectSettingsTab(state: ProjectDetailUiState, viewModel: ProjectDetailViewModel) {
    val project = (state.project as? UiState.Content<PagesProject>)?.data
    if (project == null) {
        val failure = state.project as? UiState.Failure
        if (failure != null) {
            ErrorState(error = failure.error, onRetry = viewModel::refresh)
        } else {
            LoadingState()
        }
        return
    }
    val navigator = LocalNavigator.current
    var environment by rememberSaveable { mutableStateOf(PagesRules.ENVIRONMENT_PRODUCTION) }
    var confirmPurge by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { SectionHeader(stringResource(R.string.pages_section_branch)) }
        item {
            BranchEditor(
                current = project.productionBranch.orEmpty(),
                busy = state.busy,
                onSave = viewModel::saveProductionBranch,
            )
        }
        item { SectionHeader(stringResource(R.string.pages_section_build)) }
        item {
            BuildConfigEditor(
                config = project.buildConfig,
                busy = state.busy,
                onSave = viewModel::saveBuildConfig,
            )
        }
        envVarItems(
            configs = project.deploymentConfigs,
            environment = environment,
            onEnvironment = { environment = it },
            busy = state.busy,
            onSave = { env, name, type, value, existing -> viewModel.saveEnvVar(env, name, type, value, existing) },
            onDelete = viewModel::deleteEnvVar,
        )
        compatibilityItems(project.deploymentConfigs, environment)
        item { SectionHeader(stringResource(R.string.pages_section_git)) }
        item {
            KeyValueRow(
                key = stringResource(R.string.pages_git_repo),
                value = project.source?.config?.let { "${it.owner.orEmpty()}/${it.repoName.orEmpty()}" }
                    ?.takeIf { project.source?.config != null }
                    ?: stringResource(R.string.pages_value_direct_upload),
                monospaceValue = true,
            )
        }
        item {
            OpenInDashboardRow(
                label = stringResource(R.string.pages_git_in_dashboard),
                description = stringResource(R.string.pages_git_in_dashboard_description),
                onOpen = { navigator.openInBrowser(PAGES_DASHBOARD_URL) },
            )
        }
        item { SectionHeader(stringResource(R.string.pages_section_maintenance)) }
        item {
            ListRow(
                headline = stringResource(R.string.pages_action_purge_cache),
                supporting = stringResource(R.string.pages_action_purge_cache_description),
                onClick = { confirmPurge = true },
            )
        }
        item { SectionHeader(stringResource(R.string.pages_section_danger)) }
        item {
            ListRow(
                headline = stringResource(R.string.pages_action_delete_project),
                supporting = stringResource(R.string.pages_action_delete_project_description),
                onClick = { confirmDelete = true },
            )
        }
    }
    if (confirmPurge) {
        ConfirmDialog(
            title = stringResource(R.string.pages_purge_title),
            message = stringResource(R.string.pages_purge_message),
            confirmLabel = stringResource(R.string.pages_purge_confirm),
            onConfirm = {
                confirmPurge = false
                viewModel.purgeBuildCache()
            },
            onDismiss = { confirmPurge = false },
            destructive = true,
        )
    }
    if (confirmDelete) {
        TypedNameConfirmDialog(
            title = stringResource(R.string.pages_delete_title),
            message = stringResource(R.string.pages_delete_message, project.name),
            expectedName = project.name,
            confirmLabel = stringResource(R.string.pages_delete_confirm),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteProject()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun BranchEditor(current: String, busy: Boolean, onSave: (String) -> Unit) {
    var branch by rememberSaveable(current) { mutableStateOf(current) }
    val focus = LocalFocusManager.current
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = branch,
            onValueChange = { branch = it },
            label = { Text(stringResource(R.string.pages_field_production_branch)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                if (!busy) onSave(branch)
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FIELD_GAP))
        Button(onClick = { onSave(branch) }, enabled = !busy) {
            Text(stringResource(R.string.pages_action_save))
        }
    }
}

@Composable
private fun BuildConfigEditor(config: PagesBuildConfig?, busy: Boolean, onSave: (String, String, String, Boolean) -> Unit) {
    var buildCommand by rememberSaveable(config) { mutableStateOf(config?.buildCommand.orEmpty()) }
    var destinationDir by rememberSaveable(config) { mutableStateOf(config?.destinationDir.orEmpty()) }
    var rootDir by rememberSaveable(config) { mutableStateOf(config?.rootDir.orEmpty()) }
    var caching by rememberSaveable(config) { mutableStateOf(config?.buildCaching == true) }
    val focus = LocalFocusManager.current
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = buildCommand,
            onValueChange = { buildCommand = it },
            label = { Text(stringResource(R.string.pages_field_build_command)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FIELD_GAP))
        OutlinedTextField(
            value = destinationDir,
            onValueChange = { destinationDir = it },
            label = { Text(stringResource(R.string.pages_field_destination_dir)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FIELD_GAP))
        OutlinedTextField(
            value = rootDir,
            onValueChange = { rootDir = it },
            label = { Text(stringResource(R.string.pages_field_root_dir)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                if (!busy) onSave(buildCommand, destinationDir, rootDir, caching)
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FIELD_GAP))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pages_field_build_caching), modifier = Modifier.padding(end = 12.dp))
            Switch(
                checked = caching,
                onCheckedChange = { caching = it },
                modifier = Modifier,
            )
        }
        Spacer(Modifier.height(FIELD_GAP))
        Button(onClick = { onSave(buildCommand, destinationDir, rootDir, caching) }, enabled = !busy) {
            Text(stringResource(R.string.pages_action_save))
        }
    }
}
