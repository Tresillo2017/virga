package com.tomasps.virga.feature.builds.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.cloudflarePath
import com.tomasps.virga.core.network.demo.DemoMode
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One page of builds. [rawCount] is the number of items the API sent, before malformed ones are dropped. */
data class BuildsPage(val records: List<BuildRecord>, val rawCount: Int)

/** A Worker as the Workers scripts list names it. The Builds API keys by [tag], not by [name]. */
data class WorkerRef(val name: String, val tag: String)

/**
 * Builds API calls. Paths follow the Workers Builds API reference (checked 2026-11-11): builds are read and
 * cancelled by build UUID, a Worker's history, settings, previews and triggers by its tag. Writes are confirmed by
 * the caller first.
 */
class BuildsRepository(
    private val api: CloudflareApi,
    private val demoMode: DemoMode? = null,
) {

    private val isDemo: Boolean
        get() = demoMode?.enabled?.value == true

    /** Every Worker in the account with its tag. One list call; the tag is the only key the Builds API accepts. */
    suspend fun workers(accountId: String): ApiResult<List<WorkerRef>> {
        val demo = isDemo
        return api.allPages(
            path = cloudflarePath("accounts", accountId, "workers", "scripts"),
            deserializer = ListSerializer(ScriptDto.serializer()),
        ).map { scripts -> scripts.mapNotNull { it.toWorkerRef(isDemo = demo) } }
    }

    /** Resolves a script name to its tag, or fails with NotFound when the account has no such Worker. */
    suspend fun tagFor(accountId: String, scriptName: String): ApiResult<String> =
        when (val result = workers(accountId)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.firstOrNull { it.name == scriptName }
                ?.let { ApiResult.Success(it.tag) }
                ?: ApiResult.Failure(ApiError.NotFound())
        }

    /** One page of a Worker's builds, newest first. The API keeps 90 days. */
    suspend fun builds(
        accountId: String,
        workerTag: String,
        scriptName: String,
        page: Int,
        perPage: Int,
    ): ApiResult<List<BuildRecord>> = buildsPage(accountId, workerTag, scriptName, page, perPage).map { it.records }

    /**
     * One page of builds with the number of items the API sent. Malformed items are dropped from [BuildsPage.records]
     * but still counted, so a short page is detected from what the API returned, not from what parsed.
     */
    suspend fun buildsPage(
        accountId: String,
        workerTag: String,
        scriptName: String,
        page: Int,
        perPage: Int,
    ): ApiResult<BuildsPage> = api.execute(
        method = HttpMethod.Get,
        path = cloudflarePath("accounts", accountId, "builds", "workers", workerTag, "builds"),
        query = mapOf("page" to page.toString(), "per_page" to perPage.toString()),
        body = null,
        deserializer = ListSerializer(BuildDto.serializer()),
    ).map { items -> BuildsPage(records = items.mapNotNull { it.toRecord(scriptName) }, rawCount = items.size) }

    /** One build by UUID. Fails with Decode when the response has no id or creation time. */
    suspend fun build(accountId: String, buildUuid: String, scriptName: String): ApiResult<BuildRecord> =
        when (val result = api.get<BuildDto>(cloudflarePath("accounts", accountId, "builds", "builds", buildUuid))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.toRecord(scriptName)
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiError.Decode("build without id or creation time"))
        }

    /** One page of log lines. Pass the cursor from the previous page; null reads from the start. */
    suspend fun logs(accountId: String, buildUuid: String, cursor: String?): ApiResult<LogPage> {
        val query = cursor?.let { mapOf("cursor" to it) }.orEmpty()
        return api.get<JsonElement?>(
            path = cloudflarePath("accounts", accountId, "builds", "builds", buildUuid, "logs"),
            query = query,
        ).map { parseLogPage(it) }
    }

    /** Cancels a build. The caller shows a confirmation first. */
    suspend fun cancel(accountId: String, buildUuid: String): ApiResult<JsonElement?> =
        api.putEmpty(cloudflarePath("accounts", accountId, "builds", "builds", buildUuid, "cancel"))

    /** Build settings of a Worker: its repository, production settings and whether Previews are on. */
    suspend fun config(accountId: String, workerTag: String): ApiResult<BuildConfig> =
        api.get<BuildConfigDto>(cloudflarePath("accounts", accountId, "builds", "workers", workerTag))
            .map { it.toBuildConfig() }

    /** Sends only the changed fields. The response is the full settings object, read back as the new config. */
    suspend fun updateConfig(accountId: String, workerTag: String, edit: BuildConfigEdit): ApiResult<BuildConfig> =
        api.patch<JsonObject, BuildConfigDto>(
            path = cloudflarePath("accounts", accountId, "builds", "workers", workerTag),
            body = edit.toPatchBody(),
        ).map { it.toBuildConfig() }

    /** Build triggers of a Worker. A manual build needs one of these. */
    suspend fun triggers(accountId: String, workerTag: String): ApiResult<List<TriggerRef>> =
        api.get<JsonElement?>(cloudflarePath("accounts", accountId, "builds", "workers", workerTag, "triggers"))
            .map { parseTriggers(it) }

    /** Starts a build from a trigger, for a branch or a commit. Returns the new build's UUID. */
    suspend fun startBuild(accountId: String, triggerUuid: String, target: BuildTarget): ApiResult<String> =
        api.post<JsonObject, JsonElement?>(
            path = cloudflarePath("accounts", accountId, "builds", "triggers", triggerUuid, "builds"),
            body = target.toBody(),
        ).let { result ->
            when (result) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> parseStartedBuildUuid(result.value)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiError.Decode("build start without build_uuid"))
            }
        }

    suspend fun previews(accountId: String, workerTag: String): ApiResult<List<PreviewSummary>> =
        api.allPages(
            path = cloudflarePath("accounts", accountId, "builds", "workers", workerTag, "previews"),
            deserializer = ListSerializer(PreviewDto.serializer()),
        ).map { list -> list.mapNotNull { it.toSummary() } }
}
