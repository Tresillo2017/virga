package com.tomasps.virga.feature.pages.domains

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesDomain
import com.tomasps.virga.feature.pages.data.PagesDomainsRepository
import com.tomasps.virga.feature.pages.logic.DomainRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Custom domains of one project. [busyDomain] names the row with a write in flight, [adding] covers the add form,
 * and [addError] is shown under the form (a hostname that fails the local check never reaches the API).
 */
data class DomainsUiState(
    val accountId: String? = null,
    val domains: UiState<List<PagesDomain>> = UiState.Loading,
    val adding: Boolean = false,
    val addError: UiText? = null,
    /** Counts successful adds. The form watches it to clear the hostname only after the add went through. */
    val addedCount: Int = 0,
    val busyDomain: String? = null,
    val isRefreshing: Boolean = false,
    val message: UiText? = null,
)

class DomainsViewModel(
    private val projectName: String,
    session: SessionRepository,
    private val repository: PagesDomainsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DomainsUiState())
    val state: StateFlow<DomainsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId ->
                mutableState.update { it.copy(accountId = accountId, domains = UiState.Loading) }
                load(accountId)
            }
        }
    }

    fun refresh() {
        val accountId = mutableState.value.accountId ?: return
        mutableState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { load(accountId) }
    }

    fun add(rawHostname: String) {
        val accountId = mutableState.value.accountId ?: return
        val hostname = DomainRules.normalize(rawHostname)
        if (!DomainRules.isValidHostname(hostname)) {
            mutableState.update { it.copy(addError = UiText.Resource(R.string.pages_domains_error_invalid)) }
            return
        }
        mutableState.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            when (val result = repository.add(accountId, projectName, hostname)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(adding = false, addedCount = it.addedCount + 1, message = UiText.Resource(R.string.pages_domains_added))
                    }
                    load(accountId)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(adding = false, addError = ApiErrorUiMapper.toErrorUi(result.error).message)
                }
            }
        }
    }

    fun retryValidation(hostname: String) {
        runRowWrite(hostname) { accountId -> repository.retryValidation(accountId, projectName, hostname) }
    }

    fun remove(hostname: String) {
        val accountId = mutableState.value.accountId ?: return
        if (mutableState.value.busyDomain != null) return
        mutableState.update { it.copy(busyDomain = hostname) }
        viewModelScope.launch {
            when (val result = repository.remove(accountId, projectName, hostname)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(busyDomain = null, message = UiText.Resource(R.string.pages_domains_removed)) }
                    load(accountId)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(busyDomain = null, message = ApiErrorUiMapper.toErrorUi(result.error).message)
                }
            }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null, addError = null) }
    }

    private fun runRowWrite(hostname: String, call: suspend (String) -> ApiResult<PagesDomain>) {
        val accountId = mutableState.value.accountId ?: return
        if (mutableState.value.busyDomain != null) return
        mutableState.update { it.copy(busyDomain = hostname) }
        viewModelScope.launch {
            when (val result = call(accountId)) {
                is ApiResult.Success -> mutableState.update { current ->
                    current.copy(
                        busyDomain = null,
                        domains = current.domains.replacing(hostname, result.value),
                    )
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(busyDomain = null, message = ApiErrorUiMapper.toErrorUi(result.error).message)
                }
            }
        }
    }

    private suspend fun load(accountId: String) {
        val outcome = when (val result = repository.domains(accountId, projectName)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { current ->
            if (current.accountId == accountId) current.copy(domains = outcome, isRefreshing = false) else current
        }
    }

    private fun UiState<List<PagesDomain>>.replacing(hostname: String, updated: PagesDomain): UiState<List<PagesDomain>> =
        if (this is UiState.Content) {
            UiState.Content(data.map { if (it.name == hostname) updated else it })
        } else {
            this
        }
}
