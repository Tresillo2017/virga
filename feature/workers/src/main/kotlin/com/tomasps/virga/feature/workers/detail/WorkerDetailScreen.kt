package com.tomasps.virga.feature.workers.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.Navigator
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.BindingTarget
import com.tomasps.virga.feature.workers.metrics.WorkerMetricsTab
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Detail for one Worker, keyed by its script name. */
@Composable
fun WorkerDetailRoute(key: WorkerDetail) {
    val viewModel: WorkerDetailViewModel = koinViewModel(parameters = { parametersOf(key.scriptName) })
    val state by viewModel.state.collectAsState()
    WorkerDetailScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onRetry = viewModel::retry,
        onMessageShown = viewModel::clearMessage,
        viewModel = viewModel,
    )
}

@Composable
internal fun WorkerDetailScreen(
    state: WorkerDetailUiState,
    onSelectTab: (DetailTab) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    viewModel: WorkerDetailViewModel,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.resolve(context)

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) navigator.back()
    }

    ScreenScaffold(
        title = state.scriptName,
        onBack = { navigator.back() },
        snackbarHostState = snackbarHostState,
        isRefreshing = state.snapshot is UiState.Loading,
        onRefresh = onRetry,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            VirgaTabRow(
                tabs = listOf(
                    stringResource(R.string.workers_tab_overview),
                    stringResource(R.string.workers_tab_metrics),
                    stringResource(R.string.workers_tab_deployments),
                    stringResource(R.string.workers_tab_domains),
                    stringResource(R.string.workers_tab_settings),
                ),
                selectedIndex = state.tab.ordinal,
                onSelect = { index -> onSelectTab(DetailTab.entries[index]) },
            )
            if (state.tab == DetailTab.METRICS) {
                WorkerMetricsTab(scriptName = state.scriptName)
            } else {
                UiStateContent(state = state.snapshot ?: UiState.Loading, onRetry = onRetry) { snapshot ->
                    DetailTabContent(
                        tab = state.tab,
                        state = state,
                        snapshot = snapshot,
                        viewModel = viewModel,
                        onOpenBinding = { target -> openBinding(navigator, target) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailTabContent(
    tab: DetailTab,
    state: WorkerDetailUiState,
    snapshot: WorkerSnapshot,
    viewModel: WorkerDetailViewModel,
    onOpenBinding: (BindingTarget) -> Unit,
) {
    when (tab) {
        DetailTab.METRICS -> Unit // Rendered by WorkerMetricsTab outside the snapshot load.
        DetailTab.OVERVIEW -> OverviewTab(
            scriptName = state.scriptName,
            snapshot = snapshot,
            latestBuild = state.latestBuild,
            accountId = state.accountId,
            watchedBuildIds = state.watchedBuildIds,
            onWatchBuild = viewModel::watchLatestBuild,
            onStopWatchingBuild = viewModel::stopWatchingLatestBuild,
            onOpenBinding = onOpenBinding,
            onOpenDeployments = { viewModel.selectTab(DetailTab.DEPLOYMENTS) },
        )
        DetailTab.DEPLOYMENTS -> DeploymentsTab(
            snapshot = snapshot,
            isBusy = state.isBusy,
            onDeploy = viewModel::deploy,
            onRollback = viewModel::rollback,
        )
        DetailTab.DOMAINS -> DomainsTab(
            snapshot = snapshot,
            isBusy = state.isBusy,
            onSetWorkersDevAndPreviews = viewModel::setWorkersDevAndPreviews,
            onDeleteDomain = viewModel::deleteDomain,
            onAddDomain = viewModel::addCustomDomain,
        )
        DetailTab.SETTINGS -> SettingsTab(
            snapshot = snapshot,
            scriptName = state.scriptName,
            isBusy = state.isBusy,
            onOpenBinding = onOpenBinding,
            onSaveSecret = viewModel::saveSecret,
            onDeleteSecret = viewModel::deleteSecret,
            onDeleteWorker = viewModel::deleteWorker,
        )
    }
}

/** Opens the R2, D1 or KV screen that owns a binding's resource. */
internal fun openBinding(navigator: Navigator, target: BindingTarget) {
    when (target) {
        is BindingTarget.R2 -> navigator.navigate(R2Bucket(target.bucketName))
        is BindingTarget.D1 -> navigator.navigate(D1Database(target.databaseId))
        is BindingTarget.Kv -> navigator.navigate(KvNamespace(target.namespaceId))
    }
}
