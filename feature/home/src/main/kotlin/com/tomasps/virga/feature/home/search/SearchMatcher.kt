package com.tomasps.virga.feature.home.search

private const val MAX_RESULTS = 50

/**
 * Case-insensitive substring match on the title, in the order the lists arrived. A blank query matches nothing,
 * so the results area only appears once the user has typed something.
 */
fun matchSearchItems(items: List<SearchItem>, query: String): List<SearchItem> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return items.asSequence()
        .filter { it.title.lowercase().contains(needle) }
        .take(MAX_RESULTS)
        .toList()
}
