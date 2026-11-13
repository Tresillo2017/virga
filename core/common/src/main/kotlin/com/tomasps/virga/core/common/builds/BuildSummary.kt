package com.tomasps.virga.core.common.builds

import java.time.Instant

/**
 * Lifecycle state of a Workers Build, as reported by the API. Anything not listed maps to [UNKNOWN] so a new
 * API value never breaks parsing.
 */
enum class BuildStatus {
    QUEUED,
    INITIALIZING,
    RUNNING,
    STOPPED,
    UNKNOWN;

    /** True while the build can still change state (queued, initializing or running). */
    val isActive: Boolean
        get() = this == QUEUED || this == INITIALIZING || this == RUNNING

    companion object {
        /** Maps the wire value (for example "running"), ignoring case. Null or unrecognised gives [UNKNOWN]. */
        fun fromWire(raw: String?): BuildStatus = when (raw?.trim()?.lowercase()) {
            "queued" -> QUEUED
            "initializing" -> INITIALIZING
            "running" -> RUNNING
            "stopped" -> STOPPED
            else -> UNKNOWN
        }
    }
}

/** Result of a finished build. Anything not listed maps to [UNKNOWN]. */
enum class BuildOutcome {
    SUCCESS,
    FAIL,
    CANCELED,
    SKIPPED,
    UNKNOWN;

    companion object {
        /** Maps the wire value, ignoring case. Null or unrecognised gives [UNKNOWN]. */
        fun fromWire(raw: String?): BuildOutcome = when (raw?.trim()?.lowercase()) {
            "success" -> SUCCESS
            "fail" -> FAIL
            "canceled", "cancelled" -> CANCELED
            "skipped" -> SKIPPED
            else -> UNKNOWN
        }
    }
}

/**
 * The fields of a Workers Build that the rest of the app shows: on Home, on a Worker's detail screen, and in a
 * watch notification. Optional fields are null when the API did not send them.
 */
data class BuildSummary(
    val buildUuid: String,
    val scriptName: String,
    val workerTag: String? = null,
    val status: BuildStatus,
    val outcome: BuildOutcome? = null,
    val branch: String? = null,
    val commitHash: String? = null,
    val commitMessage: String? = null,
    val author: String? = null,
    val createdOn: Instant,
    val startedOn: Instant? = null,
    val stoppedOn: Instant? = null,
)
