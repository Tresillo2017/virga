package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildStatusTextTest {

    @Test
    fun `runningMapsFromAllActiveStatuses`() {
        listOf(BuildStatus.QUEUED, BuildStatus.INITIALIZING, BuildStatus.RUNNING).forEach {
            assertEquals(BuildVisualState.RUNNING, testBuild(status = it).visualState())
        }
    }

    @Test
    fun `finished outcomes map to passed failed and canceled`() {
        fun state(o: BuildOutcome?) = testBuild(outcome = o).visualState()
        assertEquals(BuildVisualState.PASSED, state(BuildOutcome.SUCCESS))
        assertEquals(BuildVisualState.FAILED, state(BuildOutcome.FAIL))
        assertEquals(BuildVisualState.CANCELED, state(BuildOutcome.CANCELED))
        assertEquals(BuildVisualState.CANCELED, state(BuildOutcome.SKIPPED))
    }

    @Test
    fun `unknownOutcomeIsUnknown`() {
        assertEquals(BuildVisualState.UNKNOWN, testBuild(outcome = BuildOutcome.UNKNOWN).visualState())
        assertEquals(BuildVisualState.UNKNOWN, testBuild(outcome = null).visualState())
        assertEquals(BuildVisualState.UNKNOWN, testBuild(status = BuildStatus.UNKNOWN).visualState())
    }
}
