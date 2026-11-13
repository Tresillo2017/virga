package com.tomasps.virga.feature.domains.ui

import com.tomasps.virga.core.navigation.ZoneDetail
import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.domains.dns.DnsRecordRepository
import com.tomasps.virga.feature.domains.dns.recordRowTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val MX_ROW_ID = "rec-com-04"
private const val TXT_ROW_ID = "rec-com-05"
private const val A_ROW_ID = "rec-com-01"
private const val MX_SUPPORTING = "MX · mail.example.com · TTL 3600"
private const val A_SUPPORTING = "A · 192.0.2.10 · TTL Auto · Proxied"
private const val CAA_SUPPORTING = "CAA · 0 issue \"ca.example\" · TTL 3600"
private const val IPV4_MESSAGE = "Enter an IPv4 address such as 192.0.2.1."
private const val FIELD_NAME = "Name"
private const val FIELD_CONTENT = "Content"
private const val FIELD_TTL = "TTL (1 is Auto, or 60 to 86400 seconds)"
private const val FIELD_COMMENT = "Comment"
private const val FIELD_TAGS = "Tags, comma separated (paid plans)"
private const val PROXY_LABEL = "Proxied through Cloudflare"
private const val SAVE = "Save"
private const val NEW_RECORD_TITLE = "New DNS record"
private const val EDIT_RECORD_TITLE = "Edit DNS record"
private const val TYPE_PICKER = "record-type-picker"

/** The DNS records tab: the list, its search and type filter, the record editor with validation, and deletes. */
@RunWith(RobolectricTestRunner::class)
class DnsRecordsFlowTest {

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
    fun `the records show their type, value, TTL and proxy state`() {
        flow.show(DnsRecords(ZONE_COM))

        flow.assertTextShown(A_SUPPORTING)
        flow.assertTextShown(MX_SUPPORTING)
        flow.assertTextShown("SRV · 10 5 5060 sip.example.com · TTL 3600")
        flow.assertTextShown(CAA_SUPPORTING)
        assertEquals(5, compose.countText("example.com"))
    }

    @Test
    fun `the search keeps only records whose name or content match`() {
        flow.show(DnsRecords(ZONE_COM))

        compose.typeIntoPlaceholder("Search records", "sip")

        flow.assertTextShown("SRV · 10 5 5060 sip.example.com · TTL 3600")
        assertEquals(0, compose.countText("example.com"))
    }

    @Test
    fun `a type filter shows only that type and says which type it is`() {
        flow.show(DnsRecords(ZONE_COM))

        flow.clickText("CAA")

        flow.assertTextShown(CAA_SUPPORTING)
        flow.assertTextShown("Showing CAA records only")
        flow.waitUntilTextGone(MX_SUPPORTING)
    }

    @Test
    fun `a type with no records shows an empty state, and its action lists every type again`() {
        flow.show(DnsRecords(ZONE_ORG))

        flow.clickText("CAA")
        flow.assertTextShown("No CAA records in this zone.")

        flow.clickText("Show all records")
        flow.assertTextShown("A · 192.0.2.20 · TTL 300 · DNS only")
    }

    @Test
    fun `a type filter chosen for one zone is not applied to another zone`() {
        flow.show(DnsRecords(ZONE_COM))
        flow.clickText("CAA")
        flow.assertTextShown("Showing CAA records only")

        flow.navigator.navigate(DnsRecords(ZONE_ORG))

        flow.assertTextShown("A · 192.0.2.20 · TTL 300 · DNS only")
        compose.assertNotShownText("Showing CAA records only")
    }

    @Test
    fun `a zone with no records says so`() {
        flow.show(DnsRecords(ZONE_NET))

        flow.assertTextShown("No DNS records yet.")
    }

    @Test
    fun `opening a record shows it for editing with its type fixed`() {
        flow.show(DnsRecords(ZONE_COM))

        compose.tapTag(recordRowTag(MX_ROW_ID))

        flow.waitForText(EDIT_RECORD_TITLE)
        flow.assertTextShown("Type: MX (cannot be changed)")
    }

    @Test
    fun `editing a record saves the change and the list shows it`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapTag(recordRowTag(MX_ROW_ID))

        flow.typeIntoField(FIELD_CONTENT, "mx2.example.com")
        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.waitUntilTextGone(EDIT_RECORD_TITLE)
        flow.assertTextShown("MX · mx2.example.com · TTL 3600")
        val stored = runBlocking { flow.get<DnsRecordRepository>().page(ZONE_COM, page = 1) } as ApiResult.Success
        assertEquals("mx2.example.com", stored.value.records.single { it.id == MX_ROW_ID }.content)
    }

    @Test
    fun `an invalid IPv4 address keeps the editor open, says why, and writes nothing`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")

        flow.typeIntoField(FIELD_NAME, "bad")
        flow.typeIntoField(FIELD_CONTENT, "999.1.1.1")
        flow.clickText(SAVE)

        flow.waitForText(IPV4_MESSAGE)
        flow.assertTextShown(NEW_RECORD_TITLE)
        assertEquals(7, storedCount(ZONE_COM))
    }

    @Test
    fun `a valid new A record is saved, listed, and the form is empty the next time`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        flow.typeIntoField(FIELD_NAME, "api")
        flow.typeIntoField(FIELD_CONTENT, "192.0.2.44")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("api.example.com")
        flow.assertTextShown("A · 192.0.2.44 · TTL Auto · DNS only")
        flow.waitUntilTextGone(NEW_RECORD_TITLE)
        compose.tapDescription("Add record")
        assertEquals(0, compose.countFieldsHolding("api"))
    }

    @Test
    fun `the proxy switch appears only for types that can be proxied`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        flow.assertTextShown(PROXY_LABEL)

        compose.tapTextInTag(TYPE_PICKER, "TXT")
        compose.assertNotShownText(PROXY_LABEL)

        compose.tapTextInTag(TYPE_PICKER, "CNAME")
        flow.assertTextShown(PROXY_LABEL)
    }

    @Test
    fun `a TTL outside 60 to 86400 is refused and 1 means Auto`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "TXT")
        flow.typeIntoField(FIELD_NAME, "ttl-check")
        flow.typeIntoField(FIELD_CONTENT, "hello")
        flow.typeIntoField(FIELD_TTL, "30")

        flow.clickText(SAVE)
        flow.waitForText("TTL must be 1 (Auto) or between 60 and 86400.")

        flow.typeIntoField(FIELD_TTL, "1")
        flow.clickText(SAVE)
        flow.waitForText("Record saved.")
        flow.assertTextShown("TXT · hello · TTL Auto")
    }

    @Test
    fun `an SRV record needs a weight`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "SRV")
        flow.typeIntoField(FIELD_NAME, "_sip._tcp")
        flow.typeIntoField("Priority", "10")
        flow.typeIntoField("Port", "5060")
        flow.typeIntoField("Target", "sip.example.com")

        flow.clickText(SAVE)

        flow.waitForText("This field is required.")
        assertEquals(7, storedCount(ZONE_COM))
    }

    @Test
    fun `a CAA tag must be issue, issuewild or iodef`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "CAA")
        flow.typeIntoField(FIELD_NAME, "caa-check")
        flow.typeIntoField("Flags", "0")
        flow.typeIntoField("Tag (issue, issuewild or iodef)", "bogus")
        flow.typeIntoField("Value", "ca.example")

        flow.clickText(SAVE)

        flow.waitForText("This value is not in the expected format.")
    }

    @Test
    fun `a TXT record needs content`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "TXT")
        flow.typeIntoField(FIELD_NAME, "empty-txt")

        flow.clickText(SAVE)

        flow.waitForText("This field is required.")
    }

    @Test
    fun `a comment over the free plan limit is refused`() {
        flow.show(DnsRecords(ZONE_ORG))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "TXT")
        flow.typeIntoField(FIELD_NAME, "long-comment")
        flow.typeIntoField(FIELD_CONTENT, "hello")
        flow.typeIntoField(FIELD_COMMENT, "x".repeat(101))

        flow.clickText(SAVE)

        flow.waitForText("Value is too long for this plan.")
    }

    @Test
    fun `tags are refused on the free plan`() {
        flow.show(DnsRecords(ZONE_ORG))
        compose.tapDescription("Add record")
        flow.assertTextShown("Tags need a paid plan.")
        compose.assertNotShownText(FIELD_TAGS)
    }

    @Test
    fun `tags are checked on a paid plan`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "TXT")
        flow.typeIntoField(FIELD_NAME, "tagged")
        flow.typeIntoField(FIELD_CONTENT, "hello")
        flow.typeIntoField(FIELD_TAGS, "no-colon")

        flow.clickText(SAVE)

        flow.waitForText("This value is not in the expected format.")
    }

    @Test
    fun `an HTTPS record saves with its priority, target and value`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "HTTPS")
        flow.typeIntoField(FIELD_NAME, "web")
        flow.typeIntoField("Priority", "1")
        flow.typeIntoField("Target", "svc.example.com")
        flow.typeIntoField("Value", "h2")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("web.example.com")
    }

    @Test
    fun `a PTR record must hold a hostname`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "PTR")
        flow.typeIntoField(FIELD_NAME, "1")
        flow.typeIntoField(FIELD_CONTENT, "not a host!")

        flow.clickText(SAVE)

        flow.waitForText("Enter a valid hostname.")
    }

    @Test
    fun `deleting from the editor asks first and removes the record`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapTag(recordRowTag(MX_ROW_ID))

        flow.clickText("Delete record")
        flow.assertTextShown("Delete records?")
        flow.clickText("Confirm")

        flow.waitForText("Deleted 1 record(s).")
        flow.waitUntilTextGone(MX_SUPPORTING)
    }

    @Test
    fun `cancelling a bulk delete keeps the selected records`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Select A example.com 192.0.2.10")

        compose.tapDescription("Delete selected records")
        flow.assertTextShown("Delete records?")
        flow.clickText("Cancel")

        flow.assertTextShown(A_SUPPORTING)
    }

    @Test
    fun `confirming a bulk delete removes every selected record`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Select A example.com 192.0.2.10")
        compose.tapDescription("Select MX example.com mail.example.com")

        compose.tapDescription("Delete selected records")
        flow.clickText("Confirm")

        flow.waitForText("Deleted 2 record(s).")
        flow.waitUntilTextGone(A_SUPPORTING)
        flow.waitUntilTextGone(MX_SUPPORTING)
    }

    @Test
    fun `a record's options menu deletes it after confirmation`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Options for TXT example.com v=spf1 -all")

        flow.clickText("Delete this record")
        flow.clickText("Confirm")

        flow.waitForText("Deleted 1 record(s).")
        assertEquals(6, storedCount(ZONE_COM))
    }

    @Test
    fun `cancelling a per-record delete keeps the record`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Options for TXT example.com v=spf1 -all")

        flow.clickText("Delete this record")
        flow.clickText("Cancel")

        assertEquals(7, storedCount(ZONE_COM))
        flow.assertTextShown("TXT · v=spf1 -all · TTL 3600")
    }

    private fun storedCount(zoneId: String): Int = runBlocking {
        val page = flow.get<DnsRecordRepository>().page(zoneId, page = 1)
        (page as ApiResult.Success).value.records.size
    }

    @Test
    fun `saving a record of another type drops the type filter so the new row shows`() {
        flow.show(DnsRecords(ZONE_COM))
        flow.clickText("CAA")
        flow.assertTextShown("Showing CAA records only")
        compose.tapDescription("Add record")
        flow.typeIntoField(FIELD_NAME, "api")
        flow.typeIntoField(FIELD_CONTENT, "192.0.2.44")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("A · 192.0.2.44 · TTL Auto · DNS only")
        compose.assertNotShownText("Showing CAA records only")
    }

    @Test
    fun `saving a record of the filtered type keeps the type filter`() {
        flow.show(DnsRecords(ZONE_COM))
        flow.clickText("CAA")
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "CAA")
        flow.typeIntoField(FIELD_NAME, "caa-kept")
        flow.typeIntoField("Flags", "0")
        flow.typeIntoField("Tag (issue, issuewild or iodef)", "issue")
        flow.typeIntoField("Value", "ca.example")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("Showing CAA records only")
    }

    @Test
    fun `the records menu offers BIND import and export`() {
        flow.show(DnsRecords(ZONE_COM))

        compose.tapDescription("More options")

        flow.assertTextShown("Import BIND file")
        flow.assertTextShown("Export BIND file")
    }

    @Test
    fun `a valid SRV record is saved and its data is shown in the row`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "SRV")
        flow.typeIntoField(FIELD_NAME, "_xmpp._tcp")
        flow.typeIntoField("Priority", "10")
        flow.typeIntoField("Weight", "5")
        flow.typeIntoField("Port", "5222")
        flow.typeIntoField("Target", "xmpp.example.com")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("SRV · 10 5 5222 xmpp.example.com · TTL Auto")
    }

    @Test
    fun `a valid CAA record is saved with its quoted value`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "CAA")
        flow.typeIntoField(FIELD_NAME, "caa-new")
        flow.typeIntoField("Flags", "0")
        flow.typeIntoField("Tag (issue, issuewild or iodef)", "issuewild")
        flow.typeIntoField("Value", "ca.example")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("CAA · 0 issuewild \"ca.example\" · TTL Auto")
    }

    @Test
    fun `a valid PTR record is saved`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "PTR")
        flow.typeIntoField(FIELD_NAME, "7")
        flow.typeIntoField(FIELD_CONTENT, "host.example.com")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("PTR · host.example.com · TTL Auto")
    }

    @Test
    fun `an MX record can be created with its priority and target`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "MX")
        flow.typeIntoField(FIELD_NAME, "mail2")
        flow.typeIntoField("Priority", "20")
        flow.typeIntoField(FIELD_CONTENT, "mx2.example.com")

        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("MX · mx2.example.com · TTL Auto")
    }

    @Test
    fun `an HTTPS priority that is not a number is refused`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        compose.tapTextInTag(TYPE_PICKER, "HTTPS")
        flow.typeIntoField(FIELD_NAME, "web2")
        flow.typeIntoField("Priority", "first")
        flow.typeIntoField("Target", "svc.example.com")

        flow.clickText(SAVE)

        flow.waitForText("Enter a whole number.")
        flow.assertTextShown(NEW_RECORD_TITLE)
    }

    @Test
    fun `turning the proxy on replaces the TTL with Auto and saves a proxied record`() {
        flow.show(DnsRecords(ZONE_COM))
        compose.tapDescription("Add record")
        flow.typeIntoField(FIELD_NAME, "proxied")
        flow.typeIntoField(FIELD_CONTENT, "192.0.2.45")

        compose.tapDescription(PROXY_LABEL)

        flow.assertTextShown("Proxied records use Auto TTL.")
        compose.assertNotShownText(FIELD_TTL)
        flow.clickText(SAVE)

        flow.waitForText("Record saved.")
        flow.assertTextShown("A · 192.0.2.45 · TTL Auto · Proxied")
    }
}
