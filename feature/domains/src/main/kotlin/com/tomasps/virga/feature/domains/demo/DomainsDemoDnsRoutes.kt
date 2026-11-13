package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.InMemoryStore
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger

private const val RECORD_NOT_FOUND = 81044
private const val DEFAULT_PER_PAGE = 100
private const val STORE_SEPARATOR = "/"

/** Store key for a record: zone and record id together, so records of different zones never collide. */
private fun recordKey(zoneId: String, recordId: String) = "$zoneId$STORE_SEPARATOR$recordId"

/**
 * DNS record routes. Literal segments (export, batch, import) are registered before the `{recordId}` routes so a
 * record id can never capture them. Writes change [records], so a second read shows the change.
 */
internal fun DemoRouteRegistry.dnsRoutes(records: InMemoryStore<JsonObject>, zoneName: (String) -> String?) {
    val counter = AtomicInteger(DEMO_CREATED_START)
    get("/zones/{zoneId}/dns_records/export") { request ->
        DemoReply.text(bindExport(records, request.pathParams["zoneId"].orEmpty()))
    }
    post("/zones/{zoneId}/dns_records/batch") { request ->
        val zoneId = request.pathParams["zoneId"].orEmpty()
        val deleted = (bodyObject(request)["deletes"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("id").textOrNull() }
        deleted.forEach { records.remove(recordKey(zoneId, it)) }
        DemoReply.ok(buildJsonObject {
            put("deletes", JsonArray(deleted.map { id -> buildJsonObject { put("id", id) } }))
            put("patches", JsonArray(emptyList()))
            put("posts", JsonArray(emptyList()))
        })
    }
    get("/zones/{zoneId}/dns_settings") { _ ->
        // Canned zone DNS settings. Writes are not routed: the app only changes these in the dashboard.
        DemoReply.ok(buildJsonObject {
            put("zone_mode", "standard")
            put("nameservers", buildJsonObject { put("type", "cloudflare.standard") })
            put("flatten_all_cnames", false)
            put("multi_provider", false)
            put("ns_ttl", 86400)
            put("secondary_overrides", false)
        })
    }
    post("/zones/{zoneId}/dns_records/import") { request ->
        val zoneId = request.pathParams["zoneId"].orEmpty()
        val parsed = parseBindZone(uploadedFileText(request.bodyText.orEmpty()))
        parsed.forEach { bind ->
            val id = "rec-demo-${counter.incrementAndGet()}"
            val json = bind.toDemoJson()
            val qualified = zoneName(zoneId)?.let { json.withQualifiedName(it) } ?: json
            records.put(recordKey(zoneId, id), qualified.withRecordDefaults(zoneId, id))
        }
        DemoReply.ok(buildJsonObject {
            put("recs_added", parsed.size)
            put("total_records_parsed", parsed.size)
        })
    }
    get("/zones/{zoneId}/dns_records") { request ->
        val zoneId = request.pathParams["zoneId"].orEmpty()
        val type = request.query["type"]
        val all = records.items.value
            .filterKeys { it.startsWith("$zoneId$STORE_SEPARATOR") }
            .values
            .filter { type == null || it["type"].textOrNull().equals(type, ignoreCase = true) }
            .sortedBy { it["name"].textOrNull().orEmpty() }
        val page = request.query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val perPage = request.query["per_page"]?.toIntOrNull()?.coerceIn(1, DEFAULT_PER_PAGE) ?: DEFAULT_PER_PAGE
        val slice = all.drop((page - 1) * perPage).take(perPage)
        DemoReply.ok(
            JsonArray(slice),
            buildJsonObject {
                put("page", page)
                put("per_page", perPage)
                put("count", slice.size)
                put("total_count", all.size)
            },
        )
    }
    post("/zones/{zoneId}/dns_records") { request ->
        val zoneId = request.pathParams["zoneId"].orEmpty()
        val body = bodyObject(request)
        val id = "rec-demo-${counter.incrementAndGet()}"
        val qualified = zoneName(zoneId)?.let { body.withQualifiedName(it) } ?: body
        val created = qualified.withRecordDefaults(zoneId, id)
        records.put(recordKey(zoneId, id), created)
        DemoReply.ok(created)
    }
    patch("/zones/{zoneId}/dns_records/{recordId}") { request ->
        val zoneId = request.pathParams["zoneId"].orEmpty()
        val key = recordKey(zoneId, request.pathParams["recordId"].orEmpty())
        val current = records.get(key)
        if (current == null) {
            recordNotFound()
        } else {
            val merged = current.plusFields(bodyObject(request))
            records.put(key, merged)
            DemoReply.ok(merged)
        }
    }
    delete("/zones/{zoneId}/dns_records/{recordId}") { request ->
        val recordId = request.pathParams["recordId"].orEmpty()
        val zoneId = request.pathParams["zoneId"].orEmpty()
        if (records.remove(recordKey(zoneId, recordId)) == null) recordNotFound() else DemoReply.ok(buildJsonObject { put("id", recordId) })
    }
}

private fun recordNotFound(): DemoReply = DemoReply.failure(HttpStatusCode.NotFound, RECORD_NOT_FOUND, "Record not found")

private const val DEMO_CREATED_START = 100

/** Fills the fields the reference would return for a newly created record. */
private fun JsonObject.withRecordDefaults(zoneId: String, id: String): JsonObject {
    val proxiable = this["type"].textOrNull().orEmpty() in PROXIABLE_DEMO_TYPES
    val defaults = buildJsonObject {
        put("id", id)
        put("zone_id", zoneId)
        put("ttl", 1)
        put("proxied", false)
        put("proxiable", proxiable)
        put("comment", "")
        put("tags", JsonArray(emptyList()))
    }
    return defaults.plusFields(this)
}

/**
 * The API returns names in full, so a name typed as `api` in zone `example.com` is stored as `api.example.com`, and
 * `@` means the apex. A name already ending in the zone is left alone.
 */
internal fun JsonObject.withQualifiedName(zoneName: String): JsonObject {
    val typed = this["name"].textOrNull().orEmpty().trim().trimEnd('.')
    val full = when {
        typed.isEmpty() || typed == "@" -> zoneName
        typed == zoneName || typed.endsWith(".$zoneName") -> typed
        else -> "$typed.$zoneName"
    }
    return JsonObject(this + ("name" to JsonPrimitive(full)))
}

private val PROXIABLE_DEMO_TYPES = setOf("A", "AAAA", "CNAME")

/** Fields of [patch] override the same fields here; the rest are kept. */
private fun JsonObject.plusFields(patch: JsonObject): JsonObject = JsonObject(this + patch)

private fun bindExport(records: InMemoryStore<JsonObject>, zoneId: String): String {
    val rows = records.items.value
        .filterKeys { it.startsWith("$zoneId$STORE_SEPARATOR") }
        .values
        .sortedBy { it["name"].textOrNull().orEmpty() }
        .map { record ->
            val type = record["type"].textOrNull().orEmpty()
            val name = record["name"].textOrNull().orEmpty()
            val ttl = (record["ttl"] as? JsonPrimitive)?.contentOrNull ?: "1"
            "$name\t$ttl\tIN\t$type\t${record["content"].textOrNull().orEmpty()}"
        }
    return buildString {
        appendLine("; BIND zone file, exported from the demo account")
        rows.forEach { appendLine(it) }
    }
}
