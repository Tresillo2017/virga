package com.tomasps.virga.feature.pages.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesProject as ProjectModel
import com.tomasps.virga.feature.pages.logic.PagesRules
import com.tomasps.virga.feature.pages.ui.PAGES_DASHBOARD_URL
import com.tomasps.virga.feature.pages.ui.PagesNote
import com.tomasps.virga.feature.pages.ui.statusTone
import org.koin.compose.viewmodel.koinViewModel

private val ROW_GAP = 4.dp

@Composable
fun PagesListRoute(viewModel: PagesListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    RefreshOnReturn(onReturn = viewModel::refresh)
    ScreenScaffold(
        title = stringResource(R.string.pages_title_pagesroot),
        onBack = { navigator.back() },
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp),
                placeholder = stringResource(R.string.pages_search_placeholder),
            )
            PagesNote(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val projects = state.projects
            if (projects == null) {
                LoadingState()
            } else {
                UiStateContent(state = projects, onRetry = viewModel::refresh) { all ->
                    ProjectsContent(
                        all = all,
                        query = state.query,
                        onOpen = { navigator.navigate(PagesProject(it)) },
                        onCreateInDashboard = { navigator.openInBrowser(PAGES_DASHBOARD_URL) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectsContent(
    all: List<ProjectModel>,
    query: String,
    onOpen: (String) -> Unit,
    onCreateInDashboard: () -> Unit,
) {
    val visible = PagesRules.filterProjects(all, query)
    LazyColumn {
        if (all.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.pages_list_empty_title),
                    message = stringResource(R.string.pages_list_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (visible.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.pages_list_no_match_title),
                    message = stringResource(R.string.pages_list_no_match_message),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            item {
                SectionCard(
                    rows = visible.map { project -> @Composable { ProjectRow(project, onClick = { onOpen(project.name) }) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            Spacer(Modifier.height(ROW_GAP))
            OpenInDashboardRow(
                label = stringResource(R.string.pages_create_in_dashboard),
                description = stringResource(R.string.pages_create_in_dashboard_description),
                onOpen = onCreateInDashboard,
            )
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectModel, onClick: () -> Unit) {
    val latest = project.latestDeployment?.latestStage?.status
    val statusText = stringResource(com.tomasps.virga.feature.pages.ui.statusLabelRes(latest))
    val domain = project.domains.orEmpty().firstOrNull() ?: project.subdomain.orEmpty()
    val repo = project.source?.config?.repoName
    val supporting = listOfNotNull(
        project.productionBranch?.let { stringResource(R.string.pages_list_branch, it) },
        domain.takeIf { it.isNotBlank() },
        repo,
    ).joinToString(" · ")
    ListRow(
        headline = project.name,
        supporting = supporting.ifBlank { null },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = statusText, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(ROW_GAP))
                StatusDot(tone = statusTone(latest), contentDescription = statusText)
            }
        },
        onClick = onClick,
    )
}
