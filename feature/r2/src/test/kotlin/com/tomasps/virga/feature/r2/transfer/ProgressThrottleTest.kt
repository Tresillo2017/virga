package com.tomasps.virga.feature.r2.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {

    @Test
    fun `small chunks are not reported until a quarter megabyte more has been copied`() {
        // Arrange
        val throttle = ProgressThrottle(totalBytes = null)
        val chunk = 64L * 1024L

        // Act
        val reports = (1..4).map { throttle.shouldReport(it * chunk) }

        // Assert
        assertEquals(listOf(false, false, false, true), reports)
    }

    @Test
    fun `reaching the known total is always reported`() {
        // Arrange
        val throttle = ProgressThrottle(totalBytes = 1_000L)

        // Act
        val atEnd = throttle.shouldReport(1_000L)

        // Assert
        assertTrue(atEnd)
        assertFalse(ProgressThrottle(totalBytes = 1_000L).shouldReport(10L))
    }
}
