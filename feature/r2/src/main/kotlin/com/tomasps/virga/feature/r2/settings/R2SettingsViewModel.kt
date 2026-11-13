package com.tomasps.virga.feature.r2.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2CustomDomain
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2LockRule
import com.tomasps.virga.feature.r2.api.R2ManagedDomain
import com.tomasps.virga.feature.r2.api.R2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add-a-custom-domain form. [minTls] is null for the API default. */
data class R2DomainForm(
    val domain: String = "",
    val zoneId: String = "",
    val minTls: String? = null,
    val error: UiText? = null,
    val isSaving: Boolean = false,
    val submitError: ErrorUi? = null,
)

/** Lock rule form. Retention is either indefinite or a number of days. */
data class R2LockForm(
    val id: String = "",
    val prefix: String = "",
    val indefinite: Boolean = true,
    val days: String = "",
    val error: UiText? = null,
    val isSaving: Boolean = false,
    val submitError: ErrorUi? = null,
)

/** Public access, custom domains and bucket lock for one bucket. Each section loads on its own. */
data class R2SettingsUiState(
    val publicAccess: UiState<R2ManagedDomain>? = null,
    val pendingPublicAccess: Boolean? = null,
    val domains: UiState<List<R2CustomDomain>>? = null,
    val domainForm: R2DomainForm? = null,
    val domainToRemove: String? = null,
    val lock: UiState<List<R2LockRule>>? = null,
    val lockForm: R2LockForm? = null,
    val lockRuleToRemove: String? = null,
    val isWorking: Boolean = false,
    val message: UiText? = null,
)

/** Loads and edits the public access, custom domain and bucket lock settings of one bucket. */
class R2SettingsViewModel(
    session: SessionRepository,
    private val repository: R2Repository,
    private val bucketName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2SettingsUiState())
    val state: StateFlow<R2SettingsUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                loadAll(id)
            }
        }
    }

    fun retryAll() {
        accountId?.let { id -> viewModelScope.launch { loadAll(id) } }
    }

    // ---- Public access (r2.dev) --------------------------------------------------------------------------------

    fun requestPublicAccess(enable: Boolean) = mutableState.update { it.copy(pendingPublicAccess = enable) }

    fun dismissPublicAccess() = mutableState.update { it.copy(pendingPublicAccess = null) }

    fun confirmPublicAccess() {
        val id = accountId ?: return
        val target = state.value.pendingPublicAccess ?: return
        if (state.value.isWorking) return
        mutableState.update { it.copy(pendingPublicAccess = null, isWorking = true) }
        viewModelScope.launch {
            val result = repository.setManagedDomainEnabled(id, bucketName, target)
            mutableState.update { it.copy(isWorking = false, message = writeMessage(result, R.string.r2_public_access_saved)) }
            loadPublicAccess(id)
        }
    }

    // ---- Custom domains ----------------------------------------------------------------------------------------

    fun openDomainForm() = mutableState.update { it.copy(domainForm = R2DomainForm()) }

    fun dismissDomainForm() = mutableState.update { it.copy(domainForm = null) }

    fun onDomainChanged(domain: String) = updateDomainForm { it.copy(domain = domain, error = null) }

    fun onDomainZoneChanged(zoneId: String) = updateDomainForm { it.copy(zoneId = zoneId, error = null) }

    fun onDomainMinTlsChanged(minTls: String?) = updateDomainForm { it.copy(minTls = minTls) }

    fun submitDomain() {
        val form = state.value.domainForm ?: return
        val id = accountId ?: return
        val error = domainError(form.domain, form.zoneId)
        if (error != null) return updateDomainForm { it.copy(error = error) }
        updateDomainForm { it.copy(isSaving = true, submitError = null) }
        viewModelScope.launch {
            val result = repository.addCustomDomain(id, bucketName, form.domain.trim().lowercase(), form.zoneId.trim(), form.minTls)
            when (result) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(domainForm = null, message = UiText.Resource(R.string.r2_domain_added)) }
                    loadDomains(id)
                }
                is ApiResult.Failure -> updateDomainForm { it.copy(isSaving = false, submitError = R2ErrorUiMapper.toErrorUi(result.error)) }
            }
        }
    }

    fun requestRemoveDomain(domain: String) = mutableState.update { it.copy(domainToRemove = domain) }

    fun dismissRemoveDomain() = mutableState.update { it.copy(domainToRemove = null) }

    fun confirmRemoveDomain() {
        val id = accountId ?: return
        val domain = state.value.domainToRemove ?: return
        if (state.value.isWorking) return
        mutableState.update { it.copy(domainToRemove = null, isWorking = true) }
        viewModelScope.launch {
            val result = repository.removeCustomDomain(id, bucketName, domain)
            mutableState.update { it.copy(isWorking = false, message = writeMessage(result, R.string.r2_domain_removed)) }
            loadDomains(id)
        }
    }

    // ---- Bucket lock -------------------------------------------------------------------------------------------

    fun openLockForm() = mutableState.update { it.copy(lockForm = R2LockForm()) }

    fun dismissLockForm() = mutableState.update { it.copy(lockForm = null) }

    fun onLockIdChanged(id: String) = updateLockForm { it.copy(id = id, error = null) }

    fun onLockPrefixChanged(prefix: String) = updateLockForm { it.copy(prefix = prefix, error = null) }

    fun onLockIndefiniteChanged(indefinite: Boolean) = updateLockForm { it.copy(indefinite = indefinite, error = null) }

    fun onLockDaysChanged(days: String) = updateLockForm { it.copy(days = days.filter { c -> c.isDigit() }, error = null) }

    fun submitLockRule() {
        val form = state.value.lockForm ?: return
        val id = accountId ?: return
        val existing = loadedLockRules() ?: return reloadFirst()
        val error = lockIdError(form.id, existing) ?: lockDaysError(form.indefinite, form.days)
        if (error != null) return updateLockForm { it.copy(error = error) }
        val rule = lockRuleFrom(form.id, form.prefix, form.indefinite, form.days)
        updateLockForm { it.copy(isSaving = true, submitError = null) }
        viewModelScope.launch {
            when (val result = repository.replaceLockRules(id, bucketName, existing + rule)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(lockForm = null, message = UiText.Resource(R.string.r2_lock_saved)) }
                    loadLock(id)
                }
                is ApiResult.Failure -> updateLockForm { it.copy(isSaving = false, submitError = R2ErrorUiMapper.toErrorUi(result.error)) }
            }
        }
    }

    fun requestRemoveLockRule(ruleId: String) = mutableState.update { it.copy(lockRuleToRemove = ruleId) }

    fun dismissRemoveLockRule() = mutableState.update { it.copy(lockRuleToRemove = null) }

    fun confirmRemoveLockRule() {
        val id = accountId ?: return
        val ruleId = state.value.lockRuleToRemove ?: return
        if (state.value.isWorking) return
        val loaded = loadedLockRules()
        if (loaded == null) {
            mutableState.update { it.copy(lockRuleToRemove = null) }
            return reloadFirst()
        }
        val remaining = loaded.filterNot { it.id == ruleId }
        mutableState.update { it.copy(lockRuleToRemove = null, isWorking = true) }
        viewModelScope.launch {
            val result = repository.replaceLockRules(id, bucketName, remaining)
            mutableState.update { it.copy(isWorking = false, message = writeMessage(result, R.string.r2_lock_removed)) }
            loadLock(id)
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    // ---- Loading -----------------------------------------------------------------------------------------------

    private suspend fun loadAll(id: String?) {
        if (id == null) {
            mutableState.update { R2SettingsUiState(message = it.message) }
            return
        }
        mutableState.update { it.copy(publicAccess = UiState.Loading, domains = UiState.Loading, lock = UiState.Loading) }
        loadPublicAccess(id)
        loadDomains(id)
        loadLock(id)
    }

    // Each section is fetched first and only then written to state, so no request runs inside an update block.

    private suspend fun loadPublicAccess(id: String) {
        val section = repository.managedDomain(id, bucketName).toUiState { it }
        mutableState.update { it.copy(publicAccess = section) }
    }

    private suspend fun loadDomains(id: String) {
        val section = repository.customDomains(id, bucketName).toUiState { it }
        mutableState.update { it.copy(domains = section) }
    }

    private suspend fun loadLock(id: String) {
        val section = repository.lockRules(id, bucketName).toUiState { it }
        mutableState.update { it.copy(lock = section) }
    }

    /** The lock rules as last loaded, or null when they did not load. A full-list write must never use a guess. */
    private fun loadedLockRules(): List<R2LockRule>? = (state.value.lock as? UiState.Content)?.data

    /** Refuses a write because the current list is unknown. Nothing is sent, so nothing is wiped. */
    private fun reloadFirst() = mutableState.update { it.copy(message = UiText.Resource(R.string.r2_settings_reload_first)) }

    private fun updateDomainForm(transform: (R2DomainForm) -> R2DomainForm) = mutableState.update { current ->
        current.domainForm?.let { current.copy(domainForm = transform(it)) } ?: current
    }

    private fun updateLockForm(transform: (R2LockForm) -> R2LockForm) = mutableState.update { current ->
        current.lockForm?.let { current.copy(lockForm = transform(it)) } ?: current
    }

    private fun writeMessage(result: ApiResult<*>, successRes: Int): UiText =
        UiText.Resource(if (result is ApiResult.Success) successRes else R.string.r2_settings_write_failed)

    private fun <T, R> ApiResult<T>.toUiState(transform: (T) -> R): UiState<R> = when (this) {
        is ApiResult.Success -> UiState.Content(transform(value))
        is ApiResult.Failure -> UiState.Failure(R2ErrorUiMapper.toErrorUi(error))
    }
}
