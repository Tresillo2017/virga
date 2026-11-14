package com.tomasps.virga.wear.builds

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildRecord

/** What the watch screens need from the build data. [WearBuildsSource] is the real one; tests substitute fakes. */
interface WearBuilds {
    suspend fun recent(accountId: String): ApiResult<List<BuildSummary>>
    /** The newest build, strictly: any failed call is a Failure, so callers never mistake a gap for "no builds". */
    suspend fun latest(accountId: String): ApiResult<BuildSummary?>
    suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary>
    /** One build by uuid, for the watch engine. A 401 takes the same once-per-operation wipe path as every other read. */
    suspend fun record(accountId: String, scriptName: String, buildUuid: String): ApiResult<BuildRecord>
    suspend fun cancel(accountId: String, buildUuid: String): ApiResult<Unit>

    /** Whether Retry can be offered for [build]. A null plan means hide Retry. */
    suspend fun retryPlan(accountId: String, build: BuildSummary): ApiResult<RetryPlan?>
    suspend fun retry(accountId: String, build: BuildSummary): ApiResult<String>
}
