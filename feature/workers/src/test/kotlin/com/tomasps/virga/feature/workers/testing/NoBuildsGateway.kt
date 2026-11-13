package com.tomasps.virga.feature.workers.testing

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A builds gateway for tests that do not look at builds: no builds, nothing watched. */
object NoBuildsGateway : BuildsGateway {
    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(emptyList())

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> = ApiResult.Success(emptyList())

    override fun watchedBuildIds(): Flow<Set<String>> = flowOf(emptySet())

    override fun startWatching(accountId: String, build: BuildSummary) = Unit

    override fun stopWatching(buildUuid: String) = Unit
}
