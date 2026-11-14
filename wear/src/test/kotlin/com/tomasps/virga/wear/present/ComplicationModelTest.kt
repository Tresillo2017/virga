package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationModelTest {

    @Test
    fun `complicationForNullIsNoBuilds`() {
        val model = complicationFor(null, NOW)

        assertEquals("No builds", model.title)
        assertEquals("None", model.shortText)
        assertEquals(BuildVisualState.UNKNOWN, model.state)
    }

    @Test
    fun `each state has its short word`() {
        fun short(status: BuildStatus, outcome: BuildOutcome?) =
            complicationFor(testBuild(status = status, outcome = outcome), NOW).shortText
        assertEquals("Run", short(BuildStatus.RUNNING, null))
        assertEquals("OK", short(BuildStatus.STOPPED, BuildOutcome.SUCCESS))
        assertEquals("Fail", short(BuildStatus.STOPPED, BuildOutcome.FAIL))
        assertEquals("Stop", short(BuildStatus.STOPPED, BuildOutcome.CANCELED))
        assertEquals("?", short(BuildStatus.STOPPED, null))
    }

    @Test
    fun `longWorkerNameIsKeptInTitleAndShortTextStaysUnderSevenChars`() {
        val name = "a-very-long-worker-name-that-the-platform-truncates"
        BuildVisualState.entries.forEach { state ->
            val build = when (state) {
                BuildVisualState.RUNNING -> testBuild(BuildStatus.RUNNING, scriptName = name)
                BuildVisualState.PASSED -> testBuild(outcome = BuildOutcome.SUCCESS, scriptName = name)
                BuildVisualState.FAILED -> testBuild(outcome = BuildOutcome.FAIL, scriptName = name)
                BuildVisualState.CANCELED -> testBuild(outcome = BuildOutcome.CANCELED, scriptName = name)
                BuildVisualState.UNKNOWN -> testBuild(scriptName = name)
            }
            val model = complicationFor(build, NOW)
            assertEquals(name, model.title)
            assertEquals(state, model.state)
            assertTrue(model.shortText.length < 7)
        }
    }
}
