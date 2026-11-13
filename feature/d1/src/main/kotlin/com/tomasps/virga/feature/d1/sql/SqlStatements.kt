package com.tomasps.virga.feature.d1.sql

private const val QUOTE = '"'

/** A statement with `?` placeholders and the values bound to them, in order. Values are always strings for D1. */
data class BoundStatement(val sql: String, val params: List<String>)

/** One column and its value. A null [value] is written as the SQL literal NULL, never as a bound string. */
data class ColumnValue(val column: String, val value: String?)

/** Quotes an identifier for SQLite. Identifiers cannot be bound, so embedded quotes are doubled instead. */
fun quoteIdentifier(name: String): String = buildString {
    append(QUOTE)
    name.forEach { char ->
        if (char == QUOTE) append(QUOTE)
        append(char)
    }
    append(QUOTE)
}

/**
 * A page of a table, in a stable order so that LIMIT/OFFSET pages neither skip nor repeat rows. [orderBy] is the
 * primary key; a table without one is ordered by rowid, which is unique for every rowid table. Limit and offset
 * are integers this code created, so they are written as literals; identifiers are quoted. Nothing user-typed
 * reaches the SQL text here.
 */
fun selectPage(table: String, orderBy: List<String>, limit: Int, offset: Int): String {
    require(limit > 0 && offset >= 0) { "Page bounds out of range: limit=$limit offset=$offset" }
    val order = if (orderBy.isEmpty()) "rowid" else orderBy.joinToString(", ") { quoteIdentifier(it) }
    return "SELECT * FROM ${quoteIdentifier(table)} ORDER BY $order LIMIT $limit OFFSET $offset"
}

fun insertStatement(table: String, values: List<ColumnValue>): BoundStatement {
    val target = quoteIdentifier(table)
    if (values.isEmpty()) return BoundStatement("INSERT INTO $target DEFAULT VALUES", emptyList())
    val columns = values.joinToString(", ") { quoteIdentifier(it.column) }
    val placeholders = values.joinToString(", ") { if (it.value == null) "NULL" else "?" }
    return BoundStatement(
        sql = "INSERT INTO $target ($columns) VALUES ($placeholders)",
        params = values.mapNotNull { it.value },
    )
}

/** Refuses an empty SET or an empty key, so an update can never touch every row. */
fun updateStatement(table: String, setValues: List<ColumnValue>, where: List<ColumnValue>): BoundStatement {
    require(setValues.isNotEmpty()) { "An update needs at least one changed column" }
    require(where.isNotEmpty()) { "An update needs a key, so it cannot touch every row" }
    val assignments = setValues.joinToString(", ") { assignment(it) }
    val condition = conditionOf(where)
    return BoundStatement(
        sql = "UPDATE ${quoteIdentifier(table)} SET $assignments WHERE $condition",
        params = setValues.mapNotNull { it.value } + where.mapNotNull { it.value },
    )
}

/** Refuses an empty key, so a delete can never remove every row. */
fun deleteStatement(table: String, where: List<ColumnValue>): BoundStatement {
    require(where.isNotEmpty()) { "A delete needs a key, so it cannot remove every row" }
    return BoundStatement(
        sql = "DELETE FROM ${quoteIdentifier(table)} WHERE ${conditionOf(where)}",
        params = where.mapNotNull { it.value },
    )
}

private fun assignment(value: ColumnValue): String =
    "${quoteIdentifier(value.column)} = ${if (value.value == null) "NULL" else "?"}"

/** Keys are compared with `=`, which never matches NULL, so a null key is refused rather than silently ignored. */
private fun conditionOf(where: List<ColumnValue>): String {
    require(where.all { it.value != null }) { "A key column cannot be NULL" }
    return where.joinToString(" AND ") { "${quoteIdentifier(it.column)} = ?" }
}
