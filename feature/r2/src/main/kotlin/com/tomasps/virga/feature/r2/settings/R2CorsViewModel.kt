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
import com.tomasps.virga.feature.r2.api.R2CorsAllowed
import com.tomasps.virga.feature.r2.api.R2CorsRule
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.api.R2_CORS_METHODS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The rule being edited. [index] is null for a new rule. Text fields stay text until validated; lists are
 * comma or new-line separated.
 */
data class R2CorsEditor(
    val index: Int? = null,
    /** The rule as it was when the editor opened. Saving finds it again by value, so a reloaded list cannot shift it. */
    val original: R2CorsRule? = null,
    val origins: String = "",
    val methods: Set<String> = setOf("GET"),
    val headers: String = "",
    val exposeHeaders: String = "",
    val maxAge: String = "",
    val error: UiText? = null,
    val isSaving: Boolean = false,
    val submitError: ErrorUi? = null,
)

data class R2CorsUiState(
    val rules: UiState<List<R2CorsRule>>? = null,
    val editor: R2CorsEditor? = null,
    val ruleToRemove: R2CorsRule? = null,
    val deleteAllRequested: Boolean = false,
    val isWorking: Boolean = false,
    val message: UiText? = null,
) {
    val methodOptions: List<String> get() = R2_CORS_METHODS

    val hasRules: Boolean get() = (rules as? UiState.Content)?.data?.isNotEmpty() == true
}

/**
 * CORS rules for one bucket. Each rule is edited on its own and the whole list is written back, so the other
 * rules (and their headers, exposed headers and ids) are kept exactly as the API returned them.
 */
class R2CorsViewModel(
    session: SessionRepository,
    private val repository: R2Repository,
    private val bucketName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2CorsUiState())
    val state: StateFlow<R2CorsUiState> = mutableState.asStateFlow()

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

    fun openNewRule() = mutableState.update { it.copy(editor = R2CorsEditor()) }

    fun openRule(index: Int) {
        val rule = currentRules().getOrNull(index) ?: return
        val editor = R2CorsEditor(
            index = index,
            original = rule,
            origins = rule.allowed.origins.joinToString("\n"),
            methods = rule.allowed.methods.toSet(),
            headers = rule.allowed.headers.joinToString("\n"),
            exposeHeaders = rule.exposeHeaders.joinToString("\n"),
            maxAge = rule.maxAgeSeconds?.toString().orEmpty(),
        )
        mutableState.update { it.copy(editor = editor) }
    }

    fun dismissEditor() = mutableState.update { it.copy(editor = null) }

    fun onOriginsChanged(text: String) = updateEditor { it.copy(origins = text, error = null) }

    fun onMethodToggled(method: String) = updateEditor { editor ->
        val methods = if (method in editor.methods) editor.methods - method else editor.methods + method
        editor.copy(methods = methods, error = null)
    }

    fun onHeadersChanged(text: String) = updateEditor { it.copy(headers = text, error = null) }

    fun onExposeHeadersChanged(text: String) = updateEditor { it.copy(exposeHeaders = text, error = null) }

    fun onMaxAgeChanged(text: String) = updateEditor { it.copy(maxAge = text.filter(Char::isDigit), error = null) }

    fun submit() {
        val editor = state.value.editor ?: return
        val id = accountId ?: return
        val error = editorError(editor)
        if (error != null) return updateEditor { it.copy(error = error) }
        val rules = loadedRules() ?: return refuseWriteWithoutList()
        val position = editor.original?.let { rules.indexOf(it) } ?: -1
        if (editor.original != null && position < 0) return refuseWriteWithoutList()
        val rule = R2CorsRule(
            id = editor.original?.id,
            allowed = R2CorsAllowed(
                origins = parseOrigins(editor.origins),
                methods = editor.methods.toList(),
                headers = parseOrigins(editor.headers),
            ),
            exposeHeaders = parseOrigins(editor.exposeHeaders),
            maxAgeSeconds = editor.maxAge.toIntOrNull(),
        )
        val updated = if (editor.original != null) {
            rules.mapIndexed { i, old -> if (i == position) rule else old }
        } else {
            rules + rule
        }
        updateEditor { it.copy(isSaving = true, submitError = null) }
        writeRules(id, updated, onSuccess = { it.copy(editor = null) }) { result ->
            updateEditor { it.copy(isSaving = false, submitError = R2ErrorUiMapper.toErrorUi(result.error)) }
        }
    }

    fun requestRemoveRule(index: Int) {
        val rule = currentRules().getOrNull(index) ?: return
        mutableState.update { it.copy(ruleToRemove = rule) }
    }

    fun dismissRemoveRule() = mutableState.update { it.copy(ruleToRemove = null) }

    fun confirmRemoveRule() {
        val id = accountId ?: return
        val rule = state.value.ruleToRemove ?: return
        if (state.value.isWorking) return
        val rules = loadedRules()
        val position = rules?.indexOf(rule) ?: -1
        if (rules == null || position < 0) {
            mutableState.update { it.copy(ruleToRemove = null) }
            return refuseWriteWithoutList()
        }
        val remaining = rules.filterIndexed { i, _ -> i != position }
        mutableState.update { it.copy(ruleToRemove = null, isWorking = true) }
        writeRules(id, remaining, onSuccess = { it }, onFailure = {})
    }

    fun requestDeleteAll() = mutableState.update { it.copy(deleteAllRequested = true) }

    fun dismissDeleteAll() = mutableState.update { it.copy(deleteAllRequested = false) }

    fun confirmDeleteAll() {
        val id = accountId ?: return
        mutableState.update { it.copy(deleteAllRequested = false, isWorking = true) }
        viewModelScope.launch {
            val result = repository.deleteCorsRules(id, bucketName)
            mutableState.update { it.copy(isWorking = false, message = UiText.Resource(messageFor(result))) }
            load(id)
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun writeRules(
        id: String,
        rules: List<R2CorsRule>,
        onSuccess: (R2CorsUiState) -> R2CorsUiState,
        onFailure: (ApiResult.Failure) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.replaceCorsRules(id, bucketName, rules)) {
                is ApiResult.Success -> {
                    mutableState.update { onSuccess(it).copy(isWorking = false, message = UiText.Resource(R.string.r2_cors_saved)) }
                    load(id)
                }
                is ApiResult.Failure -> {
                    mutableState.update {
                        it.copy(isWorking = false, message = UiText.Resource(R.string.r2_settings_write_failed))
                    }
                    onFailure(result)
                }
            }
        }
    }

    private fun currentRules(): List<R2CorsRule> = loadedRules().orEmpty()

    /** The rules as last loaded, or null when they did not load. A full-list write must never use a guess. */
    private fun loadedRules(): List<R2CorsRule>? = (state.value.rules as? UiState.Content)?.data

    /** Refuses a write because the rule list is unknown or changed. Nothing is sent, so nothing is wiped. */
    private fun refuseWriteWithoutList() {
        mutableState.update { it.copy(message = UiText.Resource(R.string.r2_settings_reload_first)) }
    }

    private suspend fun load(id: String) {
        val section = when (val result = repository.corsRules(id, bucketName)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(R2ErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(rules = section) }
    }

    private fun updateEditor(transform: (R2CorsEditor) -> R2CorsEditor) = mutableState.update { current ->
        current.editor?.let { current.copy(editor = transform(it)) } ?: current
    }

    private fun editorError(editor: R2CorsEditor): UiText? {
        val origins = parseOrigins(editor.origins)
        return when {
            origins.isEmpty() -> UiText.Resource(R.string.r2_cors_origin_required)
            invalidOrigins(origins).isNotEmpty() -> UiText.Resource(R.string.r2_cors_origin_invalid)
            editor.methods.isEmpty() -> UiText.Resource(R.string.r2_cors_method_required)
            editor.maxAge.isNotEmpty() && editor.maxAge.toIntOrNull() == null -> UiText.Resource(R.string.r2_cors_max_age_invalid)
            else -> null
        }
    }

    private fun messageFor(result: ApiResult<*>): Int =
        if (result is ApiResult.Success) R.string.r2_cors_deleted else R.string.r2_settings_write_failed
}
