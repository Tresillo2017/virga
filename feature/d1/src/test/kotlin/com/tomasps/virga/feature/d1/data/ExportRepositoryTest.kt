package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPORT = "/accounts/{accountId}/d1/database/{databaseId}/export"
private const val SIGNED = "https://example.invalid/dump.sql?sig=abc"

class ExportRepositoryTest {

    @Test
    fun `the first request asks for polling and sends structure-only options when chosen`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(EXPORT) { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"output_format\":\"polling\""))
                assertTrue(request.bodyText.orEmpty().contains("\"no_data\":true"))
                assertTrue(!request.bodyText.orEmpty().contains("current_bookmark"))
                DemoReply.ok(buildJsonObject { put("status", "active"); put("at_bookmark", "00000010") })
            }
        }

        // Act
        val result = ExportRepository(api).start("acc-1", "db-1", ExportOptions(structureOnly = true))

        // Assert
        assertEquals("active", (result as ApiResult.Success).value.status)
    }

    @Test
    fun `polling sends the bookmark from the first response`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(EXPORT) { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"current_bookmark\":\"00000010\""))
                assertTrue(!request.bodyText.orEmpty().contains("dump_options"))
                DemoReply.ok(buildJsonObject { put("status", "active") })
            }
        }

        val result = ExportRepository(api).poll("acc-1", "db-1", bookmark = "00000010")

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `awaiting completion polls until the export is complete and returns the signed link`() = runBlocking<Unit> {
        // Arrange
        var polls = 0
        val api = mockCloudflareApi {
            post(EXPORT) {
                polls++
                if (polls < 3) {
                    DemoReply.ok(buildJsonObject { put("status", "active"); put("at_bookmark", "00000010") })
                } else {
                    DemoReply.ok(buildJsonObject {
                        put("status", "complete")
                        put("at_bookmark", "00000010")
                        putJsonObject("result") { put("filename", "orders.sql"); put("signed_url", SIGNED) }
                    })
                }
            }
        }
        val waits = mutableListOf<Long>()
        val repository = ExportRepository(api, pause = { waits += it })

        // Act
        val file = repository.awaitCompletion(
            accountId = "acc-1",
            databaseId = "db-1",
            first = repository.start("acc-1", "db-1", ExportOptions()).let { (it as ApiResult.Success).value },
            onProgress = {},
        )

        // Assert
        val success = file as ApiResult.Success
        assertEquals(SIGNED, success.value.signedUrl)
        assertEquals("orders.sql", success.value.filename)
        assertEquals(List(2) { D1Limits.EXPORT_POLL_INTERVAL_MILLIS }, waits)
    }

    @Test
    fun `an export that reports an error fails instead of polling forever`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(EXPORT) { DemoReply.ok(buildJsonObject { put("status", "error"); put("error", "dump failed") }) }
        }
        val repository = ExportRepository(api, pause = {})

        val file = repository.awaitCompletion("acc-1", "db-1", ExportPollRef.error(), onProgress = {})

        assertTrue(file is ApiResult.Failure)
    }

    @Test
    fun `an export still running after the maximum wait fails with a local error`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(EXPORT) { DemoReply.ok(buildJsonObject { put("status", "active"); put("at_bookmark", "00000010") }) }
        }
        val repository = ExportRepository(api, pause = {})

        val file = repository.awaitCompletion("acc-1", "db-1", ExportPollRef.active(), onProgress = {})

        assertEquals(ApiError.Local(ExportRepository.TIMED_OUT_REASON), (file as ApiResult.Failure).error)
    }
}

/** Minimal first-poll values for the wait tests. */
private object ExportPollRef {
    fun active() = ExportPoll(status = "active", atBookmark = "00000010")
    fun error() = ExportPoll(status = "error", error = "dump failed", atBookmark = "00000010")
}
