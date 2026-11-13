package com.tomasps.virga.feature.home.home

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.feature.home.analytics.ScriptRequests
import com.tomasps.virga.feature.home.search.SearchItem

/** How many Workers and domains the Home cards show. The "See all" action opens the full list. */
internal const val SHORTCUT_LIMIT = 5

private const val SHORT_COMMIT_LENGTH = 7

/** One Worker row: its name, its requests in the range when analytics has them, and whether it has a build running. */
data class WorkerRow(val name: String, val requests: Long?, val isBuilding: Boolean)

/** One domain row: its name, its raw status word, and its requests in the range when analytics has them. */
data class ZoneRow(val id: String, val name: String, val status: String?, val requests: Long?)

/**
 * Every Worker from the loaded list, busiest first, then the rest by name. A Worker with no analytics row has no
 * count, and it is still listed, so the card matches the account's real Workers.
 */
internal fun workerRows(
    workers: List<SearchItem>,
    requestsByScript: List<ScriptRequests>,
    buildingScripts: Set<String>,
): List<WorkerRow> {
    val requests = requestsByScript.associate { it.scriptName to it.requests }
    return workers
        .map { worker -> WorkerRow(worker.id, requests[worker.id], worker.id in buildingScripts) }
        .sortedWith(compareByDescending<WorkerRow> { it.requests ?: NO_COUNT }.thenBy { it.name })
}

/**
 * Every zone from the loaded list, busiest first, then the rest by name. A zone without traffic in the range is
 * still listed, with no count.
 */
internal fun zoneRows(zones: List<SearchItem>, requestsByZone: Map<String, Long>): List<ZoneRow> =
    zones
        .map { zone -> ZoneRow(zone.id, zone.title, zone.status, requestsByZone[zone.id]) }
        .sortedWith(compareByDescending<ZoneRow> { it.requests ?: NO_COUNT }.thenBy { it.name })

/** The line under a build's status: its branch, else its short commit. Null when neither is known, never the build id. */
internal fun buildDetailText(build: BuildSummary): String? =
    build.branch?.takeIf { it.isNotBlank() } ?: build.commitHash?.take(SHORT_COMMIT_LENGTH)?.takeIf { it.isNotBlank() }

private const val NO_COUNT = -1L
