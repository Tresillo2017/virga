package com.tomasps.virga.feature.domains.ui

import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.DnsRecords
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.domains.DASHBOARD_URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The zone overview: status, traffic, development mode, pause and purge, routes, registrar and the links out. */
@RunWith(RobolectricTestRunner::class)
class ZoneDetailFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = domainsFlow(compose)
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `the overview shows a friendly status, the plan and the nameservers`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.waitForText("Active")
        flow.assertTextShown("Pro Plan")
        flow.assertTextShown("ada.ns.example\nben.ns.example")
    }

    @Test
    fun `the registrar shows the expiry as a date and no raw timestamp`() {
        flow.show(ZoneDetail(ZONE_COM))

        compose.scrollToText("Expires")
        flow.assertTextShown("Jun 30, 2027")
        flow.assertTextShown("Redacted")
        compose.assertNotShownText("2027-06-30T00:00:00Z")
        compose.assertNotShownText("redaction")
    }

    @Test
    fun `a domain not bought through the registrar says so`() {
        flow.show(ZoneDetail(ZONE_ORG))

        compose.scrollToText("This domain was not bought through Cloudflare Registrar.")
        flow.assertTextShown("This domain was not bought through Cloudflare Registrar.")
    }

    @Test
    fun `the traffic tiles and the requests chart show for the demo zone`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.assertTextShown("Unique visitors")
        flow.assertTextShown("Cached")
        flow.boundsOfDescription("Requests over time")
    }

    @Test
    fun `choosing a time range reloads the traffic without losing the chart`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.clickText("7d")

        flow.boundsOfDescription("Requests over time")
    }

    @Test
    fun `turning development mode on shows the countdown`() {
        flow.show(ZoneDetail(ZONE_COM))
        flow.assertTextShown("Off")

        compose.tapDescription("Development mode")

        compose.assertTextContaining("On, ")
        compose.assertTextContaining("left")
    }

    @Test
    fun `pausing asks first and cancelling leaves the zone running`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.clickText("Pause")
        flow.assertTextShown("Pause this domain?")
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Pause this domain?")
        flow.assertTextShown("Pause")
        flow.assertTextShown("Pro Plan")
    }

    @Test
    fun `confirming a pause pauses the zone and offers resume`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.clickText("Pause")
        flow.clickText("Confirm")

        flow.waitForText("Domain paused.")
        flow.assertTextShown("Resume")
    }

    @Test
    fun `resuming a paused zone asks first and then resumes it`() {
        flow.show(ZoneDetail(ZONE_ORG))
        flow.assertTextShown("Resume")

        flow.clickText("Resume")
        flow.assertTextShown("Resume this domain?")
        flow.clickText("Confirm")

        flow.waitForText("Domain resumed.")
        flow.assertTextShown("Pause")
    }

    @Test
    fun `purge with the everything choice asks to confirm before it runs`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.clickText("Purge cache")
        flow.clickText("Continue")

        flow.assertTextShown("Purge cache?")
        flow.clickText("Confirm")
        flow.waitForText("Cache purge requested.")
    }

    @Test
    fun `purge by URL refuses a URL without a scheme and keeps what was typed`() {
        flow.show(ZoneDetail(ZONE_COM))
        flow.clickText("Purge cache")
        flow.clickText("URLs")
        flow.typeIntoField("Full URLs with https://, one per line", "example.com/logo.png")

        flow.clickText("Continue")

        flow.waitForText("Each URL needs https:// or http://.")
        flow.assertTextShown("Continue")
        assertEquals(1, compose.countFieldsHolding("example.com/logo.png"))
    }

    @Test
    fun `purge by host refuses a scheme and accepts a bare hostname`() {
        flow.show(ZoneDetail(ZONE_COM))
        flow.clickText("Purge cache")
        flow.clickText("Hosts")
        flow.typeIntoField("Hostnames, one per line", "https://www.example.com")

        flow.clickText("Continue")
        flow.waitForText("Remove https:// or http:// from each item.")

        flow.typeIntoField("Hostnames, one per line", "www.example.com")
        flow.clickText("Continue")
        flow.assertTextShown("Purge cache?")
    }

    @Test
    fun `purge by prefix refuses a scheme and purge by tag asks to confirm`() {
        flow.show(ZoneDetail(ZONE_COM))
        flow.clickText("Purge cache")
        flow.clickText("Prefixes")
        flow.typeIntoField("Hostname and path without a scheme, for example example.com/images", "https://example.com/images")

        flow.clickText("Continue")
        flow.waitForText("Remove https:// or http:// from each item.")

        flow.clickText("Tags")
        flow.typeIntoField("Cache tags, one per line", "product-42")
        flow.clickText("Continue")

        flow.assertTextShown("Purge cache?")
    }

    @Test
    fun `purge by URL with nothing typed says that at least one item is needed`() {
        flow.show(ZoneDetail(ZONE_COM))
        flow.clickText("Purge cache")
        flow.clickText("URLs")

        flow.clickText("Continue")

        flow.waitForText("Enter at least one item to purge.")
    }

    @Test
    fun `a worker route opens the worker it runs`() {
        flow.show(ZoneDetail(ZONE_COM))

        compose.scrollToText("example.com/api/*")
        flow.clickText("demo-api-worker")

        flow.assertNavigatedTo(WorkerDetail("demo-api-worker"))
    }

    @Test
    fun `a route with no worker attached is shown without a link`() {
        flow.show(ZoneDetail(ZONE_COM))

        compose.scrollToText("No Worker attached")

        compose.onAllNodes(hasText("example.com/static/*") and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `a zone with no worker routes says so`() {
        flow.show(ZoneDetail(ZONE_ORG))

        compose.scrollToText("No Worker routes on this zone.")
        flow.assertTextShown("No Worker routes on this zone.")
    }

    @Test
    fun `the DNS row opens the zone's DNS records`() {
        flow.show(ZoneDetail(ZONE_COM))

        flow.clickText("DNS records")

        flow.assertNavigatedTo(DnsRecords(ZONE_COM))
    }

    @Test
    fun `the zone settings row opens the dashboard`() {
        flow.show(ZoneDetail(ZONE_COM))

        compose.scrollToText("More zone settings")
        flow.clickText("More zone settings")

        assertEquals(listOf(DASHBOARD_URL), flow.openedUrls)
    }

    @Test
    fun `a zone that does not exist shows a not-found message and no retry`() {
        flow.show(ZoneDetail("zone-that-does-not-exist"))

        flow.assertTextShown("Not found")
        flow.assertTextShown("That item no longer exists, or this token cannot see it.")
        compose.assertNotShownText("Retry")
    }

    @Test
    fun `the back navigation returns to the domain list`() {
        flow.showStack(DomainsRoot, ZoneDetail(ZONE_COM))

        flow.navigator.back()

        flow.assertTextShown("example.org")
    }

    @Test
    fun `the zone settings row and the Under Attack row both open the dashboard`() {
        flow.show(ZoneDetail(ZONE_COM))

        compose.scrollToText("Under Attack mode")
        flow.clickText("Under Attack mode")

        assertEquals(listOf(DASHBOARD_URL), flow.openedUrls)
    }

    @Test
    fun `every row of the overview starts on the same edge, and every header on another`() {
        flow.show(ZoneDetail(ZONE_COM))
        val headerEdge = compose.leftEdgeOf("Traffic")
        val rowEdge = compose.leftEdgeOf("Plan")

        assertEquals(rowEdge, compose.leftEdgeOf("Nameservers"))
        assertEquals(rowEdge, compose.leftEdgeOf("DNS records"))
        assertEquals(rowEdge, compose.leftEdgeOf("Development mode"))
        assertEquals(rowEdge, compose.leftEdgeOf("More zone settings"))
        compose.scrollToText("Worker routes")
        assertEquals(headerEdge, compose.leftEdgeOf("Worker routes"))
        assertTrue(rowEdge > headerEdge)
    }

    @Test
    fun `the overview rows and the DNS settings rows start on one edge`() {
        flow.show(ZoneDetail(ZONE_COM))
        val rowEdge = compose.leftEdgeOf("Plan")

        flow.clickText("DNS records")
        flow.clickText("DNS settings")

        compose.scrollToText("Zone mode")
        assertEquals(rowEdge, compose.leftEdgeOf("Zone mode"))
        compose.scrollToText("Change zone mode and nameservers")
        assertEquals(rowEdge, compose.leftEdgeOf("Change zone mode and nameservers"))
    }

    @Test
    fun `the overflow menu offers to delete the domain`() {
        flow.show(ZoneDetail(ZONE_COM))

        compose.tapDescription("More options")

        flow.assertTextShown("Delete domain")
    }

    @Test
    fun `cancelling the purge confirmation runs no purge`() {
        flow.show(ZoneDetail(ZONE_COM))
        flow.clickText("Purge cache")
        flow.clickText("Continue")
        flow.assertTextShown("Purge cache?")

        flow.clickText("Cancel")

        flow.waitUntilTextGone("Purge cache?")
        compose.assertNotShownText("Cache purge requested.")
    }

    @Test
    fun `turning development mode off again shows it as off`() {
        flow.show(ZoneDetail(ZONE_COM))
        compose.tapDescription("Development mode")
        compose.assertTextContaining("On, ")

        compose.tapDescription("Development mode")

        flow.assertTextShown("Off")
    }
}
