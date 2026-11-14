package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.wear.builds.RetryPlan
import com.tomasps.virga.wear.builds.WearBuilds
import java.time.Instant

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
) : WearBuilds {
    val calls = mutableListOf<String>()

    override suspend fun recent(accountId: String): ApiResult<List<BuildSummary>> {
        calls += "recent:$accountId"
        return recent
    }

    override suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary> {
        calls += "detail:$accountId"
        return detail ?: ApiResult.Success(build)
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
