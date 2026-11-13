package com.tomasps.virga.feature.pages.deployments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.data.PagesDeploymentsRepository
import com.tomasps.virga.feature.pages.logic.PagesRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val DEPLOYMENTS_PER_PAGE = 25

/**
 * Deployments of one project, newest first, loaded page by page. [currentProductionId] is the production deployment
 * the project serves, so the screen can mark it. [loadMoreFailed] keeps the footer offering a retry.
 */
data class DeploymentsUiState(
    val accountId: String? = null,
    val deployments: UiState<List<PagesDeployment>> = UiState.Loading,
    val currentProductionId: String? = null,
    val nextPage: Int = 1,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isRefreshing: Boolean = false,
)

class DeploymentsViewModel(
    private val projectName: String,
    session: SessionRepository,
    private val repository: PagesDeploymentsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DeploymentsUiState())
    val state: StateFlow<DeploymentsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId ->
                mutableState.update { it.copy(accountId = accountId, deployments = UiState.Loading) }
                reloadFirstPage(accountId)
            }
        }
    }

    fun refresh() {
        val accountId = mutableState.value.accountId ?: return
        mutableState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { reloadFirstPage(accountId) }
    }

    fun loadMore() {
        val current = mutableState.value
        val accountId = current.accountId ?: return
        if (!current.hasMore || current.isLoadingMore) return
        mutableState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            when (val result = repository.page(accountId, projectName, current.nextPage, DEPLOYMENTS_PER_PAGE)) {
                is ApiResult.Success -> appendPage(accountId, current.nextPage, result)
                is ApiResult.Failure -> mutableState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private suspend fun reloadFirstPage(accountId: String) {
        when (val result = repository.page(accountId, projectName, 1, DEPLOYMENTS_PER_PAGE)) {
            is ApiResult.Success -> mutableState.update { current ->
                if (current.accountId != accountId) return@update current
                val loaded = (current.deployments as? UiState.Content)?.data.orEmpty()
                // A refresh replaces page 1 but keeps the older pages the user already loaded, so paging state stays valid.
                val hasLoadedPages = loaded.isNotEmpty()
                val list = if (hasLoadedPages) PagesRules.mergeDeployments(result.value, loaded) else result.value
                current.copy(
                    deployments = UiState.Content(list),
                    currentProductionId = PagesRules.currentProductionId(list),
                    nextPage = if (hasLoadedPages) current.nextPage else 2,
                    hasMore = if (hasLoadedPages) current.hasMore else hasMore(result.pageInfo, 1, result.value.size),
                    isRefreshing = false,
                    loadMoreFailed = false,
                )
            }
            is ApiResult.Failure -> mutableState.update { current ->
                if (current.accountId != accountId) current else current.copy(
                    deployments = UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error)),
                    isRefreshing = false,
                )
            }
        }
    }

    private fun appendPage(accountId: String, page: Int, result: ApiResult.Success<List<PagesDeployment>>) {
        mutableState.update { current ->
            if (current.accountId != accountId) return@update current
            val existing = (current.deployments as? UiState.Content)?.data.orEmpty()
            val merged = existing + result.value.filterNot { incoming -> existing.any { it.id == incoming.id } }
            current.copy(
                deployments = UiState.Content(merged),
                currentProductionId = PagesRules.currentProductionId(merged),
                nextPage = page + 1,
                hasMore = hasMore(result.pageInfo, page, result.value.size),
                isLoadingMore = false,
            )
        }
    }

    private fun hasMore(info: PageInfo?, page: Int, received: Int): Boolean {
        val total = (info as? PageInfo.Page)?.totalCount
        return PagesRules.hasMorePages(page, DEPLOYMENTS_PER_PAGE, total, received)
    }
}
