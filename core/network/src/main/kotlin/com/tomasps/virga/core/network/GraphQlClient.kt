package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val GRAPHQL_PATH = "/graphql"

/** GraphQL over the same transport. Each query also spends from the REST budget. */
class GraphQlClient internal constructor(private val transport: CloudflareTransport) {
    suspend fun <T> query(
        query: String,
        variables: JsonObject,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val text = transport.json.encodeToString(JsonObject.serializer(), payload)
        return transport.call(
            lane = RateLane.GRAPHQL,
            method = HttpMethod.Post,
            path = GRAPHQL_PATH,
            query = emptyMap(),
            body = TextContent(text, ContentType.Application.Json),
        ) { response -> decodeGraphQl(transport.json, response, deserializer) }
    }
}

private suspend fun <T> decodeGraphQl(
    json: Json,
    response: HttpResponse,
    deserializer: DeserializationStrategy<T>,
): ApiResult<T> {
    val body = response.bodyAsText()
    val status = response.status.value
    val root = parseJsonObject(json, body)
    if (!isSuccessStatus(status)) return errorResult(status, root, response.retryAfterMillis())
    if (root == null) return ApiResult.Failure(ApiError.Decode("GraphQL response is not a JSON object"))
    val errors = parseMessages(root["errors"])
    if (errors.isNotEmpty()) return ApiResult.Failure(ApiError.GraphQl(errors))
    return decodeResult(json, root["data"], deserializer)
}
