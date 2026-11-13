package com.tomasps.virga.feature.workers.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_CLEANUP
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_EDGE_API
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_RESIZER
import com.tomasps.virga.feature.workers.demo.WorkersDemoRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

private const val DASHBOARD_WORKERS_URL = "https://dash.cloudflare.com/?to=/:account/workers-and-pages"
private const val CREATE_ROW_LABEL = "Create a Worker in the dashboard"

/** The Workers list in demo mode: rows, search, sort, empty and no-match states, and the links at the bottom. */
@RunWith(RobolectricTestRunner::class)
class WorkersListFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = workersFlow(compose)
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `list shows every demo Worker, sorted by name`() {
        flow.show(WorkersRoot)

        flow.assertTextShown(DEMO_SCRIPT_EDGE_API)
        flow.assertTextShown(DEMO_SCRIPT_RESIZER)
        flow.assertTextShown(DEMO_SCRIPT_CLEANUP)
        assertTrue(flow.boundsOf(DEMO_SCRIPT_CLEANUP).top < flow.boundsOf(DEMO_SCRIPT_EDGE_API).top)
        assertTrue(flow.boundsOf(DEMO_SCRIPT_EDGE_API).top < flow.boundsOf(DEMO_SCRIPT_RESIZER).top)
    }

    @Test
    fun `rows show the modified date as a readable date, not a raw ISO timestamp`() {
        flow.show(WorkersRoot)

        flow.waitForText("Modified ${formatIsoDate("2026-11-03T08:20:00Z")}")
        compose.onAllNodesWithText("2026-11-03", substring = true).assertCountEquals(0)
    }

    @Test
    fun `search narrows the list to matching Workers`() {
        flow.show(WorkersRoot)

        flow.typeIntoField("Search Workers", "resizer")

        flow.assertTextShown(DEMO_SCRIPT_RESIZER)
        flow.waitUntilTextGone(DEMO_SCRIPT_EDGE_API)
    }

    @Test
    fun `search with no match shows the no-match state`() {
        flow.show(WorkersRoot)

        flow.typeIntoField("Search Workers", "no-such-worker")

        flow.waitForText("No Workers match")
        flow.assertTextShown("Try a different search.")
    }

    @Test
    fun `an account with no Workers shows the empty state`() {
        val scripts = flow.get<WorkersDemoRoutes>().state.scripts
        scripts.items.value.keys.toList().forEach { scripts.remove(it) }
        flow.show(WorkersRoot)

        flow.waitForText("No Workers yet")
        flow.assertTextShown("This account has no Workers. Create one in the Cloudflare dashboard for now.")
    }

    @Test
    fun `a Worker with a build running shows the in-progress dot, and the others do not`() {
        val running = BuildSummary(
            buildUuid = "build-edge-1",
            scriptName = DEMO_SCRIPT_EDGE_API,
            status = BuildStatus.RUNNING,
            createdOn = Instant.parse("2026-11-11T08:58:00Z"),
        )
        flow.close()
        flow = workersFlow(compose, FlowBuildsGateway(latestByScript = mapOf(DEMO_SCRIPT_EDGE_API to running)))
        flow.show(WorkersRoot)
        flow.waitForText(DEMO_SCRIPT_EDGE_API)

        compose.onAllNodes(hasContentDescription("In progress")).assertCountEquals(1)
    }

    @Test
    fun `tapping a Worker opens its detail`() {
        flow.show(WorkersRoot)

        flow.clickText(DEMO_SCRIPT_EDGE_API)

        flow.assertNavigatedTo(WorkerDetail(DEMO_SCRIPT_EDGE_API))
        flow.waitForText("Overview")
    }

    @Test
    fun `the dashboard row opens the Workers page in the browser`() {
        flow.show(WorkersRoot)

        flow.clickText(CREATE_ROW_LABEL)

        assertEquals(listOf(DASHBOARD_WORKERS_URL), flow.openedUrls)
    }
}
