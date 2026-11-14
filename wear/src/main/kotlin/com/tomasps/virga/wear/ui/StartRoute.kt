package com.tomasps.virga.wear.ui

internal const val EXTRA_START_ROUTE = "com.tomasps.virga.wear.START_ROUTE"

/** The detail route a launch intent asks to open, or null for anything else (never trust an extra as a route). */
internal fun startRouteFrom(route: String?): String? {
    val parts = route?.split('/') ?: return null
    return route.takeIf { parts.size == 3 && parts[0] == "build" && parts.all { it.isNotBlank() } }
}
