package com.tomasps.virga.feature.workers.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.WorkerScript
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val ROW_SPACING = 8.dp
private val SCREEN_GUTTER = 16.dp
private const val DASHBOARD_WORKERS_URL = "https://dash.cloudflare.com/?to=/:account/workers-and-pages"

/** The Workers list, reached from the Compute hub. */
@Composable
fun WorkersListRoute(viewModel: WorkersListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    // Runs each time the list comes back into view, so a Worker deleted on its detail screen is gone from this list.
    LaunchedEffect(Unit) { viewModel.refresh() }
    WorkersListScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun WorkersListScreen(
    state: WorkersListUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (WorkerSort) -> Unit,
    onRetry: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val loading = state.scripts is UiState.Loading
    ScreenScaffold(
        title = stringResource(R.string.workers_title_workersroot),
        onBack = { navigator.back() },
        isRefreshing = loading,
        onRefresh = onRetry,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = SCREEN_GUTTER)) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.workers_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            VirgaTabRow(
                tabs = listOf(
                    stringResource(R.string.workers_sort_name),
                    stringResource(R.string.workers_sort_modified),
                ),
                selectedIndex = state.sort.ordinal,
                onSelect = { index -> onSortChange(WorkerSort.entries[index]) },
            )
            UiStateContent(state = state.scripts ?: UiState.Loading, onRetry = onRetry) { _ ->
                WorkersRows(
                    rows = state.visibleScripts(),
                    buildingScripts = state.buildingScripts,
                    hasAnyWorkers = state.scripts is UiState.Content && (state.scripts as UiState.Content).data.isNotEmpty(),
                    onOpen = { script -> navigator.navigate(WorkerDetail(script.id)) },
                    onOpenDashboard = { navigator.openInBrowser(DASHBOARD_WORKERS_URL) },
                )
            }
        }
    }
}

@Composable
private fun WorkersRows(
    rows: List<WorkerScript>,
    buildingScripts: Set<String>,
    hasAnyWorkers: Boolean,
    onOpen: (WorkerScript) -> Unit,
    onOpenDashboard: () -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyState(
            title = stringResource(if (hasAnyWorkers) R.string.workers_no_matches_title else R.string.workers_empty_title),
            message = stringResource(if (hasAnyWorkers) R.string.workers_no_matches_message else R.string.workers_empty_message),
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
        item {
            SectionCard(rows = scriptRows(rows, buildingScripts, onOpen))
        }
        item {
            SectionCard(
                rows = listOf {
                    OpenInDashboardRow(
                        label = stringResource(R.string.workers_create_in_dashboard),
                        description = stringResource(R.string.workers_create_in_dashboard_description),
                        onOpen = onOpenDashboard,
                    )
                },
            )
        }
    }
}

private fun scriptRows(
    rows: List<WorkerScript>,
    buildingScripts: Set<String>,
    onOpen: (WorkerScript) -> Unit,
): List<@Composable () -> Unit> = rows.map { script ->
    {
        ListRow(
            headline = script.id,
            supporting = formatIsoDate(script.modifiedOn)?.let { stringResource(R.string.workers_modified_on, it) },
            leading = if (script.id in buildingScripts) BuildingDot else null,
            onClick = { onOpen(script) },
        )
    }
}

/** A Worker with a build running now shows the same pulsing dot as the overview. */
private val BuildingDot: @Composable () -> Unit = { StatusDot(tone = StatusTone.Pending) }
