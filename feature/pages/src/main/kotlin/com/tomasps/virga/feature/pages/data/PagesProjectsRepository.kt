package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Project-level Pages calls. Repositories take [CloudflareApi] and return [ApiResult]; they never touch the UI. */
class PagesProjectsRepository(private val api: CloudflareApi) {

    suspend fun projects(accountId: String): ApiResult<List<PagesProject>> =
        api.allPages(projectsPath(accountId), deserializer = ListSerializer(PagesProject.serializer()))

    suspend fun project(accountId: String, projectName: String): ApiResult<PagesProject> =
        api.get(projectPath(accountId, projectName))

    /** PATCH with exactly [body]. Build the body with the builders in PagesRequests.kt. */
    suspend fun updateSettings(accountId: String, projectName: String, body: JsonObject): ApiResult<PagesProject> =
        api.patch(projectPath(accountId, projectName), body)

    suspend fun deleteProject(accountId: String, projectName: String): ApiResult<Unit> =
        api.delete(projectPath(accountId, projectName))

    suspend fun purgeBuildCache(accountId: String, projectName: String): ApiResult<Unit> =
        api.postEmpty(projectPath(accountId, projectName, "purge_build_cache"))
}
