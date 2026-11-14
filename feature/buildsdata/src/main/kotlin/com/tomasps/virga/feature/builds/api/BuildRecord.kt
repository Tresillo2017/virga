package com.tomasps.virga.feature.builds.api

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import java.time.Instant

/** The four stages Virga shows. The API has no step progress, so a stage comes from the status alone. */
enum class BuildStage { QUEUED, INITIALIZING, RUNNING, DONE }

/**
 * A build as the feature sees it: every timestamp the API sent, used for stage timing and the log view.
 * [toSummary] drops the detail the rest of the app does not need.
 */
data class BuildRecord(
    val buildUuid: String,
    val scriptName: String,
    val status: BuildStatus,
    val outcome: BuildOutcome?,
    val branch: String? = null,
    val commitHash: String? = null,
    val commitMessage: String? = null,
    val author: String? = null,
    val createdOn: Instant,
    val initializingOn: Instant? = null,
    val runningOn: Instant? = null,
    val stoppedOn: Instant? = null,
) {
    /** A build is finished once it has stopped, or once the API reports a known outcome. */
    val isTerminal: Boolean
        get() = status == BuildStatus.STOPPED || stoppedOn != null || (outcome != null && outcome != BuildOutcome.UNKNOWN)

    /** The stage to show, or null when the status is one Virga does not recognise. */
    val stage: BuildStage?
        get() = when {
            isTerminal -> BuildStage.DONE
            status == BuildStatus.QUEUED -> BuildStage.QUEUED
            status == BuildStatus.INITIALIZING -> BuildStage.INITIALIZING
            status == BuildStatus.RUNNING -> BuildStage.RUNNING
            else -> null
        }

    fun toSummary(): BuildSummary = BuildSummary(
        buildUuid = buildUuid,
        scriptName = scriptName,
        status = status,
        outcome = outcome,
        branch = branch,
        commitHash = commitHash,
        commitMessage = commitMessage,
        author = author,
        createdOn = createdOn,
        startedOn = runningOn ?: initializingOn,
        stoppedOn = stoppedOn,
    )
}
