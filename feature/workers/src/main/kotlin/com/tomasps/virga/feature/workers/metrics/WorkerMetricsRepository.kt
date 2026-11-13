package com.tomasps.virga.feature.workers.metrics

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** Row limit for the invocations query. Kept in one place so the truncation check uses the same number. */
internal const val METRICS_ROW_LIMIT = 1000

/**
 * Worker invocation metrics through the GraphQL Analytics API (dataset workersInvocationsAdaptive, fields confirmed in
 * the reference). The API keeps about one month of data, so callers keep the window inside that.
 */
class WorkerMetricsRepository(private val api: CloudflareApi) {

    suspend fun load(accountId: String, scriptName: String, start: Instant, end: Instant): ApiResult<WorkerMetrics> {
        val variables = buildJsonObject {
            put("accountTag", accountId)
            put("scriptName", scriptName)
            put("datetimeStart", start.toString())
            put("datetimeEnd", end.toString())
        }
        return when (val result = api.graphQl.query(INVOCATIONS_QUERY, variables, JsonObject.serializer())) {
            is ApiResult.Success -> ApiResult.Success(WorkerMetricsParser.parse(result.value, rowLimit = METRICS_ROW_LIMIT))
            is ApiResult.Failure -> result
        }
    }

    private companion object {
        val INVOCATIONS_QUERY = """
            query GetWorkerInvocations(${'$'}accountTag: string, ${'$'}scriptName: string, ${'$'}datetimeStart: string, ${'$'}datetimeEnd: string) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  workersInvocationsAdaptive(limit: $METRICS_ROW_LIMIT, filter: {
                    scriptName: ${'$'}scriptName,
                    datetime_geq: ${'$'}datetimeStart,
                    datetime_leq: ${'$'}datetimeEnd
                  }) {
                    sum { requests errors subrequests }
                    quantiles { cpuTimeP50 cpuTimeP99 }
                    dimensions { datetime scriptName status }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
