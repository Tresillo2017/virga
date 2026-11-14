package com.tomasps.virga.feature.builds.api

import java.time.Duration
import java.time.Instant

/** Time spent in one stage, or null when the stage has not started (or the API did not say when). */
data class StageTiming(val stage: BuildStage, val elapsed: Duration?)

/**
 * Per-stage elapsed time from the timestamps. A running stage counts up to [now]; a finished one stops at its
 * end. Negative spans from clock skew in the API are clamped to zero. DONE shows the total for a finished build.
 */
fun stageTimings(record: BuildRecord, now: Instant): List<StageTiming> {
    val initStart = record.initializingOn
    val runStart = record.runningOn
    val stop = record.stoppedOn
    return listOf(
        StageTiming(
            stage = BuildStage.QUEUED,
            elapsed = span(record.createdOn, initStart ?: runStart ?: stop ?: now),
        ),
        StageTiming(
            stage = BuildStage.INITIALIZING,
            elapsed = initStart?.let { span(it, runStart ?: stop ?: now) },
        ),
        StageTiming(
            stage = BuildStage.RUNNING,
            elapsed = runStart?.let { span(it, stop ?: now) },
        ),
        StageTiming(
            stage = BuildStage.DONE,
            elapsed = stop?.let { span(record.createdOn, it) },
        ),
    )
}

private fun span(from: Instant, to: Instant): Duration {
    val between = Duration.between(from, to)
    return if (between.isNegative) Duration.ZERO else between
}
