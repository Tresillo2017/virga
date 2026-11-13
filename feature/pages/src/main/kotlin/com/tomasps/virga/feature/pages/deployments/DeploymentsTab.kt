package com.tomasps.virga.feature.pages.deployments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.tomasps.virga.core.common.formatIsoDate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.pages.PagesDeploymentDetail
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.list.RefreshOnReturn
import com.tomasps.virga.feature.pages.logic.PagesRules
import com.tomasps.virga.feature.pages.ui.statusLabelRes
import com.tomasps.virga.feature.pages.ui.statusTone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Deployments tab: production and preview deployments, newest first, with paging and pull to refresh. */
@Composable
fun DeploymentsTab(projectName: String) {
    val viewModel: DeploymentsViewModel = koinViewModel(parameters = { parametersOf(projectName) })
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    // A deployment screen can retry, roll back or delete; coming back must show that, not the list loaded before it.
    RefreshOnReturn(onReturn = viewModel::refresh)
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        UiStateContent(state = state.deployments, onRetry = viewModel::refresh) { list ->
            if (list.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.pages_deployments_empty_title),
                    message = stringResource(R.string.pages_deployments_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { deployment ->
                        DeploymentRow(
                            deployment = deployment,
                            isCurrent = deployment.id == state.currentProductionId,
                            onClick = { navigator.navigate(PagesDeploymentDetail(projectName, deployment.id)) },
                        )
                    }
                    item {
                        LoadMoreFooter(
                            state = loadMoreStateOf(
                                hasMore = state.hasMore,
                                isLoading = state.isLoadingMore,
                                hasFailed = state.loadMoreFailed,
                            ),
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeploymentRow(deployment: PagesDeployment, isCurrent: Boolean, onClick: () -> Unit) {
    val status = deployment.latestStage?.status
    val statusText = stringResource(statusLabelRes(status))
    val environment = stringResource(
        if (deployment.environment == PagesRules.ENVIRONMENT_PRODUCTION) R.string.pages_env_production else R.string.pages_env_preview,
    )
    val title = deployment.trigger?.metadata?.commitMessage?.takeIf { it.isNotBlank() }
        ?: deployment.shortId?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.pages_deployment_title)
    val branch = deployment.trigger?.metadata?.branch.orEmpty()
    val supporting = listOfNotNull(environment, branch.ifBlank { null }, formatIsoDate(deployment.createdOn))
        .joinToString(" · ")
    ListRow(
        headline = if (isCurrent) stringResource(R.string.pages_deployment_current, title) else title,
        supporting = supporting,
        trailing = {
            Text(text = statusText, style = MaterialTheme.typography.labelMedium)
            StatusDot(tone = statusTone(status), contentDescription = statusText)
        },
        onClick = onClick,
    )
}
