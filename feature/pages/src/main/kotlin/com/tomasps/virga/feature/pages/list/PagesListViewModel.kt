package com.tomasps.virga.feature.pages.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.pages.data.PagesProject
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [projects] is null while no account is selected. The search query filters on the screen, not on the server. */
data class PagesListUiState(
    val accountId: String? = null,
    val query: String = "",
    val projects: UiState<List<PagesProject>>? = null,
    val isRefreshing: Boolean = false,
)

class PagesListViewModel(
    session: SessionRepository,
    private val repository: PagesProjectsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(PagesListUiState())
    val state: StateFlow<PagesListUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId -> onAccountSelected(accountId) }
        }
    }

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun refresh() {
        val accountId = mutableState.value.accountId ?: return
        viewModelScope.launch { load(accountId, refreshing = true) }
    }

    private suspend fun onAccountSelected(accountId: String) {
        mutableState.update { it.copy(accountId = accountId, projects = UiState.Loading, isRefreshing = false) }
        load(accountId, refreshing = false)
    }

    private suspend fun load(accountId: String, refreshing: Boolean) {
        mutableState.update { it.copy(isRefreshing = refreshing) }
        val outcome = when (val result = repository.projects(accountId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { current ->
            if (current.accountId == accountId) current.copy(projects = outcome, isRefreshing = false) else current
        }
    }
}
