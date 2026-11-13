package com.tomasps.virga.feature.builds.api

import org.junit.Assert.assertEquals
import org.junit.Test

class LogStreamTest {

    @Test
    fun `a cursored read appends the new lines and keeps the cursor`() {
        val first = LogStream().applied(LogPage(listOf("a", "b"), cursor = "c2"), requestCursor = null)
        val second = first.applied(LogPage(listOf("c"), cursor = "c3"), requestCursor = "c2")

        assertEquals(listOf("a", "b"), first.lines)
        assertEquals(listOf("a", "b", "c"), second.lines)
        assertEquals("c3", second.cursor)
    }

    @Test
    fun `a read without a cursor replaces the log`() {
        val held = LogStream(listOf("a", "b"), cursor = "c2")

        val replaced = held.applied(LogPage(listOf("full"), cursor = "c1"), requestCursor = null)

        assertEquals(listOf("full"), replaced.lines)
        assertEquals("c1", replaced.cursor)
    }

    @Test
    fun `a cursored reply that omits the cursor drops its lines and forces a full read next time`() {
        val held = LogStream(listOf("a", "b"), cursor = "c2")

        val unsure = held.applied(LogPage(listOf("b", "c"), cursor = null), requestCursor = "c2")

        assertEquals(listOf("a", "b"), unsure.lines)
        assertEquals(null, unsure.cursor)
        val full = unsure.applied(LogPage(listOf("a", "b", "c"), cursor = "c3"), requestCursor = unsure.cursor)
        assertEquals(listOf("a", "b", "c"), full.lines)
        assertEquals("c3", full.cursor)
    }

    @Test
    fun `held lines are capped at the newest MAX_LOG_LINES`() {
        val many = (1..MAX_LOG_LINES + 5).map { "line $it" }
        val held = LogStream(lines = many.take(MAX_LOG_LINES), cursor = "c1")

        val capped = held.applied(LogPage(many.takeLast(5), cursor = "c2"), requestCursor = "c1")

        assertEquals(MAX_LOG_LINES, capped.lines.size)
        assertEquals("line ${MAX_LOG_LINES + 5}", capped.lines.last())
        assertEquals("line 6", capped.lines.first())
    }
}
