package com.tomasps.virga.feature.pages.deployments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.data.PagesDeploymentsRepository
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.logic.isInProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val DEPLOYMENT_POLL_MS = 5_000L

/** The newest build log lines kept in state. Older lines are dropped so a long log cannot grow without limit. */
internal const val MAX_LOG_LINES = 1_000

/**
 * One deployment with its stage progress. While a stage is running the screen polls every [DEPLOYMENT_POLL_MS]
 * (in-app only, no Live Update). [logs] is null until the user asks for them.
 *
 * [currentProductionId] is the deployment the project serves in production. Rollback and delete are offered only once
 * [productionKnown] is true, so a live production deployment is never offered for those actions by mistake.
 */
data class DeploymentDetailUiState(
    val accountId: String? = null,
    val deployment: UiState<PagesDeployment> = UiState.Loading,
    val isPolling: Boolean = false,
    val isRefreshing: Boolean = false,
    val busy: Boolean = false,
    val logs: UiState<List<String>>? = null,
    val message: UiText? = null,
    val deleted: Boolean = false,
    val currentProductionId: String? = null,
    val productionKnown: Boolean = false,
)

class DeploymentDetailViewModel(
    private val projectName: String,
    private val deploymentId: String,
    session: SessionRepository,
    private val repository: PagesDeploymentsRepository,
    private val projects: PagesProjectsRepository,
    private val pollIntervalMs: Long = DEPLOYMENT_POLL_MS,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DeploymentDetailUiState())
    val state: StateFlow<DeploymentDetailUiState> = mutableState.asStateFlow()

    /** The one load-and-poll chain. A new load cancels the previous chain, so two polls never run at once. */
    private var loadJob: Job? = null

    /** Reads which deployment is production. Separate from [loadJob] so polling never re-reads the project. */
    private var productionJob: Job? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId ->
                mutableState.update {
                    it.copy(
                        accountId = accountId,
                        deployment = UiState.Loading,
                        isPolling = false,
                        logs = null,
                        currentProductionId = null,
                        productionKnown = false,
                    )
                }
                restart(accountId)
            }
        }
    }

    fun refresh() {
        val accountId = mutableState.value.accountId ?: return
        mutableState.update { it.copy(isRefreshing = true) }
        restart(accountId)
    }

    fun retry() = runWrite(R.string.pages_deployment_message_retried) { accountId ->
        repository.retry(accountId, projectName, deploymentId)
    }

    fun rollback() = runWrite(R.string.pages_deployment_message_rolled_back) { accountId ->
        repository.rollback(accountId, projectName, deploymentId)
    }

    fun delete() = runWrite(R.string.pages_deployment_message_deleted, markDeleted = true) { accountId ->
        repository.delete(accountId, projectName, deploymentId, force = false)
    }

    fun loadLogs() {
        val accountId = mutableState.value.accountId ?: return
        mutableState.update { it.copy(logs = UiState.Loading) }
        viewModelScope.launch {
            val outcome = when (val result = repository.logs(accountId, projectName, deploymentId)) {
                is ApiResult.Success -> UiState.Content(
                    result.value.data.orEmpty().mapNotNull { it.line }.takeLast(MAX_LOG_LINES),
                )
                is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
            }
            mutableState.update { current ->
                if (current.accountId != accountId) current else current.copy(logs = outcome)
            }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun restart(accountId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(accountId) }
        productionJob?.cancel()
        productionJob = viewModelScope.launch { loadCurrentProduction(accountId) }
    }

    private suspend fun load(accountId: String) {
        val outcome = when (val result = repository.deployment(accountId, projectName, deploymentId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        val moving = (outcome as? UiState.Content)?.data?.isInProgress() == true
        mutableState.update { current ->
            if (current.accountId != accountId) current else current.copy(
                deployment = outcome,
                isPolling = moving,
                isRefreshing = false,
            )
        }
        if (moving) {
            delay(pollIntervalMs)
            if (mutableState.value.accountId == accountId) load(accountId)
        }
    }

    /** Reads the project's canonical (production) deployment. A failed read leaves [DeploymentDetailUiState.productionKnown] false. */
    private suspend fun loadCurrentProduction(accountId: String) {
        val result = projects.project(accountId, projectName)
        mutableState.update { current ->
            if (current.accountId != accountId || result !is ApiResult.Success) current else current.copy(
                currentProductionId = result.value.canonicalDeployment?.id,
                productionKnown = true,
            )
        }
    }

    /** Runs one write; on success the deployment is replaced by the returned one (or the screen is left after a delete). */
    private fun runWrite(
        successText: Int,
        markDeleted: Boolean = false,
        call: suspend (String) -> ApiResult<*>,
    ) {
        val accountId = mutableState.value.accountId ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = call(accountId)
            mutableState.update { current ->
                when (result) {
                    is ApiResult.Success -> current.copy(
                        busy = false,
                        message = UiText.Resource(successText),
                        deployment = (result.value as? PagesDeployment)?.let { UiState.Content(it) } ?: current.deployment,
                        deleted = current.deleted || markDeleted,
                    )
                    is ApiResult.Failure -> current.copy(
                        busy = false,
                        message = ApiErrorUiMapper.toErrorUi(result.error).message,
                    )
                }
            }
            if (result is ApiResult.Success && !markDeleted) restart(accountId)
        }
    }
}
