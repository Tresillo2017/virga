package com.tomasps.virga.feature.domains.zones

/** Zones whose name contains [query] (case-insensitive) and whose status matches [status] when it is set, sorted by name. */
fun filterZones(zones: List<Zone>, query: String, status: String?): List<Zone> {
    val needle = query.trim().lowercase()
    return zones
        .filter { zone -> needle.isEmpty() || zone.name.lowercase().contains(needle) }
        .filter { zone -> status == null || zone.status.equals(status, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
}
