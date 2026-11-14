package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.wear.builds.RetryPlan
import com.tomasps.virga.wear.builds.WearBuilds
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

internal const val ACCOUNT = "acc-1"

internal val CONNECTED = com.tomasps.virga.wear.session.WatchSessionState.Connected(ACCOUNT, isDemo = false)

internal fun build(
    id: String = "b-1",
    status: BuildStatus = BuildStatus.STOPPED,
    outcome: BuildOutcome? = BuildOutcome.SUCCESS,
) = BuildSummary(
    buildUuid = id,
    scriptName = "api",
    workerTag = "tag-api",
    status = status,
    outcome = outcome,
    branch = "main",
    commitHash = "abc",
    createdOn = Instant.parse("2026-11-14T10:00:00Z"),
)

internal val RUNNING = build(status = BuildStatus.RUNNING, outcome = null)
internal val PLAN = RetryPlan("trigger-1", BuildTarget.Commit("abc"))

/** Scripted [WearBuilds] that records every call, so tests can assert that nothing was called. */
internal class FakeWearBuilds(
    var recent: ApiResult<List<BuildSummary>> = ApiResult.Success(emptyList()),
    var detail: ApiResult<BuildSummary>? = null,
    var plan: ApiResult<RetryPlan?> = ApiResult.Success(null),
    var cancel: ApiResult<Unit> = ApiResult.Success(Unit),
    var retry: ApiResult<String> = ApiResult.Success("b-2"),
    /** When null, [latest] is derived from [recent]. */
    var latest: ApiResult<BuildSummary?>? = null,
    var record: ApiResult<BuildRecord> = ApiResult.Failure(ApiError.Local("no record scripted")),
) : WearBuilds {
    val calls = mutableListOf<String>()

    override suspend fun recent(accountId: String): ApiResult<List<BuildSummary>> {
        calls += "recent:$accountId"
        return recent
    }

    override suspend fun latest(accountId: String): ApiResult<BuildSummary?> {
        calls += "latest:$accountId"
        latest?.let { return it }
        return when (val result = recent) {
            is ApiResult.Success -> ApiResult.Success(result.value.firstOrNull())
            is ApiResult.Failure -> result
        }
    }

    override suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary> {
        calls += "detail:$accountId"
        return detail ?: ApiResult.Success(build)
    }

    override suspend fun record(accountId: String, scriptName: String, buildUuid: String): ApiResult<BuildRecord> {
        calls += "record:$accountId:$scriptName/$buildUuid"
        return record
    }

    override suspend fun cancel(accountId: String, buildUuid: String): ApiResult<Unit> {
        calls += "cancel:$buildUuid"
        return cancel
    }

    override suspend fun retryPlan(accountId: String, build: BuildSummary): ApiResult<RetryPlan?> {
        calls += "retryPlan:${build.buildUuid}"
        return plan
    }

    override suspend fun retry(accountId: String, build: BuildSummary): ApiResult<String> {
        calls += "retry:${build.buildUuid}"
        return retry
    }
}

internal fun failure(error: ApiError) = ApiResult.Failure(error)

/** Records what the detail screen asks of the watcher, and mirrors the watched set the way the registry does. */
internal class FakeWatchStarter : com.tomasps.virga.wear.alerts.WatchStarter {
    val keys = MutableStateFlow<Set<String>>(emptySet())
    val calls = mutableListOf<String>()
    var refuseStart = false
    override val watchedKeys: kotlinx.coroutines.flow.Flow<Set<String>> get() = keys

    override fun start(accountId: String, scriptName: String, buildUuid: String): Boolean {
        calls += "start:$accountId:$scriptName/$buildUuid"
        if (refuseStart) return false
        keys.value += "$scriptName/$buildUuid"
        return true
    }

    override fun stop(scriptName: String, buildUuid: String) {
        calls += "stop:$scriptName/$buildUuid"
        keys.value -= "$scriptName/$buildUuid"
    }
}
