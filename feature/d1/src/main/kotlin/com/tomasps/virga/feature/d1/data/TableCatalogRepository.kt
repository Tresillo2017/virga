package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult

/**
 * Reads a database's tables and their schemas through SQL. D1 has no schema endpoint, so this uses sqlite_master
 * and the table-valued pragma functions, with the table name bound as a parameter. Internal tables (sqlite_,
 * d1_, _cf_ prefixes) are excluded from the list.
 */
class TableCatalogRepository(private val sql: SqlRepository) {

    suspend fun tables(accountId: String, databaseId: String): ApiResult<List<String>> =
        when (val result = sql.run(accountId, databaseId, LIST_TABLES_SQL)) {
            is ApiResult.Success -> ApiResult.Success(
                rowsOf(result.value.columns, result.value.rows)
                    .mapNotNull { it.text("name") }
                    .filter { it.isNotEmpty() },
            )
            is ApiResult.Failure -> result
        }

    suspend fun schema(accountId: String, databaseId: String, table: String): ApiResult<TableSchema> {
        val columns = read(accountId, databaseId, COLUMNS_SQL, table, ::columnsFrom)
        val indexes = read(accountId, databaseId, INDEXES_SQL, table, ::indexesFrom)
        val foreignKeys = read(accountId, databaseId, FOREIGN_KEYS_SQL, table, ::foreignKeysFrom)
        val firstFailure = listOf(columns, indexes, foreignKeys).filterIsInstance<ApiResult.Failure>().firstOrNull()
        if (firstFailure != null) return firstFailure
        return ApiResult.Success(
            TableSchema(
                name = table,
                columns = (columns as ApiResult.Success).value,
                indexes = (indexes as ApiResult.Success).value,
                foreignKeys = (foreignKeys as ApiResult.Success).value,
            ),
        )
    }

    private suspend fun <T> read(
        accountId: String,
        databaseId: String,
        statement: String,
        table: String,
        parse: (List<RowView>) -> List<T>,
    ): ApiResult<List<T>> = when (val result = sql.run(accountId, databaseId, statement, listOf(table))) {
        is ApiResult.Success -> ApiResult.Success(parse(rowsOf(result.value.columns, result.value.rows)))
        is ApiResult.Failure -> result
    }

    private fun columnsFrom(rows: List<RowView>): List<ColumnInfo> = rows.mapNotNull { row ->
        val name = row.text("name") ?: return@mapNotNull null
        ColumnInfo(
            name = name,
            type = row.text("type").orEmpty(),
            notNull = row.flag("notnull"),
            defaultValue = row.text("dflt_value"),
            pkPosition = row.int("pk") ?: 0,
        )
    }

    /** Index rows come one per column; they are grouped back into one entry per index, in first-seen order. */
    private fun indexesFrom(rows: List<RowView>): List<IndexInfo> {
        val grouped = linkedMapOf<String, Pair<Boolean, MutableList<String>>>()
        rows.forEach { row ->
            val name = row.text("index_name") ?: return@forEach
            val entry = grouped.getOrPut(name) { row.flag("is_unique") to mutableListOf() }
            row.text("column_name")?.let { entry.second += it }
        }
        return grouped.map { (name, entry) -> IndexInfo(name = name, unique = entry.first, columns = entry.second.toList()) }
    }

    private fun foreignKeysFrom(rows: List<RowView>): List<ForeignKeyInfo> = rows.mapNotNull { row ->
        val from = row.text("from") ?: return@mapNotNull null
        val table = row.text("table") ?: return@mapNotNull null
        ForeignKeyInfo(fromColumn = from, table = table, toColumn = row.text("to"), onDelete = row.text("on_delete"))
    }

    private companion object {
        /** Prefix checks use substr, not LIKE: in LIKE an underscore matches any character and would hide user tables. */
        const val LIST_TABLES_SQL =
            "SELECT name FROM sqlite_master WHERE type = 'table' AND substr(name, 1, 7) <> 'sqlite_' " +
                "AND substr(name, 1, 3) <> 'd1_' AND substr(name, 1, 4) <> '_cf_' ORDER BY name"
        const val COLUMNS_SQL = "SELECT name, type, \"notnull\", dflt_value, pk FROM pragma_table_info(?)"
        const val INDEXES_SQL =
            "SELECT il.name AS index_name, il.\"unique\" AS is_unique, ii.name AS column_name " +
                "FROM pragma_index_list(?) AS il CROSS JOIN pragma_index_info(il.name) AS ii " +
                "ORDER BY il.seq, ii.seqno"
        const val FOREIGN_KEYS_SQL = "SELECT \"from\", \"table\", \"to\", on_delete FROM pragma_foreign_key_list(?)"
    }
}
