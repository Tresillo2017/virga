package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.feature.builds.api.StageTiming

/** State of one segment of the four-stage bar on the build detail. */
internal enum class StageSegment { DONE, CURRENT, PENDING }

/**
 * Maps the stage timings to bar segments. While a build is still moving, the last stage that has started is the
 * current one and everything before it is done. A finished build marks every stage done.
 */
internal fun stageSegments(timings: List<StageTiming>, isTerminal: Boolean): List<StageSegment> {
    if (isTerminal) return timings.map { StageSegment.DONE }
    val current = timings.indexOfLast { it.elapsed != null }
    return timings.mapIndexed { index, _ ->
        when {
            current < 0 -> StageSegment.PENDING
            index < current -> StageSegment.DONE
            index == current -> StageSegment.CURRENT
            else -> StageSegment.PENDING
        }
    }
}
