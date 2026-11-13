package com.tomasps.virga.feature.home.home

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.home.R
import com.tomasps.virga.feature.home.analytics.ScriptRequests
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeResourceRowsTest {

    private fun worker(name: String) = SearchItem(SearchSource.WORKERS, name, name)
    private fun zone(id: String, name: String, status: String? = "active") = SearchItem(SearchSource.ZONES, id, name, status)

    @Test
    fun `Workers with traffic come first, busiest first, then the rest by name`() {
        val workers = listOf(worker("zeta"), worker("alpha"), worker("orbit-api"), worker("notes-sync"))
        val requests = listOf(ScriptRequests("orbit-api", 4_200), ScriptRequests("notes-sync", 2_650))

        val rows = workerRows(workers, requests, buildingScripts = emptySet())

        assertEquals(listOf("orbit-api", "notes-sync", "alpha", "zeta"), rows.map { it.name })
        assertEquals(listOf(4_200L, 2_650L, null, null), rows.map { it.requests })
    }

    @Test
    fun `a Worker with a running build is marked as building`() {
        val rows = workerRows(listOf(worker("orbit-api"), worker("notes-sync")), emptyList(), buildingScripts = setOf("notes-sync"))

        assertEquals(mapOf("orbit-api" to false, "notes-sync" to true), rows.associate { it.name to it.isBuilding })
    }

    @Test
    fun `zones are ordered by requests and zones without traffic are kept with no count`() {
        val zones = listOf(zone("z1", "quiet.example"), zone("z2", "busy.example"), zone("z3", "mid.example"))

        val rows = zoneRows(zones, mapOf("z2" to 900L, "z3" to 40L))

        assertEquals(listOf("busy.example", "mid.example", "quiet.example"), rows.map { it.name })
        assertEquals(listOf(900L, 40L, null), rows.map { it.requests })
    }

    @Test
    fun `a zone row carries the zone's status`() {
        val rows = zoneRows(listOf(zone("z1", "a.example", status = "moved")), emptyMap())

        assertEquals("moved", rows.single().status)
    }

    @Test
    fun `an active zone is healthy, a zone being set up is in progress, and a moved zone needs attention`() {
        assertEquals(StatusTone.Success, zoneStatusTone("active"))
        assertEquals(StatusTone.Pending, zoneStatusTone("pending"))
        assertEquals(StatusTone.Pending, zoneStatusTone("initializing"))
        assertEquals(StatusTone.Warning, zoneStatusTone("moved"))
    }

    @Test
    fun `a missing or unrecognised zone status has no dot and no label`() {
        assertNull(zoneStatusTone(null))
        assertNull(zoneStatusTone("something-new"))
        assertNull(zoneStatusLabelRes(null))
        assertNull(zoneStatusLabelRes("something-new"))
    }

    @Test
    fun `each known zone status has its own friendly label, and the raw word is never the label`() {
        assertEquals(R.string.home_zone_status_active, zoneStatusLabelRes("active"))
        assertEquals(R.string.home_zone_status_pending, zoneStatusLabelRes("pending"))
        assertEquals(R.string.home_zone_status_initializing, zoneStatusLabelRes("initializing"))
        assertEquals(R.string.home_zone_status_moved, zoneStatusLabelRes("moved"))
        assertEquals(R.string.home_zone_status_deactivated, zoneStatusLabelRes("deactivated"))
    }

    @Test
    fun `each build status has its own label, so Stopped is never shown as Stopping`() {
        assertEquals(R.string.home_build_status_running, buildStatusLabelRes(BuildStatus.RUNNING))
        assertEquals(R.string.home_build_status_queued, buildStatusLabelRes(BuildStatus.QUEUED))
        assertEquals(R.string.home_build_status_starting, buildStatusLabelRes(BuildStatus.INITIALIZING))
        assertEquals(R.string.home_build_status_stopped, buildStatusLabelRes(BuildStatus.STOPPED))
        assertEquals(R.string.home_build_status_unknown, buildStatusLabelRes(BuildStatus.UNKNOWN))
    }

    @Test
    fun `a build line names its branch, then its short commit, and never shows the build id`() {
        assertEquals("main", buildDetailText(build(branch = "main", commit = "abcdef123456")))
        assertEquals("abcdef1", buildDetailText(build(branch = null, commit = "abcdef123456")))
        assertEquals(null, buildDetailText(build(branch = null, commit = null)))
    }

    private fun build(branch: String?, commit: String?) = BuildSummary(
        buildUuid = "9f8e7d6c-5b4a-3210-fedc-ba9876543210",
        scriptName = "orbit-api",
        status = BuildStatus.RUNNING,
        outcome = null as BuildOutcome?,
        branch = branch,
        commitHash = commit,
        createdOn = Instant.parse("2026-11-11T11:50:00Z"),
    )
}
