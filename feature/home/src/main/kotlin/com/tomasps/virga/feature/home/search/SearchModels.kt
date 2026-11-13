package com.tomasps.virga.feature.home.search

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.feature.home.R

/**
 * The lists Home searches. Each one names the token permission that reads it, so a missing permission is shown
 * as a row for that source rather than failing the whole search.
 */
enum class SearchSource(val permission: String, val labelRes: Int) {
    WORKERS("Workers Scripts Read", R.string.home_source_workers),
    ZONES("Zone Read", R.string.home_source_zones),
    PAGES("Cloudflare Pages Read", R.string.home_source_pages),
    R2("Workers R2 Storage Read", R.string.home_source_r2),
    D1("D1 Read", R.string.home_source_d1),
    KV("Workers KV Storage Read", R.string.home_source_kv),
}

/**
 * One searchable item. [id] is what the destination key needs (zone id, D1 uuid, KV namespace id, or a name).
 * [status] is the API's raw status word for a zone (for example "active"); other sources have none.
 */
@Immutable
data class SearchItem(
    val source: SearchSource,
    val id: String,
    val title: String,
    val status: String? = null,
)

/** Where a search hit opens. Only cross-feature keys from core:navigation are used. */
fun SearchItem.destination(): NavKey = when (source) {
    SearchSource.WORKERS -> WorkerDetail(scriptName = id)
    SearchSource.ZONES -> ZoneDetail(zoneId = id)
    SearchSource.PAGES -> PagesProject(projectName = id)
    SearchSource.R2 -> R2Bucket(bucketName = id)
    SearchSource.D1 -> D1Database(databaseId = id)
    SearchSource.KV -> KvNamespace(namespaceId = id)
}
