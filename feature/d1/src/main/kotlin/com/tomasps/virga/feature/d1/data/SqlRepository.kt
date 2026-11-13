package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/**
 * The first statement's columns and rows, plus the cost of the whole batch. A console batch can run several
 * statements; [statementCount] says how many ran, and the totals cover all of them. Only the first result is kept.
 */
data class SqlOutcome(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val rowsRead: Long,
    val rowsWritten: Long,
    val durationMs: Double?,
    val statementCount: Int = 1,
)

/**
 * Runs one parameterised statement through the raw endpoint, which returns rows as arrays in column order.
 * Every call is bounded by [D1Limits.REQUEST_LIMIT_MILLIS], the 30-second REST limit, so a stalled statement
 * fails with a local error instead of waiting on the 60-second transport cap.
 */
class SqlRepository(private val api: CloudflareApi) {

    suspend fun run(
        accountId: String,
        databaseId: String,
        sql: String,
        params: List<String> = emptyList(),
    ): ApiResult<SqlOutcome> {
        val path = cloudflarePath("accounts", accountId, "d1", "database", databaseId, "raw")
        val response = withTimeoutOrNull(D1Limits.REQUEST_LIMIT_MILLIS) {
            api.post<RawRequest, List<RawStatementResult>>(path, RawRequest(sql = sql, params = params))
        } ?: return ApiResult.Failure(ApiError.Local(D1Limits.REQUEST_LIMIT_REASON))
        return when (response) {
            is ApiResult.Success -> toOutcome(response.value)
            is ApiResult.Failure -> response
        }
    }

    private fun toOutcome(statements: List<RawStatementResult>): ApiResult<SqlOutcome> {
        val first = statements.firstOrNull()
            ?: return ApiResult.Failure(ApiError.Decode(NO_STATEMENT_RESULT))
        val rows = first.results
        val metas = statements.mapNotNull { it.meta }
        return ApiResult.Success(
            SqlOutcome(
                columns = rows?.columns.orEmpty(),
                rows = rows?.rows.orEmpty(),
                rowsRead = metas.sumOf { it.rowsRead?.toLong() ?: 0L },
                rowsWritten = metas.sumOf { it.rowsWritten?.toLong() ?: 0L },
                durationMs = metas.mapNotNull { it.timings?.sqlDurationMs }.takeIf { it.isNotEmpty() }?.sum(),
                statementCount = statements.size,
            ),
        )
    }

    private companion object {
        const val NO_STATEMENT_RESULT = "The database returned no statement result"
    }
}
