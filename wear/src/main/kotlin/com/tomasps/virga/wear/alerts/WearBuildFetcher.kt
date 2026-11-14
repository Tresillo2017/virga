package com.tomasps.virga.wear.alerts

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.wear.builds.WearBuilds

/**
 * The watch engine's reader. It goes through [WearBuilds.record], the same guarded path as every other watch read, so
 * a rejected token wipes the session once per operation and the loop then stops on the session state.
 */
class WearBuildFetcher(private val builds: WearBuilds) : BuildFetcher {
    override suspend fun fetch(accountId: String, scriptName: String, buildUuid: String): ApiResult<BuildRecord> =
        builds.record(accountId, scriptName, buildUuid)
}
