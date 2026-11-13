package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.feature.workers.data.Deployment
import com.tomasps.virga.feature.workers.data.DeploymentVersion
import com.tomasps.virga.feature.workers.data.WorkerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val NEW_ID = "9f1c0e2a-0000-4000-8000-000000000002"
private const val OLD_ID = "9f1c0e2a-0000-4000-8000-000000000001"
private const val UNKNOWN_ID = "abcdef12-0000-4000-8000-000000000009"

class BuildingNowTest {

    private fun build(status: BuildStatus, outcome: BuildOutcome? = null, stoppedOn: Instant? = null) = BuildSummary(
        buildUuid = "build-1",
        scriptName = "edge-api",
        status = status,
        outcome = outcome,
        createdOn = Instant.parse("2026-11-11T08:58:00Z"),
        stoppedOn = stoppedOn,
    )

    @Test
    fun `a running build with no outcome is building`() {
        assertTrue(build(BuildStatus.RUNNING).isBuildingNow())
        assertTrue(build(BuildStatus.QUEUED).isBuildingNow())
    }

    @Test
    fun `a build with an outcome has finished even when its status is stale`() {
        assertFalse(build(BuildStatus.RUNNING, outcome = BuildOutcome.SUCCESS).isBuildingNow())
    }

    @Test
    fun `a build with a stop time has finished even when its status is stale`() {
        assertFalse(build(BuildStatus.RUNNING, stoppedOn = Instant.parse("2026-11-11T09:00:00Z")).isBuildingNow())
    }

    @Test
    fun `a stopped build is not building`() {
        assertFalse(build(BuildStatus.STOPPED).isBuildingNow())
    }
}

class VersionNamesTest {

    private val snapshot = snapshotWith(
        versions = listOf(WorkerVersion(id = NEW_ID, number = 2), WorkerVersion(id = OLD_ID, number = 1)),
    )
    private val names = VersionNames(snapshot, versionFormat = "Version %1\$s", splitFormat = "%1\$s at %2\$s%%")

    @Test
    fun `a known version reads as its number`() {
        assertEquals("Version 2", names.version(NEW_ID))
        assertEquals("Version 1", names.version(OLD_ID))
    }

    @Test
    fun `an unknown version falls back to its short id instead of a blank`() {
        assertEquals("abcdef12", names.version(UNKNOWN_ID))
    }

    @Test
    fun `a split reads as each version at its share`() {
        val deployment = Deployment(
            id = "dep-1",
            versions = listOf(DeploymentVersion(NEW_ID, 90.0), DeploymentVersion(OLD_ID, 10.0)),
        )

        assertEquals("Version 2 at 90, Version 1 at 10", names.split(deployment).replace("%", ""))
        assertEquals("Version 2 at 90%, Version 1 at 10%", names.split(deployment))
    }

    private fun snapshotWith(versions: List<WorkerVersion>) = WorkerSnapshot(
        settings = com.tomasps.virga.feature.workers.data.ScriptSettings(),
        bindings = emptyList(),
        subdomain = com.tomasps.virga.feature.workers.data.SubdomainState(),
        domains = emptyList(),
        deployments = emptyList(),
        versions = versions,
    )
}
