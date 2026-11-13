package com.tomasps.virga.feature.account.details

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDetailsRepositoryTest {

    @Test
    fun `details are read from the account path and decoded, ignoring unknown fields`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}") { request ->
                assertEquals("acc-1", request.pathParams["accountId"])
                DemoReply.ok(buildJsonObject {
                    put("id", "acc-1")
                    put("name", "Alpha")
                    put("type", "standard")
                    put("created_on", "2024-01-02T03:04:05Z")
                    put("future_field", "ignored")
                })
            }
        }

        // Act
        val result = AccountDetailsRepository(api).details("acc-1")

        // Assert
        val details = (result as ApiResult.Success).value
        assertEquals(AccountDetails(id = "acc-1", name = "Alpha", type = "standard", createdOn = "2024-01-02T03:04:05Z"), details)
    }

    @Test
    fun `an unknown account maps to NotFound`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}") {
                DemoReply.failure(HttpStatusCode.NotFound, code = 7003, message = "gone")
            }
        }

        // Act
        val result = AccountDetailsRepository(api).details("missing")

        // Assert
        assertTrue((result as ApiResult.Failure).error is ApiError.NotFound)
    }
}
