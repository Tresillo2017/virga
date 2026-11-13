package com.tomasps.virga.feature.kv.value

import org.junit.Assert.assertEquals
import org.junit.Test

class KvWriteThrottleTest {

    @Test
    fun `a key written just now must wait the rest of one second`() {
        // Arrange
        var now = 10_000L
        val throttle = KvWriteThrottle(clock = { now })
        throttle.markWritten("ns", "k")

        // Act
        now = 10_300L
        val remaining = throttle.remainingMillis("ns", "k")

        // Assert
        assertEquals(700L, remaining)
    }

    @Test
    fun `writes to other keys or namespaces are not blocked`() {
        // Arrange
        val throttle = KvWriteThrottle(clock = { 5_000L })
        throttle.markWritten("ns", "k")

        // Act and Assert
        assertEquals(0L, throttle.remainingMillis("ns", "other"))
        assertEquals(0L, throttle.remainingMillis("ns2", "k"))
    }

    @Test
    fun `after one second the key is free again`() {
        // Arrange
        var now = 0L
        val throttle = KvWriteThrottle(clock = { now })
        throttle.markWritten("ns", "k")

        // Act
        now = 1_000L

        // Assert
        assertEquals(0L, throttle.remainingMillis("ns", "k"))
    }
}
