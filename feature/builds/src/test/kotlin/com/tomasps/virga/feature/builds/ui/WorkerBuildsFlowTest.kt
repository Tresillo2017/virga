@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.feature.builds.BuildConfigScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Worker build history and the start-a-build sheet, driven through the real screens in demo mode. */
@RunWith(RobolectricTestRunner::class)
class WorkerBuildsFlowTest {

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
    fun `history lists the live build and the finished builds, each with its status`() {
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.assertTextShown("Fix login redirect")
        flow.assertTextShown("Bump the parser dependency")
        flow.assertTextShown("Initial deploy")
        compose.assertStatusShown("Queued")
        compose.assertStatusShown("Succeeded")
        compose.assertStatusShown("Failed")
        compose.assertStatusShown("Skipped")
        compose.assertStatusShown("Cancelled")
    }

    @Test
    fun `tapping a build opens its detail`() {
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Fix login redirect")

        flow.assertNavigatedTo(BuildDetail(DEMO_PERSONAL, DEMO_HISTORY_NEWEST_UUID, DEMO_WORKER))
    }

    @Test
    fun `build settings in the top bar opens this Worker's configuration`() {
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Build settings")

        flow.assertNavigatedTo(BuildConfigScreen(DEMO_PERSONAL, DEMO_WORKER))
    }

    @Test
    fun `a start by branch refuses blank input, cancelling the confirmation sends nothing, and a confirmed start builds that branch`() {
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))
        val buildsBefore = flow.workerBuilds().size

        flow.clickText("Start a build")
        flow.assertTextShown("By branch")
        flow.clickText("Start build")
        flow.assertTextShown("Enter a branch or a commit hash.")
        assertEquals(buildsBefore, flow.workerBuilds().size)

        flow.typeIntoField("Branch", "feature/checkout")
        flow.clickText("Start build")
        compose.waitForDialogText("Start this build?")
        compose.waitUntilAtLeastOneExists(hasText("Start a build of branch feature/checkout", substring = true), FLOW_WAIT_TIMEOUT_MS)
        compose.clickInDialog("Cancel")
        compose.waitForDialogTextGone("Start this build?")
        assertEquals(buildsBefore, flow.workerBuilds().size)

        flow.clickText("Start build")
        compose.waitForDialogText("Start this build?")
        compose.clickInDialog("Start")

        compose.waitForDialogTextGone("Start this build?")
        compose.waitUntilAtLeastOneExists(hasText("feature/checkout", substring = true), FLOW_WAIT_TIMEOUT_MS)
        val started = flow.workerBuilds()
        assertEquals(buildsBefore + 1, started.size)
        val fresh = started.single { it.branch == "feature/checkout" }
        assertEquals(BuildStatus.QUEUED, fresh.status)
        assertFalse(fresh.isTerminal)
    }

    @Test
    fun `a start by commit sends that commit hash and shows it on the new build`() {
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Start a build")
        flow.assertTextShown("By commit")
        flow.clickText("By commit")
        flow.typeIntoField("Commit hash", "abc1234")
        flow.clickText("Start build")
        compose.waitForDialogText("Start this build?")
        compose.waitUntilAtLeastOneExists(hasText("Start a build of commit abc1234", substring = true), FLOW_WAIT_TIMEOUT_MS)
        compose.clickInDialog("Start")

        compose.waitForDialogTextGone("Start this build?")
        compose.waitUntilAtLeastOneExists(hasText("abc1234", substring = true), FLOW_WAIT_TIMEOUT_MS)
        assertEquals("abc1234", flow.workerBuilds().single { it.commitHash == "abc1234" }.commitHash)
    }
}
