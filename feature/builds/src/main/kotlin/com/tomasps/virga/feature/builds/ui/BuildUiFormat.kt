package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.formatDuration
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildStage
import java.time.Duration

/** Colour of a build's status dot, from its stage and outcome. */
internal fun BuildRecord.statusTone(): StatusTone = when {
    !isTerminal -> StatusTone.Pending
    outcome == BuildOutcome.SUCCESS -> StatusTone.Success
    outcome == BuildOutcome.FAIL -> StatusTone.Error
    else -> StatusTone.Neutral
}

/** Label for the stage, used on the detail screen's stage rows. */
internal fun stageLabelRes(stage: BuildStage): Int = when (stage) {
    BuildStage.QUEUED -> R.string.builds_stage_queued
    BuildStage.INITIALIZING -> R.string.builds_stage_initializing
    BuildStage.RUNNING -> R.string.builds_stage_running
    BuildStage.DONE -> R.string.builds_stage_done
}

/** Label for the finished outcome, or the in-progress stage when the build has not finished. */
internal fun statusLabelRes(record: BuildRecord): Int {
    if (!record.isTerminal) return record.stage?.let(::stageLabelRes) ?: R.string.builds_stage_unknown
    return when (record.outcome) {
        BuildOutcome.SUCCESS -> R.string.builds_outcome_success
        BuildOutcome.FAIL -> R.string.builds_outcome_fail
        BuildOutcome.CANCELED -> R.string.builds_outcome_canceled
        BuildOutcome.SKIPPED -> R.string.builds_outcome_skipped
        BuildOutcome.UNKNOWN, null -> R.string.builds_outcome_finished
    }
}

/** Short build id for display. The full UUID is still used for every call. */
internal fun shortBuildId(buildUuid: String): String = buildUuid.take(SHORT_ID_LENGTH)

private const val SHORT_ID_LENGTH = 8

/** Characters of a commit hash shown in a list row. The full hash stays on the detail screen. */
internal const val SHORT_COMMIT_LENGTH = 7

/** Elapsed time of a finished build, from the first stage it reached to its stop. Null while it is still running. */
internal fun buildDurationText(build: BuildRecord): String? {
    val stop = build.stoppedOn ?: return null
    val start = build.runningOn ?: build.initializingOn ?: build.createdOn
    return formatDuration(Duration.between(start, stop).toMillis().coerceAtLeast(0L))
}
