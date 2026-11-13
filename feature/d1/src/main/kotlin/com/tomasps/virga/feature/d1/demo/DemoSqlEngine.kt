package com.tomasps.virga.feature.d1.demo

import com.tomasps.virga.feature.d1.detail.tables.cellText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** What one demo statement returns: its columns, rows, the cost it reports, and the database after any write. */
internal data class DemoSqlResult(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val rowsWritten: Long = 0L,
    val updated: DemoDatabase? = null,
)

/** Raised for statements the demo does not run. The message is shown to the user as the database's own error. */
internal class DemoSqlUnsupported : RuntimeException()

/**
 * A small, honest SQL stand-in for demo mode. It runs the statements this app sends (catalog pragmas, paged
 * SELECTs, parameterised INSERT, UPDATE and DELETE by key) and plain SELECTs from demo tables. Anything else is
 * refused rather than faked.
 */
internal object DemoSqlEngine {

    fun run(database: DemoDatabase, sql: String, params: List<String>): DemoSqlResult {
        val text = sql.trim().trimEnd(';').trim()
        return when {
            SQLITE_MASTER.containsMatchIn(text) -> tableNames(database)
            PRAGMA_COLUMNS.containsMatchIn(text) -> columnsOf(database, params.firstOrNull())
            PRAGMA_INDEXES.containsMatchIn(text) -> indexesOf(database, params.firstOrNull())
            PRAGMA_FOREIGN_KEYS.containsMatchIn(text) -> foreignKeysOf(database, params.firstOrNull())
            text.startsWith("INSERT", ignoreCase = true) -> insert(database, text, params)
            text.startsWith("UPDATE", ignoreCase = true) -> update(database, text, params)
            text.startsWith("DELETE", ignoreCase = true) -> delete(database, text, params)
            text.startsWith("SELECT", ignoreCase = true) -> select(database, text)
            else -> throw DemoSqlUnsupported()
        }
    }

    private fun tableNames(database: DemoDatabase) = DemoSqlResult(
        columns = listOf("name"),
        rows = database.tables.keys.sorted().map { listOf(JsonPrimitive(it)) },
    )

    private fun columnsOf(database: DemoDatabase, table: String?): DemoSqlResult {
        val columns = database.tables[table]?.columns.orEmpty()
        return DemoSqlResult(
            columns = listOf("cid", "name", "type", "notnull", "dflt_value", "pk"),
            rows = columns.mapIndexed { index, c ->
                listOf(
                    JsonPrimitive(index),
                    JsonPrimitive(c.name),
                    JsonPrimitive(c.type),
                    JsonPrimitive(if (c.notNull) 1 else 0),
                    c.defaultValue?.let { JsonPrimitive(it) } ?: JsonNull,
                    JsonPrimitive(c.pkPosition),
                )
            },
        )
    }

    private fun indexesOf(database: DemoDatabase, table: String?): DemoSqlResult {
        val indexes = database.tables[table]?.indexes.orEmpty()
        return DemoSqlResult(
            columns = listOf("index_name", "is_unique", "column_name"),
            rows = indexes.flatMap { index ->
                index.columns.map { column ->
                    listOf(JsonPrimitive(index.name), JsonPrimitive(if (index.unique) 1 else 0), JsonPrimitive(column))
                }
            },
        )
    }

    private fun foreignKeysOf(database: DemoDatabase, table: String?): DemoSqlResult {
        val keys = database.tables[table]?.foreignKeys.orEmpty()
        return DemoSqlResult(
            columns = listOf("from", "table", "to", "on_delete"),
            rows = keys.map { listOf(JsonPrimitive(it.from), JsonPrimitive(it.table), JsonPrimitive(it.to), JsonPrimitive(it.onDelete)) },
        )
    }

    /** Only the paged form the app uses: SELECT * FROM "t" LIMIT n OFFSET m. Any other SELECT returns the whole table. */
    private fun select(database: DemoDatabase, text: String): DemoSqlResult {
        val table = tableIn(text) ?: throw DemoSqlUnsupported()
        val demo = database.tables[table] ?: throw DemoSqlUnsupported()
        val projected = projectionOf(text, demo)
        val matching = applyWhere(text, demo)
        val ordered = applyOrder(text, demo, matching)
        val page = PAGE.find(text)
        val offset = page?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val limit = page?.groupValues?.get(1)?.toIntOrNull() ?: ordered.size
        return DemoSqlResult(
            columns = projected.map { demo.columns[it].name },
            rows = ordered.drop(offset).take(limit).map { row -> projected.map { row[it] } },
        )
    }

    /** Column indexes named in the select list, or every column for `*`. Unknown names are refused. */
    private fun projectionOf(text: String, demo: DemoTable): List<Int> {
        val list = SELECT_LIST.find(text)?.groupValues?.get(1)?.trim() ?: throw DemoSqlUnsupported()
        if (list == "*") return demo.columns.indices.toList()
        return list.split(',').map { indexOfColumn(demo, unquote(it.trim())) }
    }

    /** Only a single `column = value` condition is supported. Any other predicate is refused, never ignored. */
    private fun applyWhere(text: String, demo: DemoTable): List<List<JsonElement>> {
        if (!WHERE_KEYWORD.containsMatchIn(text)) return demo.rows
        val clause = WHERE_EQUALS.find(text) ?: throw DemoSqlUnsupported()
        val rest = text.substring(clause.range.last + 1).trim()
        if (rest.isNotEmpty() && !REST_AFTER_WHERE.matches(rest)) throw DemoSqlUnsupported()
        val index = indexOfColumn(demo, unquote(clause.groupValues[1]))
        val expected = literalText(clause.groupValues[2])
        return demo.rows.filter { row -> cellText(row.getOrNull(index)) == expected }
    }

    /** ORDER BY terms, each a column or `rowid` (kept in stored order). Any term that is not understood is refused. */
    private fun applyOrder(text: String, demo: DemoTable, rows: List<List<JsonElement>>): List<List<JsonElement>> {
        if (!ORDER_KEYWORD.containsMatchIn(text)) return rows
        val clause = ORDER_CLAUSE.find(text) ?: throw DemoSqlUnsupported()
        val comparator = clause.groupValues[1].split(',')
            .map { orderTerm(it.trim(), demo) }
            .fold(Comparator<List<JsonElement>> { _, _ -> 0 }) { acc, term -> acc.thenComparing(term.comparator()) }
        return rows.sortedWith(comparator)
    }

    private fun orderTerm(term: String, demo: DemoTable): OrderTerm {
        val match = ORDER_TERM.matchEntire(term) ?: throw DemoSqlUnsupported()
        val name = unquote(match.groupValues[1])
        val descending = match.groupValues[2].equals("DESC", ignoreCase = true)
        return OrderTerm(if (name == ROWID) null else indexOfColumn(demo, name), descending)
    }

    private fun compareCells(left: String?, right: String?): Int {
        val leftNumber = left?.toDoubleOrNull()
        val rightNumber = right?.toDoubleOrNull()
        return if (leftNumber != null && rightNumber != null) {
            leftNumber.compareTo(rightNumber)
        } else {
            (left ?: "").compareTo(right ?: "")
        }
    }

    private data class OrderTerm(val index: Int?, val descending: Boolean) {
        fun comparator(): Comparator<List<JsonElement>> {
            val column = index ?: return Comparator { _, _ -> 0 }
            val ascending = Comparator<List<JsonElement>> { a, b ->
                compareCells(cellText(a.getOrNull(column)), cellText(b.getOrNull(column)))
            }
            return if (descending) ascending.reversed() else ascending
        }
    }

    private fun indexOfColumn(demo: DemoTable, name: String): Int =
        demo.columns.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: throw DemoSqlUnsupported()

    private fun unquote(text: String): String =
        if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) {
            text.substring(1, text.length - 1).replace("\"\"", "\"")
        } else {
            text
        }

    private fun literalText(token: String): String =
        if (token.startsWith("'")) token.substring(1, token.length - 1).replace("''", "'") else token

    private fun insert(database: DemoDatabase, text: String, params: List<String>): DemoSqlResult {
        val table = tableIn(text) ?: throw DemoSqlUnsupported()
        val demo = database.tables[table] ?: throw DemoSqlUnsupported()
        val columnNames = Regex("""\(([^()]*)\)\s*VALUES""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.let(::identifiers).orEmpty()
        val tokens = Regex("""VALUES\s*\((.*)\)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            ?.let(::splitOutsideQuotes)?.map { it.trim() }.orEmpty()
        val values = bindValues(tokens, params)
        val row = demo.columns.map { column ->
            val index = columnNames.indexOf(column.name)
            if (index < 0) JsonNull else values.getOrElse(index) { JsonNull }
        }
        val updated = database.copy(tables = database.tables + (table to demo.copy(rows = demo.rows + listOf(row))))
        return DemoSqlResult(columns = emptyList(), rows = emptyList(), rowsWritten = 1, updated = updated)
    }

    private fun update(database: DemoDatabase, text: String, params: List<String>): DemoSqlResult {
        val table = tableIn(text) ?: throw DemoSqlUnsupported()
        val demo = database.tables[table] ?: throw DemoSqlUnsupported()
        val setPart = Regex("""SET\s+(.*?)\s+WHERE\s+(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(text) ?: throw DemoSqlUnsupported()
        val assignments = assignmentsOf(setPart.groupValues[1])
        val conditions = assignmentsOf(setPart.groupValues[2].replace(" AND ", ",", ignoreCase = true))
        val setCount = assignments.count { it.second == PLACEHOLDER }
        val setValues = bindValues(assignments.map { it.second }, params.take(setCount))
        val whereValues = bindValues(conditions.map { it.second }, params.drop(setCount))
        val matches = demo.rows.map { row -> conditions.zip(whereValues).all { (c, v) -> matchesCell(demo, row, c.first, v) } }
        val changed = demo.rows.mapIndexed { index, row ->
            if (!matches[index]) row else demo.columns.mapIndexed { col, column ->
                val assignmentIndex = assignments.indexOfFirst { it.first == column.name }
                if (assignmentIndex < 0) row[col] else setValues[assignmentIndex]
            }
        }
        val count = matches.count { it }.toLong()
        return DemoSqlResult(
            columns = emptyList(),
            rows = emptyList(),
            rowsWritten = count,
            updated = database.copy(tables = database.tables + (table to demo.copy(rows = changed))),
        )
    }

    private fun delete(database: DemoDatabase, text: String, params: List<String>): DemoSqlResult {
        if (params.isEmpty()) return deleteLiteral(database, text)
        val table = tableIn(text) ?: throw DemoSqlUnsupported()
        val demo = database.tables[table] ?: throw DemoSqlUnsupported()
        val whereText = Regex("""WHERE\s+(.*)$""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: throw DemoSqlUnsupported()
        val conditions = assignmentsOf(whereText.replace(" AND ", ",", ignoreCase = true))
        val whereValues = bindValues(conditions.map { it.second }, params)
        val kept = demo.rows.filterNot { row -> conditions.zip(whereValues).all { (c, v) -> matchesCell(demo, row, c.first, v) } }
        val removed = (demo.rows.size - kept.size).toLong()
        return DemoSqlResult(
            columns = emptyList(),
            rows = emptyList(),
            rowsWritten = removed,
            updated = database.copy(tables = database.tables + (table to demo.copy(rows = kept))),
        )
    }

    /**
     * A console DELETE with literal values: `DELETE FROM t` removes every row, and `DELETE FROM t WHERE c = v` removes the
     * rows that single equality matches. Identical rows match the same predicate, so removing by set difference is exact.
     */
    private fun deleteLiteral(database: DemoDatabase, text: String): DemoSqlResult {
        val table = tableIn(text) ?: throw DemoSqlUnsupported()
        val demo = database.tables[table] ?: throw DemoSqlUnsupported()
        val kept = demo.rows - applyWhere(text, demo).toSet()
        return DemoSqlResult(
            columns = emptyList(),
            rows = emptyList(),
            rowsWritten = (demo.rows.size - kept.size).toLong(),
            updated = database.copy(tables = database.tables + (table to demo.copy(rows = kept))),
        )
    }

    private fun matchesCell(table: DemoTable, row: List<JsonElement>, column: String, expected: JsonElement): Boolean {
        val index = table.columns.indexOfFirst { it.name == column }
        return index >= 0 && cellText(row.getOrNull(index)) == cellText(expected)
    }

    /** Pairs each `"name" = ?` or `"name" = NULL` fragment with its value: a placeholder takes the next bound text. */
    private fun assignmentsOf(list: String): List<Pair<String, String>> = splitOutsideQuotes(list).mapNotNull { fragment ->
        val parts = fragment.split('=', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        identifiers(parts[0]).firstOrNull()?.let { it to parts[1].trim() }
    }

    /** Turns placeholder tokens and NULL into values, consuming bound parameters in order. */
    private fun bindValues(tokens: List<String>, params: List<String>): List<JsonElement> {
        var next = 0
        return tokens.map { token ->
            when {
                token == PLACEHOLDER -> JsonPrimitive(params.getOrElse(next++) { "" })
                else -> literalValue(token) ?: throw DemoSqlUnsupported()
            }
        }
    }

    /** A literal the demo can store: NULL, a single-quoted string, or a plain number. Anything else is not a value here. */
    private fun literalValue(token: String): JsonElement? = when {
        token.equals("NULL", ignoreCase = true) -> JsonNull
        STRING_LITERAL.matches(token) -> JsonPrimitive(literalText(token))
        NUMBER_LITERAL.matches(token) -> JsonPrimitive(token.toLongOrNull() ?: token.toDouble())
        else -> null
    }

    /** Splits on commas that sit outside single and double quotes, so a literal such as 'a, b' stays one value. */
    private fun splitOutsideQuotes(list: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        for (ch in list) {
            when {
                ch == '\'' && !inDouble -> inSingle = !inSingle
                ch == '"' && !inSingle -> inDouble = !inDouble
            }
            if (ch == ',' && !inSingle && !inDouble) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(ch)
            }
        }
        parts += current.toString()
        return parts
    }

    private fun identifiers(text: String): List<String> = QUOTED.findAll(text).map { it.groupValues[1].replace("\"\"", "\"") }
        .toList()
        .ifEmpty { text.split(',').map { it.trim() }.filter { it.isNotEmpty() } }

    private fun tableIn(text: String): String? = FROM_TABLE.nameIn(text)
        ?: INTO_TABLE.nameIn(text)
        ?: UPDATE_TABLE.nameIn(text)

    /** The name from a quoted (group 1) or bare (group 2) identifier match. */
    private fun Regex.nameIn(text: String): String? = find(text)?.let { match ->
        match.groupValues[1].ifEmpty { match.groupValues[2] }.replace("\"\"", "\"")
    }

    private const val PLACEHOLDER = "?"
    private val STRING_LITERAL = Regex("""'(?:[^']|'')*'""")
    private val NUMBER_LITERAL = Regex("""-?\d+(\.\d+)?""")
    private val SQLITE_MASTER = Regex("""FROM\s+sqlite_master""", RegexOption.IGNORE_CASE)
    private val PRAGMA_COLUMNS = Regex("""pragma_table_info""", RegexOption.IGNORE_CASE)
    private val PRAGMA_INDEXES = Regex("""pragma_index_list""", RegexOption.IGNORE_CASE)
    private val PRAGMA_FOREIGN_KEYS = Regex("""pragma_foreign_key_list""", RegexOption.IGNORE_CASE)
    private val PAGE = Regex("""LIMIT\s+(\d+)\s+OFFSET\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("\"((?:[^\"]|\"\")+)\"")
    private val FROM_TABLE = Regex("""FROM\s+(?:"((?:[^"]|"")+)"|(\w+))""", RegexOption.IGNORE_CASE)
    private val INTO_TABLE = Regex("""INTO\s+(?:"((?:[^"]|"")+)"|(\w+))""", RegexOption.IGNORE_CASE)
    private val UPDATE_TABLE = Regex("""UPDATE\s+(?:"((?:[^"]|"")+)"|(\w+))""", RegexOption.IGNORE_CASE)
    private val SELECT_LIST = Regex("""^SELECT\s+(.*?)\s+FROM\s""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val WHERE_KEYWORD = Regex("""\sWHERE\s""", RegexOption.IGNORE_CASE)
    private val WHERE_EQUALS = Regex("""WHERE\s+("(?:[^"]|"")+"|\w+)\s*=\s*('(?:[^']|'')*'|-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val REST_AFTER_WHERE = Regex("""(?i)(ORDER\s+BY.*|LIMIT\s+\d+(\s+OFFSET\s+\d+)?)""")
    private val ORDER_KEYWORD = Regex("""ORDER\s+BY""", RegexOption.IGNORE_CASE)
    private val ORDER_CLAUSE = Regex("""ORDER\s+BY\s+(.*?)(?:\s+LIMIT\s+\d+\s+OFFSET\s+\d+)?\s*$""", RegexOption.IGNORE_CASE)
    private val ORDER_TERM = Regex("""("(?:[^"]|"")+"|\w+)(?:\s+(ASC|DESC))?""", RegexOption.IGNORE_CASE)
    private const val ROWID = "rowid"
}
