package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import java.time.Instant

internal val NOW: Instant = Instant.parse("2026-11-14T12:00:00Z")

internal fun testBuild(
    status: BuildStatus = BuildStatus.STOPPED,
    outcome: BuildOutcome? = null,
    scriptName: String = "api",
    createdOn: Instant = NOW.minusSeconds(3 * 3600),
    startedOn: Instant? = null,
    stoppedOn: Instant? = null,
) = BuildSummary(
    buildUuid = "b-1",
    scriptName = scriptName,
    status = status,
    outcome = outcome,
    createdOn = createdOn,
    startedOn = startedOn,
    stoppedOn = stoppedOn,
)
