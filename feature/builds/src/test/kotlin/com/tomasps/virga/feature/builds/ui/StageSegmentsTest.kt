package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.feature.builds.api.BuildStage
import com.tomasps.virga.feature.builds.api.StageTiming
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class StageSegmentsTest {

    private val started = Duration.ofSeconds(5)

    private fun timings(vararg elapsed: Duration?): List<StageTiming> =
        BuildStage.entries.zip(elapsed.toList()) { stage, time -> StageTiming(stage, time) }

    @Test
    fun `a running build is done up to the stage that has started, then current`() {
        // Arrange
        val timings = timings(started, started, Duration.ofSeconds(9), null)

        // Act
        val segments = stageSegments(timings, isTerminal = false)

        // Assert
        assertEquals(
            listOf(StageSegment.DONE, StageSegment.DONE, StageSegment.CURRENT, StageSegment.PENDING),
            segments,
        )
    }

    @Test
    fun `a build that has not started any stage has every segment pending`() {
        // Arrange
        val timings = timings(null, null, null, null)

        // Act
        val segments = stageSegments(timings, isTerminal = false)

        // Assert
        assertEquals(List(4) { StageSegment.PENDING }, segments)
    }

    @Test
    fun `a finished build marks every stage done`() {
        // Arrange
        val timings = timings(started, started, started, Duration.ofSeconds(20))

        // Act
        val segments = stageSegments(timings, isTerminal = true)

        // Assert
        assertEquals(List(4) { StageSegment.DONE }, segments)
    }
}
