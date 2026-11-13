package com.tomasps.virga.feature.d1.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Header of a database screen: its details, loaded once and reloaded on retry or account change. */
data class D1DetailUiState(
    val info: UiState<D1Database> = UiState.Loading,
)

/** Loads the database header. The tabs load their own data through their own view models. */
class D1DetailViewModel(
    session: SessionRepository,
    private val repository: DatabasesRepository,
    private val databaseId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(D1DetailUiState())
    val state: StateFlow<D1DetailUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId -> load(accountId) }
        }
    }

    fun retry() {
        val id = accountId ?: return
        viewModelScope.launch { load(id) }
    }

    private suspend fun load(id: String) {
        accountId = id
        mutableState.update { it.copy(info = UiState.Loading) }
        val outcome = when (val result = repository.get(id, databaseId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(info = outcome) }
    }
}
