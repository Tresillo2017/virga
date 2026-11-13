package com.tomasps.virga.feature.workers.demo

import com.tomasps.virga.core.network.demo.InMemoryStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val DEMO_SCRIPT_EDGE_API = "edge-api"
internal const val DEMO_SCRIPT_RESIZER = "image-resizer"
internal const val DEMO_SCRIPT_CLEANUP = "cron-cleanup"
internal val DEMO_SCRIPT_NAMES = listOf(DEMO_SCRIPT_EDGE_API, DEMO_SCRIPT_RESIZER, DEMO_SCRIPT_CLEANUP)

internal const val DEMO_KV_NAMESPACE_ID = "5f1c0a9e2b7d4c3a8e6f1b2d3c4e5f60"
internal const val DEMO_R2_BUCKET_NAME = "demo-assets"
internal const val DEMO_D1_DATABASE_ID = "demo-db-orders"

private const val DEMO_VERSION_OLD = "9f1c0e2a-0000-4000-8000-000000000001"
private const val DEMO_VERSION_NEW = "9f1c0e2a-0000-4000-8000-000000000002"
private const val DEMO_VERSION_CANDIDATE = "9f1c0e2a-0000-4000-8000-000000000003"

/**
 * In-memory state behind the demo account. Reads come from these stores and writes update them, so a deployment,
 * a domain removal or a secret change shows up on the next read. Nothing here is persisted.
 */
internal class WorkersDemoState {
    val scripts = InMemoryStore(seedScripts())
    val deployments = InMemoryStore(seedDeployments())
    /** Uploaded versions per Worker, newest first. Every version a deployment names is listed here. */
    val versions = InMemoryStore(seedVersions())
    val subdomains = InMemoryStore(seedSubdomains())
    val domains = InMemoryStore(seedDomains())
    /** Secret names only. Values are never stored in demo state either. */
    val secretNames = InMemoryStore(seedSecretNames())
}

private fun seedScripts(): Map<String, JsonObject> = DEMO_SCRIPT_NAMES.associateWith { name ->
    buildJsonObject {
        put("id", name)
        put("created_on", "2025-03-02T10:00:00Z")
        put("modified_on", "2026-11-03T08:20:00Z")
        put("usage_model", "standard")
    }
}

private fun seedDeployments(): Map<String, JsonArray> = mapOf(
    DEMO_SCRIPT_EDGE_API to buildJsonArray {
        add(deploymentJson("dep-edge-2", "2026-11-03T08:20:00Z", listOf(DEMO_VERSION_NEW to 100.0), "Fix cache header"))
        add(deploymentJson("dep-edge-1", "2026-10-21T14:05:00Z", listOf(DEMO_VERSION_OLD to 100.0), "Initial release"))
    },
    DEMO_SCRIPT_RESIZER to buildJsonArray {
        add(
            deploymentJson(
                "dep-resizer-1",
                "2026-11-06T09:00:00Z",
                listOf(DEMO_VERSION_NEW to 90.0, DEMO_VERSION_CANDIDATE to 10.0),
                "Gradual test of new resize filter",
            ),
        )
    },
    DEMO_SCRIPT_CLEANUP to buildJsonArray {
        add(deploymentJson("dep-cleanup-1", "2026-09-30T12:00:00Z", listOf(DEMO_VERSION_OLD to 100.0), null))
    },
)

internal fun deploymentJson(
    id: String,
    createdOn: String,
    split: List<Pair<String, Double>>,
    message: String?,
): JsonObject = buildJsonObject {
    put("id", id)
    put("created_on", createdOn)
    put("strategy", "percentage")
    put("author_email", "demo@example.org")
    put("versions", buildJsonArray {
        split.forEach { (versionId, percentage) ->
            add(buildJsonObject {
                put("version_id", versionId)
                put("percentage", percentage)
            })
        }
    })
    if (message != null) {
        put("annotations", buildJsonObject { put("workers/message", message) })
    }
}

private fun seedSubdomains(): Map<String, JsonObject> = mapOf(
    DEMO_SCRIPT_EDGE_API to subdomainJson(enabled = true, previews = false),
    DEMO_SCRIPT_RESIZER to subdomainJson(enabled = true, previews = true),
    DEMO_SCRIPT_CLEANUP to subdomainJson(enabled = false, previews = false),
)

internal fun subdomainJson(enabled: Boolean, previews: Boolean): JsonObject = buildJsonObject {
    put("enabled", enabled)
    put("previews_enabled", previews)
}

private fun seedDomains(): Map<String, JsonArray> = mapOf(
    DEMO_SCRIPT_EDGE_API to buildJsonArray {
        add(buildJsonObject {
            put("id", "demo-domain-1")
            put("hostname", "api.example.org")
            put("service", DEMO_SCRIPT_EDGE_API)
            put("zone_name", "example.org")
            put("enabled", true)
            put("previews_enabled", false)
        })
    },
)

private fun seedSecretNames(): Map<String, JsonArray> = mapOf(
    DEMO_SCRIPT_EDGE_API to buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("API_TOKEN_SIGNING")) },
    DEMO_SCRIPT_RESIZER to buildJsonArray { },
    DEMO_SCRIPT_CLEANUP to buildJsonArray { },
)

private fun seedVersions(): Map<String, JsonArray> = mapOf(
    DEMO_SCRIPT_EDGE_API to buildJsonArray {
        add(versionJson(DEMO_VERSION_NEW, number = 2, createdOn = "2026-11-03T08:20:00Z"))
        add(versionJson(DEMO_VERSION_OLD, number = 1, createdOn = "2026-10-21T14:05:00Z"))
    },
    DEMO_SCRIPT_RESIZER to buildJsonArray {
        add(versionJson(DEMO_VERSION_CANDIDATE, number = 3, createdOn = "2026-11-06T09:00:00Z"))
        add(versionJson(DEMO_VERSION_NEW, number = 2, createdOn = "2026-10-30T12:00:00Z"))
    },
    DEMO_SCRIPT_CLEANUP to buildJsonArray {
        add(versionJson(DEMO_VERSION_OLD, number = 1, createdOn = "2026-09-30T12:00:00Z"))
    },
)

private fun versionJson(id: String, number: Int, createdOn: String): JsonObject = buildJsonObject {
    put("id", id)
    put("number", number)
    put("metadata", buildJsonObject {
        put("created_on", createdOn)
        put("author_email", "demo@example.org")
        put("source", "api")
    })
}
