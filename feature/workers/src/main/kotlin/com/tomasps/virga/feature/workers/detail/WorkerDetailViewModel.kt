package com.tomasps.virga.feature.workers.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.errorOrNull
import com.tomasps.virga.core.common.result.getOrNull
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.CustomDomain
import com.tomasps.virga.feature.workers.data.DeploymentPage
import com.tomasps.virga.feature.workers.data.DeploymentVersion
import com.tomasps.virga.feature.workers.data.ScriptSettings
import com.tomasps.virga.feature.workers.data.SubdomainState
import com.tomasps.virga.feature.workers.data.WorkerVersion
import com.tomasps.virga.feature.workers.data.WorkersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One Worker's detail. Every entry gets its own instance (see the feature module guide), so two Workers never share
 * state. The snapshot reloads whenever the selected account changes.
 */
class WorkerDetailViewModel(
    session: SessionRepository,
    private val repository: WorkersRepository,
    private val builds: BuildsGateway,
    private val scriptName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(WorkerDetailUiState(scriptName = scriptName))
    val state: StateFlow<WorkerDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId -> onAccountSelected(accountId) }
        }
        viewModelScope.launch {
            builds.watchedBuildIds().collect { ids -> mutableState.update { it.copy(watchedBuildIds = ids) } }
        }
    }

    fun selectTab(tab: DetailTab) {
        mutableState.update { it.copy(tab = tab) }
    }

    fun retry() {
        mutableState.value.accountId?.let { accountId -> viewModelScope.launch { reload(accountId) } }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    /** Creates a percentage deployment after re-reading the active one, so a change made elsewhere is not overwritten. */
    fun deploy(split: List<DeploymentVersion>, expectedActiveDeploymentId: String?) {
        if (!DeploymentSplit.isValid(split.map { it.percentage })) {
            mutableState.update { it.copy(message = UiText.Resource(R.string.workers_split_must_total_100)) }
            return
        }
        runAction(failureMessage = R.string.workers_deploy_failed) { accountId ->
            val current = repository.deployments(accountId, scriptName)
            val active = current.getOrNull()?.deployments?.firstOrNull()?.id
            when {
                current.errorOrNull() != null -> ActionOutcome.Failed(current.errorOrNull())
                active != expectedActiveDeploymentId -> ActionOutcome.Message(R.string.workers_deployment_changed)
                else -> {
                    val result = repository.deployPercentages(accountId, scriptName, split)
                    result.errorOrNull()?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Message(R.string.workers_deployment_saved)
                }
            }
        }
    }

    /** Rolls back by deploying one older version at 100 percent. Bindings and data are not rolled back. */
    fun rollback(versionId: String, expectedActiveDeploymentId: String?) {
        deploy(
            split = DeploymentSplit.rollbackTo(versionId).map { (id, pct) -> DeploymentVersion(id, pct) },
            expectedActiveDeploymentId = expectedActiveDeploymentId,
        )
    }

    fun setWorkersDevAndPreviews(enabled: Boolean, previewsEnabled: Boolean) {
        runAction(failureMessage = R.string.workers_subdomain_failed) { accountId ->
            val result = repository.setSubdomain(accountId, scriptName, enabled, previewsEnabled)
            result.errorOrNull()?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Message(R.string.workers_subdomain_saved)
        }
    }

    /** Attaches a hostname to this Worker. Cloudflare decides the zone, so only the hostname shape is checked here. */
    fun addCustomDomain(hostname: String) {
        val normalised = normaliseHostname(hostname)
        if (!isValidHostname(normalised)) {
            mutableState.update { it.copy(message = UiText.Resource(R.string.workers_domain_invalid)) }
            return
        }
        runAction(failureMessage = R.string.workers_domain_add_failed) { accountId ->
            val result = repository.addCustomDomain(accountId, normalised, scriptName)
            result.errorOrNull()?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Message(R.string.workers_domain_added)
        }
    }

    fun deleteDomain(domain: CustomDomain) {
        runAction(failureMessage = R.string.workers_domain_delete_failed) { accountId ->
            val result = repository.deleteCustomDomain(accountId, domain.id)
            result.errorOrNull()?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Message(R.string.workers_domain_deleted)
        }
    }

    /** Saves one secret. The value goes straight to the request and is not kept in state. */
    fun saveSecret(name: String, value: String) {
        if (name.isBlank() || value.isEmpty()) {
            mutableState.update { it.copy(message = UiText.Resource(R.string.workers_secret_invalid)) }
            return
        }
        runAction(failureMessage = R.string.workers_secret_failed) { accountId ->
            val result = repository.updateSecrets(accountId, scriptName, mapOf(name to value))
            result.errorOrNull()?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Message(R.string.workers_secret_saved)
        }
    }

    fun deleteSecret(name: String) {
        runAction(failureMessage = R.string.workers_secret_failed) { accountId ->
            val result = repository.updateSecrets(accountId, scriptName, mapOf(name to null))
            result.errorOrNull()?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Message(R.string.workers_secret_deleted)
        }
    }

    fun deleteWorker() {
        runAction(failureMessage = R.string.workers_delete_failed) { accountId ->
            when (val result = repository.deleteWorker(accountId, scriptName)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(deleted = true) }
                    ActionOutcome.Message(R.string.workers_deleted)
                }
                is ApiResult.Failure -> ActionOutcome.Failed(result.error)
            }
        }
    }

    /** Starts watching the latest build. The gateway runs the watch; the screen asks for notification permission first. */
    fun watchLatestBuild() {
        val build = mutableState.value.latestBuild ?: return
        val accountId = mutableState.value.accountId ?: return
        builds.startWatching(accountId, build)
    }

    /** Stops the watch on the latest build. Offered on the building card once the build is already watched. */
    fun stopWatchingLatestBuild() {
        val build = mutableState.value.latestBuild ?: return
        builds.stopWatching(build.buildUuid)
    }

    private suspend fun onAccountSelected(accountId: String) {
        mutableState.update { it.copy(accountId = accountId, snapshot = UiState.Loading) }
        reload(accountId)
    }

    private suspend fun reload(accountId: String) {
        mutableState.update { it.copy(snapshot = UiState.Loading) }
        val snapshotResult = fetchSnapshot(accountId)
        val latest = builds.recentBuilds(accountId, scriptName, limit = 1).getOrNull()?.firstOrNull()
        mutableState.update { current ->
            if (current.accountId != accountId) {
                current
            } else {
                current.copy(
                    snapshot = when (snapshotResult) {
                        is ApiResult.Success -> UiState.Content(snapshotResult.value)
                        is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(snapshotResult.error))
                    },
                    latestBuild = latest,
                )
            }
        }
    }

    private suspend fun fetchSnapshot(accountId: String): ApiResult<WorkerSnapshot> {
        val settings = repository.settings(accountId, scriptName)
        val subdomain = repository.subdomain(accountId, scriptName)
        val domains = repository.customDomains(accountId, scriptName)
        val deployments = repository.deployments(accountId, scriptName)
        val versions = repository.versions(accountId, scriptName)
        return combineSnapshot(settings, subdomain, domains, deployments, versions)
    }

    /**
     * Runs one write. A second write is ignored while one is in flight, so a double tap cannot send two deployments.
     * [WorkerDetailUiState.isBusy] is set before the coroutine starts and always cleared, even if the action throws.
     */
    private fun runAction(failureMessage: Int, action: suspend (String) -> ActionOutcome) {
        val accountId = mutableState.value.accountId ?: return
        if (mutableState.value.isBusy) return
        mutableState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val message = try {
                when (val outcome = action(accountId)) {
                    is ActionOutcome.Message -> UiText.Resource(outcome.resId)
                    is ActionOutcome.Failed -> UiText.Resource(failureMessage)
                }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
            mutableState.update { it.copy(message = message) }
            if (!mutableState.value.deleted) reload(accountId)
        }
    }

    private sealed interface ActionOutcome {
        data class Message(val resId: Int) : ActionOutcome
        data class Failed(val error: ApiError?) : ActionOutcome
    }
}

/** Joins the four reads into one snapshot. The first failure wins, so the screen shows one clear error. */
internal fun combineSnapshot(
    settings: ApiResult<ScriptSettings>,
    subdomain: ApiResult<SubdomainState>,
    domains: ApiResult<List<CustomDomain>>,
    deployments: ApiResult<DeploymentPage>,
    versions: ApiResult<List<WorkerVersion>>,
): ApiResult<WorkerSnapshot> {
    settings.errorOrNull()?.let { return ApiResult.Failure(it) }
    subdomain.errorOrNull()?.let { return ApiResult.Failure(it) }
    domains.errorOrNull()?.let { return ApiResult.Failure(it) }
    deployments.errorOrNull()?.let { return ApiResult.Failure(it) }
    versions.errorOrNull()?.let { return ApiResult.Failure(it) }
    val loadedSettings = settings.getOrNull() ?: return ApiResult.Failure(ApiError.Local("settings missing"))
    val loadedSubdomain = subdomain.getOrNull() ?: return ApiResult.Failure(ApiError.Local("subdomain missing"))
    val loadedDomains = domains.getOrNull().orEmpty()
    val loadedDeployments = deployments.getOrNull()?.deployments.orEmpty()
    return ApiResult.Success(
        WorkerSnapshot(
            settings = loadedSettings,
            bindings = bindingsFrom(loadedSettings.bindings),
            subdomain = loadedSubdomain,
            domains = loadedDomains,
            deployments = loadedDeployments,
            versions = versions.getOrNull().orEmpty(),
        ),
    )
}
