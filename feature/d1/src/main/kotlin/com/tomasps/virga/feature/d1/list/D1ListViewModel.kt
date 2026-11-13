package com.tomasps.virga.feature.d1.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.CreateDatabaseBody
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Databases of the selected account: paged list, create, delete, and restore of a database deleted on this device
 * within the API's undelete window. Reloads when the account changes.
 */
class D1ListViewModel(
    session: SessionRepository,
    private val repository: DatabasesRepository,
    private val recentlyDeleted: RecentlyDeletedLog,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val mutableState = MutableStateFlow(D1ListUiState())
    val state: StateFlow<D1ListUiState> = mutableState.asStateFlow()

    private var accountId: String? = null
    private var hasSeenFirstResume = false

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                loadFirstPage(id)
            }
        }
        viewModelScope.launch {
            session.selectedAccountIds()
                .flatMapLatest { id -> recentlyDeleted.observe(id) }
                .collect { entries ->
                    val live = RecentlyDeletedRules.restorable(entries, nowMillis())
                    mutableState.update { it.copy(recentlyDeleted = live) }
                }
        }
    }

    /**
     * Called each time the list comes back on top. The first resume after creation is skipped, because init has just
     * loaded the list. Every later resume reloads, so a database deleted or restored on its own screen is current.
     */
    fun onResumed() {
        if (!hasSeenFirstResume) {
            hasSeenFirstResume = true
            return
        }
        refresh()
    }

    fun refresh() {
        val id = accountId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            loadFirstPage(id)
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMore() {
        val id = accountId ?: return
        val content = (mutableState.value.databases as? UiState.Content)?.data ?: return
        val page = content.nextPage ?: return
        if (!content.hasMore || content.isLoadingMore) return
        setContent { it.copy(isLoadingMore = true, moreFailed = false) }
        viewModelScope.launch {
            when (val result = repository.list(id, page)) {
                is ApiResult.Success -> setContent { current ->
                    current.copy(
                        items = current.items + result.value,
                        nextPage = page + 1,
                        hasMore = hasMoreAfter(result.pageInfo, page),
                        isLoadingMore = false,
                    )
                }
                is ApiResult.Failure -> setContent { it.copy(isLoadingMore = false, moreFailed = true) }
            }
        }
    }

    fun openCreate() = mutableState.update { it.copy(create = CreateDatabaseState(), actionError = null) }

    fun dismissCreate() = mutableState.update { it.copy(create = null) }

    fun onNameChanged(name: String) = updateCreate { it.copy(name = name, nameError = false, error = null) }

    fun onJurisdictionChanged(jurisdiction: String?) = updateCreate { it.copy(jurisdiction = jurisdiction) }

    fun onLocationChanged(location: String?) = updateCreate { it.copy(locationHint = location) }

    fun submitCreate() {
        val id = accountId ?: return
        val draft = mutableState.value.create ?: return
        if (draft.isSubmitting) return
        val name = draft.name.trim()
        if (name.isEmpty()) {
            updateCreate { it.copy(nameError = true) }
            return
        }
        updateCreate { it.copy(isSubmitting = true, error = null) }
        val body = CreateDatabaseBody(
            name = name,
            jurisdiction = draft.jurisdiction,
            primaryLocationHint = draft.locationHint,
        )
        viewModelScope.launch {
            when (val result = repository.create(id, body)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(create = null) }
                    loadFirstPage(id)
                }
                is ApiResult.Failure -> updateCreate {
                    it.copy(isSubmitting = false, error = D1ErrorMapper.toErrorUi(result.error))
                }
            }
        }
    }

    fun requestDelete(database: D1Database) = mutableState.update { it.copy(deleteTarget = database, actionError = null) }

    fun cancelDelete() = mutableState.update { it.copy(deleteTarget = null) }

    fun confirmDelete() {
        val id = accountId ?: return
        val target = mutableState.value.deleteTarget ?: return
        val databaseId = target.uuid ?: return
        mutableState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.delete(id, databaseId)) {
                is ApiResult.Success -> {
                    val now = nowMillis()
                    recentlyDeleted.remember(id, RecentlyDeletedDatabase(databaseId, target.name ?: databaseId, now), now)
                    mutableState.update { it.copy(deleteTarget = null, isDeleting = false) }
                    loadFirstPage(id)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(
                        deleteTarget = null,
                        isDeleting = false,
                        actionError = D1ErrorMapper.toErrorUi(result.error),
                    )
                }
            }
        }
    }

    fun dismissActionError() = mutableState.update { it.copy(actionError = null) }

    /** Restores a database this device deleted. The API refuses it after the undelete window, which is shown as an error. */
    fun restore(entry: RecentlyDeletedDatabase) {
        val id = accountId ?: return
        if (mutableState.value.restoringUuid != null) return
        mutableState.update { it.copy(restoringUuid = entry.uuid, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.undelete(id, entry.uuid)) {
                is ApiResult.Success -> {
                    recentlyDeleted.forget(id, entry.uuid)
                    mutableState.update { it.copy(restoringUuid = null) }
                    loadFirstPage(id)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(restoringUuid = null, actionError = D1ErrorMapper.toErrorUi(result.error))
                }
            }
        }
    }

    private suspend fun loadFirstPage(id: String?) {
        if (id == null) {
            mutableState.update { it.copy(databases = UiState.Content(DatabaseListContent())) }
            return
        }
        mutableState.update { it.copy(databases = UiState.Loading) }
        val outcome = when (val result = repository.list(id, FIRST_PAGE)) {
            is ApiResult.Success -> UiState.Content(
                DatabaseListContent(
                    items = result.value,
                    nextPage = FIRST_PAGE + 1,
                    hasMore = hasMoreAfter(result.pageInfo, FIRST_PAGE),
                ),
            )
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { if (accountId == id) it.copy(databases = outcome) else it }
    }

    private fun setContent(transform: (DatabaseListContent) -> DatabaseListContent) {
        mutableState.update { state ->
            val content = (state.databases as? UiState.Content)?.data ?: return@update state
            state.copy(databases = UiState.Content(transform(content)))
        }
    }

    private fun updateCreate(transform: (CreateDatabaseState) -> CreateDatabaseState) {
        mutableState.update { state -> state.create?.let { state.copy(create = transform(it)) } ?: state }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}

/** True when the server reports more pages after [page]. Without result_info, a full page is assumed to have more. */
internal fun hasMoreAfter(pageInfo: PageInfo?, page: Int): Boolean {
    val info = pageInfo as? PageInfo.Page ?: return false
    val total = info.totalCount ?: return false
    val perPage = info.perPage ?: return false
    return page * perPage < total
}
