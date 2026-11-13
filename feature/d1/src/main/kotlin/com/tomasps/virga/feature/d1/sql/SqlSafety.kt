package com.tomasps.virga.feature.d1.sql

enum class RiskKind { DROP, ALTER, DELETE_WITHOUT_WHERE, UPDATE_WITHOUT_WHERE }

/** One statement from a console batch that needs confirmation, as written, with the reasons it was flagged. */
data class StatementRisk(val statement: String, val kinds: Set<RiskKind>)

/**
 * Finds statements that deserve a confirmation before they run: DROP, ALTER, and DELETE or UPDATE with no WHERE.
 * This is a token-level check, not a SQL parser. Statements are split on semicolons that sit outside quotes and
 * comments. Each piece is then sanitized (comments and quoted text removed) before its keywords are read, so a
 * keyword inside a string or identifier does not trigger it. A WITH clause is flagged when it contains DELETE or
 * UPDATE without WHERE.
 */
object SqlSafety {

    fun risks(sql: String): List<StatementRisk> = splitStatements(sql).mapNotNull { statement ->
        val kinds = kindsOf(sanitize(statement))
        if (kinds.isEmpty()) null else StatementRisk(statement = statement, kinds = kinds)
    }

    private fun kindsOf(cleaned: String): Set<RiskKind> {
        val words = topLevelWords(cleaned)
        if (words.isEmpty()) return emptySet()
        val kinds = mutableSetOf<RiskKind>()
        when (words.first()) {
            "DROP" -> kinds += RiskKind.DROP
            "ALTER" -> kinds += RiskKind.ALTER
        }
        val verbs = if (words.first() == "WITH") words.drop(1) else words.take(1)
        val hasWhere = "WHERE" in words
        if ("DELETE" in verbs && !hasWhere) kinds += RiskKind.DELETE_WITHOUT_WHERE
        if ("UPDATE" in verbs && !hasWhere) kinds += RiskKind.UPDATE_WITHOUT_WHERE
        return kinds
    }

    /**
     * The keywords outside every parenthesis, in order. A WHERE inside a subquery or a CTE body belongs to that
     * inner query, so it must not make the outer DELETE or UPDATE look keyed.
     */
    private fun topLevelWords(cleaned: String): List<String> {
        val words = mutableListOf<String>()
        var depth = 0
        var position = 0
        WORD.findAll(cleaned).forEach { match ->
            depth = (depth + parenthesisDelta(cleaned, position, match.range.first)).coerceAtLeast(0)
            if (depth == 0) words += match.value.uppercase()
            position = match.range.last + 1
        }
        return words
    }

    private fun parenthesisDelta(text: String, from: Int, to: Int): Int {
        var delta = 0
        for (index in from until to) {
            when (text[index]) {
                '(' -> delta++
                ')' -> delta--
            }
        }
        return delta
    }

    /**
     * True when any statement could change data. Only SELECT, PRAGMA and EXPLAIN are read-only. Used to pick the
     * wording of a failure: a timed-out write may still have been applied.
     */
    fun mayWrite(sql: String): Boolean = splitStatements(sql).any { statement ->
        val first = WORD.find(sanitize(statement))?.value?.uppercase()
        first !in READ_ONLY_VERBS
    }

    /**
     * Replaces comments and quoted text with neutral placeholders, in one left-to-right pass. A pass per pattern would
     * let a comment marker inside a string literal start a comment that eats the literal's closing quote, or let a
     * keyword inside a literal reach the keyword check. Used for analysis only, never for display.
     */
    private fun sanitize(statement: String): String {
        val cleaned = StringBuilder()
        var index = 0
        while (index < statement.length) {
            val end = skipQuotedOrComment(statement, index)
            if (end == index) {
                cleaned.append(statement[index])
                index++
            } else {
                cleaned.append(placeholderFor(statement[index]))
                index = end
            }
        }
        return cleaned.toString()
    }

    private fun placeholderFor(start: Char): String = when (start) {
        '\'' -> "''"
        '"', '`', '[' -> "X"
        else -> " "
    }

    /** Splits on semicolons that are outside quotes and comments. Empty pieces are dropped. */
    internal fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < sql.length) {
            val end = skipQuotedOrComment(sql, index)
            if (end > index) {
                current.append(sql, index, end)
                index = end
                continue
            }
            val char = sql[index]
            if (char == ';') {
                flush(current, statements)
            } else {
                current.append(char)
            }
            index++
        }
        flush(current, statements)
        return statements
    }

    private fun flush(current: StringBuilder, statements: MutableList<String>) {
        val text = current.toString().trim()
        if (text.isNotEmpty()) statements += text
        current.setLength(0)
    }

    /** If a quoted region or comment starts at [start], returns the index just past it; otherwise [start]. */
    private fun skipQuotedOrComment(sql: String, start: Int): Int {
        if (start >= sql.length) return start
        val char = sql[start]
        return when {
            char == '\'' || char == '"' || char == '`' -> closingIndex(sql, start, char)
            char == '[' -> sql.indexOf(']', start).let { if (it < 0) sql.length else it + 1 }
            sql.startsWith("--", start) -> sql.indexOf('\n', start).let { if (it < 0) sql.length else it + 1 }
            sql.startsWith("/*", start) -> sql.indexOf("*/", start + 2).let { if (it < 0) sql.length else it + 2 }
            else -> start
        }
    }

    /** Index just past the closing quote. A doubled quote inside a region is an escaped quote, not the end. */
    private fun closingIndex(sql: String, start: Int, quote: Char): Int {
        var index = start + 1
        while (index < sql.length) {
            if (sql[index] == quote) {
                if (index + 1 < sql.length && sql[index + 1] == quote) {
                    index += 2
                    continue
                }
                return index + 1
            }
            index++
        }
        return sql.length
    }

    private val READ_ONLY_VERBS = setOf("SELECT", "PRAGMA", "EXPLAIN")
    private val WORD = Regex("[A-Za-z_]+")
}
