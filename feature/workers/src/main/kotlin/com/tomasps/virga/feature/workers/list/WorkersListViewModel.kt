package com.tomasps.virga.feature.workers.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.getOrNull
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.workers.data.WorkerScript
import com.tomasps.virga.feature.workers.data.WorkersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.tomasps.virga.feature.workers.detail.isBuildingNow

enum class WorkerSort { NAME, MODIFIED }

/**
 * [scripts] is null while signed out or when no account is selected. [buildingScripts] names the Workers with a build
 * running now. It is best effort: when the builds read fails, no Worker shows a dot, and the list still loads.
 */
data class WorkersListUiState(
    val query: String = "",
    val sort: WorkerSort = WorkerSort.NAME,
    val scripts: UiState<List<WorkerScript>>? = null,
    val buildingScripts: Set<String> = emptySet(),
) {
    /** The rows the screen shows: matched by name (case-insensitive), then sorted. */
    fun visibleScripts(): List<WorkerScript> {
        val loaded = (scripts as? UiState.Content)?.data.orEmpty()
        val needle = query.trim()
        val matched = if (needle.isEmpty()) loaded else loaded.filter { it.id.contains(needle, ignoreCase = true) }
        return when (sort) {
            WorkerSort.NAME -> matched.sortedBy { it.id.lowercase() }
            WorkerSort.MODIFIED -> matched.sortedByDescending { it.modifiedOn.orEmpty() }
        }
    }
}

/** Lists the Workers of the selected account and reloads when the account changes. */
class WorkersListViewModel(
    session: SessionRepository,
    private val repository: WorkersRepository,
    private val builds: BuildsGateway,
) : ViewModel() {

    private val mutableState = MutableStateFlow(WorkersListUiState())
    val state: StateFlow<WorkersListUiState> = mutableState.asStateFlow()

    private var loadedAccountId: String? = null

    init {
        viewModelScope.launch {
            session.state.collectLatest { sessionState -> onSessionChanged(sessionState) }
        }
    }

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun onSortChange(sort: WorkerSort) {
        mutableState.update { it.copy(sort = sort) }
    }

    fun retry() {
        loadedAccountId?.let { accountId -> viewModelScope.launch { load(accountId) } }
    }

    /** Reloads the list when it is shown again. Skipped while a load is already running, so a first visit loads once. */
    fun refresh() {
        val accountId = loadedAccountId ?: return
        if (mutableState.value.scripts is UiState.Loading) return
        viewModelScope.launch { load(accountId) }
    }

    private suspend fun onSessionChanged(sessionState: SessionState) {
        val accountId = (sessionState as? SessionState.SignedIn)?.selectedAccountId
        loadedAccountId = accountId
        mutableState.update { it.copy(scripts = if (accountId == null) null else UiState.Loading) }
        if (accountId != null) load(accountId)
    }

    private suspend fun load(accountId: String) {
        mutableState.update { it.copy(scripts = UiState.Loading) }
        val outcome = when (val result = repository.listScripts(accountId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        val building = if (outcome is UiState.Content) buildingScriptNames(accountId) else emptySet()
        mutableState.update { current ->
            if (loadedAccountId == accountId) current.copy(scripts = outcome, buildingScripts = building) else current
        }
    }

    private suspend fun buildingScriptNames(accountId: String): Set<String> =
        builds.activeBuilds(accountId).getOrNull().orEmpty()
            .filter { it.isBuildingNow() }
            .map { it.scriptName }
            .toSet()
}
