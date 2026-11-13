package com.tomasps.virga.feature.kv.namespaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvJurisdiction
import com.tomasps.virga.feature.kv.data.KvNamespaceDto
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.kvActionMessage
import com.tomasps.virga.feature.kv.value.KV_MAX_TITLE_CHARS
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [namespaces] is null while no account is selected. */
data class KvNamespacesUiState(
    val namespaces: UiState<List<KvNamespaceDto>>? = null,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
)

/** Lists the namespaces of the selected account and creates new ones. */
class KvNamespacesViewModel(
    session: SessionRepository,
    private val repository: KvRepository,
    changes: KvNamespaceChanges,
) : ViewModel() {

    private val mutableState = MutableStateFlow(KvNamespacesUiState())
    val state: StateFlow<KvNamespacesUiState> = mutableState.asStateFlow()

    private val mutableMessages = MutableSharedFlow<UiText>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<UiText> = mutableMessages.asSharedFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                load(id)
            }
        }
        viewModelScope.launch {
            changes.version.drop(1).collectLatest { accountId?.let { load(it) } }
        }
    }

    fun refresh() {
        val id = accountId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            load(id)
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun create(title: String, jurisdiction: KvJurisdiction? = null) {
        val id = accountId ?: return
        val trimmed = title.trim()
        val problem = titleProblem(trimmed)
        if (problem != null) {
            mutableMessages.tryEmit(problem)
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isCreating = true) }
            when (val result = repository.createNamespace(id, trimmed, jurisdiction)) {
                is ApiResult.Success -> {
                    mutableMessages.emit(UiText.Resource(R.string.kv_message_namespace_created, listOf(trimmed)))
                    mutableState.update { it.copy(isCreating = false) }
                    load(id)
                }
                is ApiResult.Failure -> {
                    mutableState.update { it.copy(isCreating = false) }
                    mutableMessages.emit(kvActionMessage(result.error, isSingleKeyWrite = false))
                }
            }
        }
    }

    private suspend fun load(id: String) {
        val current = mutableState.value.namespaces
        if (current !is UiState.Content) mutableState.update { it.copy(namespaces = UiState.Loading) }
        val outcome = when (val result = repository.namespaces(id)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { if (id == accountId) it.copy(namespaces = outcome) else it }
    }

    private fun titleProblem(title: String): UiText? = when {
        title.isEmpty() -> UiText.Resource(R.string.kv_error_title_required)
        title.length > KV_MAX_TITLE_CHARS -> UiText.Resource(R.string.kv_error_title_too_long)
        else -> null
    }

    private companion object {
        const val MESSAGE_BUFFER = 4
    }
}
