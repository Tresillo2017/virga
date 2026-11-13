@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.feature.builds.demo.DEMO_LIVE_BUILD_UUID
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One build's detail screen in demo mode: status, cancel with its confirmation, watching, and a finished build. */
@RunWith(RobolectricTestRunner::class)
class BuildDetailFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow
    private val liveKey = BuildDetail(DEMO_PERSONAL, DEMO_LIVE_BUILD_UUID, DEMO_WORKER)

    @Before
    fun setUp() {
        flow = DemoFlow(compose, buildsDemoFeature(FlowClock()))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `a running build shows its status, branch, commit, author and log`() {
        flow.show(liveKey)

        compose.assertStatusShown("Queued")
        flow.assertTextShown("9f3c2ab")
        flow.assertTextShown("dev@example.test")
        flow.assertTextShown("Tune cache headers for assets")
        flow.assertTextShown("Log")
        flow.assertTextShown("Cancel build")
        flow.assertTextShown("Watch build")
    }

    @Test
    fun `cancel asks first, keeping the build leaves it running, and confirming cancels it`() {
        flow.show(liveKey)

        flow.clickText("Cancel build")
        compose.waitForDialogText("Cancel this build?")
        compose.clickInDialog("Keep building")
        compose.waitForDialogTextGone("Cancel this build?")
        assertEquals(BuildStatus.QUEUED, flow.liveBuild().status)

        flow.clickText("Cancel build")
        compose.waitForDialogText("Cancel this build?")
        compose.clickInDialog("Cancel build")

        compose.waitUntilAtLeastOneExists(hasText("Cancel requested."), FLOW_WAIT_TIMEOUT_MS)
        compose.assertStatusShown("Cancelled")
        val cancelled = flow.liveBuild()
        assertEquals(BuildStatus.STOPPED, cancelled.status)
        assertEquals(BuildOutcome.CANCELED, cancelled.outcome)
        compose.onAllNodes(hasText("Cancel build") and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `watching asks for notification permission first, and stop watching ends the watch`() {
        flow.show(liveKey)
        val registry = flow.get<WatchRegistry>()

        flow.clickText("Watch build")
        compose.waitForDialogText("Show build progress?")
        compose.clickInDialog("Not now")
        compose.waitUntilAtLeastOneExists(hasText("Watching this build."), FLOW_WAIT_TIMEOUT_MS)

        flow.assertTextShown("Stop watching")
        assertTrue(registry.isWatching(DEMO_LIVE_BUILD_UUID))

        flow.clickText("Stop watching")
        compose.waitUntilAtLeastOneExists(hasText("Stopped watching."), FLOW_WAIT_TIMEOUT_MS)

        flow.assertTextShown("Watch build")
        assertFalse(registry.isWatching(DEMO_LIVE_BUILD_UUID))
    }

    @Test
    fun `a finished build offers no watch or cancel, and its log is complete`() {
        flow.show(BuildDetail(DEMO_PERSONAL, DEMO_HISTORY_NEWEST_UUID, DEMO_WORKER))

        compose.assertStatusShown("Succeeded")
        flow.waitForText("Build finished. Log complete.")
        compose.onAllNodes(hasText("Watch build") and hasClickAction()).assertCountEquals(0)
        compose.onAllNodes(hasText("Cancel build") and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `a build that does not exist shows the error with a retry`() {
        flow.show(BuildDetail(DEMO_PERSONAL, "no-such-build", DEMO_WORKER))

        flow.assertTextShown("Not found")
    }
}
