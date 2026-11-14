package com.tomasps.virga.wear.builds

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult

/** What the watch screens need from the build data. [WearBuildsSource] is the real one; tests substitute fakes. */
interface WearBuilds {
    suspend fun recent(accountId: String): ApiResult<List<BuildSummary>>
    suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary>
    suspend fun cancel(accountId: String, buildUuid: String): ApiResult<Unit>

    /** Whether Retry can be offered for [build]. A null plan means hide Retry. */
    suspend fun retryPlan(accountId: String, build: BuildSummary): ApiResult<RetryPlan?>
    suspend fun retry(accountId: String, build: BuildSummary): ApiResult<String>
}
