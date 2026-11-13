package com.tomasps.virga.feature.d1.sql

import org.junit.Assert.assertEquals
import org.junit.Test

class StatementValuesTest {

    @Test
    fun `values are numbered in placeholder order`() {
        val statement = BoundStatement("INSERT INTO t (a, b) VALUES (?, ?)", listOf("x", "y"))

        assertEquals(listOf("1. \"x\"", "2. \"y\""), statementValueLines(statement))
    }

    @Test
    fun `an empty string, a comma, and the text NULL are each shown unambiguously`() {
        val statement = BoundStatement("UPDATE t SET a = ?, b = ?, c = ? WHERE id = ?", listOf("", "a, b", "NULL", "7"))

        assertEquals(
            listOf("1. \"\"", "2. \"a, b\"", "3. \"NULL\"", "4. \"7\""),
            statementValueLines(statement),
        )
    }

    @Test
    fun `quotes and backslashes inside a value are escaped so the line cannot be misread`() {
        val statement = BoundStatement("INSERT INTO t (a) VALUES (?)", listOf("say \"hi\" \\ bye"))

        assertEquals(listOf("1. \"say \\\"hi\\\" \\\\ bye\""), statementValueLines(statement))
    }

    @Test
    fun `a statement with no bound values has no value lines`() {
        assertEquals(emptyList<String>(), statementValueLines(BoundStatement("DELETE FROM t", emptyList())))
    }
}
