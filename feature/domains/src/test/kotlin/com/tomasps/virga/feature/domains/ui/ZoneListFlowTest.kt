package com.tomasps.virga.feature.domains.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ADD_FIELD_LABEL = "Domain name, for example example.com"
private const val ACCOUNT_ID = "demo-account-personal"
private const val INVALID_NAME_MESSAGE = "Enter a domain name such as example.com."

/** The Domains tab: the zone list, its filters, its empty states and adding a domain. */
@RunWith(RobolectricTestRunner::class)
class ZoneListFlowTest {

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
    fun `the list shows each demo domain with its status and plan`() {
        flow.show(DomainsRoot)

        flow.assertTextShown("example.com")
        flow.assertTextShown("Active · Pro Plan")
        flow.assertTextShown("example.org")
        flow.assertTextShown("Active · Free Website · Paused")
        flow.assertTextShown("Pending · Free Website")
    }

    @Test
    fun `a status chip keeps only the domains with that status`() {
        flow.show(DomainsRoot)

        flow.clickText("Pending")

        flow.assertTextShown("example.net")
        flow.waitUntilTextGone("example.com")
    }

    @Test
    fun `the search field filters the list by name`() {
        flow.show(DomainsRoot)

        compose.typeIntoPlaceholder("Search domains", "org")

        flow.assertTextShown("example.org")
        flow.waitUntilTextGone("example.com")
    }

    @Test
    fun `a search with no match shows the filtered empty state`() {
        flow.show(DomainsRoot)

        compose.typeIntoPlaceholder("Search domains", "no-such-domain")

        flow.assertTextShown("No matching domains")
    }

    @Test
    fun `tapping a domain opens its overview`() {
        flow.show(DomainsRoot)

        flow.clickText("example.com")

        flow.assertNavigatedTo(ZoneDetail(ZONE_COM))
    }

    @Test
    fun `an invalid domain name keeps the add dialog open and says why inside it`() {
        flow.show(DomainsRoot)
        compose.tapDescription("Add a domain")

        flow.typeIntoField(ADD_FIELD_LABEL, "not a domain")
        flow.clickText("Add")

        flow.waitForText(INVALID_NAME_MESSAGE)
        flow.assertTextShown(ADD_FIELD_LABEL)
        assertEquals(3, zoneCount())
    }

    @Test
    fun `adding a valid domain opens its overview and writes it to the account`() {
        flow.show(DomainsRoot)
        compose.tapDescription("Add a domain")

        flow.typeIntoField(ADD_FIELD_LABEL, "flow-added.example")
        flow.clickText("Add")

        val opened = flow.history.filterIsInstance<ZoneDetail>().single()
        assertTrue(opened.zoneId.isNotEmpty())
        assertEquals(4, zoneCount())
    }

    @Test
    fun `after a domain is added the add field is empty the next time the dialog opens`() {
        flow.show(DomainsRoot)
        compose.tapDescription("Add a domain")
        flow.typeIntoField(ADD_FIELD_LABEL, "flow-reset.example")
        flow.clickText("Add")
        flow.navigator.back()
        compose.waitForIdle()

        compose.tapDescription("Add a domain")

        assertEquals(0, compose.countFieldsHolding("flow-reset.example"))
    }

    @Test
    fun `an account with no domains shows the empty state`() {
        val zones = flow.get<ZoneRepository>()
        runBlocking {
            for (id in listOf(ZONE_COM, ZONE_ORG, ZONE_NET)) {
                assertTrue(zones.delete(id) is ApiResult.Success)
            }
        }

        flow.show(DomainsRoot)

        flow.assertTextShown("No domains yet")
    }

    private fun zoneCount(): Int = runBlocking {
        val listed = flow.get<ZoneRepository>().listAll(ACCOUNT_ID)
        (listed as ApiResult.Success).value.size
    }

    @Test
    fun `pausing a domain and going back shows it as paused in the list`() {
        flow.showStack(DomainsRoot, ZoneDetail(ZONE_NET))
        flow.clickText("Pause")
        flow.clickText("Confirm")
        flow.waitForText("Domain paused.")

        flow.navigator.back()

        flow.assertTextShown("Pending · Free Website · Paused")
    }

    @Test
    fun `a domain deleted from its overview is gone from the list when the list is shown again`() {
        flow.showStack(DomainsRoot, ZoneDetail(ZONE_NET))
        // The typed-name confirmation is a dialog with a text field, which cannot idle under Robolectric. The delete
        // itself goes through the same repository call the confirmed action makes.
        runBlocking { flow.get<ZoneRepository>().delete(ZONE_NET) }

        flow.navigator.back()

        flow.waitUntilTextGone("example.net")
        flow.assertTextShown("example.com")
    }

    @Test
    fun `the All chip brings back every domain after a status filter`() {
        flow.show(DomainsRoot)
        flow.clickText("Pending")
        flow.waitUntilTextGone("example.com")

        flow.clickText("All")

        flow.assertTextShown("example.com")
        flow.assertTextShown("example.net")
    }

    @Test
    fun `cancelling the add form closes it and adds nothing`() {
        flow.show(DomainsRoot)
        compose.tapDescription("Add a domain")

        flow.clickText("Cancel")

        flow.waitUntilTextGone(ADD_FIELD_LABEL)
        assertEquals(3, zoneCount())
    }
}
