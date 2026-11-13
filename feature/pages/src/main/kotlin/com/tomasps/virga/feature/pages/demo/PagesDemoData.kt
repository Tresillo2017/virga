package com.tomasps.virga.feature.pages.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * Canned Pages data for the demo account. Every name, branch and commit is invented. Projects, deployments and
 * domains are stored as JSON so demo writes can merge into them exactly as the API merges a PATCH.
 */

internal const val LANTERN = "lantern-docs"
internal const val TIDEWATER = "tidewater-blog"
internal const val CURRENT_PRODUCTION = "lantern-prod-3"

private const val SUBDOMAIN_SUFFIX = ".pages.dev"

internal fun seedProjects(): Map<String, JsonObject> = mapOf(
    LANTERN to lanternProject(),
    TIDEWATER to tidewaterProject(),
)

internal fun seedDeployments(): Map<String, JsonObject> = listOf(
    deployment("lantern-prod-1", LANTERN, "production", "2026-10-02T08:10:00Z", "Initial site", "main", success()),
    deployment("lantern-prod-2", LANTERN, "production", "2026-10-20T16:45:00Z", "Fix footer links", "main", success()),
    deployment("lantern-prod-3", LANTERN, "production", "2026-11-09T14:02:11Z", "Update pricing page", "main", success()),
    deployment("lantern-prev-4", LANTERN, "preview", "2026-11-10T11:30:00Z", "Try new image pipeline", "feature/images", failedAtBuild()),
    deployment("lantern-prev-5", LANTERN, "preview", "2026-11-11T09:15:00Z", "Add search index", "feature/search", buildingNow()),
    deployment("tidewater-prod-1", TIDEWATER, "production", "2026-11-04T10:00:00Z", "Direct upload", "main", success()),
).associateBy { it.string("id") }

internal fun seedDomains(): Map<String, JsonObject> = listOf(
    domain(LANTERN, "docs.lantern.example", "active"),
    domain(LANTERN, "shop.lantern.example", "pending"),
).associateBy { "${it.string("_project")}/${it.string("name")}" }

/** Demo domain values carry a private "_project" key so the store can tell projects apart. Routes strip it. */

private fun lanternProject(): JsonObject = buildJsonObject {
    put("id", "demo-project-lantern")
    put("name", LANTERN)
    put("production_branch", "main")
    put("subdomain", LANTERN + SUBDOMAIN_SUFFIX)
    put("framework", "astro")
    put("uses_functions", false)
    put("created_on", "2026-09-30T12:00:00Z")
    put("domains", jsonStrings("docs.lantern.example"))
    put("source", buildJsonObject {
        put("type", "github")
        putJsonObject("config") {
            put("owner", "example-team")
            put("owner_id", "demo-owner")
            put("repo_name", LANTERN)
            put("repo_id", "demo-repo-lantern")
            put("production_branch", "main")
            put("deployments_enabled", true)
            put("production_deployments_enabled", true)
            put("pr_comments_enabled", true)
            put("preview_deployment_setting", "all")
            put("path_includes", jsonStrings("*"))
            put("path_excludes", JsonArray(emptyList()))
            put("preview_branch_includes", jsonStrings("*"))
            put("preview_branch_excludes", JsonArray(emptyList()))
        }
    })
    put("build_config", buildJsonObject {
        put("build_command", "npm run build")
        put("destination_dir", "dist")
        put("root_dir", "")
        put("build_caching", true)
    })
    put("deployment_configs", buildJsonObject {
        putJsonObject("production") {
            putJsonObject("env_vars") {
                putJsonObject("SITE_NAME") { put("type", "plain_text"); put("value", "Lantern") }
                putJsonObject("ANALYTICS_TOKEN") { put("type", "secret_text"); put("value", "") }
            }
            put("compatibility_date", "2026-01-15")
            put("compatibility_flags", jsonStrings("nodejs_compat"))
            put("usage_model", "standard")
            putJsonObject("kv_namespaces") { putJsonObject("CACHE") { put("namespace_id", "5f1c0a9e2b7d4c3a8e6f1b2d3c4e5f60") } }
        }
        putJsonObject("preview") {
            putJsonObject("env_vars") {
                putJsonObject("SITE_NAME") { put("type", "plain_text"); put("value", "Lantern (preview)") }
            }
            put("compatibility_date", "2026-01-15")
            put("usage_model", "standard")
        }
    })
    put("canonical_deployment", deployment("lantern-prod-3", LANTERN, "production", "2026-11-09T14:02:11Z", "Update pricing page", "main", success()))
}

private fun tidewaterProject(): JsonObject = buildJsonObject {
    put("id", "demo-project-tidewater")
    put("name", TIDEWATER)
    put("production_branch", "main")
    put("subdomain", TIDEWATER + SUBDOMAIN_SUFFIX)
    put("framework", "hugo")
    put("uses_functions", false)
    put("created_on", "2026-10-28T09:00:00Z")
    put("domains", JsonArray(emptyList()))
    put("build_config", buildJsonObject {
        put("build_command", "")
        put("destination_dir", "public")
        put("root_dir", "")
        put("build_caching", false)
    })
    put("deployment_configs", buildJsonObject {
        putJsonObject("production") { putJsonObject("env_vars") { } }
        putJsonObject("preview") { putJsonObject("env_vars") { } }
    })
}

internal fun domain(project: String, hostname: String, status: String): JsonObject = buildJsonObject {
    put("_project", project)
    put("id", "demo-domain-$hostname")
    put("name", hostname)
    put("status", status)
    put("created_on", "2026-10-01T10:00:00Z")
    put("certificate_authority", "lets_encrypt")
    put("zone_tag", "demo-zone-lantern")
    putJsonObject("validation_data") {
        put("method", "txt")
        put("status", status)
        put("txt_name", "_cf-custom-hostname." + hostname.substringBefore('.'))
        put("txt_value", "demo-" + hostname.substringBefore('.') + "-token")
    }
    putJsonObject("verification_data") { put("status", status) }
}

private fun deployment(
    id: String,
    project: String,
    environment: String,
    createdOn: String,
    commitMessage: String,
    branch: String,
    stages: JsonArray,
): JsonObject = buildJsonObject {
    put("id", id)
    put("short_id", id.takeLast(8))
    put("project_name", project)
    put("environment", environment)
    put("url", "https://$id.$project$SUBDOMAIN_SUFFIX")
    put("created_on", createdOn)
    put("modified_on", createdOn)
    put("is_skipped", false)
    put("aliases", if (environment == "production") jsonStrings("https://$project$SUBDOMAIN_SUFFIX") else JsonArray(emptyList()))
    putJsonObject("deployment_trigger") {
        put("type", "github:push")
        putJsonObject("metadata") {
            put("branch", branch)
            put("commit_hash", demoCommitHash(id))
            put("commit_message", commitMessage)
            put("commit_dirty", false)
        }
    }
    put("stages", stages)
    put("latest_stage", latestStage(stages))
}

/** Hex commit id derived from the deployment id. Plain hashCode can be negative, which would put a minus sign in the hash. */
private fun demoCommitHash(id: String): String =
    (id.hashCode().toLong() and UINT_MASK).toString(16).padStart(8, '0').repeat(5)

private const val UINT_MASK = 0xffffffffL

private fun stage(name: String, status: String) = buildJsonObject {
    put("name", name)
    put("status", status)
    put("started_on", "2026-11-09T14:00:00Z")
    put("ended_on", if (status == "active" || status == "idle") "" else "2026-11-09T14:01:00Z")
}

private fun success(): JsonArray = stagesWith(
    "queued" to "success", "initialize" to "success", "clone_repo" to "success", "build" to "success", "deploy" to "success",
)

private fun failedAtBuild(): JsonArray = stagesWith(
    "queued" to "success", "initialize" to "success", "clone_repo" to "success", "build" to "failure", "deploy" to "idle",
)

private fun buildingNow(): JsonArray = stagesWith(
    "queued" to "success", "initialize" to "success", "clone_repo" to "success", "build" to "active", "deploy" to "idle",
)

private fun stagesWith(vararg pairs: Pair<String, String>): JsonArray = buildJsonArray {
    pairs.forEach { (name, status) -> add(stage(name, status)) }
}

/** The stage the API reports as latest: the last one that is not idle, or the first stage if none has started. */
internal fun latestStage(stages: JsonArray): JsonObject {
    val started = stages.map { it as JsonObject }.lastOrNull { it.string("status") != "idle" }
    return started ?: stages.first() as JsonObject
}
