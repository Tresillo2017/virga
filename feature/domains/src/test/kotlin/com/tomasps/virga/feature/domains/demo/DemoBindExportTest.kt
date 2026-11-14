package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.domains.dns.DnsRecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBindExportTest {

    private val api = mockCloudflareApi { DomainsDemoRoutes().contribute(this) }

    @Test
    fun `demo BIND export writes the structured SRV and CAA values`() = runBlocking {
        // Arrange
        val records = DnsRecordRepository(api)

        // Act
        val exported = (records.exportBind(DEMO_ZONE_EXAMPLE_COM) as ApiResult.Success).value

        // Assert
        assertTrue(exported.contains("_sip._tcp.example.com\t3600\tIN\tSRV\t10 5 5060 sip.example.com"))
        assertTrue(exported.contains("example.com\t3600\tIN\tCAA\t0 issue \"ca.example\""))
    }
}
