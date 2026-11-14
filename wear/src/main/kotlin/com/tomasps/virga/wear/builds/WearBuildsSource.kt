package com.tomasps.virga.wear.builds

import android.util.Log
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.api.WorkerRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val MAX_WORKERS_SCANNED = 20
internal const val MAX_LISTED = 10
internal const val MAX_PARALLEL_FETCHES = 4
private const val BUILDS_PER_WORKER = 5
private const val FIRST_PAGE = 1
private const val TAG = "WearBuildsSource"

/**
 * The watch's build data. Reads go through [BuildsRepository]. A 401 on a token that was present wipes the watch
 * session through [onUnauthorized]; the local "no token" failure (stale session, handoff in progress) does not,
 * which [hasToken] tells apart.
 */
class WearBuildsSource(
    private val repository: BuildsRepository,
    private val onUnauthorized: suspend () -> Unit,
    private val hasToken: suspend () -> Boolean,
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {

    /**
     * The newest builds across the first [MAX_WORKERS_SCANNED] Workers, at most [MAX_LISTED]. A Worker whose builds
     * cannot be read is skipped. Fails only when the Worker list cannot be read. After a 401 on a present token the
     * session is wiped but the call still returns its own result: the UI reacts to the session state change.
     */
    suspend fun recent(accountId: String): ApiResult<List<BuildSummary>> {
        val workers = when (val result = guarded { repository.workers(accountId) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val permits = Semaphore(MAX_PARALLEL_FETCHES)
        val builds = coroutineScope {
            workers.take(MAX_WORKERS_SCANNED)
                .map { worker -> async { permits.withPermit { buildsOf(accountId, worker) } } }
                .awaitAll()
        }.flatten()
        return ApiResult.Success(builds.sortedByDescending { it.createdOn }.take(MAX_LISTED))
    }

    suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary> =
        guarded { repository.build(accountId, build.buildUuid, build.scriptName) }
            .map { record -> record.toSummary().copy(workerTag = build.workerTag) }

    suspend fun cancel(accountId: String, buildUuid: String): ApiResult<Unit> =
        guarded { repository.cancel(accountId, buildUuid) }.map { }

    /** Whether Retry can be offered for [build], and from which trigger and target. A null plan means hide Retry. */
    suspend fun retryPlan(accountId: String, build: BuildSummary): ApiResult<RetryPlan?> {
        // No branch and no commit means no plan whatever the triggers say, so skip the network.
        if (RetryPlanner.targetOf(build) == null) return ApiResult.Success(null)
        val tag = build.workerTag ?: when (val resolved = guarded { repository.tagFor(accountId, build.scriptName) }) {
            is ApiResult.Failure -> return resolved
            is ApiResult.Success -> resolved.value
        }
        return guarded { repository.triggers(accountId, tag) }.map { RetryPlanner.plan(build, it) }
    }

    /** Starts a new build for [build]. Fails locally, without a network start call, when there is no plan. */
    suspend fun retry(accountId: String, build: BuildSummary): ApiResult<String> {
        val plan = when (val result = retryPlan(accountId, build)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value ?: return ApiResult.Failure(ApiError.Local("no retry plan"))
        }
        return guarded { repository.startBuild(accountId, plan.triggerUuid, plan.target) }
    }

    private suspend fun buildsOf(accountId: String, worker: WorkerRef): List<BuildSummary> =
        when (val result = guarded { repository.builds(accountId, worker.tag, worker.name, FIRST_PAGE, BUILDS_PER_WORKER) }) {
            is ApiResult.Failure -> emptyList()
            is ApiResult.Success -> result.value.map { it.toSummaryWithTag(worker.tag) }
        }

    private fun BuildRecord.toSummaryWithTag(tag: String): BuildSummary = toSummary().copy(workerTag = tag)

    private suspend fun <T> guarded(call: suspend () -> ApiResult<T>): ApiResult<T> {
        val result = call()
        if (result is ApiResult.Failure && result.error is ApiError.Unauthorized && tokenWasPresent()) wipeSession()
        return result
    }

    /** A token check that fails (storage trouble) counts as no token: never wipe on a guess. */
    private suspend fun tokenWasPresent(): Boolean = try {
        hasToken()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        false
    }

    private suspend fun wipeSession() {
        try {
            onUnauthorized()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Class name only: the message could carry storage paths or credential material.
            warn("Could not clear the session after a 401: ${failure.javaClass.simpleName}")
        }
    }
}
