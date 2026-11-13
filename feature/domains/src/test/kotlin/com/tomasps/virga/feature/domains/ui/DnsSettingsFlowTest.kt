package com.tomasps.virga.feature.domains.ui

import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.domains.DASHBOARD_URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

private const val DS_RECORD = "example.com. 3600 IN DS 2371 13 2 0A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F9"
private const val ENABLE_DNSSEC = "Enable DNSSEC"
private const val DISABLE_DNSSEC = "Disable DNSSEC"

/** The DNS settings tab: DNSSEC with its DS record, the enable and disable confirmations, and the read-only zone settings. */
@RunWith(RobolectricTestRunner::class)
class DnsSettingsFlowTest {

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
    fun `DNSSEC starts disabled, with an enable action and no DS record`() {
        openSettingsTab(ZONE_COM)

        flow.assertTextShown("Disabled")
        flow.assertTextShown(ENABLE_DNSSEC)
        compose.assertNotShownText(DS_RECORD)
    }

    @Test
    fun `cancelling the enable confirmation leaves DNSSEC disabled`() {
        openSettingsTab(ZONE_COM)

        flow.clickText(ENABLE_DNSSEC)
        flow.assertTextShown("Enable DNSSEC?")
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Enable DNSSEC?")
        flow.assertTextShown("Disabled")
    }

    @Test
    fun `confirming enable shows the DS record for the registrar in full`() {
        openSettingsTab(ZONE_COM)

        flow.clickText(ENABLE_DNSSEC)
        flow.clickText("Confirm")

        flow.waitForText("DNSSEC enabled. Add the DS record at your registrar.")
        flow.assertTextShown("Active")
        flow.assertTextShown(DS_RECORD)
        assertTrue(flow.boundsOf(DS_RECORD).right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
    }

    @Test
    fun `the copy action puts the DS record on the clipboard`() {
        openSettingsTab(ZONE_COM)
        flow.clickText(ENABLE_DNSSEC)
        flow.clickText("Confirm")
        flow.waitForText(DS_RECORD)

        flow.clickText("Copy DS record")

        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals(DS_RECORD, clipboard?.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `disabling DNSSEC asks first and then turns it off`() {
        openSettingsTab(ZONE_COM)
        flow.clickText(ENABLE_DNSSEC)
        flow.clickText("Confirm")
        flow.waitForText(DS_RECORD)

        flow.clickText(DISABLE_DNSSEC)
        flow.assertTextShown("Disable DNSSEC?")
        flow.clickText("Confirm")

        flow.waitForText("DNSSEC disabled.")
        flow.assertTextShown("Disabled")
        compose.assertNotShownText(DS_RECORD)
    }

    @Test
    fun `the zone DNS settings read in words, not as API ids`() {
        openSettingsTab(ZONE_COM)

        flow.assertTextShown("Zone DNS settings")
        flow.assertTextShown("Standard")
        flow.assertTextShown("Cloudflare standard")
        flow.assertTextShown("86400")
        compose.assertNotShownText("cloudflare.standard")
    }

    @Test
    fun `the dashboard row for zone DNS changes opens the dashboard`() {
        openSettingsTab(ZONE_COM)

        compose.scrollToText("Change zone mode and nameservers")
        flow.clickText("Change zone mode and nameservers")

        assertEquals(listOf(DASHBOARD_URL), flow.openedUrls)
    }

    private fun openSettingsTab(zoneId: String) {
        flow.show(DnsRecords(zoneId))
        flow.clickText("DNS settings")
    }

    @Test
    fun `the DNS settings tab lines up its headers and rows on one edge each`() {
        openSettingsTab(ZONE_COM)
        val dnssecHeaderEdge = compose.leftEdgeOf("DNSSEC")
        val statusEdge = compose.leftEdgeOf("Status")

        compose.scrollToText("Zone DNS settings")
        val zoneHeaderEdge = compose.leftEdgeOf("Zone DNS settings")
        val zoneModeEdge = compose.leftEdgeOf("Zone mode")

        assertEquals(dnssecHeaderEdge, zoneHeaderEdge)
        assertEquals(statusEdge, zoneModeEdge)
        assertTrue(statusEdge > zoneHeaderEdge)
    }

    @Test
    fun `cancelling the disable confirmation leaves DNSSEC on`() {
        openSettingsTab(ZONE_COM)
        flow.clickText(ENABLE_DNSSEC)
        flow.clickText("Confirm")
        flow.waitForText(DS_RECORD)

        flow.clickText(DISABLE_DNSSEC)
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Disable DNSSEC?")
        flow.assertTextShown("Active")
        flow.assertTextShown(DS_RECORD)
    }

    @Test
    fun `a zone that does not exist shows DNSSEC as not found and still lists the zone DNS settings`() {
        openSettingsTab("zone-that-does-not-exist")

        flow.assertTextShown("Not found")
        compose.scrollToText("Zone mode")
        flow.assertTextShown("Standard")
    }
}
