package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves Ktor MockEngine resolves for unit tests in a network module. */
class MockEngineSmokeTest {

    @Test
    fun mockEngineServesCannedBody() = runTest {
        // Arrange
        val engine = MockEngine {
            respond(
                content = """{"success":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)

        // Act
        val body = client.get("https://api.cloudflare.com/client/v4/user/tokens/verify").bodyAsText()

        // Assert
        assertEquals("""{"success":true}""", body)
        client.close()
    }
}
