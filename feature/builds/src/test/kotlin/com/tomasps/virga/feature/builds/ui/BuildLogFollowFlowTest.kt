@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.builds.demo.DEMO_LIVE_BUILD_UUID
import com.tomasps.virga.feature.builds.ui.DETAIL_LIST_TAG
import com.tomasps.virga.feature.builds.ui.LOG_LINE_TAG_PREFIX
import com.tomasps.virga.feature.builds.ui.DETAIL_POLL_MILLIS
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LINES_AT_START = 15
private const val LINE_EVERY_MS = 2_000L
private const val JUMP_LABEL = "Jump to latest"
private const val SWIPES_TO_REACH_BOTTOM = 4

/**
 * The live log's follow rule on a short window, so the log overflows and the scroll is real. Following stays on at the
 * bottom, a drag away pauses it, and new lines do not pull the view back until "Jump to latest" is tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h320dp-xxhdpi")
class BuildLogFollowFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var clock: FlowClock
    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        clock = FlowClock()
        flow = DemoFlow(compose, buildsDemoFeature(clock))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `the log follows the newest line, a drag up pauses it, and Jump to latest resumes it`() {
        flow.show(BuildDetail(DEMO_PERSONAL, DEMO_LIVE_BUILD_UUID, DEMO_WORKER))
        clock.advance(LINES_AT_START * LINE_EVERY_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        val newestAtStart = lineTag(LINES_AT_START - 1)
        flow.waitForTag(newestAtStart)
        assertTrue("following: the newest line is on screen", compose.isDisplayed(newestAtStart))
        assertFalse(compose.isTextShown(JUMP_LABEL))

        compose.onNodeWithTag(DETAIL_LIST_TAG).performTouchInput { swipeDown() }
        compose.waitForIdle()
        flow.waitForText(JUMP_LABEL)
        assertFalse("a drag up stops following", compose.isDisplayed(newestAtStart))

        clock.advance(LINE_EVERY_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        val newestNow = lineTag(LINES_AT_START)
        assertFalse("a new line does not pull a paused view down", compose.isDisplayed(newestNow))
        flow.waitForText(JUMP_LABEL)

        flow.clickText(JUMP_LABEL)

        flow.waitForTag(newestNow)
        assertTrue("Jump to latest brings the newest line back", compose.isDisplayed(newestNow))
        compose.onAllNodes(hasText(JUMP_LABEL)).assertCountEquals(0)
    }

    @Test
    fun `scrolling back to the bottom by hand resumes following`() {
        flow.show(BuildDetail(DEMO_PERSONAL, DEMO_LIVE_BUILD_UUID, DEMO_WORKER))
        clock.advance(LINES_AT_START * LINE_EVERY_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        flow.waitForTag(lineTag(LINES_AT_START - 1))

        compose.onNodeWithTag(DETAIL_LIST_TAG).performTouchInput { swipeDown() }
        compose.waitForIdle()
        flow.waitForText(JUMP_LABEL)

        repeat(SWIPES_TO_REACH_BOTTOM) { compose.onNodeWithTag(DETAIL_LIST_TAG).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        compose.onAllNodes(hasText(JUMP_LABEL)).assertCountEquals(0)

        clock.advance(LINE_EVERY_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        flow.waitForTag(lineTag(LINES_AT_START))
        assertTrue("back at the bottom, the new line is followed", compose.isDisplayed(lineTag(LINES_AT_START)))
    }

    private fun lineTag(index: Int) = "$LOG_LINE_TAG_PREFIX$index"
}

/** Whether the node with [tag] is on screen right now. A lazy list that has not composed it counts as off screen. */
private fun ComposeContentTestRule.isDisplayed(tag: String): Boolean {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) return false
    return runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
}

private fun ComposeContentTestRule.isTextShown(text: String): Boolean =
    onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
