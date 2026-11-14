package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary

/** The five looks a build can have on the watch. */
enum class BuildVisualState { RUNNING, PASSED, FAILED, CANCELED, UNKNOWN }

/** Maps a build to its watch look: active is RUNNING, finished builds follow their outcome. */
fun BuildSummary.visualState(): BuildVisualState = when {
    status.isActive -> BuildVisualState.RUNNING
    status != BuildStatus.STOPPED -> BuildVisualState.UNKNOWN
    else -> when (outcome) {
        BuildOutcome.SUCCESS -> BuildVisualState.PASSED
        BuildOutcome.FAIL -> BuildVisualState.FAILED
        BuildOutcome.CANCELED, BuildOutcome.SKIPPED -> BuildVisualState.CANCELED
        BuildOutcome.UNKNOWN, null -> BuildVisualState.UNKNOWN
    }
}
