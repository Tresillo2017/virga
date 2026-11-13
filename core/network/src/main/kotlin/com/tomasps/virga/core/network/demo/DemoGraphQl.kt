package com.tomasps.virga.core.network.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/**
 * Builds the `viewer.accounts[0]` shape the GraphQL Analytics API returns, with one array per dataset
 * (for example `r2OperationsAdaptiveGroups`). Features use it from their GraphQL demo handlers.
 */
fun graphQlAccountData(datasets: Map<String, JsonArray>): JsonElement = buildJsonObject {
    put("viewer", buildJsonObject {
        put("accounts", buildJsonArray {
            add(buildJsonObject { datasets.forEach { (name, rows) -> put(name, rows) } })
        })
    })
}

/** The `variables` object of a GraphQL request body, or null when the body has none or is not JSON. */
fun DemoRequest.graphQlVariables(): JsonObject? = bodyText?.let { body ->
    runCatching { Json.parseToJsonElement(body).jsonObject["variables"]?.jsonObject }.getOrNull()
}
