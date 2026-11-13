@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.tomasps.virga.feature.pages.flow

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.feature.pages.PagesDeploymentDetail
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.ui.PAGES_DASHBOARD_URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PROD_CURRENT = "lantern-prod-3"
private const val PROD_OLDER = "lantern-prod-2"
private const val PROD_FIRST = "lantern-prod-1"
private const val PREVIEW_FAILED = "lantern-prev-4"
private const val PREVIEW_RUNNING = "lantern-prev-5"
private const val IN_PROGRESS = "Deployment in progress"
private const val POLL_TICK_MS = 5_000L

/** One deployment: stages, commit facts, menu actions with their confirmations, logs, and the running view. */
@RunWith(RobolectricTestRunner::class)
class PagesDeploymentFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    /** Scrolls the list to [text] if needed, then asserts it is shown once. Rows below the fold are not composed until then. */
    private fun shown(text: String) {
        compose.scrollToText(text)
        flow.assertTextShown(text)
    }

    /** Like [shown], for a text node that holds [text] among other lines (the build log is one node). */
    private fun shownContaining(text: String) {
        compose.scrollToText(text, substring = true)
        compose.onNode(hasText(text, substring = true)).assertIsDisplayed()
    }

    @Before
    fun setUp() {
        flow = pagesFlow(compose)
    }

    @After
    fun tearDown() {
        flow.close()
    }

    // --- Detail body ------------------------------------------------------------------------------

    @Test
    fun `a finished deployment lists its five stages with status and the commit it was built from`() {
        flow.show(PagesDeploymentDetail(LANTERN, PROD_CURRENT))

        shown("Queued")
        shown("Clone repository")
        shown("Deploy")
        compose.onAllNodes(hasText("Success")).assertCountEquals(5)
        shown("Update pricing page")
        shown("main")
        shown("Build log")
    }

    @Test
    fun `the current production deployment offers no rollback and no delete`() {
        flow.show(PagesDeploymentDetail(LANTERN, PROD_CURRENT))

        compose.tapText("More")

        compose.onAllNodes(hasText("Roll back to this")).assertCountEquals(0)
        compose.onAllNodes(hasText("Delete")).assertCountEquals(0)
    }

    @Test
    fun `an older production deployment offers rollback and delete`() {
        flow.show(PagesDeploymentDetail(LANTERN, PROD_OLDER))

        compose.tapText("More")

        shown("Roll back to this")
        shown("Delete")
    }

    @Test
    fun `a failed preview offers retry and delete, but never rollback`() {
        flow.show(PagesDeploymentDetail(LANTERN, PREVIEW_FAILED))

        compose.tapText("More")

        shown("Retry deployment")
        shown("Delete")
        compose.onAllNodes(hasText("Roll back to this")).assertCountEquals(0)
    }

    // --- Rollback ---------------------------------------------------------------------------------

    @Test
    fun `rollback asks first - cancel changes nothing, confirm makes the older deployment production`() = runBlocking {
        flow.show(PagesDeploymentDetail(LANTERN, PROD_OLDER))

        compose.tapText("More")
        compose.tapText("Roll back to this")
        compose.assertDialogText("Roll back production?")
        compose.tapInDialog("Cancel")
        compose.assertNoDialogText("Roll back production?")
        assertEquals(PROD_CURRENT, canonicalId())

        compose.tapText("More")
        compose.tapText("Roll back to this")
        compose.assertDialogText("Roll back production?")
        compose.tapInDialog("Roll back to this")

        shown("Production rolled back.")
        assertEquals(PROD_OLDER, canonicalId())
    }

    @Test
    fun `after a rollback, going back shows the new production deployment in the project's list`() {
        flow.showStack(PagesProject(LANTERN), PagesDeploymentDetail(LANTERN, PROD_FIRST))

        compose.tapText("More")
        compose.tapText("Roll back to this")
        compose.tapInDialog("Roll back to this")
        shown("Production rolled back.")

        flow.navigator.back()

        flow.waitForText("Current production: Initial site")
    }

    // --- Retry ------------------------------------------------------------------------------------

    @Test
    fun `retrying a failed deployment confirms, and its first stage starts again`() {
        flow.show(PagesDeploymentDetail(LANTERN, PREVIEW_FAILED))

        compose.tapText("More")
        compose.tapText("Retry deployment")

        shown("Retry started.")
        shown("Running")
    }

    // --- Delete -----------------------------------------------------------------------------------

    @Test
    fun `deleting a preview asks first, and once deleted the project's list no longer shows it`() {
        flow.showStack(PagesProject(LANTERN), PagesDeploymentDetail(LANTERN, PREVIEW_FAILED))

        compose.tapText("More")
        compose.tapText("Delete")
        compose.assertDialogText("Delete this deployment?")
        compose.tapInDialog("Cancel")
        compose.assertNoDialogText("Delete this deployment?")

        compose.tapText("More")
        compose.tapText("Delete")
        compose.assertDialogText("Delete this deployment?")
        compose.tapInDialog("Delete")

        flow.waitUntilTextGone("Try new image pipeline")
        shown("Add search index")
    }

    // --- Logs -------------------------------------------------------------------------------------

    @Test
    fun `the build log loads on request and shows the failure line for a failed build`() {
        flow.show(PagesDeploymentDetail(LANTERN, PREVIEW_FAILED))

        compose.tapText("Show build log")

        shownContaining("Build failed with exit code 1")
    }

    @Test
    fun `the build log of a successful deployment ends with its completion line`() {
        flow.show(PagesDeploymentDetail(LANTERN, PROD_CURRENT))

        compose.tapText("Show build log")

        shownContaining("Deployment complete")
    }

    // --- Open in dashboard rows -------------------------------------------------------------------

    @Test
    fun `a preview offers promotion in the dashboard, which opens the dashboard`() {
        flow.show(PagesDeploymentDetail(LANTERN, PREVIEW_FAILED))

        compose.tapText("Promote this preview in the dashboard")

        assertEquals(listOf(PAGES_DASHBOARD_URL), flow.openedUrls)
    }

    @Test
    fun `a production deployment offers no promotion row`() {
        flow.show(PagesDeploymentDetail(LANTERN, PROD_CURRENT))

        compose.onAllNodes(hasText("Promote this preview in the dashboard")).assertCountEquals(0)
    }

    // --- Running deployment -----------------------------------------------------------------------

    @Test
    fun `a running deployment shows progress, polls, and stops polling once it finishes`() {
        flow.show(PagesDeploymentDetail(LANTERN, PREVIEW_RUNNING))
        compose.onAllNodes(hasContentDescription(IN_PROGRESS)).assertCountEquals(1)

        flow.advanceClock(POLL_TICK_MS)
        // The tick's read returns asynchronously, so wait for the progress view to go rather than checking once.
        compose.waitUntilDoesNotExist(hasContentDescription(IN_PROGRESS), FLOW_WAIT_TIMEOUT_MS)
        compose.onAllNodes(hasText("Success")).assertCountEquals(5)

        flow.advanceClock(POLL_TICK_MS * 3)
        compose.onAllNodes(hasContentDescription(IN_PROGRESS)).assertCountEquals(0)
    }

    private suspend fun canonicalId(): String? =
        when (val result = flow.get<PagesProjectsRepository>().project(DEMO_ACCOUNT, LANTERN)) {
            is ApiResult.Success -> result.value.canonicalDeployment?.id
            is ApiResult.Failure -> error("the demo project could not be read: ${result.error}")
        }
}
