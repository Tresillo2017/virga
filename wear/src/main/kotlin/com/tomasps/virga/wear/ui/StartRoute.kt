package com.tomasps.virga.wear.ui

import android.net.Uri

internal const val EXTRA_START_ROUTE = "com.tomasps.virga.wear.START_ROUTE"

private const val DETAIL_PREFIX = "build"
private const val DETAIL_PARTS = 3

/**
 * The detail route a launch intent asks to open, or null for anything else. The extra comes from an exported
 * activity, so it is only accepted in exactly the shape [detailRoute] produces: `build/{script}/{uuid}` with each part
 * encoded canonically (it must survive a decode and encode round trip unchanged).
 */
internal fun startRouteFrom(route: String?): String? {
    val parts = route?.split('/') ?: return null
    if (parts.size != DETAIL_PARTS || parts[0] != DETAIL_PREFIX) return null
    val isValid = parts.drop(1).all { it.isNotBlank() && Uri.encode(Uri.decode(it)) == it }
    return route.takeIf { isValid }
}
