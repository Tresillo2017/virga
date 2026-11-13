package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.builds.demo.DEMO_LIVE_BUILD_UUID
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val STAGE_INITIALIZING_MS = 7_000L
private const val STAGE_RUNNING_MS = 6_000L
private const val RUNNING_TO_DONE_MS = 20_000L
private const val FINISH_MS = 10_000L

/**
 * A running build on its detail screen, with the demo clock moved by the test. The screen polls on virtual time, so each
 * [DemoFlow.advanceClock] of one poll interval reads the build once more.
 */
@RunWith(RobolectricTestRunner::class)
class BuildDetailLiveFlowTest {

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
    fun `status and log follow the build through its stages, and polling ends when it finishes`() {
        flow.show(BuildDetail(DEMO_PERSONAL, DEMO_LIVE_BUILD_UUID, DEMO_WORKER))
        compose.assertStatusShown("Queued")

        clock.advance(STAGE_INITIALIZING_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        compose.assertStatusShown("Initializing")

        clock.advance(STAGE_RUNNING_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        compose.assertStatusShown("Running")
        flow.waitForText("Cloning repository")
        flow.waitForText("Installing dependencies")

        clock.advance(RUNNING_TO_DONE_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        compose.assertStatusShown("Running")

        clock.advance(FINISH_MS)
        flow.advanceClock(DETAIL_POLL_MILLIS)
        compose.assertStatusShown("Succeeded")
        flow.waitForText("Build finished. Log complete.")
    }
}
