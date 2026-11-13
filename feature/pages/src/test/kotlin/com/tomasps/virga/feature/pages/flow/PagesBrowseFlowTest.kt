package com.tomasps.virga.feature.pages.flow

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.navigation.PagesRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.pages.PagesDeploymentDetail
import com.tomasps.virga.feature.pages.data.PagesDomainsRepository
import com.tomasps.virga.feature.pages.ui.PAGES_DASHBOARD_URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DNS_STUB = "DNS stub screen"
private const val DOCS = "docs.lantern.example"
private const val SHOP = "shop.lantern.example"
private const val MISSING_PROJECT = "missing-project"
private const val NOT_FOUND_MESSAGE = "That item no longer exists, or this token cannot see it."
private const val NOTE_WORKERS = "Cloudflare recommends Workers for new projects. Pages keeps working."

/** Pages list, project header, deployments and domains, in demo mode through the real Koin graph and registration. */
@RunWith(RobolectricTestRunner::class)
class PagesBrowseFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = pagesFlow(compose) { registry ->
            registry.route(DnsRecords.serializer()) { Text(DNS_STUB) }
        }
    }

    @After
    fun tearDown() {
        flow.close()
    }

    // --- Projects list -------------------------------------------------------------------------

    @Test
    fun `the project list shows the demo projects with their branch and newest status, and the Workers note`() {
        flow.show(PagesRoot)

        flow.assertTextShown(LANTERN)
        flow.assertTextShown(TIDEWATER)
        flow.assertTextShown("Branch main · docs.lantern.example · lantern-docs")
        flow.assertTextShown("Running")
        flow.assertTextShown("Success")
        flow.assertTextShown(NOTE_WORKERS)
    }

    @Test
    fun `search narrows the list, and a query with no match shows the no-match state`() {
        flow.show(PagesRoot)

        compose.onNode(hasSetTextAction()).performTextReplacement("tide")
        compose.waitForIdle()
        flow.assertTextShown(TIDEWATER)
        compose.onAllNodes(hasText(LANTERN)).assertCountEquals(0)

        compose.onNode(hasSetTextAction()).performTextReplacement("zzz-nothing")
        compose.waitForIdle()
        flow.assertTextShown("No matches")
        flow.assertTextShown("No project name, domain, branch or repository matches your search.")
    }

    @Test
    fun `tapping a project opens its detail`() {
        flow.show(PagesRoot)

        compose.tapText(LANTERN)

        flow.assertNavigatedTo(PagesProject(LANTERN))
        flow.waitForText("Subdomain")
    }

    @Test
    fun `the create-in-dashboard row opens the Cloudflare dashboard in the browser`() {
        flow.show(PagesRoot)

        compose.tapText("Create a project in the dashboard")

        assertEquals(listOf(PAGES_DASHBOARD_URL), flow.openedUrls)
    }

    // --- Project header and tabs ---------------------------------------------------------------

    @Test
    fun `the project header shows the subdomain and production branch, and the three tabs are offered`() {
        flow.show(PagesProject(LANTERN))

        flow.assertTextShown("lantern-docs.pages.dev")
        flow.assertTextShown("Production branch")
        flow.assertTextShown("main")
        flow.assertTextShown("Deployments")
        flow.assertTextShown("Domains")
        flow.assertTextShown("Settings")
    }

    @Test
    fun `a project that fails to load shows the error in the header and in Settings, not a spinner`() {
        flow.show(PagesProject(MISSING_PROJECT))
        // The header and the deployments tab both report the failure.
        compose.onAllNodes(hasText("Not found")).assertCountEquals(2)

        compose.tapText("Settings")

        // The header and Settings both report the failure, each with a retry, and Settings never spins forever.
        // Not found is not retryable, so the screens show the explanation and no retry button.
        compose.onAllNodes(hasText("Not found")).assertCountEquals(2)
        compose.onAllNodes(hasText(NOT_FOUND_MESSAGE)).assertCountEquals(2)
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
    }

    // --- Deployments tab -----------------------------------------------------------------------

    @Test
    fun `the deployments tab lists every deployment, marks the live production one, and shows a readable date`() {
        flow.show(PagesProject(LANTERN))

        flow.assertTextShown("Current production: Update pricing page")
        flow.assertTextShown("Add search index")
        flow.assertTextShown("Try new image pipeline")
        flow.assertTextShown("Fix footer links")
        flow.assertTextShown("Initial site")
        flow.assertTextShown("Preview · feature/search · Nov 11, 2026")
        compose.onAllNodes(hasText("2026-11-09T14:02:11Z")).assertCountEquals(0)
    }

    @Test
    fun `tapping a deployment opens its detail with the project and deployment ids`() {
        flow.show(PagesProject(LANTERN))

        compose.tapText("Try new image pipeline")

        flow.assertNavigatedTo(PagesDeploymentDetail(LANTERN, "lantern-prev-4"))
        flow.waitForText("Build")
    }

    // --- Domains tab ---------------------------------------------------------------------------

    @Test
    fun `the domains tab lists each domain with its status and the validation record to publish`() {
        flow.show(PagesProject(LANTERN))

        compose.tapText("Domains")

        flow.assertTextShown(DOCS)
        flow.assertTextShown("Active")
        flow.assertTextShown(SHOP)
        flow.assertTextShown("Waiting for DNS")
        flow.assertTextShown("_cf-custom-hostname.docs")
        flow.assertTextShown("demo-docs-token")
    }

    @Test
    fun `an invalid hostname is refused under the form and nothing is added`() = runBlocking {
        flow.show(PagesProject(LANTERN))
        compose.tapText("Domains")

        flow.typeIntoField("Hostname, for example docs.example.com", "not a host")
        compose.tapText("Add domain")

        flow.assertTextShown("Enter a valid hostname, such as docs.example.com.")
        assertEquals(2, domainCount())
    }

    @Test
    fun `a valid hostname is added, listed and counted`() = runBlocking {
        flow.show(PagesProject(LANTERN))
        compose.tapText("Domains")

        flow.typeIntoField("Hostname, for example docs.example.com", "API.Lantern.Example")
        compose.tapText("Add domain")

        flow.assertTextShown("Domain added.")
        compose.scrollToText("api.lantern.example")
        flow.assertTextShown("api.lantern.example")
        assertEquals(3, domainCount())
    }

    @Test
    fun `retrying validation on a waiting domain makes it active`() {
        flow.show(PagesProject(LANTERN))
        compose.tapText("Domains")

        compose.tapText("Retry validation", index = 1)

        flow.waitUntilTextGone("Waiting for DNS")
        compose.onAllNodes(hasText("Active")).assertCountEquals(2)
    }

    @Test
    fun `removing a domain asks first - cancel keeps it, confirm deletes it`() = runBlocking {
        flow.show(PagesProject(LANTERN))
        compose.tapText("Domains")

        compose.tapText("Remove", index = 1)
        compose.assertDialogText("Remove this domain?")
        compose.tapInDialog("Cancel")
        compose.assertNoDialogText("Remove this domain?")
        assertEquals(2, domainCount())

        compose.tapText("Remove", index = 1)
        compose.assertDialogText("Remove this domain?")
        compose.tapInDialog("Remove")

        flow.assertTextShown("Domain removed.")
        flow.waitUntilTextGone(SHOP)
        assertEquals(1, domainCount())
    }

    @Test
    fun `the DNS records button opens the zone's DNS screen`() {
        flow.show(PagesProject(LANTERN))
        compose.tapText("Domains")

        compose.tapText("DNS records", index = 0)

        flow.assertNavigatedTo(DnsRecords("demo-zone-lantern"))
        flow.assertTextShown(DNS_STUB)
    }

    @Test
    fun `a project with no custom domains shows the empty state`() {
        flow.show(PagesProject(TIDEWATER))

        compose.tapText("Domains")

        flow.assertTextShown("No custom domains")
        flow.assertTextShown("Add a hostname below, then publish the validation record at your DNS provider.")
    }

    private suspend fun domainCount(): Int =
        when (val result = flow.get<PagesDomainsRepository>().domains(DEMO_ACCOUNT, LANTERN)) {
            is ApiResult.Success -> result.value.size
            is ApiResult.Failure -> -1
        }
}
