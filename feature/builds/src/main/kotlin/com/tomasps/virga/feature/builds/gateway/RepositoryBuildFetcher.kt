package com.tomasps.virga.feature.builds.gateway

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.watch.BuildFetcher

/** The watch loop's read: one build by UUID, straight from the repository. */
class RepositoryBuildFetcher(private val repository: BuildsRepository) : BuildFetcher {
    override suspend fun fetch(accountId: String, scriptName: String, buildUuid: String): ApiResult<BuildRecord> =
        repository.build(accountId, buildUuid, scriptName)
}
