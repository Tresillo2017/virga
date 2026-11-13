package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.InMemoryStore
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val NOT_FOUND_CODE = 7003
private const val PAGE_INFO_PER_PAGE = 50

/** Parses a demo request body as a JSON object. Demo bodies come from this app, so anything else reads as empty. */
internal fun bodyObject(request: DemoRequest): JsonObject = runCatching {
    Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
}.getOrElse { JsonObject(emptyMap()) }

internal fun zoneNotFound(): DemoReply = DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, "Zone not found")

/** Zone reads and writes, development mode, purge, DNSSEC, Worker routes and registrar, all over in-memory stores. */
internal fun DemoRouteRegistry.zoneRoutes(zones: InMemoryStore<JsonObject>, dnssec: InMemoryStore<JsonObject>) {
    get("/zones") { request ->
        val accountId = request.query["account.id"]
        val visible = zones.items.value.values.map { it.withAccount(accountId) }
        DemoReply.ok(
            JsonArray(visible),
            buildJsonObject {
                put("page", 1)
                put("per_page", PAGE_INFO_PER_PAGE)
                put("count", visible.size)
                put("total_count", visible.size)
            },
        )
    }
    post("/zones") { request ->
        val body = bodyObject(request)
        val name = body["name"].textOrNull().orEmpty().trim().lowercase()
        if (name.isEmpty()) {
            DemoReply.failure(HttpStatusCode.BadRequest, NOT_FOUND_CODE, "Zone name is required")
        } else {
            val accountId = body["account"]?.jsonObject?.get("id").textOrNull() ?: DEMO_ZONE_ACCOUNT_ID
            val id = newZoneId()
            val created = demoZone(id, name, "pending", paused = false, plan = "Free Website").withAccount(accountId)
            zones.put(id, created)
            DemoReply.ok(created)
        }
    }
    get("/zones/{zoneId}") { request ->
        zones.get(request.pathParams["zoneId"].orEmpty())?.let { DemoReply.ok(it) } ?: zoneNotFound()
    }
    patch("/zones/{zoneId}") { request ->
        val id = request.pathParams["zoneId"].orEmpty()
        val zone = zones.get(id)
        if (zone == null) {
            zoneNotFound()
        } else {
            val paused = bodyObject(request)["paused"].boolOrNull() ?: zone["paused"].boolOrNull() ?: false
            val updated = zone.with("paused" to JsonPrimitive(paused))
            zones.put(id, updated)
            DemoReply.ok(updated)
        }
    }
    delete("/zones/{zoneId}") { request ->
        val id = request.pathParams["zoneId"].orEmpty()
        if (zones.remove(id) == null) zoneNotFound() else DemoReply.ok(buildJsonObject { put("id", id) })
    }
    get("/zones/{zoneId}/settings/development_mode") { request ->
        val zone = zones.get(request.pathParams["zoneId"].orEmpty())
        if (zone == null) zoneNotFound() else DemoReply.ok(developmentSetting(zone))
    }
    patch("/zones/{zoneId}/settings/development_mode") { request ->
        val id = request.pathParams["zoneId"].orEmpty()
        val zone = zones.get(id)
        if (zone == null) {
            zoneNotFound()
        } else {
            val on = bodyObject(request)["value"].textOrNull() == "on"
            val updated = zone.with("development_mode" to JsonPrimitive(developmentSecondsFor(on)))
            zones.put(id, updated)
            DemoReply.ok(developmentSetting(updated))
        }
    }
    post("/zones/{zoneId}/purge_cache") { request ->
        DemoReply.ok(buildJsonObject { put("id", request.pathParams["zoneId"].orEmpty()) })
    }
    get("/zones/{zoneId}/dnssec") { request ->
        val id = request.pathParams["zoneId"].orEmpty()
        if (zones.get(id) == null) zoneNotFound() else DemoReply.ok(dnssec.get(id) ?: demoDnssecOff(id))
    }
    patch("/zones/{zoneId}/dnssec") { request ->
        val id = request.pathParams["zoneId"].orEmpty()
        if (zones.get(id) == null) {
            zoneNotFound()
        } else {
            val enable = bodyObject(request)["status"].textOrNull() == "active"
            val next = if (enable) demoDnssecOn(id) else demoDnssecOff(id)
            dnssec.put(id, next)
            DemoReply.ok(next)
        }
    }
    get("/zones/{zoneId}/workers/routes") { request ->
        DemoReply.ok(JsonArray(demoRoutes(request.pathParams["zoneId"].orEmpty())))
    }
    get("/accounts/{accountId}/registrar/registrations/{domain}") { request ->
        val registration = demoRegistration(request.pathParams["domain"].orEmpty())
        if (registration == null) {
            DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, "Registration not found")
        } else {
            DemoReply.ok(registration)
        }
    }
}

/** Cloudflare zone ids are 32 lowercase hex characters; the analytics and DNS paths rely on that shape. */
internal fun newZoneId(): String = UUID.randomUUID().toString().replace("-", "")

/** development_mode as the settings endpoint reports it: `time_remaining` is `false` when the mode was never enabled. */
private fun developmentSetting(zone: JsonObject): JsonObject {
    val seconds = (zone["development_mode"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    val remaining: JsonElement = if (seconds > 0) JsonPrimitive(seconds) else JsonPrimitive(false)
    return buildJsonObject {
        put("id", "development_mode")
        put("value", if (seconds > 0) "on" else "off")
        put("editable", true)
        put("time_remaining", remaining)
    }
}
