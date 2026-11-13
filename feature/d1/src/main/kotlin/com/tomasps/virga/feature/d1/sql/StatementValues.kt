package com.tomasps.virga.feature.d1.sql

/**
 * The bound values of a statement, numbered in the order the placeholders take them, each in double quotes. Quoting
 * keeps an empty string, a value with a comma, and the text NULL visibly different from each other. SQL NULL is not
 * bound at all: it appears in the statement text as the keyword NULL, so it never shows up here.
 */
fun statementValueLines(statement: BoundStatement): List<String> = statement.params.mapIndexed { index, value ->
    "${index + 1}. ${quoteValue(value)}"
}

private fun quoteValue(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
