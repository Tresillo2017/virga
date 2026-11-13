package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

private const val BOOKMARK_PATH = "/accounts/{accountId}/d1/database/{databaseId}/time_travel/bookmark"
private const val RESTORE_PATH = "/accounts/{accountId}/d1/database/{databaseId}/time_travel/restore"

class TimeTravelRepositoryTest {

    @Test
    fun `current bookmark is read from the bookmark endpoint without a timestamp`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get(BOOKMARK_PATH) { request ->
                assertEquals(null, request.query["timestamp"])
                DemoReply.ok(buildJsonObject { put("bookmark", "00000001-00000002") })
            }
        }

        val bookmark = TimeTravelRepository(api).currentBookmark("acc-1", "db-1")

        assertEquals("00000001-00000002", (bookmark as ApiResult.Success).value.bookmark)
    }

    @Test
    fun `bookmark at a timestamp passes the ISO timestamp as a query parameter`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get(BOOKMARK_PATH) { request ->
                assertEquals("2026-11-10T08:00:00Z", request.query["timestamp"])
                DemoReply.ok(buildJsonObject { put("bookmark", "00000009") })
            }
        }

        val result = TimeTravelRepository(api).bookmarkAt("acc-1", "db-1", "2026-11-10T08:00:00Z")

        assertEquals("00000009", (result as ApiResult.Success).value.bookmark)
    }

    @Test
    fun `restore to a bookmark sends it as a query parameter and returns the undo bookmark`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(RESTORE_PATH) { request ->
                assertEquals("00000009", request.query["bookmark"])
                assertEquals(null, request.query["timestamp"])
                DemoReply.ok(buildJsonObject {
                    put("message", "Database restored")
                    put("previous_bookmark", "00000012")
                })
            }
        }

        val result = TimeTravelRepository(api).restoreToBookmark("acc-1", "db-1", "00000009")

        assertEquals("00000012", (result as ApiResult.Success).value.previousBookmark)
    }

    @Test
    fun `restore to a timestamp sends it as a query parameter`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(RESTORE_PATH) { request ->
                assertEquals("2026-11-10T08:00:00Z", request.query["timestamp"])
                DemoReply.ok(buildJsonObject { put("bookmark", "00000009") })
            }
        }

        val result = TimeTravelRepository(api).restoreToTimestamp("acc-1", "db-1", "2026-11-10T08:00:00Z")

        assertEquals("00000009", (result as ApiResult.Success).value.bookmark)
    }
}
