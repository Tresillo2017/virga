package com.tomasps.virga.feature.builds.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchServiceStopPolicyTest {

    @Test
    fun `service may stop only when nothing is watched`() {
        // Arrange
        val idle = 0
        val oneWatch = 1

        // Act and Assert
        assertTrue(WatchServiceStopPolicy.mayStop(idle))
        assertFalse(WatchServiceStopPolicy.mayStop(oneWatch))
    }

    @Test
    fun `destroy after a deliberate stop keeps watches active`() {
        // Arrange: a start that raced the stop may have added a watch

        // Act
        val pauses = WatchServiceStopPolicy.shouldPauseOnDestroy(deliberateStop = true, activeWatches = 1)

        // Assert
        assertFalse(pauses)
    }

    @Test
    fun `destroy by Android pauses the watches that were still active`() {
        // Arrange

        // Act
        val pauses = WatchServiceStopPolicy.shouldPauseOnDestroy(deliberateStop = false, activeWatches = 2)

        // Assert
        assertTrue(pauses)
    }

    @Test
    fun `destroy by Android with nothing active does not pause anything`() {
        // Arrange

        // Act
        val pauses = WatchServiceStopPolicy.shouldPauseOnDestroy(deliberateStop = false, activeWatches = 0)

        // Assert
        assertFalse(pauses)
    }

    @Test
    fun `loop stops at once when the last watch has finished, without waiting for another tick`() {
        // Arrange: the engine reports no due watch once the last one is gone

        // Act
        val step = WatchServiceStopPolicy.nextLoopStep(activeWatches = 0, nextDueInMillis = null)

        // Assert
        assertEquals(WatchLoopStep.Stop, step)
    }

    @Test
    fun `loop stops at once even when a stale due time is still reported`() {
        // Arrange

        // Act
        val step = WatchServiceStopPolicy.nextLoopStep(activeWatches = 0, nextDueInMillis = 4_000L)

        // Assert
        assertEquals(WatchLoopStep.Stop, step)
    }

    @Test
    fun `loop sleeps until the next due poll while a watch is active`() {
        // Arrange

        // Act
        val step = WatchServiceStopPolicy.nextLoopStep(activeWatches = 1, nextDueInMillis = 3_000L)

        // Assert
        assertEquals(WatchLoopStep.Sleep(3_000L), step)
    }

    @Test
    fun `loop sleep is clamped to the loop bounds`() {
        // Arrange

        // Act
        val step = WatchServiceStopPolicy.nextLoopStep(activeWatches = 2, nextDueInMillis = 60_000L)

        // Assert
        assertEquals(WatchLoopStep.Sleep(WatchPolicy.MAX_LOOP_DELAY_MS), step)
    }
}
