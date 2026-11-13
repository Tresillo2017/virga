package com.tomasps.virga.feature.pages.deployments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.CodeBlock
import com.tomasps.virga.core.ui.components.DestructiveMenuItem
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.logic.PagesRules
import com.tomasps.virga.feature.pages.logic.canDelete
import com.tomasps.virga.feature.pages.logic.canRetry
import com.tomasps.virga.feature.pages.logic.canRollbackTo
import com.tomasps.virga.feature.pages.logic.stageViews
import com.tomasps.virga.feature.pages.ui.PAGES_DASHBOARD_URL
import com.tomasps.virga.feature.pages.ui.stageLabelRes
import com.tomasps.virga.feature.pages.ui.statusLabelRes
import com.tomasps.virga.feature.pages.ui.statusTone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val SECTION_SPACING = 8.dp

/** One deployment: stage progress (polled while it runs), commit facts, build log, and the retry, rollback and delete actions. */
@Composable
fun DeploymentDetailRoute(projectName: String, deploymentId: String) {
    val viewModel: DeploymentDetailViewModel = koinViewModel(
        parameters = { parametersOf(projectName, deploymentId) },
    )
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.asString()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmRollback by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) navigator.back()
    }

    ScreenScaffold(
        title = stringResource(R.string.pages_deployment_title),
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
        actions = {
            val deployment = (state.deployment as? UiState.Content)?.data
            if (deployment != null) {
                TextButton(onClick = { menuOpen = true }, enabled = !state.busy) {
                    Text(stringResource(R.string.pages_action_more))
                }
                DeploymentMenu(
                    expanded = menuOpen,
                    deployment = deployment,
                    currentProductionId = state.currentProductionId,
                    productionKnown = state.productionKnown,
                    onDismiss = { menuOpen = false },
                    onRetry = { menuOpen = false; viewModel.retry() },
                    onRollback = { menuOpen = false; confirmRollback = true },
                    onDelete = { menuOpen = false; confirmDelete = true },
                )
            }
        },
        onRefresh = viewModel::refresh,
        isRefreshing = state.isRefreshing,
    ) { padding ->
        UiStateContent(state = state.deployment, onRetry = viewModel::refresh) { deployment ->
            DeploymentBody(
                deployment = deployment,
                isPolling = state.isPolling,
                logs = state.logs,
                padding = padding,
                onLoadLogs = viewModel::loadLogs,
                onOpenDashboard = { navigator.openInBrowser(PAGES_DASHBOARD_URL) },
            )
        }
    }

    if (confirmRollback) {
        ConfirmDialog(
            title = stringResource(R.string.pages_rollback_title),
            message = stringResource(R.string.pages_rollback_message),
            confirmLabel = stringResource(R.string.pages_action_rollback),
            onConfirm = {
                confirmRollback = false
                viewModel.rollback()
            },
            onDismiss = { confirmRollback = false },
            destructive = true,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.pages_deployment_delete_title),
            message = stringResource(R.string.pages_deployment_delete_message),
            confirmLabel = stringResource(R.string.pages_action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun DeploymentMenu(
    expanded: Boolean,
    deployment: PagesDeployment,
    currentProductionId: String?,
    productionKnown: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRollback: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (deployment.canRetry()) {
            DropdownMenuItem(text = { Text(stringResource(R.string.pages_action_retry)) }, onClick = onRetry)
        }
        // Until the production deployment is known, rollback and delete stay hidden rather than guessed.
        if (productionKnown && deployment.canRollbackTo(currentProductionId)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.pages_action_rollback)) }, onClick = onRollback)
        }
        if (productionKnown && deployment.canDelete(currentProductionId)) {
            DestructiveMenuItem(text = stringResource(R.string.pages_action_delete), onClick = onDelete)
        }
    }
}

@Composable
private fun DeploymentBody(
    deployment: PagesDeployment,
    isPolling: Boolean,
    logs: UiState<List<String>>?,
    padding: PaddingValues,
    onLoadLogs: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val progressDescription = stringResource(R.string.pages_deployment_in_progress_description)
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
        if (isPolling) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = progressDescription
                    },
                )
            }
        }
        item { SectionHeader(stringResource(R.string.pages_section_stages)) }
        item(key = "stages") {
            SectionCard(
                rows = deployment.stageViews().map { stage ->
                    @Composable { StageRow(name = stage.name, status = stage.status) }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item { SectionHeader(stringResource(R.string.pages_section_commit)) }
        item {
            SectionCard(
                rows = commitRows(deployment),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (deployment.environment == PagesRules.ENVIRONMENT_PREVIEW) {
            item {
                SectionCard(
                    rows = listOf {
                        OpenInDashboardRow(
                            label = stringResource(R.string.pages_promote_in_dashboard),
                            description = stringResource(R.string.pages_promote_in_dashboard_description),
                            onOpen = onOpenDashboard,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item { SectionHeader(stringResource(R.string.pages_section_log)) }
        item {
            TextButton(
                onClick = onLoadLogs,
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(R.string.pages_action_show_log))
            }
        }
        item { LogBlock(logs) }
    }
}

@Composable
private fun LogBlock(logs: UiState<List<String>>?) {
    when (logs) {
        null -> Unit
        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        is UiState.Failure -> Text(
            text = stringResource(R.string.pages_log_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        is UiState.Content -> CodeBlock(
            code = logs.data.joinToString("\n").ifBlank { stringResource(R.string.pages_log_empty) },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun StageRow(name: String, status: String) {
    val statusText = stringResource(statusLabelRes(status))
    ListRow(
        headline = stringResource(stageLabelRes(name)),
        trailing = {
            Text(text = statusText, style = MaterialTheme.typography.labelMedium)
            StatusDot(tone = statusTone(status), contentDescription = statusText)
        },
    )
}

private const val COMMIT_SHORT_LENGTH = 8

@Composable
private fun commitRows(deployment: PagesDeployment): List<@Composable () -> Unit> {
    val metadata = deployment.trigger?.metadata
    val none = stringResource(R.string.pages_value_none)
    return listOf(
        {
            KeyValueRow(
                key = stringResource(R.string.pages_deployment_branch),
                value = metadata?.branch.orEmpty().ifBlank { none },
                monospaceValue = true,
            )
        },
        {
            KeyValueRow(
                key = stringResource(R.string.pages_deployment_commit),
                value = metadata?.commitHash.orEmpty().take(COMMIT_SHORT_LENGTH).ifBlank { none },
                monospaceValue = true,
            )
        },
        {
            KeyValueRow(
                key = stringResource(R.string.pages_deployment_message),
                value = metadata?.commitMessage.orEmpty().ifBlank { none },
            )
        },
        {
            KeyValueRow(
                key = stringResource(R.string.pages_deployment_url),
                value = deployment.url.orEmpty().ifBlank { none },
                monospaceValue = true,
            )
        },
    )
}
