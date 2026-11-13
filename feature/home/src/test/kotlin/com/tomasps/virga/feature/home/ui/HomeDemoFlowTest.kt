package com.tomasps.virga.feature.home.ui

import android.Manifest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.testing.DEFAULT_DEMO_ACCOUNTS
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.home.demo.DEMO_ZONES
import com.tomasps.virga.feature.home.home.HOME_LIST_TAG
import com.tomasps.virga.feature.home.homeKoinModule
import com.tomasps.virga.feature.home.ui.PERSONAL_ACCOUNT
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

private const val PERSONAL_NAME = "Personal (demo)"
private const val STAGING_NAME = "Staging (demo)"
private const val NO_BUILDS_DOMAINS_NOTE = "No domains in this account yet."
private const val CPU_INFO = "About CPU time percentiles"
private const val DASHBOARD_URL = "https://dash.cloudflare.com/"
private const val RATIONALE_TITLE = "Get updates on this build?"
private const val NO_BUILDS = "Nothing is building right now."
private const val LOADING_LABEL = "Loading"
private const val NO_PREVIOUS_PERIOD = "No previous period is compared for ranges longer than 7 days."
private const val DELTA_SUFFIX = "vs previous period"
private val BUILD_TIME: Instant = Instant.parse("2026-11-11T11:50:00Z")

/** Screen-level flows for Home in demo mode, through the feature's real Koin graph and real route registration. */
@RunWith(RobolectricTestRunner::class)
class HomeDemoFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var builds: FlowBuildsGateway
    private lateinit var lists: FlowLists
    private lateinit var analytics: FlowAnalytics
    private var flow: DemoFlow? = null

    @Before
    fun setUp() {
        builds = FlowBuildsGateway()
        lists = FlowLists()
        analytics = FlowAnalytics()
    }

    @After
    fun tearDown() {
        flow?.close()
    }

    /** Starts a flow with the test's fakes. Call it after the test has configured them. */
    private fun startFlow(
        accounts: List<CloudflareAccount> = DEFAULT_DEMO_ACCOUNTS,
        selectedAccountId: String = accounts.first().id,
    ): DemoFlow {
        val started = DemoFlow(
            compose,
            FeatureUnderTest(listOf(homeKoinModule, homeFlowModule(builds, lists, analytics)), ::registerHomeFlowEntries),
            accounts = accounts,
            selectedAccountId = selectedAccountId,
        )
        flow = started
        return started
    }

    private fun build(
        uuid: String,
        script: String,
        status: BuildStatus = BuildStatus.RUNNING,
        branch: String? = "main",
    ) = BuildSummary(
        buildUuid = uuid,
        scriptName = script,
        status = status,
        branch = branch,
        createdOn = BUILD_TIME,
    )

    /** Scrolls Home's list until a node with [text] is on screen. Rows below the fold are not laid out until then. */
    private fun reveal(text: String) = reveal(hasText(text))

    /**
     * Scrolls until a row matching [matcher] is composed and displayed. Rows arrive when their call answers, and a row
     * that is already on screen can move out again when a later section loads, so this waits for the row to be
     * displayed, not only for a scroll to succeed.
     */
    private fun reveal(matcher: SemanticsMatcher) {
        compose.waitUntil(FLOW_WAIT_TIMEOUT_MS) {
            runCatching {
                compose.onNodeWithTag(HOME_LIST_TAG).performScrollToNode(matcher)
                compose.onNode(matcher).assertIsDisplayed()
            }.isSuccess
        }
    }

    /** Scrolls to and asserts a node with [text]. Use it for anything that is not in the first screen of Home. */
    private fun DemoFlow.shown(text: String) {
        reveal(text)
        assertTextShown(text)
    }

    /** Scrolls to and taps the node with the content description [description] (the Watch buttons). */
    private fun tapDescription(description: String) {
        reveal(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitForIdle()
    }

    /** Opens the range menu from its current label and picks [to]. */
    private fun DemoFlow.changeRange(from: String, to: String) {
        clickText(from)
        clickText(to)
    }

    private fun hasDeltaText(): Boolean =
        compose.onAllNodesWithText(DELTA_SUFFIX, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Searches for [query] and taps the first result with [title], which is the search hit above the shortcut lists. */
    private fun searchAndOpen(home: DemoFlow, query: String, title: String) {
        home.typeIntoSearch(query)
        // The search field holds the query as its own text, so a result is the clickable node that is not editable.
        compose.onAllNodes(hasText(title) and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.waitForIdle()
    }

    private fun DemoFlow.typeIntoSearch(text: String) {
        waitForText("Search Workers, zones, Pages, R2, D1 and KV")
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        compose.waitForIdle()
    }

    // Analytics and account

    @Test
    fun `home shows the analytics tiles for the selected account`() {
        val home = startFlow()
        home.show(HomeRoot)

        home.assertTextShown("Requests")
        home.assertTextShown("Worker invocations")
    }

    @Test
    fun `comparison deltas show for 24 hours and drop for 30 days`() {
        val home = startFlow()
        home.show(HomeRoot)
        home.assertTextShown("Requests")
        assertTrue("24h compares with the previous period", hasDeltaText())

        home.changeRange(from = "24h", to = "30d")

        home.shown(NO_PREVIOUS_PERIOD)
        assertTrue("30d has no comparison deltas", !hasDeltaText())
    }

    @Test
    fun `switching the account reloads Home for the other account`() {
        val home = startFlow()
        home.show(HomeRoot)
        home.assertTextShown("Requests")

        home.clickText(PERSONAL_NAME)
        home.clickText(STAGING_NAME)

        home.shown("This account has no Workers yet.")
        home.shown("No Workers in this account yet.")
        assertEquals(STAGING_ACCOUNT, builds.activeCalls.last())
    }

    @Test
    fun `with one account the name is plain text and no switcher is offered`() {
        val home = startFlow(accounts = listOf(DEFAULT_DEMO_ACCOUNTS.first()))
        home.show(HomeRoot)

        home.assertTextShown(PERSONAL_NAME)
        assertTrue("the name is not a button", compose.onAllNodes(hasText(PERSONAL_NAME) and hasClickAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an account with no name is labelled as unnamed, never by its id`() {
        val home = startFlow(accounts = listOf(CloudflareAccount(id = "acc-raw-7f3a", name = "")))
        home.show(HomeRoot)

        home.assertTextShown("Unnamed account")
        assertTrue("the raw id is not shown", compose.onAllNodesWithText("acc-raw-7f3a", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `analytics shows its loading state until the answer arrives, then the tiles`() {
        val gate = CompletableDeferred<Unit>()
        analytics.workersGate = gate
        val home = startFlow()
        home.show(HomeRoot)

        assertTrue("no tiles while loading", compose.onAllNodesWithText("Requests").fetchSemanticsNodes().isEmpty())
        assertEquals("a spinner stands in for the tiles", 1, compose.onAllNodesWithContentDescription("Analytics").fetchSemanticsNodes().size)

        gate.complete(Unit)

        home.assertTextShown("Requests")
        assertTrue("the spinner is gone", compose.onAllNodesWithContentDescription("Analytics").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the builds list shows a loading state until the builds arrive`() {
        val gate = CompletableDeferred<Unit>()
        builds.activeGate = gate
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        assertEquals("the builds list is loading", 1, compose.onAllNodesWithContentDescription(LOADING_LABEL).fetchSemanticsNodes().size)
        assertTrue("no build rows yet", compose.onAllNodesWithText("Running · main").fetchSemanticsNodes().isEmpty())

        gate.complete(Unit)

        home.shown("Running · main")
    }

    @Test
    fun `the CPU info dialog opens and its OK button closes it`() {
        val home = startFlow()
        home.show(HomeRoot)
        home.assertTextShown("CPU time P50")

        compose.onNodeWithContentDescription(CPU_INFO).performClick()
        home.assertTextShown("Why no P90?")
        home.clickText("OK")

        home.waitUntilTextGone("Why no P90?")
    }

    @Test
    fun `the CPU info dialog also closes on back`() {
        val home = startFlow()
        home.show(HomeRoot)
        home.assertTextShown("CPU time P50")
        compose.onNodeWithContentDescription(CPU_INFO).performClick()
        home.assertTextShown("Why no P90?")

        Espresso.pressBack()
        compose.waitForIdle()

        home.waitUntilTextGone("Why no P90?")
        assertTrue("back closes it without leaving Home", home.history.isEmpty())
    }

    @Test
    fun `build minutes opens the dashboard in the browser`() {
        val home = startFlow()
        home.show(HomeRoot)

        home.clickText("Build minutes")

        assertEquals(listOf(DASHBOARD_URL), home.openedUrls)
    }

    @Test
    fun `missing account analytics permission names the permission`() {
        analytics.workersError = graphQlError("You are not authorized to read this account")
        val home = startFlow()
        home.show(HomeRoot)

        home.assertTextShown("Missing permission: Account Analytics: Read. Add it to this token to see these numbers.")
    }

    @Test
    fun `missing zone analytics permission names the zone permission`() {
        analytics.zonesError = graphQlError("not authorized for zone analytics")
        val home = startFlow()
        home.show(HomeRoot)

        home.assertTextShown("Missing permission: Zone Analytics: Read. Add it to this token to see these numbers.")
    }

    @Test
    fun `a rate-limited analytics call says to try again later`() {
        analytics.workersError = graphQlError("rate limit exceeded")
        val home = startFlow()
        home.show(HomeRoot)

        home.assertTextShown("The analytics rate limit is reached. Try again in a few minutes.")
    }

    @Test
    fun `a failed analytics call says analytics could not be loaded`() {
        analytics.workersError = ApiError.Network("offline")
        val home = startFlow()
        home.show(HomeRoot)

        home.assertTextShown("Analytics could not be loaded.")
    }

    // Search

    @Test
    fun `search opens a Worker with its key`() {
        val home = startFlow()
        home.show(HomeRoot)

        searchAndOpen(home, "edge", "edge-api")

        home.assertNavigatedTo(WorkerDetail(scriptName = "edge-api"))
    }

    @Test
    fun `search opens a zone with its key`() {
        val home = startFlow()
        home.show(HomeRoot)

        searchAndOpen(home, "example.com", DEMO_ZONES.first().name)

        home.assertNavigatedTo(ZoneDetail(zoneId = DEMO_ZONES.first().id))
    }

    @Test
    fun `search opens a Pages project with its key`() {
        val home = startFlow()
        home.show(HomeRoot)

        searchAndOpen(home, "sc-landing", "sc-landing")

        home.assertNavigatedTo(PagesProject(projectName = "sc-landing"))
    }

    @Test
    fun `search opens an R2 bucket with its key`() {
        val home = startFlow()
        home.show(HomeRoot)

        searchAndOpen(home, "assets", "assets-cdn")

        home.assertNavigatedTo(R2Bucket(bucketName = "assets-cdn"))
    }

    @Test
    fun `search opens a D1 database by its uuid`() {
        val home = startFlow()
        home.show(HomeRoot)

        searchAndOpen(home, "ledger", "ledger-db")

        home.assertNavigatedTo(D1Database(databaseId = "d1-ledger-uuid"))
    }

    @Test
    fun `search opens a KV namespace by its id`() {
        val home = startFlow()
        home.show(HomeRoot)

        searchAndOpen(home, "session", "Session cache")

        home.assertNavigatedTo(KvNamespace(namespaceId = "kv-session-id"))
    }

    @Test
    fun `search with no match says so`() {
        val home = startFlow()
        home.show(HomeRoot)

        home.typeIntoSearch("zzz-nothing")

        home.assertTextShown("No matches for “zzz-nothing”")
    }

    @Test
    fun `search names a source whose list needs a permission`() {
        lists.forbidden += "pages"
        val home = startFlow()
        home.show(HomeRoot)

        home.typeIntoSearch("sc-")

        home.assertTextShown("Pages projects needs the Cloudflare Pages Read permission")
    }

    @Test
    fun `search says a source could not be loaded when its list fails`() {
        lists.failing += "pages"
        val home = startFlow()
        home.show(HomeRoot)

        home.typeIntoSearch("sc-")

        home.assertTextShown("Pages projects could not be loaded")
    }

    @Test
    fun `clearing the search removes its results`() {
        val home = startFlow()
        home.show(HomeRoot)
        home.typeIntoSearch("edge")
        home.assertTextShown("Workers")

        compose.onNodeWithText("Clear search").performClick()

        home.waitUntilTextGone("No matches for “edge”")
    }

    // Shortcuts

    @Test
    fun `a top Worker shows its requests and opens its detail`() {
        val home = startFlow()
        home.show(HomeRoot)

        home.shown("4.2k requests")
        home.clickText("edge-api")

        home.assertNavigatedTo(WorkerDetail(scriptName = "edge-api"))
    }

    @Test
    fun `a top domain opens its zone detail`() {
        val home = startFlow()
        home.show(HomeRoot)

        reveal(DEMO_ZONES.first().name)
        home.clickText(DEMO_ZONES.first().name)

        home.assertNavigatedTo(ZoneDetail(zoneId = DEMO_ZONES.first().id))
    }

    @Test
    fun `an account without zones shows the empty domain note`() {
        val home = startFlow(accounts = DEFAULT_DEMO_ACCOUNTS, selectedAccountId = STAGING_ACCOUNT)
        home.show(HomeRoot)

        home.shown(NO_BUILDS_DOMAINS_NOTE)
        home.shown("This account has no zones, so zone tiles are empty.")
    }

    // Building now and Watch

    @Test
    fun `no running builds says so`() {
        val home = startFlow()
        home.show(HomeRoot)

        home.shown(NO_BUILDS)
    }

    @Test
    fun `a running build opens its build detail`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        reveal("Running · main")
        home.clickText("Running · main")

        home.assertNavigatedTo(BuildDetail(PERSONAL_ACCOUNT, "build-1", "edge-api"))
    }

    @Test
    fun `Watch asks for the notification rationale and Not now still watches the build`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        tapDescription("Watch edge-api")
        home.assertTextShown(RATIONALE_TITLE)
        home.clickText("Not now")

        home.waitUntilTextGone(RATIONALE_TITLE)
        assertEquals(listOf(PERSONAL_ACCOUNT to "build-1"), builds.started.map { it.first to it.second.buildUuid })
        home.shown("Watching")
    }

    @Test
    fun `Continue on the rationale waits for the permission answer before watching`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        tapDescription("Watch edge-api")
        home.assertTextShown(RATIONALE_TITLE)
        home.clickText("Continue")

        home.waitUntilTextGone(RATIONALE_TITLE)
        assertTrue("nothing is watched until the system answers", builds.started.isEmpty())
    }

    @Test
    fun `dismissing the rationale without a choice does not watch the build`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        tapDescription("Watch edge-api")
        home.assertTextShown(RATIONALE_TITLE)
        Espresso.pressBack()
        compose.waitForIdle()

        home.waitUntilTextGone(RATIONALE_TITLE)
        assertTrue("a dismissed rationale is a cancelled Watch", builds.started.isEmpty())
    }

    @Test
    fun `Watch with notifications already granted watches straight away`() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        tapDescription("Watch edge-api")

        assertEquals(listOf("build-1"), builds.started.map { it.second.buildUuid })
        home.shown("Watching")
    }

    @Test
    fun `Stop watching on a watched build stops the watch and offers Watch again`() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)
        tapDescription("Watch edge-api")
        home.shown("Watching")

        tapDescription("Stop watching edge-api")

        assertEquals(listOf("build-1"), builds.stopped)
        reveal("Running · main")
        compose.onNodeWithContentDescription("Watch edge-api").assertIsDisplayed()
    }

    // Workers and Domains cards

    @Test
    fun `a Worker with a running build has a status dot and See all opens the full Workers list`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("build-2", "image-resizer")))
        val home = startFlow()
        home.show(HomeRoot)
        home.shown("Workers · 3")

        // The builds list loads on its own call, so the status dot can arrive after the Workers card does.
        compose.waitUntil(FLOW_WAIT_TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription("A build is running").fetchSemanticsNodes().size == 1
        }
        assertEquals("one Worker is building", 1, compose.onAllNodesWithContentDescription("A build is running").fetchSemanticsNodes().size)
        compose.onAllNodes(hasText("See all") and hasClickAction())[0].performScrollTo().performClick()

        home.assertNavigatedTo(WorkersRoot)
    }

    @Test
    fun `domains show a friendly status word, never the raw word, and See all opens the domains list`() {
        val home = startFlow()
        home.show(HomeRoot)
        home.shown("Domains · 3")

        reveal(hasText("Pending setup", substring = true))
        assertTrue("the pending zone says Pending setup", compose.onAllNodesWithText("Pending setup", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the raw word is never shown", compose.onAllNodesWithText("pending").fetchSemanticsNodes().isEmpty())
        assertTrue("the raw word is never shown", compose.onAllNodesWithText("active").fetchSemanticsNodes().isEmpty())
        compose.onAllNodes(hasText("See all") and hasClickAction())[1].performScrollTo().performClick()

        home.assertNavigatedTo(DomainsRoot)
    }

    @Test
    fun `a domain with no status shows no dot and no status word`() {
        lists.zonesWithoutStatus = true
        val home = startFlow()
        home.show(HomeRoot)
        home.shown("Domains · 3")

        reveal("example.com")
        assertTrue("no status word", compose.onAllNodesWithText("Active", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue("no status dot", compose.onAllNodesWithContentDescription("Active").fetchSemanticsNodes().isEmpty())
        assertTrue("no status dot", compose.onAllNodesWithContentDescription("Status unknown").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a build with no branch or commit shows its status and never its build id`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Success(listOf(build("9f8e7d6c-raw", "edge-api", branch = null)))
        val home = startFlow()
        home.show(HomeRoot)

        home.shown("Running")

        assertTrue("the build id is not shown", compose.onAllNodesWithText("9f8e7d6c", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a Workers list that needs a permission says which permission`() {
        lists.forbidden += "workers"
        val home = startFlow()
        home.show(HomeRoot)

        home.shown("Workers needs the Workers Scripts Read permission")
    }

    @Test
    fun `a failed analytics call does not claim Workers had no requests`() {
        analytics.workersError = ApiError.Network("offline")
        val home = startFlow()
        home.show(HomeRoot)

        home.shown("edge-api")
        assertTrue("no count is invented", compose.onAllNodesWithText("No requests in this range").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a failed builds call offers Retry, which then shows the builds`() {
        builds.activeResponses.clear()
        builds.activeResponses += ApiResult.Failure(ApiError.Network("offline"))
        builds.activeResponses += ApiResult.Success(listOf(build("build-1", "edge-api")))
        val home = startFlow()
        home.show(HomeRoot)

        reveal("Retry")
        home.clickText("Retry")

        home.shown("Running · main")
    }
}
