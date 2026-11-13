package com.tomasps.virga.feature.home.search

import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.ZoneDetail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchParsingTest {

    private fun parse(source: SearchSource, text: String) =
        parseSearchItems(source, (Json.parseToJsonElement(text) as? JsonArray)?.toList().orEmpty())

    @Test
    fun `a Worker is identified by its script name`() {
        val items = parse(SearchSource.WORKERS, """[{"id":"orbit-api"},{"name":"no-id"}]""")

        assertEquals(listOf(SearchItem(SearchSource.WORKERS, "orbit-api", "orbit-api")), items)
    }

    @Test
    fun `a zone uses its id and shows its name`() {
        val items = parse(SearchSource.ZONES, """[{"id":"abc","name":"harbor-lane.example"}]""")

        assertEquals("abc", items.single().id)
        assertEquals("harbor-lane.example", items.single().title)
    }

    @Test
    fun `a zone keeps its status for the domain list`() {
        val items = parse(SearchSource.ZONES, """[{"id":"abc","name":"x.example","status":"pending"}]""")

        assertEquals("pending", items.single().status)
    }

    @Test
    fun `only zones carry a status`() {
        val items = parse(SearchSource.WORKERS, """[{"id":"orbit-api","status":"active"}]""")

        assertNull(items.single().status)
    }

    @Test
    fun `R2 buckets are read from the buckets wrapper`() {
        val items = parse(SearchSource.R2, """[{"name":"photo-archive"}]""")

        assertEquals(listOf("photo-archive"), items.map { it.title })
    }

    @Test
    fun `D1 databases use the uuid as the id and KV namespaces use the title`() {
        val d1 = parse(SearchSource.D1, """[{"uuid":"u-1","name":"orders"}]""").single()
        val kv = parse(SearchSource.KV, """[{"id":"k-1","title":"session-cache"}]""").single()

        assertEquals("u-1", d1.id)
        assertEquals("orders", d1.title)
        assertEquals("session-cache", kv.title)
    }

    @Test
    fun `an unexpected shape yields no items instead of an exception`() {
        assertTrue(parse(SearchSource.R2, """"not a list"""").isEmpty())
        assertTrue(parse(SearchSource.PAGES, """{"unexpected":true}""").isEmpty())
    }

    @Test
    fun `matching ignores case and returns nothing for a blank query`() {
        val items = listOf(
            SearchItem(SearchSource.WORKERS, "orbit-api", "orbit-api"),
            SearchItem(SearchSource.R2, "Photo-Archive", "Photo-Archive"),
        )

        assertEquals(listOf("Photo-Archive"), matchSearchItems(items, "  PHOTO ").map { it.title })
        assertTrue(matchSearchItems(items, "   ").isEmpty())
    }

    @Test
    fun `matching stops at fifty results`() {
        val items = (1..80).map { SearchItem(SearchSource.KV, "id$it", "ns-$it") }

        assertEquals(50, matchSearchItems(items, "ns").size)
    }

    @Test
    fun `each search hit opens the cross-feature key for its source`() {
        assertEquals(WorkerDetail("orbit-api"), SearchItem(SearchSource.WORKERS, "orbit-api", "x").destination())
        assertEquals(ZoneDetail("z1"), SearchItem(SearchSource.ZONES, "z1", "x").destination())
        assertEquals(R2Bucket("photo-archive"), SearchItem(SearchSource.R2, "photo-archive", "x").destination())
        assertEquals(D1Database("u-1"), SearchItem(SearchSource.D1, "u-1", "x").destination())
        assertEquals(KvNamespace("k-1"), SearchItem(SearchSource.KV, "k-1", "x").destination())
    }
}
