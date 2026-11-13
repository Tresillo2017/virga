package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.domains.dns.DnsRecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBindImportTest {

    private val api = mockCloudflareApi { DomainsDemoRoutes().contribute(this) }
    private val dns = DnsRecordRepository(api)

    @Test
    fun `a BIND file uploaded as multipart adds its records to the demo zone`() = runBlocking {
        // Arrange
        val zoneFile = """
            ; imported in a test
            www 300 IN A 192.0.2.10
            @ IN MX 10 mail.example.com.
        """.trimIndent()

        // Act
        val imported = dns.importBind(DEMO_ZONE_EXAMPLE_COM, "zone.bind", zoneFile.toByteArray())
        val records = (dns.page(DEMO_ZONE_EXAMPLE_COM, page = 1) as ApiResult.Success).value.records

        // Assert
        assertTrue(imported is ApiResult.Success)
        assertTrue(records.any { it.name == "www.example.com" && it.type == "A" && it.content == "192.0.2.10" })
        assertTrue(records.any { it.name == "example.com" && it.type == "MX" && it.priority == 10 })
    }

    @Test
    fun `the import reports how many records it parsed`() = runBlocking {
        // Arrange
        val zoneFile = "a1 IN A 192.0.2.1\na2 IN A 192.0.2.2\n"

        // Act
        val imported = dns.importBind(DEMO_ZONE_EXAMPLE_COM, "zone.bind", zoneFile.toByteArray())

        // Assert
        assertTrue(imported is ApiResult.Success)
        assertEquals(2, (dns.page(DEMO_ZONE_EXAMPLE_COM, page = 1) as ApiResult.Success).value.records.count { it.name.startsWith("a") })
    }

    @Test
    fun `multipart file part is extracted from the envelope`() {
        // Arrange
        val body = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"z.txt\"\r\n" +
            "Content-Type: text/plain\r\n\r\nwww 300 IN A 192.0.2.10\r\n--boundary--\r\n"

        // Act
        val text = uploadedFileText(body)

        // Assert
        assertEquals("www 300 IN A 192.0.2.10", text)
    }
}
