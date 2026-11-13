package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.d1.sql.BoundStatement
import com.tomasps.virga.feature.d1.sql.selectPage
import kotlinx.serialization.json.JsonElement

/** One page of table rows. [hasMore] is true when at least one more row exists after this page. */
data class RowPage(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val hasMore: Boolean,
)

/**
 * Browsing and editing a table's rows. Browsing is a LIMIT/OFFSET page. Edits arrive as [BoundStatement]s built
 * by the sql package, so the UI can show the exact statement before it runs, and this class only executes it.
 */
class TableRowsRepository(private val sql: SqlRepository) {

    /** [orderBy] is the table's primary key, so pages are stable. Empty means rowid order. */
    suspend fun page(
        accountId: String,
        databaseId: String,
        table: String,
        offset: Int,
        pageRows: Int,
        orderBy: List<String> = emptyList(),
    ): ApiResult<RowPage> {
        val size = pageRows.coerceIn(1, D1Limits.MAX_GRID_PAGE_ROWS)
        // One extra row tells us whether another page exists without a separate COUNT(*) scan.
        val statement = selectPage(table = table, orderBy = orderBy, limit = size + 1, offset = offset)
        return when (val result = sql.run(accountId, databaseId, statement)) {
            is ApiResult.Success -> ApiResult.Success(
                RowPage(
                    columns = result.value.columns,
                    rows = result.value.rows.take(size),
                    hasMore = result.value.rows.size > size,
                ),
            )
            is ApiResult.Failure -> result
        }
    }

    suspend fun execute(accountId: String, databaseId: String, statement: BoundStatement): ApiResult<SqlOutcome> =
        sql.run(accountId, databaseId, statement.sql, statement.params)
}
