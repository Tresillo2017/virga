package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaginationTest {
    @Serializable
    data class Item(val id: Int)

    private val itemsSerializer = ListSerializer(Item.serializer())

    private fun items(from: Int, count: Int): JsonArray = buildJsonArray {
        for (i in from until from + count) add(buildJsonObject { put("id", i) })
    }

    @Test
    fun `page style walks every page until a short page`() = runBlocking {
        val pagesRequested = mutableListOf<String?>()
        val api = mockCloudflareApi {
            get("/zones") { request ->
                pagesRequested += request.query["page"]
                val page = request.query["page"]!!.toInt()
                val (data, total) = if (page == 1) items(1, 2) to 3 else items(3, 1) to 3
                DemoReply.ok(data, buildJsonObject { put("page", page); put("per_page", 2); put("count", data.size); put("total_count", total) })
            }
        }
        val pages = api.pagedFlow("/zones", perPage = 2, deserializer = itemsSerializer).toList()
        assertEquals(listOf("1", "2"), pagesRequested)
        val ids = pages.flatMap { (it as ApiResult.Success).value }.map { it.id }
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun `cursor style follows the next cursor until it is empty`() = runBlocking {
        val cursorsSeen = mutableListOf<String?>()
        val api = mockCloudflareApi {
            get("/logs") { request ->
                cursorsSeen += request.query["cursor"]
                val result: JsonArray
                val info: JsonObject
                when (request.query["cursor"]) {
                    null -> { result = items(1, 2); info = buildJsonObject { put("cursors", buildJsonObject { put("after", "c2") }) } }
                    "c2" -> { result = items(3, 1); info = buildJsonObject { put("cursors", buildJsonObject { put("after", "") }) } }
                    else -> error("unexpected cursor")
                }
                DemoReply.ok(result, info)
            }
        }
        val all = api.pagedFlow("/logs", deserializer = itemsSerializer).toList()
        assertEquals(listOf(null, "c2"), cursorsSeen)
        assertEquals(listOf(1, 2, 3), all.flatMap { (it as ApiResult.Success).value }.map { it.id })
    }

    @Test
    fun `a failing page is emitted and ends the walk`() = runBlocking {
        val api = mockCloudflareApi {
            get("/zones") { request ->
                if (request.query["page"] == "1") DemoReply.ok(items(1, 2), buildJsonObject { put("page", 1); put("per_page", 2); put("total_count", 10) })
                else DemoReply.failure(HttpStatusCode.InternalServerError, 1, "down")
            }
        }
        val pages = api.pagedFlow("/zones", perPage = 2, deserializer = itemsSerializer).toList()
        assertEquals(2, pages.size)
        assert(pages.last() is ApiResult.Failure)
    }

    @Test
    fun `next position stops on an empty page or a missing cursor`() {
        assertNull(nextPagePosition(PageInfo.Page(1, 2, 0, null), 0, PagePosition(), 2))
        assertNull(nextPagePosition(PageInfo.Cursor(null, null, null), 5, PagePosition(), 2))
        assertNull(nextPagePosition(null, 2, PagePosition(), 2))
    }

    @Test
    fun `next position stops at the reported total`() {
        assertNull(nextPagePosition(PageInfo.Page(2, 2, 2, 4), 2, PagePosition(page = 2), 2))
        assertEquals(PagePosition(page = 2), nextPagePosition(PageInfo.Page(1, 2, 2, 4), 2, PagePosition(), 2))
    }
}
