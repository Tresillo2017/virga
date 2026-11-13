package com.tomasps.virga.feature.workers.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.workers.demo.DEMO_D1_DATABASE_ID
import com.tomasps.virga.feature.workers.demo.DEMO_KV_NAMESPACE_ID
import com.tomasps.virga.feature.workers.demo.DEMO_R2_BUCKET_NAME
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_CLEANUP
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_EDGE_API
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_RESIZER
import com.tomasps.virga.feature.workers.demo.WorkersDemoRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

private const val BUILD_UUID = "build-edge-1"
private const val DASHBOARD_ROUTES_URL = "https://dash.cloudflare.com/?to=/:account/workers-and-pages"
private const val OLD_VERSION_DATE_SOURCE = "2026-10-21T14:05:00Z"
private const val MAX_SWIPES = 8
private const val OVERVIEW_BINDINGS = "Bindings · 5"

/** The Worker detail screen in demo mode: overview, deployments, domains and settings, and every write they make. */
@RunWith(RobolectricTestRunner::class)
class WorkerDetailFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow
    private lateinit var builds: FlowBuildsGateway

    @Before
    fun setUp() {
        builds = FlowBuildsGateway()
        flow = workersFlow(compose, builds)
    }

    @After
    fun tearDown() {
        flow.close()
    }

    private fun showEdgeApi(tab: String? = null) {
        flow.show(WorkerDetail(DEMO_SCRIPT_EDGE_API))
        flow.waitForText("Overview")
        tab?.let { flow.clickText(it) }
    }

    // Overview

    @Test
    fun `overview shows the active split by version number, bindings and recent deployments`() {
        showEdgeApi()

        flow.assertTextShown("Version 2 at 100%")
        flow.assertTextShown(OVERVIEW_BINDINGS)
        scrollUntilTextShown("Fix cache header")
        flow.assertTextShown("api.example.org")
        compose.onAllNodesWithText("9f1c0e2a", substring = true).assertCountEquals(0)
    }

    @Test
    fun `tapping a binding opens the KV, R2 and D1 screen that owns it`() {
        showEdgeApi()

        flow.clickText("CACHE")
        flow.assertNavigatedTo(KvNamespace(DEMO_KV_NAMESPACE_ID))
    }

    @Test
    fun `tapping the R2 binding opens its bucket`() {
        showEdgeApi()

        flow.clickText("ASSETS")

        flow.assertNavigatedTo(R2Bucket(DEMO_R2_BUCKET_NAME))
    }

    @Test
    fun `tapping the D1 binding opens its database`() {
        showEdgeApi()

        flow.clickText("ORDERS")

        flow.assertNavigatedTo(D1Database(DEMO_D1_DATABASE_ID))
    }

    @Test
    fun `all builds opens the build list for this Worker`() {
        showEdgeApi()

        flow.clickText("All builds")

        flow.assertNavigatedTo(WorkerBuilds(DEMO_ACCOUNT_ID, DEMO_SCRIPT_EDGE_API))
    }

    @Test
    fun `a Worker with no connected build shows that state`() {
        showEdgeApi()

        flow.assertTextShown("No connected build for this Worker")
        compose.onAllNodesWithText("Watch build").assertCountEquals(0)
    }

    @Test
    fun `a running build shows the building card and can be watched after the notice`() {
        builds = FlowBuildsGateway(latestByScript = mapOf(DEMO_SCRIPT_EDGE_API to runningBuild()))
        flow = workersFlow(compose, builds)
        showEdgeApi()

        flow.assertTextShown("Building now")
        flow.assertTextShown("Building · main")
        flow.clickText("Watch build")
        flow.waitForText("Get updates on this build?")
        flow.clickText("Not now")

        flow.waitForText("Stop watching")
        assertEquals(listOf(BUILD_UUID), builds.started)
        flow.clickText("Stop watching")
        flow.waitForText("Watch build")
        assertEquals(listOf(BUILD_UUID), builds.stopped)
    }

    @Test
    fun `a build that already finished is not shown as building or watchable`() {
        builds = FlowBuildsGateway(latestByScript = mapOf(DEMO_SCRIPT_EDGE_API to runningBuild().copy(outcome = BuildOutcome.SUCCESS, stoppedOn = Instant.parse("2026-11-11T08:30:00Z"))))
        flow = workersFlow(compose, builds)
        showEdgeApi()

        flow.assertTextShown("Latest build")
        compose.onAllNodesWithText("Building now").assertCountEquals(0)
        compose.onAllNodesWithText("Watch build").assertCountEquals(0)
    }

    @Test
    fun `view logs on a running build opens that build`() {
        builds = FlowBuildsGateway(latestByScript = mapOf(DEMO_SCRIPT_EDGE_API to runningBuild()))
        flow = workersFlow(compose, builds)
        showEdgeApi()

        flow.clickText("View logs")

        flow.assertNavigatedTo(BuildDetail(DEMO_ACCOUNT_ID, BUILD_UUID, DEMO_SCRIPT_EDGE_API))
    }

    @Test
    fun `a Worker that does not exist shows the not-found error`() {
        flow.show(WorkerDetail("no-such-worker"))

        flow.waitForText("Not found")
        flow.assertTextShown("That item no longer exists, or this token cannot see it.")
    }

    // Deployments

    @Test
    fun `deployments list the active split and earlier versions with their numbers and dates`() {
        showEdgeApi("Deployments")

        flow.assertTextShown("Earlier versions")
        flow.assertTextShown("Version 1")
        flow.assertTextShown(formatIsoDate(OLD_VERSION_DATE_SOURCE)!!)
        flow.assertTextShown("Roll back")
    }

    @Test
    fun `rollback asks first, and cancelling sends nothing`() {
        showEdgeApi("Deployments")
        val before = deploymentCount()

        flow.clickText("Roll back")
        flow.waitForText("Roll back?")
        flow.assertTextShown("Send all traffic to Version 1. Bindings, variables and data are not rolled back, so a rollback can break code that depends on newer bindings or data.")
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Roll back?")
        assertEquals(before, deploymentCount())
    }

    @Test
    fun `confirming rollback deploys the older version at one hundred percent`() {
        showEdgeApi("Deployments")

        flow.clickText("Roll back")
        flow.waitForText("Roll back?")
        flow.clickText("Confirm")

        flow.waitForText("Deployment saved.")
        assertEquals(listOf(1 to 100.0), flow.activeSplit(DEMO_SCRIPT_EDGE_API))
    }

    @Test
    fun `the gradual split editor keeps its Save and Cancel buttons on screen`() {
        showEdgeApi("Deployments")
        flow.clickText("Gradual deployment")
        flow.waitForText("Version 2, percent")

        compose.onNodeWithText("Deploy this split").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `gradual deployment stays disabled until the split totals exactly one hundred`() {
        showEdgeApi("Deployments")
        flow.clickText("Gradual deployment")
        flow.waitForText("Version 2, percent")

        flow.typeIntoField("Version 2, percent", "90")
        flow.waitForText("Total: 90%. Must be exactly 100.")
        compose.onNodeWithText("Deploy this split").assertIsNotEnabled()
        flow.typeIntoField("Version 2, percent", "100")
        flow.waitForText("Total: 100%. Must be exactly 100.")
        compose.onNodeWithText("Deploy this split").assertIsEnabled()
    }

    @Test
    fun `a typed value in the second gradual field is kept`() {
        showEdgeApi("Deployments")
        flow.clickText("Gradual deployment")
        flow.waitForText("Version 2, percent")

        flow.typeIntoField("Version 1, percent", "15")

        flow.waitForText("Total: 15%. Must be exactly 100.")
    }

    @Test
    fun `saving a valid gradual split deploys it`() {
        builds = FlowBuildsGateway()
        flow = workersFlow(compose, builds)
        flow.show(WorkerDetail(DEMO_SCRIPT_RESIZER))
        flow.waitForText("Overview")
        flow.clickText("Deployments")
        flow.clickText("Gradual deployment")
        flow.waitForText("Version 2, percent")
        flow.typeIntoField("Version 2, percent", "60")
        flow.typeIntoField("Version 3, percent", "40")

        flow.clickText("Deploy this split")

        flow.waitForText("Deployment saved.")
        assertEquals(listOf(2 to 60.0, 3 to 40.0), flow.activeSplit(DEMO_SCRIPT_RESIZER))
    }

    // Domains

    @Test
    fun `workers dev toggle asks before it changes and cancelling keeps the setting`() {
        showEdgeApi("Domains")

        compose.onAllNodes(isToggleable())[0].performClick()
        flow.waitForText("Turn off workers.dev?")
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Turn off workers.dev?")
        assertTrue(flow.subdomainEnabled(DEMO_SCRIPT_EDGE_API))
    }

    @Test
    fun `turning workers dev on for a Worker that has it off asks first, then saves`() {
        flow.show(WorkerDetail(DEMO_SCRIPT_CLEANUP))
        flow.waitForText("Overview")
        flow.clickText("Domains")

        compose.onAllNodes(isToggleable())[0].performClick()
        flow.waitForText("Turn on workers.dev?")
        flow.clickText("Confirm")

        flow.waitForText("Setting saved.")
        assertTrue(flow.subdomainEnabled(DEMO_SCRIPT_CLEANUP))
    }

    @Test
    fun `turning workers dev off after confirming saves the setting`() {
        showEdgeApi("Domains")

        compose.onAllNodes(isToggleable())[0].performClick()
        flow.waitForText("Turn off workers.dev?")
        flow.clickText("Confirm")

        flow.waitForText("Setting saved.")
        assertFalse(flow.subdomainEnabled(DEMO_SCRIPT_EDGE_API))
    }

    @Test
    fun `preview URL toggle confirms and saves the new setting`() {
        showEdgeApi("Domains")

        compose.onAllNodes(isToggleable())[1].performClick()
        flow.waitForText("Turn on preview URLs?")
        flow.clickText("Confirm")

        flow.waitForText("Setting saved.")
        assertTrue(flow.previewsEnabled(DEMO_SCRIPT_EDGE_API))
    }

    @Test
    fun `adding a custom domain rejects a bad hostname and cannot submit it`() {
        showEdgeApi("Domains")
        flow.clickText("Add custom domain")

        flow.typeIntoField("Hostname", "not a hostname")

        flow.assertTextShown("Enter a hostname such as api.example.com.")
        compose.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `cancelling the add-domain confirmation routes nothing`() {
        showEdgeApi("Domains")
        flow.clickText("Add custom domain")
        flow.typeIntoField("Hostname", "shop.example.org")
        compose.onNodeWithText("Add").performClick()
        flow.waitForText("Route this hostname to the Worker?")

        flow.clickText("Cancel")

        flow.waitUntilTextGone("Route this hostname to the Worker?")
        assertFalse(flow.hasDomain(DEMO_SCRIPT_EDGE_API, "shop.example.org"))
    }

    @Test
    fun `adding a custom domain confirms, then routes it`() {
        showEdgeApi("Domains")
        flow.clickText("Add custom domain")
        flow.typeIntoField("Hostname", "Shop.Example.org")

        compose.onNodeWithText("Add").performClick()
        flow.waitForText("Route this hostname to the Worker?")
        flow.clickText("Confirm")

        flow.waitForText("Domain added.")
        flow.assertTextShown("shop.example.org")
        assertTrue(flow.hasDomain(DEMO_SCRIPT_EDGE_API, "shop.example.org"))
    }

    @Test
    fun `removing a custom domain confirms, and cancelling keeps it`() {
        showEdgeApi("Domains")

        flow.clickText("Remove")
        flow.waitForText("Remove this domain?")
        flow.clickText("Cancel")
        flow.waitUntilTextGone("Remove this domain?")
        flow.assertTextShown("api.example.org")

        flow.clickText("Remove")
        flow.waitForText("Remove this domain?")
        flow.clickText("Confirm")

        flow.waitForText("Domain removed.")
        flow.waitUntilTextGone("api.example.org")
        assertFalse(flow.hasDomain(DEMO_SCRIPT_EDGE_API, "api.example.org"))
    }

    @Test
    fun `routes open the dashboard`() {
        showEdgeApi("Domains")

        flow.clickText("Open in dashboard")

        assertEquals(listOf(DASHBOARD_ROUTES_URL), flow.openedUrls)
    }

    // Settings

    @Test
    fun `settings show readable binding types and variables, and never a raw type id`() {
        showEdgeApi("Settings")

        flow.assertTextShown("LOG_LEVEL")
        flow.assertTextShown("info")
        flow.assertTextShown("KV namespace")
        flow.assertTextShown("R2 bucket")
        compose.onAllNodesWithText("kv_namespace").assertCountEquals(0)
        compose.onAllNodesWithText("r2_bucket").assertCountEquals(0)
    }

    @Test
    fun `saving a secret needs both a name and a value`() {
        showEdgeApi("Settings")
        flow.clickText("Add or replace a secret")

        flow.typeIntoField("Name", "ONLY_NAME")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        flow.typeIntoField("Name", "")
        flow.typeIntoField("Value", "only-value")
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `a secret is added, its value is never shown, and only its name is kept`() {
        showEdgeApi("Settings")
        flow.clickText("Add or replace a secret")

        flow.typeIntoField("Name", "NEW_KEY")
        flow.typeIntoField("Value", "hunter2-value")
        flow.clickText("Save")

        flow.waitForText("Secret saved.")
        flow.assertTextShown("NEW_KEY")
        compose.onAllNodesWithText("hunter2-value").assertCountEquals(0)
        assertTrue(flow.hasSecret(DEMO_SCRIPT_EDGE_API, "NEW_KEY"))
    }

    @Test
    fun `deleting a secret asks first and removes it after confirming`() {
        showEdgeApi("Settings")

        flow.clickText("Delete")
        flow.waitForText("Delete secret?")
        flow.clickText("Cancel")
        flow.waitUntilTextGone("Delete secret?")
        flow.assertTextShown("API_TOKEN_SIGNING")

        flow.clickText("Delete")
        flow.waitForText("Delete secret?")
        flow.clickText("Confirm")

        flow.waitForText("Secret deleted.")
        flow.waitUntilTextGone("API_TOKEN_SIGNING")
        assertFalse(flow.hasSecret(DEMO_SCRIPT_EDGE_API, "API_TOKEN_SIGNING"))
    }

    @Test
    fun `the Worker list drops a Worker that was deleted before the list came back into view`() {
        flow.showStack(WorkersRoot, WorkerDetail(DEMO_SCRIPT_RESIZER))
        flow.waitForText("Overview")
        flow.get<WorkersDemoRoutes>().state.scripts.remove(DEMO_SCRIPT_RESIZER)

        flow.navigator.back()

        flow.waitForText("Workers")
        flow.waitUntilTextGone(DEMO_SCRIPT_RESIZER)
        flow.assertTextShown(DEMO_SCRIPT_EDGE_API)
    }

    /** Swipes the overview up until a lazily composed row that is below the fold exists. */
    private fun scrollUntilTextShown(text: String) {
        repeat(MAX_SWIPES) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        }
        flow.waitForText(text)
    }

    private fun deploymentCount(): Int = flow.deploymentCount(DEMO_SCRIPT_EDGE_API)

    private fun runningBuild(): BuildSummary = BuildSummary(
        buildUuid = BUILD_UUID,
        scriptName = DEMO_SCRIPT_EDGE_API,
        status = BuildStatus.RUNNING,
        branch = "main",
        commitMessage = "Fix cache header",
        createdOn = Instant.parse("2026-11-11T08:58:00Z"),
        startedOn = Instant.parse("2026-11-11T08:59:00Z"),
    )

}