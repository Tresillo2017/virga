package com.tomasps.virga.wear.builds

import android.util.Log
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.api.WorkerRef
import java.util.concurrent.atomic.AtomicBoolean
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
) : WearBuilds {

    /**
     * The newest builds across the first [MAX_WORKERS_SCANNED] Workers, at most [MAX_LISTED]. A Worker whose builds
     * cannot be read is skipped. Fails only when the Worker list cannot be read. After a 401 on a present token the
     * session is wiped but the call still returns its own result: the UI reacts to the session state change.
     */
    override suspend fun recent(accountId: String): ApiResult<List<BuildSummary>> {
        val perWorker = when (val scanned = scan(Operation(), accountId)) {
            is ApiResult.Failure -> return scanned
            is ApiResult.Success -> scanned.value
        }
        val builds = perWorker.flatMap { (it as? ApiResult.Success)?.value.orEmpty() }
        return ApiResult.Success(newestFirst(builds).take(MAX_LISTED))
    }

    /**
     * The newest build, strictly: fails with the first error when the Worker list or ANY Worker's builds call failed,
     * so a missing Worker is never mistaken for "no builds" or hides a newer build. Success(null) means every call
     * succeeded and the account has no builds.
     */
    override suspend fun latest(accountId: String): ApiResult<BuildSummary?> {
        val perWorker = when (val scanned = scan(Operation(), accountId)) {
            is ApiResult.Failure -> return scanned
            is ApiResult.Success -> scanned.value
        }
        perWorker.filterIsInstance<ApiResult.Failure>().firstOrNull()?.let { return it }
        val builds = perWorker.flatMap { (it as ApiResult.Success).value }
        return ApiResult.Success(newestFirst(builds).firstOrNull())
    }

    /** A build without a uuid or Worker name cannot be opened. A Worker listed twice yields one copy, the newest. */
    private fun newestFirst(builds: List<BuildSummary>): List<BuildSummary> =
        builds.filter { it.scriptName.isNotBlank() && it.buildUuid.isNotBlank() }
            .sortedByDescending { it.createdOn }
            .distinctBy { it.routeKey() }

    /** Fails only when the Worker list cannot be read; each Worker's own result is kept so callers choose how strict to be. */
    private suspend fun scan(op: Operation, accountId: String): ApiResult<List<ApiResult<List<BuildSummary>>>> {
        val workers = when (val result = guarded(op) { repository.workers(accountId) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val permits = Semaphore(MAX_PARALLEL_FETCHES)
        return ApiResult.Success(
            coroutineScope {
                workers.take(MAX_WORKERS_SCANNED)
                    .map { worker -> async { permits.withPermit { buildsOf(op, accountId, worker) } } }
                    .awaitAll()
            },
        )
    }

    override suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary> =
        guarded(Operation()) { repository.build(accountId, build.buildUuid, build.scriptName) }
            .map { record -> record.toSummary().copy(workerTag = build.workerTag) }

    override suspend fun cancel(accountId: String, buildUuid: String): ApiResult<Unit> =
        guarded(Operation()) { repository.cancel(accountId, buildUuid) }.map { }

    /** Whether Retry can be offered for [build], and from which trigger and target. A null plan means hide Retry. */
    override suspend fun retryPlan(accountId: String, build: BuildSummary): ApiResult<RetryPlan?> =
        retryPlan(Operation(), accountId, build)

    private suspend fun retryPlan(op: Operation, accountId: String, build: BuildSummary): ApiResult<RetryPlan?> {
        // No branch and no commit means no plan whatever the triggers say, so skip the network.
        if (RetryPlanner.targetOf(build) == null) return ApiResult.Success(null)
        val tag = build.workerTag ?: when (val resolved = guarded(op) { repository.tagFor(accountId, build.scriptName) }) {
            is ApiResult.Failure -> return resolved
            is ApiResult.Success -> resolved.value
        }
        return guarded(op) { repository.triggers(accountId, tag) }.map { RetryPlanner.plan(build, it) }
    }

    /** Starts a new build for [build]. Fails locally, without a network start call, when there is no plan. */
    override suspend fun retry(accountId: String, build: BuildSummary): ApiResult<String> {
        val op = Operation()
        val plan = when (val result = retryPlan(op, accountId, build)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value ?: return ApiResult.Failure(ApiError.Local("no retry plan"))
        }
        return guarded(op) { repository.startBuild(accountId, plan.triggerUuid, plan.target) }
    }

    private suspend fun buildsOf(op: Operation, accountId: String, worker: WorkerRef): ApiResult<List<BuildSummary>> =
        guarded(op) { repository.builds(accountId, worker.tag, worker.name, FIRST_PAGE, BUILDS_PER_WORKER) }
            .map { records -> records.map { it.toSummaryWithTag(worker.tag) } }

    private fun BuildRecord.toSummaryWithTag(tag: String): BuildSummary = toSummary().copy(workerTag = tag)

    /** One top-level call. Several of its requests can be rejected at once, but the session is wiped only once. */
    private class Operation {
        private val wiped = AtomicBoolean(false)
        fun claimWipe(): Boolean = wiped.compareAndSet(false, true)
    }

    private suspend fun <T> guarded(op: Operation, call: suspend () -> ApiResult<T>): ApiResult<T> {
        val result = call()
        val rejected = result is ApiResult.Failure && result.error is ApiError.Unauthorized
        if (rejected && tokenWasPresent() && op.claimWipe()) wipeSession()
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
