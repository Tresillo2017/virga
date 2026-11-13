package com.tomasps.virga.feature.domains

import com.tomasps.virga.core.common.result.PageInfo

/**
 * Whether another page follows the one just read. Uses the total when the reference returns one, otherwise a full
 * page means there may be more. Cursor-style results follow the next cursor.
 */
fun hasMorePages(info: PageInfo?, page: Int, itemCount: Int, requestedPerPage: Int): Boolean = when (info) {
    is PageInfo.Page -> {
        val perPage = info.perPage?.takeIf { it > 0 } ?: requestedPerPage
        info.totalCount?.let { page * perPage < it } ?: (itemCount >= perPage)
    }
    is PageInfo.Cursor -> !info.nextCursor.isNullOrEmpty()
    null -> false
}
