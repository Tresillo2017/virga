package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.network.CloudflareApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * One bucket's operation counts and stored size from the GraphQL Analytics API (the r2 operations and storage
 * adaptive datasets). Results are adaptive samples, so they are estimates.
 */
class R2MetricsRepository(private val api: CloudflareApi) {

    suspend fun bucketMetrics(accountId: String, bucketName: String, start: Instant, end: Instant): ApiResult<R2BucketMetrics> {
        val variables: JsonObject = buildJsonObject {
            put("accountTag", accountId)
            put("bucketName", bucketName)
            put("startDate", start.toString())
            put("endDate", end.toString())
        }
        return api.graphQl.query(R2_METRICS_QUERY, variables, R2GraphQlData.serializer())
            .map { data -> data.toBucketMetrics() }
    }
}

/** Parsed view of one bucket's metrics. [storage] is ordered by time. */
data class R2BucketMetrics(
    val operations: List<R2OperationCount>,
    val storage: List<R2StoragePoint>,
)

data class R2OperationCount(val action: String, val requests: Long)

data class R2StoragePoint(val datetime: String, val objectCount: Long, val payloadBytes: Long)

private const val MAX_GROUPS = 1000

private val R2_METRICS_QUERY = """
    query R2BucketMetrics(${'$'}accountTag: string!, ${'$'}startDate: Time!, ${'$'}endDate: Time!, ${'$'}bucketName: string!) {
      viewer {
        accounts(filter: { accountTag: ${'$'}accountTag }) {
          r2OperationsAdaptiveGroups(
            limit: $MAX_GROUPS
            filter: { datetime_geq: ${'$'}startDate, datetime_leq: ${'$'}endDate, bucketName: ${'$'}bucketName }
          ) {
            sum { requests }
            dimensions { actionType }
          }
          r2StorageAdaptiveGroups(
            limit: $MAX_GROUPS
            filter: { datetime_geq: ${'$'}startDate, datetime_leq: ${'$'}endDate, bucketName: ${'$'}bucketName }
            orderBy: [datetime_ASC]
          ) {
            max { objectCount payloadSize }
            dimensions { datetime }
          }
        }
      }
    }
"""

@Serializable
internal data class R2GraphQlData(val viewer: R2GraphQlViewer = R2GraphQlViewer())

@Serializable
internal data class R2GraphQlViewer(val accounts: List<R2GraphQlAccount> = emptyList())

@Serializable
internal data class R2GraphQlAccount(
    val r2OperationsAdaptiveGroups: List<R2OperationGroup> = emptyList(),
    val r2StorageAdaptiveGroups: List<R2StorageGroup> = emptyList(),
)

@Serializable
internal data class R2OperationGroup(val sum: R2RequestSum = R2RequestSum(), val dimensions: R2ActionDimensions = R2ActionDimensions())

@Serializable
internal data class R2RequestSum(val requests: Long = 0)

@Serializable
internal data class R2ActionDimensions(val actionType: String? = null)

@Serializable
internal data class R2StorageGroup(val max: R2StorageMax = R2StorageMax(), val dimensions: R2StorageDimensions = R2StorageDimensions())

@Serializable
internal data class R2StorageMax(val objectCount: Long = 0, val payloadSize: Long = 0)

@Serializable
internal data class R2StorageDimensions(val datetime: String? = null)

internal fun R2GraphQlData.toBucketMetrics(): R2BucketMetrics {
    val account = viewer.accounts.firstOrNull() ?: R2GraphQlAccount()
    return R2BucketMetrics(
        operations = account.r2OperationsAdaptiveGroups
            .groupBy { it.dimensions.actionType ?: UNKNOWN_ACTION }
            .map { (action, groups) -> R2OperationCount(action, groups.sumOf { it.sum.requests }) }
            .sortedByDescending { it.requests },
        storage = account.r2StorageAdaptiveGroups.map {
            R2StoragePoint(it.dimensions.datetime.orEmpty(), it.max.objectCount, it.max.payloadSize)
        },
    )
}

private const val UNKNOWN_ACTION = "unknown"
