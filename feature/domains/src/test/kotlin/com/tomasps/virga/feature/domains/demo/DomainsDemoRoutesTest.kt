package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.domains.analytics.ZoneTrafficRepository
import com.tomasps.virga.feature.domains.dns.DnsRecordRepository
import com.tomasps.virga.feature.domains.dns.DnssecRepository
import com.tomasps.virga.feature.domains.dns.ZoneDnsSettingsRepository
import com.tomasps.virga.feature.domains.dns.ZoneMode
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import com.tomasps.virga.feature.domains.zones.ZoneSettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsDemoRoutesTest {

    private val api = mockCloudflareApi { DomainsDemoRoutes().contribute(this) }

    @Test
    fun `demo account lists the canned zones under the demo account id`() = runBlocking {
        // Arrange / Act
        val result = ZoneRepository(api).listAll(DEMO_ZONE_ACCOUNT_ID)

        // Assert
        val names = (result as ApiResult.Success).value.map { it.name }
        assertEquals(listOf("example.com", "example.org", "example.net"), names)
    }

    @Test
    fun `zone created in the demo gets a 32-hex id and reports traffic`() = runBlocking {
        // Arrange
        val zones = ZoneRepository(api)

        // Act
        val created = (zones.create(DEMO_ZONE_ACCOUNT_ID, "shop.example") as ApiResult.Success).value
        val traffic = (ZoneTrafficRepository(api.graphQl).traffic(created.id, TimeRange.TWENTY_FOUR_HOURS) as ApiResult.Success).value

        // Assert
        assertTrue(Regex("[0-9a-f]{32}").matches(created.id))
        assertTrue(traffic.requests > 0)
        assertTrue(traffic.series.isNotEmpty())
    }

    @Test
    fun `development mode switched on in the demo reads back on as the next value`() = runBlocking {
        // Arrange
        val settings = ZoneSettingsRepository(api)

        // Act
        settings.setDevelopmentMode(DEMO_ZONE_EXAMPLE_COM, on = true)
        val read = settings.developmentMode(DEMO_ZONE_EXAMPLE_COM)

        // Assert
        assertTrue((read as ApiResult.Success).value.isOn)
    }

    @Test
    fun `bulk delete in the demo removes those records from later reads`() = runBlocking {
        // Arrange
        val dns = DnsRecordRepository(api)
        val before = (dns.page(DEMO_ZONE_EXAMPLE_COM, page = 1) as ApiResult.Success).value.records
        val target = before.first().id

        // Act
        dns.deleteBatch(DEMO_ZONE_EXAMPLE_COM, listOf(target))
        val after = (dns.page(DEMO_ZONE_EXAMPLE_COM, page = 1) as ApiResult.Success).value.records

        // Assert
        assertEquals(before.size - 1, after.size)
        assertTrue(after.none { it.id == target })
    }

    @Test
    fun `DNSSEC enabled in the demo returns a DS record`() = runBlocking {
        // Arrange
        val dnssec = DnssecRepository(api)

        // Act
        dnssec.setEnabled(DEMO_ZONE_EXAMPLE_COM, enabled = true)
        val details = (dnssec.details(DEMO_ZONE_EXAMPLE_COM) as ApiResult.Success).value

        // Assert
        assertTrue(details.ds.orEmpty().isNotBlank())
    }

    @Test
    fun `the demo serves zone DNS settings in the reference shape`() = runBlocking {
        // Arrange
        val settings = ZoneDnsSettingsRepository(api)

        // Act
        val read = (settings.settings(DEMO_ZONE_EXAMPLE_COM) as ApiResult.Success).value

        // Assert
        assertEquals(ZoneMode.STANDARD, read.zoneMode)
        assertEquals("cloudflare.standard", read.nameserverType)
    }

    @Test
    fun `the demo lists every record of a zone through the paging helper`() = runBlocking {
        // Arrange
        val dns = DnsRecordRepository(api)

        // Act
        val result = (dns.page(DEMO_ZONE_EXAMPLE_COM, page = 1, perPage = 100) as ApiResult.Success).value

        // Assert
        assertTrue(result.records.isNotEmpty())
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `a record typed with a short name is stored under the full name in its zone`() = runBlocking {
        // Arrange
        val dns = DnsRecordRepository(api)
        val body = buildJsonObject {
            put("type", "A")
            put("name", "api")
            put("content", "192.0.2.50")
        }

        // Act
        val created = (dns.create(DEMO_ZONE_EXAMPLE_COM, body) as ApiResult.Success).value
        val apex = (dns.create(DEMO_ZONE_EXAMPLE_COM, buildJsonObject { put("type", "TXT"); put("name", "@"); put("content", "x") }) as ApiResult.Success).value

        // Assert
        assertEquals("api.example.com", created.name)
        assertEquals("example.com", apex.name)
    }
}
