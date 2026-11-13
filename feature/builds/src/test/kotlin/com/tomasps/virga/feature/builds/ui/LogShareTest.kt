package com.tomasps.virga.feature.builds.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LogShareTest {

    @Test
    fun `the whole log is shared when it fits`() {
        assertEquals("a\nb", logAsText(listOf("a", "b")))
    }

    @Test
    fun `when the log is too big the newest lines are kept and the oldest dropped`() {
        val lines = List(10) { "x".repeat(10) }

        val tail = logTailForShare(lines, budget = 35)

        assertEquals(3, tail.size)
    }

    @Test
    fun `an empty log shares nothing`() {
        assertEquals(emptyList<String>(), logTailForShare(emptyList()))
    }
}
