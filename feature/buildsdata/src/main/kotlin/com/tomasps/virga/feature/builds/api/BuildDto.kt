package com.tomasps.virga.feature.builds.api

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One build as the Builds API returns it. Every field is nullable or defaulted, because the list item schema is
 * loosely typed and several fields are beta.
 */
@Serializable
data class BuildDto(
    @SerialName("build_uuid") val buildUuid: String? = null,
    val status: String? = null,
    @SerialName("build_outcome") val buildOutcome: String? = null,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("initializing_on") val initializingOn: String? = null,
    @SerialName("running_on") val runningOn: String? = null,
    @SerialName("stopped_on") val stoppedOn: String? = null,
    @SerialName("build_trigger_metadata") val trigger: TriggerMetadataDto? = null,
)

@Serializable
data class TriggerMetadataDto(
    val branch: String? = null,
    @SerialName("commit_hash") val commitHash: String? = null,
    @SerialName("commit_message") val commitMessage: String? = null,
    val author: String? = null,
)

/** Maps a wire build to a record. Returns null when the build has no id or no readable creation time. */
fun BuildDto.toRecord(scriptName: String): BuildRecord? {
    val uuid = buildUuid?.takeIf { it.isNotBlank() } ?: return null
    val created = parseInstant(createdOn) ?: return null
    return BuildRecord(
        buildUuid = uuid,
        scriptName = scriptName,
        status = BuildStatus.fromWire(status),
        outcome = BuildOutcome.fromWire(normalizeOutcome(buildOutcome)),
        branch = trigger?.branch?.takeIf { it.isNotBlank() },
        commitHash = trigger?.commitHash?.takeIf { it.isNotBlank() },
        commitMessage = trigger?.commitMessage?.takeIf { it.isNotBlank() },
        author = trigger?.author?.takeIf { it.isNotBlank() },
        createdOn = created,
        initializingOn = parseInstant(initializingOn),
        runningOn = parseInstant(runningOn),
        stoppedOn = parseInstant(stoppedOn),
    )
}

/**
 * The events schema spells outcomes differently from the REST API ("failure" and "failed" for fail). Map both
 * spellings onto the REST value before the shared enum parses it.
 */
internal fun normalizeOutcome(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    "failure", "failed" -> "fail"
    else -> raw
}

/** Parses an ISO-8601 instant, or returns null for a missing or malformed value. Never throws. */
internal fun parseInstant(raw: String?): Instant? =
    raw?.takeIf { it.isNotBlank() }?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
