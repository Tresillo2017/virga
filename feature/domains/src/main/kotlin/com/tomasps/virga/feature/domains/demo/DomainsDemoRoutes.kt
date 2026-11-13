package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.InMemoryStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Canned Domains data for the demo account. Zones, DNS records and DNSSEC state live in in-memory stores, so writes
 * in the demo change what the next read returns.
 */
class DomainsDemoRoutes : DemoRouteContributor {

    private val zones = InMemoryStore(demoZones().associateBy { it.textId() })

    private val records = InMemoryStore(
        DEMO_ZONE_IDS.flatMap { zoneId ->
            demoRecordsFor(zoneId).map { record -> "$zoneId$RECORD_KEY_SEPARATOR${record.textId()}" to record }
        }.toMap(),
    )

    private val dnssec = InMemoryStore<JsonObject>()

    override fun contribute(registry: DemoRouteRegistry) {
        registry.zoneRoutes(zones, dnssec)
        registry.dnsRoutes(records) { zoneId -> zones.get(zoneId)?.get("name")?.let { (it as? JsonPrimitive)?.content } }
        registry.trafficRoutes()
    }

    private fun JsonObject.textId(): String = (this["id"] as? JsonPrimitive)?.content.orEmpty()

    private companion object {
        val DEMO_ZONE_IDS = listOf(DEMO_ZONE_EXAMPLE_COM, DEMO_ZONE_EXAMPLE_ORG, DEMO_ZONE_EXAMPLE_NET)
        const val RECORD_KEY_SEPARATOR = "/"
    }
}
