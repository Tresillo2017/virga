package com.tomasps.virga.feature.domains.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.domains.dns.DnsRecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A BIND zone file uploaded to a demo zone. The file picker itself cannot run under Robolectric, so the test sends the
 * bytes the picker would have read, through the repository the screen uses, and then shows the records tab.
 */
@RunWith(RobolectricTestRunner::class)
class BindImportFlowTest {

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
    fun `an uploaded BIND file adds its records to the zone`() {
        val imported = runBlocking {
            flow.get<DnsRecordRepository>().importBind(ZONE_NET, "zone.bind", REALISTIC_ZONE_FILE.toByteArray())
        }
        assertEquals(true, imported is ApiResult.Success)

        flow.show(DnsRecords(ZONE_NET))

        assertEquals(2, compose.countText("www.example.net"))
        flow.assertTextShown("A · 192.0.2.10 · TTL 300 · DNS only")
        flow.assertTextShown("A · 192.0.2.11 · TTL 300 · DNS only")
        flow.assertTextShown("MX · mx.example.net · TTL 300")
        flow.assertTextShown("TXT · v=spf1 a mx -all · TTL 300")
    }

    @Test
    fun `the import leaves out the SOA line and keeps the four records that remain`() {
        runBlocking {
            flow.get<DnsRecordRepository>().importBind(ZONE_NET, "zone.bind", REALISTIC_ZONE_FILE.toByteArray())
        }

        flow.show(DnsRecords(ZONE_NET))

        assertEquals(4, storedCount())
    }

    private fun storedCount(): Int = runBlocking {
        val page = flow.get<DnsRecordRepository>().page(ZONE_NET, page = 1)
        (page as ApiResult.Success).value.records.size
    }

    private companion object {
        /** A zone file as BIND writes it: directives, a multi-line SOA, an owner-less line, a quoted TXT, and comments. */
        val REALISTIC_ZONE_FILE = """
            ${'$'}ORIGIN example.net.
            ${'$'}TTL 300
            @       IN  SOA ns1.example.net. hostmaster.example.net. (
                            2026110101 ; serial
                            7200       ; refresh
                            3600       ; retry
                            1209600    ; expire
                            300 )      ; minimum
            www     IN  A     192.0.2.10
                    IN  A     192.0.2.11   ; owner-less line: same name as the line above
            mail    IN  MX    10 mx.example.net.
            @       IN  TXT   "v=spf1 a mx -all"
        """.trimIndent()
    }

    @Test
    fun `the two www records of the file have their own checkboxes, so one can be selected`() {
        runBlocking {
            flow.get<DnsRecordRepository>().importBind(ZONE_NET, "zone.bind", REALISTIC_ZONE_FILE.toByteArray())
        }
        flow.show(DnsRecords(ZONE_NET))

        compose.tapDescription("Select A www.example.net 192.0.2.11")

        compose.onNodeWithContentDescription("Delete selected records").assertIsDisplayed()
    }
}
