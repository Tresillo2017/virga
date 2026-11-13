package com.tomasps.virga.feature.domains.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Account id used for the canned zones. Requests with another account id get the same zones re-labelled. */
internal const val DEMO_ZONE_ACCOUNT_ID = "demo-account-personal"
internal const val DEMO_ZONE_ACCOUNT_NAME = "Demo account"

/** Reserved example domains (RFC 2606), so no real customer name appears anywhere in the demo. */
internal const val DEMO_ZONE_EXAMPLE_COM = "3f9a1c7e2b4d4e6f8a0b1c2d3e4f5a61"
internal const val DEMO_ZONE_EXAMPLE_ORG = "7b2e4d6f8a0c1e3b5d7f9a1c3e5b7d92"
internal const val DEMO_ZONE_EXAMPLE_NET = "c4d6e8f0a2b4c6d8e0f1a3b5c7d9e0f2"

private const val DEMO_DEV_MODE_SECONDS = 10_800L
private const val DEMO_ROUTE_SCRIPT = "demo-api-worker"

internal fun JsonObject.with(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(this + fields)

internal fun JsonObject.withAccount(accountId: String?): JsonObject {
    if (accountId == null) return this
    val account = buildJsonObject {
        put("id", accountId)
        put("name", DEMO_ZONE_ACCOUNT_NAME)
    }
    return with("account" to account)
}

internal fun demoZone(
    id: String,
    name: String,
    status: String,
    paused: Boolean,
    plan: String,
    developmentModeSeconds: Long = 0,
): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("status", status)
    put("paused", paused)
    put("type", "full")
    put("name_servers", JsonArray(listOf("ada.ns.example", "ben.ns.example").map { JsonPrimitive(it) }))
    put("original_registrar", "Example Registrar")
    put("development_mode", developmentModeSeconds)
    put("account", buildJsonObject {
        put("id", DEMO_ZONE_ACCOUNT_ID)
        put("name", DEMO_ZONE_ACCOUNT_NAME)
    })
    put("plan", buildJsonObject {
        put("id", plan.lowercase().replace(' ', '-'))
        put("name", plan)
    })
}

internal fun demoZones(): List<JsonObject> = listOf(
    demoZone(DEMO_ZONE_EXAMPLE_COM, "example.com", "active", paused = false, plan = "Pro Plan"),
    demoZone(DEMO_ZONE_EXAMPLE_ORG, "example.org", "active", paused = true, plan = "Free Website"),
    demoZone(DEMO_ZONE_EXAMPLE_NET, "example.net", "pending", paused = false, plan = "Free Website"),
)

/** Development mode time left, as the zone holds it: seconds, 0 when off. */
internal fun developmentSecondsFor(on: Boolean): Long = if (on) DEMO_DEV_MODE_SECONDS else 0L

internal fun demoRecord(
    zoneId: String,
    id: String,
    name: String,
    type: String,
    content: String?,
    ttl: Int = 1,
    proxied: Boolean = false,
    comment: String? = null,
    tags: List<String> = emptyList(),
    priority: Int? = null,
    data: JsonObject? = null,
): JsonObject = buildJsonObject {
    put("id", id)
    put("zone_id", zoneId)
    put("name", name)
    put("type", type)
    if (content != null) put("content", content) else put("content", JsonNull)
    put("ttl", ttl)
    put("proxied", proxied)
    put("proxiable", type in PROXIABLE_TYPES)
    put("comment", comment ?: "")
    put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
    if (priority != null) put("priority", priority)
    if (data != null) put("data", data)
}

private val PROXIABLE_TYPES = setOf("A", "AAAA", "CNAME")

internal fun demoRecordsFor(zoneId: String): List<JsonObject> = when (zoneId) {
    DEMO_ZONE_EXAMPLE_COM -> listOf(
        demoRecord(zoneId, "rec-com-01", "example.com", "A", "192.0.2.10", ttl = 1, proxied = true),
        demoRecord(zoneId, "rec-com-02", "example.com", "AAAA", "2001:db8::10", ttl = 1, proxied = true),
        demoRecord(zoneId, "rec-com-03", "www.example.com", "CNAME", "example.com", ttl = 1, proxied = true),
        demoRecord(zoneId, "rec-com-04", "example.com", "MX", "mail.example.com", ttl = 3600, priority = 10),
        demoRecord(zoneId, "rec-com-05", "example.com", "TXT", "v=spf1 -all", ttl = 3600, comment = "Mail policy", tags = listOf("owner:mail")),
        demoRecord(zoneId, "rec-com-06", "_sip._tcp.example.com", "SRV", null, ttl = 3600,
            data = buildJsonObject { put("priority", 10); put("weight", 5); put("port", 5060); put("target", "sip.example.com") }),
        demoRecord(zoneId, "rec-com-07", "example.com", "CAA", null, ttl = 3600,
            data = buildJsonObject { put("flags", 0); put("tag", "issue"); put("value", "ca.example") }),
    )
    DEMO_ZONE_EXAMPLE_ORG -> listOf(
        demoRecord(zoneId, "rec-org-01", "example.org", "A", "192.0.2.20", ttl = 300),
        demoRecord(zoneId, "rec-org-02", "docs.example.org", "CNAME", "example.org", ttl = 300),
    )
    else -> emptyList()
}

internal fun demoDnssecOff(zoneId: String): JsonObject = buildJsonObject {
    put("status", "disabled")
    put("dnssec_multi_signer", false)
    put("dnssec_presigned", false)
    put("dnssec_use_nsec3", false)
    put("modified_on", "2026-09-01T00:00:00Z")
    put("zone_id", zoneId)
}

internal fun demoDnssecOn(zoneId: String): JsonObject = buildJsonObject {
    put("status", "active")
    put("ds", "example.com. 3600 IN DS 2371 13 2 0A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F9")
    put("digest", "0A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F9")
    put("digest_algorithm", "SHA256")
    put("digest_type", "2")
    put("key_tag", 2371)
    put("algorithm", "ECDSAP256SHA256")
    put("flags", 257)
    put("key_type", "ECDSA Curve P-256 with SHA-256")
    put("public_key", "demo-public-key-not-real")
    put("modified_on", "2026-11-01T00:00:00Z")
    put("zone_id", zoneId)
}

internal fun demoRoutes(zoneId: String): List<JsonObject> = if (zoneId == DEMO_ZONE_EXAMPLE_COM) {
    listOf(
        buildJsonObject {
            put("id", "route-com-01")
            put("pattern", "example.com/api/*")
            put("script", DEMO_ROUTE_SCRIPT)
        },
        buildJsonObject {
            put("id", "route-com-02")
            put("pattern", "example.com/static/*")
        },
    )
} else {
    emptyList()
}

/** Registrar card for the one demo domain bought through Cloudflare. Other demo domains are not registered here. */
internal fun demoRegistration(domain: String): JsonObject? = if (domain == "example.com") {
    buildJsonObject {
        put("domain_name", domain)
        put("status", "active")
        put("expires_at", "2027-06-30T00:00:00Z")
        put("auto_renew", true)
        put("privacy_mode", "redaction")
        put("locked", true)
        put("created_at", "2024-06-30T00:00:00Z")
    }
} else {
    null
}

internal fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Boolean value of a JSON primitive, or null for anything else (including a missing field). */
internal fun JsonElement?.boolOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
