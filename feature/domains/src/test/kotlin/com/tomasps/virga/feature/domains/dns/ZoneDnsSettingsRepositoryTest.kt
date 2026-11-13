package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneDnsSettingsRepositoryTest {

    private fun repositoryReplying(body: kotlinx.serialization.json.JsonObject) = ZoneDnsSettingsRepository(
        mockCloudflareApi {
            get("/zones/{zoneId}/dns_settings") { DemoReply.ok(body) }
        },
    )

    @Test
    fun `a dns_only zone and its nameserver type are read from the reference shape`() = runBlocking {
        // Arrange
        val repository = repositoryReplying(
            buildJsonObject {
                put("zone_mode", "dns_only")
                put("nameservers", buildJsonObject { put("type", "cloudflare.advanced") })
                put("ns_ttl", 3600)
                put("multi_provider", true)
            },
        )

        // Act
        val settings = (repository.settings("z1") as ApiResult.Success).value

        // Assert
        assertEquals(ZoneMode.DNS_ONLY, settings.zoneMode)
        assertEquals("cloudflare.advanced", settings.nameserverType)
        assertEquals(3600L, settings.nsTtl)
        assertEquals(true, settings.multiProvider)
    }

    @Test
    fun `an unknown zone mode is shown as unknown instead of failing the read`() = runBlocking {
        // Arrange
        val repository = repositoryReplying(buildJsonObject { put("zone_mode", "future_mode") })

        // Act
        val settings = (repository.settings("z1") as ApiResult.Success).value

        // Assert
        assertEquals(ZoneMode.UNKNOWN, settings.zoneMode)
    }

    @Test
    fun `missing fields stay null rather than defaulting to a value`() = runBlocking {
        // Arrange
        val repository = repositoryReplying(buildJsonObject { })

        // Act
        val settings = (repository.settings("z1") as ApiResult.Success).value

        // Assert
        assertEquals(ZoneMode.UNKNOWN, settings.zoneMode)
        assertNull(settings.nameserverType)
        assertNull(settings.nsTtl)
    }
}
