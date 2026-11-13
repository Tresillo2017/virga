package com.tomasps.virga.feature.pages.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesProject
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.data.buildConfigPatch
import com.tomasps.virga.feature.pages.data.productionBranchPatch
import com.tomasps.virga.feature.pages.logic.EnvVarEntry
import com.tomasps.virga.feature.pages.logic.EnvVarRules
import com.tomasps.virga.feature.pages.logic.EnvVarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Project header and every setting Virga can change. [message] is a one-off note the screen shows and then clears. */
data class ProjectDetailUiState(
    val accountId: String? = null,
    val project: UiState<PagesProject> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val busy: Boolean = false,
    val message: UiText? = null,
    val deleted: Boolean = false,
)

class ProjectDetailViewModel(
    private val projectName: String,
    session: SessionRepository,
    private val repository: PagesProjectsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ProjectDetailUiState())
    val state: StateFlow<ProjectDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId ->
                mutableState.update { it.copy(accountId = accountId, project = UiState.Loading) }
                load(accountId, refreshing = false)
            }
        }
    }

    fun refresh() {
        val accountId = mutableState.value.accountId ?: return
        viewModelScope.launch { load(accountId, refreshing = true) }
    }

    fun saveProductionBranch(branch: String) {
        val trimmed = branch.trim()
        if (trimmed.isEmpty()) return showMessage(UiText.Resource(R.string.pages_message_branch_empty))
        update(UiText.Resource(R.string.pages_message_branch_saved)) { accountId ->
            repository.updateSettings(accountId, projectName, productionBranchPatch(trimmed))
        }
    }

    fun saveBuildConfig(buildCommand: String, destinationDir: String, rootDir: String, buildCaching: Boolean) {
        update(UiText.Resource(R.string.pages_message_build_saved)) { accountId ->
            repository.updateSettings(
                accountId,
                projectName,
                buildConfigPatch(buildCommand.trim(), destinationDir.trim(), rootDir.trim(), buildCaching),
            )
        }
    }

    /**
     * Saves one variable under [environment]. A blank secret on an existing secret keeps its stored value, so nothing
     * is sent; a new secret must have a value.
     */
    fun saveEnvVar(environment: String, name: String, type: EnvVarType, value: String, existing: EnvVarEntry?) {
        val trimmedName = name.trim()
        if (!EnvVarRules.isValidName(trimmedName)) return showMessage(UiText.Resource(R.string.pages_message_env_name_invalid))
        if (EnvVarRules.needsSecretValue(type, value, existing)) {
            return showMessage(UiText.Resource(R.string.pages_message_env_value_required))
        }
        val body = EnvVarRules.saveBody(environment, trimmedName, type, value, existing)
            ?: return showMessage(UiText.Resource(R.string.pages_message_env_unchanged))
        update(UiText.Resource(R.string.pages_message_env_saved)) { accountId ->
            repository.updateSettings(accountId, projectName, body)
        }
    }

    fun deleteEnvVar(environment: String, name: String) {
        update(UiText.Resource(R.string.pages_message_env_deleted)) { accountId ->
            repository.updateSettings(accountId, projectName, EnvVarRules.deleteBody(environment, name))
        }
    }

    fun purgeBuildCache() {
        runAction(UiText.Resource(R.string.pages_message_cache_purged)) { accountId ->
            repository.purgeBuildCache(accountId, projectName)
        }
    }

    /** Only a successful delete sets [ProjectDetailUiState.deleted]; the screen then leaves the project. */
    fun deleteProject() {
        runAction(UiText.Resource(R.string.pages_message_project_deleted), markDeleted = true) { accountId ->
            repository.deleteProject(accountId, projectName)
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private suspend fun load(accountId: String, refreshing: Boolean) {
        mutableState.update { it.copy(isRefreshing = refreshing) }
        val outcome = when (val result = repository.project(accountId, projectName)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { current ->
            if (current.accountId == accountId) current.copy(project = outcome, isRefreshing = false) else current
        }
    }

    private fun update(success: UiText, call: suspend (String) -> ApiResult<PagesProject>) {
        runAction(success) { accountId -> call(accountId) }
    }

    /**
     * Runs one write, replaces the header with a returned project, and reports the outcome. [markDeleted] is set
     * only by the project delete. Calls made while another write is running are dropped.
     */
    private fun runAction(success: UiText, markDeleted: Boolean = false, call: suspend (String) -> ApiResult<*>) {
        val accountId = mutableState.value.accountId ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = call(accountId)
            mutableState.update { current -> current.afterWrite(result, success, markDeleted) }
        }
    }

    private fun ProjectDetailUiState.afterWrite(
        result: ApiResult<*>,
        success: UiText,
        markDeleted: Boolean,
    ): ProjectDetailUiState = when (result) {
        is ApiResult.Success -> copy(
            busy = false,
            message = success,
            project = (result.value as? PagesProject)?.let { UiState.Content(it) } ?: project,
            deleted = deleted || markDeleted,
        )
        is ApiResult.Failure -> copy(
            busy = false,
            message = ApiErrorUiMapper.toErrorUi(result.error).message,
        )
    }

    private fun showMessage(message: UiText) {
        mutableState.update { it.copy(message = message) }
    }
}
