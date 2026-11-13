package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val LIST = "/accounts/{accountId}/d1/database"
private const val ONE = "/accounts/{accountId}/d1/database/{databaseId}"

class DatabasesRepositoryTest {

    @Test
    fun `list requests the page and page size and keeps result_info`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            get(LIST) { request ->
                assertEquals("2", request.query["page"])
                assertEquals(D1Limits.LIST_PAGE_SIZE.toString(), request.query["per_page"])
                DemoReply.ok(
                    buildJsonArray {
                        add(buildJsonObject { put("uuid", "d-1"); put("name", "orders") })
                    },
                    resultInfo = buildJsonObject {
                        put("page", 2)
                        put("per_page", D1Limits.LIST_PAGE_SIZE)
                        put("total_count", 51)
                    },
                )
            }
        }

        // Act
        val result = DatabasesRepository(api).list(ACCOUNT, page = 2)

        // Assert
        val success = result as ApiResult.Success
        assertEquals("orders", success.value.single().name)
        assertEquals(51, (success.pageInfo as PageInfo.Page).totalCount)
    }

    @Test
    fun `create sends name and omits jurisdiction and hint when they are not chosen`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(LIST) { request ->
                val body = request.bodyText.orEmpty()
                assertTrue(body.contains("\"name\":\"ledger\""))
                assertTrue(!body.contains("jurisdiction") && !body.contains("primary_location_hint"))
                DemoReply.ok(buildJsonObject { put("uuid", "d-9"); put("name", "ledger") })
            }
        }

        // Act
        val result = DatabasesRepository(api).create(ACCOUNT, CreateDatabaseBody(name = "ledger"))

        // Assert
        assertEquals("d-9", (result as ApiResult.Success).value.uuid)
    }

    @Test
    fun `create sends jurisdiction when chosen`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(LIST) { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"jurisdiction\":\"eu\""))
                DemoReply.ok(buildJsonObject { put("name", "ledger") })
            }
        }

        val result = DatabasesRepository(api).create(ACCOUNT, CreateDatabaseBody(name = "ledger", jurisdiction = "eu"))

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `detail reads read replication mode and tolerates a missing file size`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get(ONE) {
                DemoReply.ok(buildJsonObject {
                    put("uuid", "d-1")
                    put("name", "orders")
                    putJsonObject("read_replication") { put("mode", "auto") }
                })
            }
        }

        val database = (DatabasesRepository(api).get(ACCOUNT, "d-1") as ApiResult.Success).value

        assertEquals("auto", database.readReplication?.mode)
        assertNull(database.fileSize)
    }

    @Test
    fun `replication change is a PUT that sends only the documented read_replication field`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            put(ONE) { request ->
                assertEquals("{\"read_replication\":{\"mode\":\"disabled\"}}", request.bodyText)
                DemoReply.ok(buildJsonObject { put("uuid", "d-1") })
            }
        }

        val result = DatabasesRepository(api).setReadReplication(ACCOUNT, "d-1", mode = "disabled")

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `undelete posts to the undelete path and omits new_name when no new name is given`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/d1/database/{databaseId}/undelete") { request ->
                assertEquals("{}", request.bodyText)
                DemoReply.ok(buildJsonObject { put("uuid", "d-1"); put("name", "orders") })
            }
        }

        val result = DatabasesRepository(api).undelete(ACCOUNT, "d-1")

        assertEquals("orders", (result as ApiResult.Success).value.name)
    }

    @Test
    fun `undelete sends new_name only when a replacement name is given`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/d1/database/{databaseId}/undelete") { request ->
                assertEquals("{\"new_name\":\"orders-2\"}", request.bodyText)
                DemoReply.ok(buildJsonObject { put("uuid", "d-1") })
            }
        }

        val result = DatabasesRepository(api).undelete(ACCOUNT, "d-1", newName = "orders-2")

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `delete succeeds on an empty result object`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            delete(ONE) { DemoReply.ok(buildJsonObject {}) }
        }

        val result = DatabasesRepository(api).delete(ACCOUNT, "d-1")

        assertTrue(result is ApiResult.Success)
    }
}
