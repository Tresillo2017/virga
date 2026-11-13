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
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** The lifecycle rule being edited. [original] is null for a new rule and keeps unedited fields on save. */
data class R2LifecycleForm(
    val original: JsonObject? = null,
    val id: String = "",
    val prefix: String = "",
    val enabled: Boolean = true,
    val expireDays: String = "",
    /** The day count as shown when the form opened. Unchanged means the original expiry is kept as it was. */
    val originalExpireDays: String = "",
    val error: UiText? = null,
    val isSaving: Boolean = false,
    val submitError: ErrorUi? = null,
)

data class R2LifecycleUiState(
    val rules: UiState<List<JsonObject>>? = null,
    val form: R2LifecycleForm? = null,
    val ruleToRemove: String? = null,
    val isWorking: Boolean = false,
    val message: UiText? = null,
)

/** Lifecycle rules for one bucket. The whole list is written back; rules are read and kept as raw JSON. */
class R2LifecycleViewModel(
    session: SessionRepository,
    private val repository: R2Repository,
    private val bucketName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2LifecycleUiState())
    val state: StateFlow<R2LifecycleUiState> = mutableState.asStateFlow()

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

    fun openNewRule() = mutableState.update { it.copy(form = R2LifecycleForm()) }

    fun openRule(id: String) {
        val rule = currentRules().firstOrNull { it.toLifecycleEntry().id == id } ?: return
        val entry = rule.toLifecycleEntry()
        val shownDays = entry.expireAfterDays?.toString().orEmpty()
        val form = R2LifecycleForm(
            original = rule,
            id = entry.id,
            prefix = entry.prefix,
            enabled = entry.enabled,
            expireDays = shownDays,
            originalExpireDays = shownDays,
        )
        mutableState.update { it.copy(form = form) }
    }

    fun dismissForm() = mutableState.update { it.copy(form = null) }

    fun onIdChanged(id: String) = updateForm { it.copy(id = id.trim(), error = null) }

    fun onPrefixChanged(prefix: String) = updateForm { it.copy(prefix = prefix, error = null) }

    fun onEnabledChanged(enabled: Boolean) = updateForm { it.copy(enabled = enabled) }

    fun onExpireDaysChanged(days: String) = updateForm { it.copy(expireDays = days.filter(Char::isDigit), error = null) }

    fun submit() {
        val form = state.value.form ?: return
        val id = accountId ?: return
        val existing = loadedRules() ?: return refuseWriteWithoutList()
        val error = formError(form, existing)
        if (error != null) return updateForm { it.copy(error = error) }
        val rule = lifecycleRuleFrom(
            id = form.id,
            enabled = form.enabled,
            prefix = form.prefix.trim(),
            expireAfterDays = form.expireDays.takeIf { it.isNotBlank() }?.toLong(),
            existing = form.original,
            expiryChanged = form.original == null || form.expireDays != form.originalExpireDays,
        )
        val updated = form.original?.let { original ->
            val originalId = original.toLifecycleEntry().id
            existing.map { if (it.toLifecycleEntry().id == originalId) rule else it }
        } ?: (existing + rule)
        updateForm { it.copy(isSaving = true, submitError = null) }
        viewModelScope.launch {
            when (val result = repository.replaceLifecycleRules(id, bucketName, updated)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(form = null, message = UiText.Resource(R.string.r2_lifecycle_saved)) }
                    load(id)
                }
                is ApiResult.Failure -> updateForm {
                    it.copy(isSaving = false, submitError = R2ErrorUiMapper.toErrorUi(result.error))
                }
            }
        }
    }

    fun requestRemove(ruleId: String) = mutableState.update { it.copy(ruleToRemove = ruleId) }

    fun dismissRemove() = mutableState.update { it.copy(ruleToRemove = null) }

    fun confirmRemove() {
        val id = accountId ?: return
        val ruleId = state.value.ruleToRemove ?: return
        if (state.value.isWorking) return
        val loaded = loadedRules()
        if (loaded == null) {
            mutableState.update { it.copy(ruleToRemove = null) }
            return refuseWriteWithoutList()
        }
        val remaining = loaded.filterNot { it.toLifecycleEntry().id == ruleId }
        mutableState.update { it.copy(ruleToRemove = null, isWorking = true) }
        viewModelScope.launch {
            val result = repository.replaceLifecycleRules(id, bucketName, remaining)
            val message = if (result is ApiResult.Success) R.string.r2_lifecycle_removed else R.string.r2_settings_write_failed
            mutableState.update { it.copy(isWorking = false, message = UiText.Resource(message)) }
            load(id)
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun currentRules(): List<JsonObject> = loadedRules().orEmpty()

    /** The rules as last loaded, or null when they did not load. A full-list write must never use a guess. */
    private fun loadedRules(): List<JsonObject>? = (state.value.rules as? UiState.Content)?.data

    private fun refuseWriteWithoutList() {
        mutableState.update { it.copy(message = UiText.Resource(R.string.r2_settings_reload_first)) }
    }

    private suspend fun load(id: String) {
        val section = when (val result = repository.lifecycleRules(id, bucketName)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(R2ErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(rules = section) }
    }

    private fun updateForm(transform: (R2LifecycleForm) -> R2LifecycleForm) = mutableState.update { current ->
        current.form?.let { current.copy(form = transform(it)) } ?: current
    }

    private fun formError(form: R2LifecycleForm, existing: List<JsonObject>): UiText? {
        val idTaken = form.original == null && existing.any { it.toLifecycleEntry().id == form.id }
        return when {
            form.id.isBlank() -> UiText.Resource(R.string.r2_lifecycle_id_required)
            idTaken -> UiText.Resource(R.string.r2_lock_id_taken)
            isInvalidExpiryDays(form.expireDays) -> UiText.Resource(R.string.r2_lifecycle_days_invalid)
            else -> null
        }
    }
}
