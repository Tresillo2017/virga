package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildTileModelTest {

    private val finished = testBuild(
        outcome = BuildOutcome.SUCCESS,
        stoppedOn = NOW.minusSeconds(2 * 3600),
    )

    @Test
    fun `tileMarksStaleAfterThirtyMinutes`() {
        assertFalse(tileFor(finished, NOW.minus(Duration.ofMinutes(30)), NOW).isStale)
        assertTrue(tileFor(finished, NOW.minus(Duration.ofMinutes(31)), NOW).isStale)
        assertTrue(tileFor(finished, null, NOW).isStale)
        assertTrue(tileFor(finished, NOW.minusSeconds(60), NOW, Duration.ofSeconds(30)).isStale)
    }

    @Test
    fun `finished build detail is age of stopped time`() {
        val tile = tileFor(finished, NOW, NOW)

        assertEquals(BuildTileModel("api", BuildVisualState.PASSED, "2h", false), tile)
    }

    @Test
    fun `finished build without stoppedOn falls back to createdOn`() {
        assertEquals("3h", tileFor(testBuild(outcome = BuildOutcome.FAIL), NOW, NOW).detail)
    }

    @Test
    fun `running build detail is age of started time`() {
        val running = testBuild(BuildStatus.RUNNING, startedOn = NOW.minusSeconds(5 * 60))

        val tile = tileFor(running, NOW, NOW)

        assertEquals("5m", tile.detail)
        assertEquals(BuildVisualState.RUNNING, tile.state)
    }

    @Test
    fun `null latest gives no builds tile`() {
        val tile = tileFor(null, NOW, NOW)

        assertEquals("No builds", tile.workerName)
        assertEquals(BuildVisualState.UNKNOWN, tile.state)
    }
}
