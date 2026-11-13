package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DemoGraphQlDispatchTest {

    @Test
    fun `a GraphQL body is answered by the operation it names`() = runBlocking {
        val registry = DemoRouteRegistry()
            .graphQl("alphaOperation") { DemoReply.graphQl(JsonPrimitive("alpha")) }
            .graphQl("betaOperation") { DemoReply.graphQl(JsonPrimitive("beta")) }
        val body = """{"query":"query betaOperation { viewer { id } }"}""".toByteArray()

        val reply = registry.dispatch(HttpMethod.Post, "/graphql", emptyMap(), body)

        assertEquals("""{"data":"beta"}""", reply.body.decodeToString())
    }

    @Test
    fun `a GraphQL body naming no served operation gets an error envelope`() = runBlocking {
        val registry = DemoRouteRegistry().graphQl("alphaOperation") { DemoReply.graphQl(JsonPrimitive("alpha")) }
        val body = """{"query":"query unknownOperation { viewer { id } }"}""".toByteArray()

        val reply = registry.dispatch(HttpMethod.Post, "/graphql", emptyMap(), body)

        assertEquals(true, reply.body.decodeToString().contains("\"errors\""))
    }

    @Test
    fun `a second handler for the same GraphQL operation is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            DemoRouteRegistry()
                .graphQl("alphaOperation") { DemoReply.graphQl(JsonPrimitive("first")) }
                .graphQl("alphaOperation") { DemoReply.graphQl(JsonPrimitive("second")) }
        }
    }
}
