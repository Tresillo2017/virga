package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.feature.builds.api.BuildStage
import com.tomasps.virga.feature.builds.testing.record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNotificationModelTest {

    @Test
    fun `each known outcome maps to its own final notification kind`() {
        assertEquals(FinalKind.SUCCEEDED, finalKindOf(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS)))
        assertEquals(FinalKind.FAILED, finalKindOf(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.FAIL)))
        assertEquals(FinalKind.CANCELED, finalKindOf(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.CANCELED)))
        assertEquals(FinalKind.SKIPPED, finalKindOf(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SKIPPED)))
    }

    @Test
    fun `a finished build with no known outcome reads as finished, not as a failure`() {
        assertEquals(FinalKind.FINISHED, finalKindOf(record(status = BuildStatus.STOPPED, outcome = null)))
        assertEquals(FinalKind.FINISHED, finalKindOf(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.UNKNOWN)))
    }

    @Test
    fun `segments fill with the stage and a finished build fills all four`() {
        assertEquals(0, completedSegments(BuildStage.QUEUED))
        assertEquals(0, completedSegments(null))
        assertEquals(1, completedSegments(BuildStage.INITIALIZING))
        assertEquals(2, completedSegments(BuildStage.RUNNING))
        assertEquals(WATCH_SEGMENT_COUNT, completedSegments(BuildStage.DONE))
    }

    @Test
    fun `the ongoing and result notification ids are stable per build and never collide`() {
        val build = "build-1"
        val other = "build-2"

        assertEquals(notificationIdFor(build), notificationIdFor(build))
        assertNotEquals(notificationIdFor(build), notificationIdFor(other))
        assertNotEquals(notificationIdFor(build), resultNotificationIdFor(build))
        assertTrue(resultNotificationIdFor(build) >= 50_000_000)
    }

    @Test
    fun `the cancel request code differs from the content intent id for the same build`() {
        assertNotEquals(notificationIdFor("build-1"), cancelRequestCodeFor("build-1"))
    }
}
