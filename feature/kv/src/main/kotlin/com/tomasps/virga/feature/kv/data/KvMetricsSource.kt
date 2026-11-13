package com.tomasps.virga.feature.kv.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.GraphQlClient
import com.tomasps.virga.core.network.demo.DemoMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** One day of KV requests for one action type (read, write, delete, list). */
data class KvDailyRequests(val date: String, val action: String, val requests: Long)

/** One day of storage: keys and bytes, the day's maximum. */
data class KvDailyStorage(val date: String, val keyCount: Long, val byteCount: Long)

data class KvMetrics(
    val operations: List<KvDailyRequests>,
    val storage: List<KvDailyStorage>,
)

/** Where metrics come from. The live implementation queries GraphQL; demo mode serves canned data. */
interface KvMetricsSource {
    suspend fun load(accountId: String, namespaceId: String, start: LocalDate, end: LocalDate): ApiResult<KvMetrics>
}

/** Picks the demo or the live source at call time, so the same screen works for both. */
class SwitchingKvMetricsSource(
    private val demoMode: DemoMode,
    private val live: KvMetricsSource,
    private val demo: KvMetricsSource,
) : KvMetricsSource {
    override suspend fun load(accountId: String, namespaceId: String, start: LocalDate, end: LocalDate) =
        if (demoMode.enabled.value) {
            demo.load(accountId, namespaceId, start, end)
        } else {
            live.load(accountId, namespaceId, start, end)
        }
}

/** Live analytics through the GraphQL Analytics API: kvOperationsAdaptiveGroups and kvStorageAdaptiveGroups. */
class GraphQlKvMetricsSource(private val graphQl: GraphQlClient) : KvMetricsSource {

    override suspend fun load(
        accountId: String,
        namespaceId: String,
        start: LocalDate,
        end: LocalDate,
    ): ApiResult<KvMetrics> {
        val variables = metricsVariables(accountId, namespaceId, start, end)
        val operations = when (val result = graphQl.query(OPERATIONS_QUERY, variables, GqlOperationsData.serializer())) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value.toDomain()
        }
        val storage = when (val result = graphQl.query(STORAGE_QUERY, variables, GqlStorageData.serializer())) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value.toDomain()
        }
        return ApiResult.Success(KvMetrics(operations = operations, storage = storage))
    }

    private fun metricsVariables(accountId: String, namespaceId: String, start: LocalDate, end: LocalDate) =
        buildJsonObject {
            put("accountTag", accountId)
            put("namespaceId", namespaceId)
            put("start", start.toString())
            put("end", end.toString())
        }

    private companion object {
        val OPERATIONS_QUERY = """
            query KvOperations(${'$'}accountTag: string!, ${'$'}namespaceId: string, ${'$'}start: Date, ${'$'}end: Date) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  kvOperationsAdaptiveGroups(
                    filter: { namespaceId: ${'$'}namespaceId, date_geq: ${'$'}start, date_leq: ${'$'}end }
                    limit: 10000
                    orderBy: [date_DESC]
                  ) {
                    sum { requests }
                    dimensions { date actionType }
                  }
                }
              }
            }
        """.trimIndent()

        val STORAGE_QUERY = """
            query KvStorage(${'$'}accountTag: string!, ${'$'}namespaceId: string, ${'$'}start: Date, ${'$'}end: Date) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  kvStorageAdaptiveGroups(
                    filter: { namespaceId: ${'$'}namespaceId, date_geq: ${'$'}start, date_leq: ${'$'}end }
                    limit: 10000
                    orderBy: [date_DESC]
                  ) {
                    max { keyCount byteCount }
                    dimensions { date }
                  }
                }
              }
            }
        """.trimIndent()
    }
}

@Serializable
private data class GqlOperationsData(val viewer: GqlOperationsViewer = GqlOperationsViewer()) {
    fun toDomain(): List<KvDailyRequests> = viewer.accounts.firstOrNull()?.groups.orEmpty().map {
        KvDailyRequests(it.dimensions.date.orEmpty(), it.dimensions.actionType.orEmpty(), it.sum.requests)
    }
}

@Serializable
private data class GqlOperationsViewer(val accounts: List<GqlOperationsAccount> = emptyList())

@Serializable
private data class GqlOperationsAccount(
    @SerialName("kvOperationsAdaptiveGroups") val groups: List<GqlOperationGroup> = emptyList(),
)

@Serializable
private data class GqlOperationGroup(
    val sum: GqlRequestSum = GqlRequestSum(),
    val dimensions: GqlOperationDimensions = GqlOperationDimensions(),
)

@Serializable
private data class GqlRequestSum(val requests: Long = 0)

@Serializable
private data class GqlOperationDimensions(val date: String? = null, val actionType: String? = null)

@Serializable
private data class GqlStorageData(val viewer: GqlStorageViewer = GqlStorageViewer()) {
    fun toDomain(): List<KvDailyStorage> = viewer.accounts.firstOrNull()?.groups.orEmpty().map {
        KvDailyStorage(it.dimensions.date.orEmpty(), it.max.keyCount, it.max.byteCount)
    }
}

@Serializable
private data class GqlStorageViewer(val accounts: List<GqlStorageAccount> = emptyList())

@Serializable
private data class GqlStorageAccount(
    @SerialName("kvStorageAdaptiveGroups") val groups: List<GqlStorageGroup> = emptyList(),
)

@Serializable
private data class GqlStorageGroup(
    val max: GqlStorageMax = GqlStorageMax(),
    val dimensions: GqlStorageDimensions = GqlStorageDimensions(),
)

@Serializable
private data class GqlStorageMax(val keyCount: Long = 0, val byteCount: Long = 0)

@Serializable
private data class GqlStorageDimensions(val date: String? = null)
