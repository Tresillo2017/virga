package com.tomasps.virga.feature.home.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.cloudflarePath
import com.tomasps.virga.feature.home.analytics.AnalyticsWindow
import com.tomasps.virga.feature.home.analytics.MAX_ZONES_ON_HOME
import com.tomasps.virga.feature.home.analytics.WORKERS_QUERY
import com.tomasps.virga.feature.home.analytics.WorkersAnalytics
import com.tomasps.virga.feature.home.analytics.ZONES_PER_QUERY
import com.tomasps.virga.feature.home.analytics.ZonesAnalytics
import com.tomasps.virga.feature.home.analytics.parseWorkersAnalytics
import com.tomasps.virga.feature.home.analytics.parseZonesAnalytics
import com.tomasps.virga.feature.home.analytics.zonesQuery
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import com.tomasps.virga.feature.home.search.parseSearchItems
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

private const val ZONE_ACCOUNT_FILTER = "account.id"
private val ZONE_TAG = Regex("^[0-9a-f]{32}$")
private const val R2_WRAPPER_KEY = "buckets"

/** Reads Home's data from the REST and GraphQL APIs. Lists are cached per account; analytics are not. */
class CloudflareHomeRepository(private val api: CloudflareApi) : HomeDataRepository {

    private val listCache = ConcurrentHashMap<ListKey, List<SearchItem>>()

    private data class ListKey(val accountId: String, val source: SearchSource)

    override suspend fun list(
        accountId: String,
        source: SearchSource,
        forceRefresh: Boolean,
    ): ApiResult<List<SearchItem>> {
        val key = ListKey(accountId, source)
        if (!forceRefresh) listCache[key]?.let { return ApiResult.Success(it) }
        return when (val rows = readRows(accountId, source)) {
            is ApiResult.Success -> {
                val items = parseSearchItems(source, rows.value)
                listCache[key] = items
                ApiResult.Success(items)
            }
            is ApiResult.Failure -> rows
        }
    }

    /** Reads every page of a list. Workers is a single unpaged array. A list past the page bound fails visibly. */
    private suspend fun readRows(accountId: String, source: SearchSource): ApiResult<List<JsonElement>> {
        val path = source.path(accountId)
        val query = source.query(accountId)
        return when (source) {
            SearchSource.WORKERS -> api.get<List<JsonElement>>(path, query)
            SearchSource.R2 -> api.allPages(path, query, R2BucketRows)
            else -> api.allPages(path, query, ListSerializer(JsonElement.serializer()))
        }
    }

    override suspend fun workerAnalytics(accountId: String, window: AnalyticsWindow): ApiResult<WorkersAnalytics> {
        val variables = workerVariables(accountId, window)
        return when (val result = api.graphQl.query(WORKERS_QUERY, variables, JsonObject.serializer())) {
            is ApiResult.Success -> ApiResult.Success(parseWorkersAnalytics(result.value, window))
            is ApiResult.Failure -> result
        }
    }

    private fun workerVariables(accountId: String, window: AnalyticsWindow): JsonObject = buildJsonObject {
        put("accountTag", accountId)
        put("start", (window.previousStart ?: window.currentStart).toString())
        put("split", window.currentStart.toString())
        put("splitBefore", window.currentStart.minusSeconds(1).toString())
        put("end", window.end.toString())
    }

    override suspend fun zoneAnalytics(
        accountId: String,
        zoneIds: List<String>,
        window: AnalyticsWindow,
    ): ApiResult<ZonesAnalytics> {
        val distinct = zoneIds.distinct()
        val queried = distinct.filter { ZONE_TAG.matches(it) }.take(MAX_ZONES_ON_HOME)
        if (queried.isEmpty()) {
            return ApiResult.Success(parseZonesAnalytics(emptyList(), window, zonesQueried = 0, zonesTotal = distinct.size))
        }
        val responses = mutableListOf<JsonObject?>()
        var answeredZones = 0
        var chunkFailures = 0
        var firstFailure: ApiError? = null
        for (chunk in queried.chunked(ZONES_PER_QUERY)) {
            when (val result = api.graphQl.query(zonesQuery(chunk), zoneVariables(accountId, window), JsonObject.serializer())) {
                is ApiResult.Success -> {
                    responses += result.value
                    answeredZones += chunk.size
                }
                is ApiResult.Failure -> {
                    chunkFailures += 1
                    if (firstFailure == null) firstFailure = result.error
                }
            }
        }
        if (answeredZones == 0) return ApiResult.Failure(firstFailure ?: ApiError.Local(NO_ZONE_ANSWER))
        return ApiResult.Success(
            parseZonesAnalytics(
                responses,
                window,
                zonesQueried = answeredZones,
                zonesTotal = distinct.size,
                chunkFailures = chunkFailures,
            ),
        )
    }

    private fun zoneVariables(accountId: String, window: AnalyticsWindow): JsonObject = buildJsonObject {
        put("accountTag", accountId)
        put("start", (window.previousStart ?: window.currentStart).toString())
        put("end", window.end.toString())
    }

    private companion object {
        const val NO_ZONE_ANSWER = "No zone analytics query answered"
    }
}

/** R2 wraps its buckets in an object (`{"buckets": [...]}`). This reads the array out of that wrapper. */
private object R2BucketRows : DeserializationStrategy<List<JsonElement>> {
    private val delegate: KSerializer<List<JsonElement>> = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<JsonElement> =
        when (val root = decoder.decodeSerializableValue(JsonElement.serializer())) {
            is JsonArray -> root.toList()
            is JsonObject -> (root[R2_WRAPPER_KEY] as? JsonArray)?.toList().orEmpty()
            else -> emptyList()
        }
}

private fun SearchSource.path(accountId: String): String = when (this) {
    SearchSource.WORKERS -> cloudflarePath("accounts", accountId, "workers", "scripts")
    SearchSource.ZONES -> cloudflarePath("zones")
    SearchSource.PAGES -> cloudflarePath("accounts", accountId, "pages", "projects")
    SearchSource.R2 -> cloudflarePath("accounts", accountId, "r2", "buckets")
    SearchSource.D1 -> cloudflarePath("accounts", accountId, "d1", "database")
    SearchSource.KV -> cloudflarePath("accounts", accountId, "storage", "kv", "namespaces")
}

/** Only the account filter goes here. Paging parameters are added by the core pager, which owns per_page. */
private fun SearchSource.query(accountId: String): Map<String, String> = when (this) {
    SearchSource.ZONES -> mapOf(ZONE_ACCOUNT_FILTER to accountId)
    else -> emptyMap()
}
