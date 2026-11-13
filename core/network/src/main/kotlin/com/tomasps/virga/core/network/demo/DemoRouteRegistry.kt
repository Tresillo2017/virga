package com.tomasps.virga.core.network.demo

import com.tomasps.virga.core.network.ApiLogger
import com.tomasps.virga.core.network.CloudflareEndpoints
import com.tomasps.virga.core.network.readOutgoingBytes
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val NOT_FOUND_CODE = 7003
private const val NOT_FOUND_MESSAGE = "No route for the URI"
private const val GRAPHQL_PATH = "/graphql"
private val GRAPHQL_OPERATION = Regex("""(?:query|mutation)\s+(\w+)""")

/**
 * Canned routes served through MockEngine. Features contribute handlers via [DemoRouteContributor].
 * Paths are relative to /client/v4, for example `/accounts/{accountId}/workers/scripts`. Unmatched requests are
 * logged (method and path only) so a missing canned route is visible during development.
 */
class DemoRouteRegistry(private val logger: ApiLogger = ApiLogger.None) {
    private val routes = mutableListOf<DemoRoutePattern>()
    private val graphQlOperations = mutableMapOf<String, DemoHandler>()

    /**
     * Serves one named GraphQL operation (the `query Name(...)` identifier). Every GraphQL call shares
     * `POST /graphql`, so the registry picks the handler by operation name. Registering a name twice throws.
     */
    fun graphQl(operationName: String, handler: DemoHandler): DemoRouteRegistry = apply {
        require(operationName !in graphQlOperations) { "Demo GraphQL operation $operationName is already served" }
        graphQlOperations[operationName] = handler
    }

    /**
     * Adds a route. Registering a second route with the same method and the same shape (parameter names do not
     * count) throws, because the first would silently win.
     */
    fun route(method: HttpMethod, pattern: String, handler: DemoHandler): DemoRouteRegistry = apply {
        val candidate = DemoRoutePattern(method, pattern, handler)
        val clash = routes.firstOrNull { it.method == method && it.shape == candidate.shape }
        require(clash == null) {
            "Demo route ${method.value} $pattern duplicates ${clash?.pattern}: same method and shape"
        }
        routes += candidate
    }

    fun get(pattern: String, handler: DemoHandler): DemoRouteRegistry = route(HttpMethod.Get, pattern, handler)
    fun post(pattern: String, handler: DemoHandler): DemoRouteRegistry = route(HttpMethod.Post, pattern, handler)
    fun put(pattern: String, handler: DemoHandler): DemoRouteRegistry = route(HttpMethod.Put, pattern, handler)
    fun patch(pattern: String, handler: DemoHandler): DemoRouteRegistry = route(HttpMethod.Patch, pattern, handler)
    fun delete(pattern: String, handler: DemoHandler): DemoRouteRegistry = route(HttpMethod.Delete, pattern, handler)

    /** First matching route wins. An unmatched request is logged and gets a Cloudflare-style 404 envelope. */
    internal suspend fun dispatch(
        method: HttpMethod,
        encodedPath: String,
        query: Map<String, String>,
        bodyBytes: ByteArray?,
    ): DemoReply {
        if (method == HttpMethod.Post && encodedPath == GRAPHQL_PATH) {
            graphQlHandlerFor(bodyBytes)?.let { handler ->
                return handler(DemoRequest(method, encodedPath, emptyMap(), query, bodyBytes?.takeIf { it.isNotEmpty() }))
            }
        }
        for (route in routes) {
            val params = route.match(method, encodedPath) ?: continue
            val request = DemoRequest(method, encodedPath, params, query, bodyBytes?.takeIf { it.isNotEmpty() })
            return route.handler(request)
        }
        logger.log("DEMO ${method.value} $encodedPath -> no demo route ($NOT_FOUND_CODE)")
        return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
    }

    /** The handler for the operation a GraphQL body names, or null so the request falls through to ordinary routes. */
    private fun graphQlHandlerFor(bodyBytes: ByteArray?): DemoHandler? {
        val queryText = graphQlQueryText(bodyBytes) ?: return null
        val operation = GRAPHQL_OPERATION.find(queryText)?.groupValues?.get(1) ?: return null
        return graphQlOperations[operation]
    }

    private fun graphQlQueryText(bodyBytes: ByteArray?): String? {
        val root = bodyBytes?.let { runCatching { Json.parseToJsonElement(it.decodeToString()) }.getOrNull() }
        return ((root as? JsonObject)?.get("query") as? JsonPrimitive)?.content
    }

    /**
     * MockEngine that answers from these routes. Used for demo mode and for tests. [dispatcher] is where MockEngine
     * runs each answer; tests pass Unconfined so the answer comes back on the caller's (virtual) scheduler.
     */
    fun toMockEngine(dispatcher: CoroutineDispatcher = Dispatchers.Default): MockEngine = MockEngine(
        MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val path = request.url.encodedPath.removePrefix(CloudflareEndpoints.BASE_PATH)
                val query = request.url.parameters.names().associateWith { request.url.parameters[it].orEmpty() }
                val reply = dispatch(request.method, path, query, readOutgoingBytes(request.body))
                respond(
                    content = reply.body,
                    status = reply.status,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(reply.contentType.toString()),
                        HttpHeaders.ContentLength to listOf(reply.body.size.toString()),
                    ),
                )
            }
        },
    )
}

/** Implemented by each feature module to add its canned routes. Bind one concrete type per feature. */
fun interface DemoRouteContributor {
    fun contribute(registry: DemoRouteRegistry)
}
