package com.tomasps.virga.feature.domains.zones

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One purge request. The reference takes exactly one selector per request. */
sealed interface PurgeRequest {
    fun toJson(): JsonObject

    data object Everything : PurgeRequest {
        override fun toJson(): JsonObject = buildJsonObject { put("purge_everything", true) }
    }

    data class Files(val urls: List<String>) : PurgeRequest {
        override fun toJson(): JsonObject = listBody("files", urls)
    }

    data class Hosts(val hosts: List<String>) : PurgeRequest {
        override fun toJson(): JsonObject = listBody("hosts", hosts)
    }

    data class Prefixes(val prefixes: List<String>) : PurgeRequest {
        override fun toJson(): JsonObject = listBody("prefixes", prefixes)
    }

    data class Tags(val tags: List<String>) : PurgeRequest {
        override fun toJson(): JsonObject = listBody("tags", tags)
    }
}

/** Why a purge selection cannot be sent. The screen maps these to text. */
enum class PurgeProblem { EMPTY, TOO_MANY, NEEDS_SCHEME, MUST_NOT_HAVE_SCHEME }

/**
 * Per-request ceilings from Cloudflare's "Purge cache: availability and limits" page (cache/how-to/purge-cache). Every
 * plan can use URL, hostname, tag and prefix purge; the maximum operations per request is 100 on Free, Pro and
 * Business. Enterprise allows 500 single-file URLs, but the app uses 100 on every plan, so one limit applies to all.
 * Rate limits (5 requests per minute on Free) reach the screen as ApiError.RateLimited and show the rate-limit text.
 */
const val MAX_PURGE_ITEMS = 100
const val MAX_PURGE_FILES = MAX_PURGE_ITEMS

/** Validates a selection before it is sent. Returns null when the selection is acceptable. */
fun validatePurge(request: PurgeRequest): PurgeProblem? = when (request) {
    PurgeRequest.Everything -> null
    is PurgeRequest.Files -> checkItems(request.urls, MAX_PURGE_FILES) ?: schemeProblem(request.urls, required = true)
    is PurgeRequest.Hosts -> checkItems(request.hosts, MAX_PURGE_ITEMS) ?: schemeProblem(request.hosts, required = false)
    is PurgeRequest.Prefixes -> checkItems(request.prefixes, MAX_PURGE_ITEMS) ?: schemeProblem(request.prefixes, required = false)
    is PurgeRequest.Tags -> checkItems(request.tags, MAX_PURGE_ITEMS)
}

private fun checkItems(items: List<String>, max: Int): PurgeProblem? = when {
    items.isEmpty() -> PurgeProblem.EMPTY
    items.size > max -> PurgeProblem.TOO_MANY
    else -> null
}

private fun schemeProblem(items: List<String>, required: Boolean): PurgeProblem? {
    val hasScheme = items.map { it.startsWith("https://") || it.startsWith("http://") }
    return when {
        required && hasScheme.any { !it } -> PurgeProblem.NEEDS_SCHEME
        !required && hasScheme.any { it } -> PurgeProblem.MUST_NOT_HAVE_SCHEME
        else -> null
    }
}

private fun listBody(key: String, items: List<String>): JsonObject = buildJsonObject {
    put(key, JsonArray(items.map { JsonPrimitive(it) }))
}

/** Splits free text (one item per line or comma separated) into trimmed, non-empty items. */
fun parsePurgeItems(text: String): List<String> =
    text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
