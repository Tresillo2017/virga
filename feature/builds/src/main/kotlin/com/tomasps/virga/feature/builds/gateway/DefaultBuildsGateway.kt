package com.tomasps.virga.feature.builds.gateway

import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.api.WorkerRef
import com.tomasps.virga.feature.builds.watch.SummaryDismissal
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.feature.builds.watch.WatchServiceStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal const val FIRST_PAGE = 1
internal const val MAX_PER_PAGE = 100
private const val ACCOUNT_SCAN_PER_PAGE = 10

/**
 * Cross-feature builds access, bound in the Koin module. Reads go through [BuildsRepository]. Watches go through
 * [WatchRegistry] and start the watch service. On sign-out, or when the selected account changes, every watch
 * that does not belong to the new account is stopped.
 */
class DefaultBuildsGateway(
    private val repository: BuildsRepository,
    private val registry: WatchRegistry,
    private val starter: WatchServiceStarter,
    private val dismissal: SummaryDismissal,
    session: SessionRepository,
    scope: CoroutineScope,
    private val maxWorkersScanned: Int = DEFAULT_WORKERS_SCANNED,
) : BuildsGateway {

    init {
        scope.launch {
            registry.restore()
            session.state.collect { state -> stopWatchesOutside(state) }
        }
    }

    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        when (val tag = repository.tagFor(accountId, scriptName)) {
            is ApiResult.Failure -> ApiResult.Failure(tag.error)
            is ApiResult.Success -> repository
                .builds(accountId, tag.value, scriptName, FIRST_PAGE, limit.coerceIn(1, MAX_PER_PAGE))
                .map { records -> records.map(BuildRecord::toSummary) }
        }

    /**
     * Builds still queued, initializing or running. The Builds API has no account-wide list, so this reads the
     * first few builds of up to [maxWorkersScanned] Workers. A Worker whose builds cannot be read is skipped, so one
     * failing Worker does not hide the rest. Fails only when the Worker list itself cannot be read.
     */
    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> {
        val workers = when (val result = repository.workers(accountId)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.value
        }
        val active = workers.take(maxWorkersScanned).flatMap { worker ->
            when (val builds = recentFor(accountId, worker)) {
                is ApiResult.Failure -> emptyList()
                is ApiResult.Success -> builds.value.filter { it.status.isActive }.map(BuildRecord::toSummary)
            }
        }
        return ApiResult.Success(active.sortedByDescending { it.createdOn })
    }

    override fun watchedBuildIds(): Flow<Set<String>> = registry.watchedIds

    override fun startWatching(accountId: String, build: BuildSummary) {
        registry.add(accountId, build.scriptName, build.buildUuid)
        dismissal.onNewWatch()
        starter.start()
    }

    override fun stopWatching(buildUuid: String) {
        registry.remove(buildUuid)
    }

    private suspend fun recentFor(accountId: String, worker: WorkerRef): ApiResult<List<BuildRecord>> =
        repository.builds(accountId, worker.tag, worker.name, FIRST_PAGE, ACCOUNT_SCAN_PER_PAGE)

    private fun stopWatchesOutside(state: SessionState) {
        when (state) {
            is SessionState.SignedIn -> registry.retainAccount(state.selectedAccountId)
            SessionState.SignedOut -> registry.retainAccount(null)
            SessionState.Restoring -> Unit
        }
    }

    companion object {
        const val DEFAULT_WORKERS_SCANNED = 20
    }
}
