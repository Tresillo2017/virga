package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildStage

/** Number of Live Update segments: queued, initializing, running, done. */
const val WATCH_SEGMENT_COUNT = 4

/** How a watch ended, which picks the wording of the final notification. */
enum class FinalKind { SUCCEEDED, FAILED, CANCELED, SKIPPED, FINISHED, EXPIRED, LOST }

/** Maps a finished build to its final kind. A known outcome names the result; anything else reads as "finished". */
fun finalKindOf(record: BuildRecord): FinalKind = when (record.outcome) {
    BuildOutcome.SUCCESS -> FinalKind.SUCCEEDED
    BuildOutcome.FAIL -> FinalKind.FAILED
    BuildOutcome.CANCELED -> FinalKind.CANCELED
    BuildOutcome.SKIPPED -> FinalKind.SKIPPED
    BuildOutcome.UNKNOWN, null -> FinalKind.FINISHED
}

/** Filled segments for a stage. A finished build fills them all; an unknown stage fills none. */
fun completedSegments(stage: BuildStage?): Int = when (stage) {
    BuildStage.QUEUED, null -> 0
    BuildStage.INITIALIZING -> 1
    BuildStage.RUNNING -> 2
    BuildStage.DONE -> WATCH_SEGMENT_COUNT
}

/** Stable notification id for a build, so later updates replace the same notification. */
fun notificationIdFor(buildUuid: String): Int = FIRST_NOTIFICATION_ID + (buildUuid.hashCode() and ID_MASK)

/** Id of the one-off result notification for a build. Kept apart from the ongoing id so the two never collide. */
fun resultNotificationIdFor(buildUuid: String): Int = RESULT_ID_BASE + (buildUuid.hashCode() and ID_MASK)

/** Request code of the Cancel action's PendingIntent. Distinct from the content intent, which shares the build's id. */
fun cancelRequestCodeFor(buildUuid: String): Int = notificationIdFor(buildUuid) xor CANCEL_REQUEST_FLAG

private const val FIRST_NOTIFICATION_ID = 40_000
private const val RESULT_ID_BASE = 50_000_000
private const val ID_MASK = 0xFFFFF
private const val CANCEL_REQUEST_FLAG = 0x4000_0000
