package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** One day of D1 analytics for one database. Dates are UTC; the datasets are daily, not hourly. */
data class DailyPoint(
    val date: String,
    val readQueries: Double,
    val writeQueries: Double,
    val rowsRead: Double,
    val rowsWritten: Double,
    val p90LatencyMs: Double?,
)

/**
 * D1 analytics through GraphQL. The daily series reads d1AnalyticsAdaptiveGroups; its dataset and field names
 * (readQueries, writeQueries, rowsRead, rowsWritten) are in the D1 metrics reference. Storage reads
 * d1StorageAdaptiveGroups with databaseSizeBytes; the dataset and field are in the reference, but the `max`
 * aggregation is not stated there. It is a separate query so a mismatch cannot take the rest of the screen down.
 */
class MetricsRepository(private val api: CloudflareApi) {

    suspend fun daily(accountId: String, databaseId: String, start: LocalDate, end: LocalDate): ApiResult<List<DailyPoint>> {
        val variables = variables(accountId, databaseId, start, end)
        return when (val result = api.graphQl.query(DAILY_QUERY, variables, JsonObject.serializer())) {
            is ApiResult.Success -> ApiResult.Success(dailyPointsFrom(result.value))
            is ApiResult.Failure -> result
        }
    }

    /** Largest database size seen in the window, in bytes. */
    suspend fun storageBytes(accountId: String, databaseId: String, start: LocalDate, end: LocalDate): ApiResult<Long> {
        val variables = variables(accountId, databaseId, start, end)
        return when (val result = api.graphQl.query(STORAGE_QUERY, variables, JsonObject.serializer())) {
            is ApiResult.Success -> ApiResult.Success(largestStorage(result.value))
            is ApiResult.Failure -> result
        }
    }

    private fun variables(accountId: String, databaseId: String, start: LocalDate, end: LocalDate): JsonObject =
        buildJsonObject {
            put("accountTag", accountId)
            put("databaseId", databaseId)
            put("start", start.toString())
            put("end", end.toString())
        }

    private fun dailyPointsFrom(root: JsonObject): List<DailyPoint> = groups(root, DAILY_DATASET).mapNotNull { group ->
        val date = (group.obj("dimensions")?.get("date") as? JsonPrimitive)?.content ?: return@mapNotNull null
        val sum = group.obj("sum")
        DailyPoint(
            date = date,
            readQueries = sum.number("readQueries"),
            writeQueries = sum.number("writeQueries"),
            rowsRead = sum.number("rowsRead"),
            rowsWritten = sum.number("rowsWritten"),
            p90LatencyMs = group.obj("quantiles")?.get("queryBatchTimeMsP90")?.let(::numberOf),
        )
    }.sortedBy { it.date }

    private fun largestStorage(root: JsonObject): Long = groups(root, STORAGE_DATASET)
        .maxOfOrNull { group -> group.obj("max").number("databaseSizeBytes").toLong() } ?: 0L

    /** Walks viewer.accounts[0].[dataset] in the unwrapped data object. A missing branch is empty, never a crash. */
    private fun groups(root: JsonObject, dataset: String): List<JsonObject> {
        val account = (root.obj("viewer")?.get("accounts") as? JsonArray)?.firstOrNull() as? JsonObject
        return (account?.get(dataset) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject?.number(key: String): Double = this?.get(key)?.let(::numberOf) ?: 0.0

    private fun numberOf(element: JsonElement): Double? =
        (element as? JsonPrimitive)?.content?.toDoubleOrNull()

    private companion object {
        const val DAILY_DATASET = "d1AnalyticsAdaptiveGroups"
        const val STORAGE_DATASET = "d1StorageAdaptiveGroups"

        val DAILY_QUERY = """
            query D1Daily(${'$'}accountTag: string!, ${'$'}databaseId: string, ${'$'}start: Date, ${'$'}end: Date) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  d1AnalyticsAdaptiveGroups(
                    limit: 10000
                    filter: { date_geq: ${'$'}start, date_leq: ${'$'}end, databaseId: ${'$'}databaseId }
                  ) {
                    sum { readQueries writeQueries rowsRead rowsWritten }
                    quantiles { queryBatchTimeMsP90 }
                    dimensions { date }
                  }
                }
              }
            }
        """.trimIndent()

        val STORAGE_QUERY = """
            query D1Storage(${'$'}accountTag: string!, ${'$'}databaseId: string, ${'$'}start: Date, ${'$'}end: Date) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  d1StorageAdaptiveGroups(
                    limit: 10000
                    filter: { date_geq: ${'$'}start, date_leq: ${'$'}end, databaseId: ${'$'}databaseId }
                  ) {
                    max { databaseSizeBytes }
                    dimensions { date }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
