package com.tomasps.virga.feature.d1.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.detail.ReadReplicationMode
import com.tomasps.virga.feature.d1.list.RecentlyDeletedDatabase
import com.tomasps.virga.feature.d1.list.RecentlyDeletedLog
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DatabaseSettingsUiState(
    val database: UiState<D1Database> = UiState.Loading,
    val isSaving: Boolean = false,
    val isDeleteRequested: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val actionError: ErrorUi? = null,
) {
    /** The mode the server reports, or disabled when it reports nothing. */
    val replicationMode: ReadReplicationMode
        get() = ReadReplicationMode.fromWire((database as? UiState.Content)?.data?.readReplication?.mode)
}

/** Read replication and delete. The name cannot be changed through the API, so the screen links to the dashboard. */
class DatabaseSettingsViewModel(
    session: SessionRepository,
    private val repository: DatabasesRepository,
    private val recentlyDeleted: RecentlyDeletedLog,
    private val databaseId: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DatabaseSettingsUiState())
    val state: StateFlow<DatabaseSettingsUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                load(id)
            }
        }
    }

    fun retry() {
        accountId?.let { id -> viewModelScope.launch { load(id) } }
    }

    fun setReadReplication(mode: ReadReplicationMode) {
        val id = accountId ?: return
        if (mutableState.value.isSaving) return
        mutableState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.setReadReplication(id, databaseId, mode.wire)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(isSaving = false, database = UiState.Content(result.value))
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(isSaving = false, actionError = D1ErrorMapper.toErrorUi(result.error))
                }
            }
        }
    }

    fun requestDelete() = mutableState.update { it.copy(isDeleteRequested = true) }

    fun cancelDelete() = mutableState.update { it.copy(isDeleteRequested = false) }

    /** Called after the typed database name matched. */
    fun confirmDelete() {
        val id = accountId ?: return
        mutableState.update { it.copy(isDeleteRequested = false, isDeleting = true) }
        val name = (mutableState.value.database as? UiState.Content)?.data?.name ?: databaseId
        viewModelScope.launch {
            when (val result = repository.delete(id, databaseId)) {
                is ApiResult.Success -> {
                    val now = nowMillis()
                    recentlyDeleted.remember(id, RecentlyDeletedDatabase(databaseId, name, now), now)
                    mutableState.update { it.copy(isDeleting = false, isDeleted = true) }
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(isDeleting = false, actionError = D1ErrorMapper.toErrorUi(result.error))
                }
            }
        }
    }

    fun dismissActionError() = mutableState.update { it.copy(actionError = null) }

    private suspend fun load(id: String) {
        mutableState.update { it.copy(database = UiState.Loading) }
        val outcome = when (val result = repository.get(id, databaseId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(database = outcome) }
    }
}
