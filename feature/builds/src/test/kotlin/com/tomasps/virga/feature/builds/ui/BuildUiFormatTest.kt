package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.formatDuration
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildStage
import com.tomasps.virga.feature.builds.testing.T0
import com.tomasps.virga.feature.builds.testing.record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildUiFormatTest {

    @Test
    fun `an active build shows the pending tone whatever stage it is in`() {
        assertEquals(StatusTone.Pending, record(status = BuildStatus.QUEUED).statusTone())
        assertEquals(StatusTone.Pending, record(status = BuildStatus.RUNNING).statusTone())
    }

    @Test
    fun `a finished build maps its outcome to a success, error or neutral tone`() {
        assertEquals(StatusTone.Success, record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS).statusTone())
        assertEquals(StatusTone.Error, record(status = BuildStatus.STOPPED, outcome = BuildOutcome.FAIL).statusTone())
        assertEquals(StatusTone.Neutral, record(status = BuildStatus.STOPPED, outcome = BuildOutcome.CANCELED).statusTone())
    }

    @Test
    fun `an in-progress build reads its stage label, not an outcome`() {
        assertEquals(R.string.builds_stage_queued, statusLabelRes(record(status = BuildStatus.QUEUED)))
        assertEquals(R.string.builds_stage_running, statusLabelRes(record(status = BuildStatus.RUNNING)))
    }

    @Test
    fun `a finished build reads its outcome label`() {
        assertEquals(R.string.builds_outcome_fail, statusLabelRes(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.FAIL)))
        assertEquals(R.string.builds_outcome_skipped, statusLabelRes(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SKIPPED)))
        assertEquals(R.string.builds_outcome_finished, statusLabelRes(record(status = BuildStatus.STOPPED, outcome = BuildOutcome.UNKNOWN)))
    }

    @Test
    fun `every stage has its own label`() {
        val labels = BuildStage.entries.map(::stageLabelRes)

        assertEquals(BuildStage.entries.size, labels.toSet().size)
    }

    @Test
    fun `the short id is the first eight characters and short ids keep short input whole`() {
        assertEquals("3f2a9c1b", shortBuildId("3f2a9c1b-4d5e-6f70-8192-a3b4c5d6e7f8"))
        assertEquals("abc", shortBuildId("abc"))
    }

    @Test
    fun `a build with no uuid has no short id to show, so the detail header omits the id`() {
        assertNull(shortBuildIdOrNull(""))
    }

    @Test
    fun `a build with a uuid shows its first eight characters as the short id`() {
        assertEquals("0a1b2c3d", shortBuildIdOrNull("0a1b2c3d-9999-4444-8888-000000000000"))
    }

    @Test
    fun `duration is shown only once the build has stopped and counts from its first started stage`() {
        val stopped = record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS, created = T0)

        assertEquals(formatDuration(28_000L), buildDurationText(stopped))
        assertNull(buildDurationText(record(status = BuildStatus.RUNNING)))
    }
}
