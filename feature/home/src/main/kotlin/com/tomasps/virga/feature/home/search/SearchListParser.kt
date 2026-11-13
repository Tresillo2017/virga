package com.tomasps.virga.feature.home.search

import com.tomasps.virga.feature.home.analytics.objectList
import com.tomasps.virga.feature.home.analytics.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Reads the rows of one list into search items. The caller has already unwrapped each product's envelope (R2's
 * `buckets` array, for example). Shapes differ per product: D1 names its id `uuid`, KV names its title `title`.
 * Anything without the needed fields is skipped, never guessed.
 */
fun parseSearchItems(source: SearchSource, rows: List<JsonElement>): List<SearchItem> =
    rows.mapNotNull { row -> (row as? JsonObject)?.toSearchItem(source) }

private fun JsonObject.toSearchItem(source: SearchSource): SearchItem? {
    val (id, title) = when (source) {
        SearchSource.WORKERS -> stringOrNull("id")?.let { it to it }
        SearchSource.ZONES -> stringOrNull("id")?.let { id -> id to (stringOrNull("name") ?: id) }
        SearchSource.PAGES -> stringOrNull("name")?.let { it to it }
        SearchSource.R2 -> stringOrNull("name")?.let { it to it }
        SearchSource.D1 -> stringOrNull("uuid")?.let { id -> id to (stringOrNull("name") ?: id) }
        SearchSource.KV -> stringOrNull("id")?.let { id -> id to (stringOrNull("title") ?: id) }
    } ?: return null
    if (id.isBlank()) return null
    val status = if (source == SearchSource.ZONES) stringOrNull("status") else null
    return SearchItem(source = source, id = id, title = title, status = status)
}
