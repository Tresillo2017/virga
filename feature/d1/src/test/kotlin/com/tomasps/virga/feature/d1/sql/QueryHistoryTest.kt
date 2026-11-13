package com.tomasps.virga.feature.d1.sql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryHistoryTest {

    @Test
    fun `newest query goes first`() {
        val history = QueryHistory.add(listOf("SELECT 1"), "SELECT 2")
        assertEquals(listOf("SELECT 2", "SELECT 1"), history)
    }

    @Test
    fun `a repeated query moves to the top instead of appearing twice`() {
        val history = QueryHistory.add(listOf("SELECT 2", "SELECT 1"), "SELECT 1")
        assertEquals(listOf("SELECT 1", "SELECT 2"), history)
    }

    @Test
    fun `blank queries are ignored and text is trimmed`() {
        val history = QueryHistory.add(listOf("SELECT 1"), "   ")
        assertEquals(listOf("SELECT 1"), history)
        assertEquals(listOf("SELECT 2"), QueryHistory.add(emptyList(), "  SELECT 2  "))
    }

    @Test
    fun `history is capped at the maximum, dropping the oldest`() {
        // Arrange
        var history = emptyList<String>()
        repeat(QueryHistory.MAX_ENTRIES + 5) { index -> history = QueryHistory.add(history, "SELECT $index") }

        // Act / Assert
        assertEquals(QueryHistory.MAX_ENTRIES, history.size)
        assertEquals("SELECT ${QueryHistory.MAX_ENTRIES + 4}", history.first())
    }

    @Test
    fun `encoded history round-trips`() {
        val history = listOf("SELECT \"a\"", "DELETE FROM t WHERE id = 1")
        assertEquals(history, QueryHistory.decode(QueryHistory.encode(history)))
    }

    @Test
    fun `corrupt or missing stored history decodes to empty instead of failing`() {
        assertEquals(emptyList<String>(), QueryHistory.decode("not json"))
        assertEquals(emptyList<String>(), QueryHistory.decode(null))
    }

    @Test
    fun `stored history with repeated entries decodes without duplicates`() {
        // Older builds could store the same text twice; the list keys by text, so duplicates must not reach the screen.
        val stored = QueryHistory.encode(listOf("SELECT 1", "SELECT 2", "SELECT 1"))

        assertEquals(listOf("SELECT 1", "SELECT 2"), QueryHistory.decode(stored))
    }

    @Test
    fun `a multi-line statement is stored exactly as it ran, one entry`() {
        val statement = "SELECT id,\n  name\nFROM customers"

        assertEquals(listOf(statement), QueryHistory.add(emptyList(), statement + "\n\n"))
    }

    @Test
    fun `headline collapses whitespace and line breaks into one clean line`() {
        assertEquals("SELECT id, name FROM customers", QueryHistory.headline("SELECT id,\n  name\n\tFROM customers"))
    }

    @Test
    fun `headline of a long statement ends with an ellipsis within the limit`() {
        val headline = QueryHistory.headline("SELECT " + "column_name, ".repeat(20) + "x FROM t", maxChars = 30)

        assertEquals(30, headline.length)
        assertTrue(headline.endsWith("\u2026"))
    }

    @Test
    fun `headline of a short statement is unchanged`() {
        assertEquals("DELETE FROM t", QueryHistory.headline("DELETE FROM t"))
    }
}
