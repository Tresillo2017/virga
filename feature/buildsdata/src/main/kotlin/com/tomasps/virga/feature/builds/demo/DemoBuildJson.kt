package com.tomasps.virga.feature.builds.demo

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

/** The status as the API spells it. */
internal fun BuildStatus.wireName(): String? = when (this) {
    BuildStatus.QUEUED -> "queued"
    BuildStatus.INITIALIZING -> "initializing"
    BuildStatus.RUNNING -> "running"
    BuildStatus.STOPPED -> "stopped"
    BuildStatus.UNKNOWN -> null
}

/** The outcome as the REST API spells it. Note "cancelled" with two Ls. */
internal fun BuildOutcome.wireName(): String? = when (this) {
    BuildOutcome.SUCCESS -> "success"
    BuildOutcome.FAIL -> "fail"
    BuildOutcome.CANCELED -> "cancelled"
    BuildOutcome.SKIPPED -> "skipped"
    BuildOutcome.UNKNOWN -> null
}

/** One demo build in the shape GET /builds/builds/{uuid} returns. */
internal fun DemoBuild.toWireJson(): JsonObject = buildJsonObject {
    put("build_uuid", uuid)
    status.wireName()?.let { put("status", it) }
    outcome?.wireName()?.let { put("build_outcome", it) }
    put("created_on", createdOn.toString())
    initializingOn?.let { put("initializing_on", it.toString()) }
    runningOn?.let { put("running_on", it.toString()) }
    stoppedOn?.let { put("stopped_on", it.toString()) }
    putJsonObject("build_trigger_metadata") {
        put("branch", branch)
        put("commit_hash", commitHash)
        put("commit_message", commitMessage)
        put("author", author)
        put("build_trigger_source", "push")
    }
}

/** The log as GET /builds/builds/{uuid}/logs returns it: `[unix seconds, message]` pairs from [fromIndex] on. */
internal fun logWire(lines: List<String>, anchorMillis: Long, fromIndex: Int, intervalMillis: Long): JsonArray =
    buildJsonArray {
        lines.drop(fromIndex).forEachIndexed { offset, message ->
            val index = fromIndex + offset
            add(buildJsonArray {
                add(JsonPrimitive((anchorMillis + index * intervalMillis) / MILLIS_PER_SECOND))
                add(JsonPrimitive(message))
            })
        }
    }

private const val MILLIS_PER_SECOND = 1_000L

/** Canned log of a finished build. Read in one piece, with no cursor, so the screen holds it as the whole log. */
internal val FINISHED_LOG_LINES: List<String> = listOf(
    "Cloning repository",
    "Installing dependencies",
    "Running build command",
    "Bundling worker",
    "Uploading version",
    "Deploying to production",
)

internal fun instantOf(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)
