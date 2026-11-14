package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildSummary
import java.time.Duration
import java.time.Instant

val STALE_AFTER: Duration = Duration.ofMinutes(30)

/** What the tile shows. [isStale] is true when the data was never fetched or is older than the stale limit. */
data class BuildTileModel(
    val workerName: String,
    val state: BuildVisualState,
    val detail: String,
    val isStale: Boolean,
)

fun tileFor(
    latest: BuildSummary?,
    fetchedAt: Instant?,
    now: Instant,
    staleAfter: Duration = STALE_AFTER,
): BuildTileModel {
    val isStale = fetchedAt == null || Duration.between(fetchedAt, now) > staleAfter
    if (latest == null) return BuildTileModel(NO_BUILDS_TEXT, BuildVisualState.UNKNOWN, "", isStale)
    val state = latest.visualState()
    val since = if (state == BuildVisualState.RUNNING) latest.startedOn else latest.stoppedOn
    return BuildTileModel(latest.scriptName, state, ageText(since ?: latest.createdOn, now), isStale)
}
