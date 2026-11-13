package com.tomasps.virga.feature.pages.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * PATCH bodies are built by hand, never from a data class. The client drops nulls, and a data class would send
 * every field it has, so each builder here sends only the keys it means to change. JSON null is the server's
 * "remove this key" signal, used for deleting an environment variable.
 */

fun productionBranchPatch(branch: String): JsonObject = buildJsonObject {
    put("production_branch", branch)
}

fun buildConfigPatch(
    buildCommand: String,
    destinationDir: String,
    rootDir: String,
    buildCaching: Boolean,
): JsonObject = buildJsonObject {
    putJsonObject("build_config") {
        put("build_command", buildCommand)
        put("destination_dir", destinationDir)
        put("root_dir", rootDir)
        put("build_caching", buildCaching)
    }
}

/** Patches only [envVars] (name to value, or null to delete) under one environment. */
fun envVarsPatch(environment: String, envVars: Map<String, JsonElement>): JsonObject = buildJsonObject {
    putJsonObject("deployment_configs") {
        putJsonObject(environment) {
            putJsonObject("env_vars") {
                envVars.forEach { (name, value) -> put(name, value) }
            }
        }
    }
}
