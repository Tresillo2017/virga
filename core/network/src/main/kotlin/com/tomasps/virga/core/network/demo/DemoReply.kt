package com.tomasps.virga.core.network.demo

import com.tomasps.virga.core.network.CloudflareEnvelopes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A canned response: status, body bytes and content type. Use the factories below. */
class DemoReply internal constructor(
    val status: HttpStatusCode,
    val body: ByteArray,
    val contentType: ContentType,
) {
    companion object {
        /** Success envelope with [result] and optional result_info. */
        fun ok(result: JsonElement, resultInfo: JsonElement? = null): DemoReply =
            json(HttpStatusCode.OK, CloudflareEnvelopes.success(result, resultInfo))

        /** Failure envelope with one Cloudflare-style error. */
        fun failure(status: HttpStatusCode, code: Int, message: String): DemoReply =
            json(status, CloudflareEnvelopes.failure(code, message))

        /** Raw bytes such as an object download. */
        fun raw(bytes: ByteArray, contentType: ContentType = ContentType.Application.OctetStream): DemoReply =
            DemoReply(HttpStatusCode.OK, bytes, contentType)

        /** Plain text, such as a log stream. */
        fun text(text: String, status: HttpStatusCode = HttpStatusCode.OK): DemoReply =
            DemoReply(status, text.encodeToByteArray(), ContentType.Text.Plain)

        /** An empty reply, as a Cloudflare call that returns nothing (for example a 204 delete). */
        fun empty(status: HttpStatusCode = HttpStatusCode.NoContent): DemoReply =
            DemoReply(status, ByteArray(0), ContentType.Application.Json)

        /** GraphQL response body: `{"data": ...}`. Pass null to simulate missing data. */
        fun graphQl(data: JsonElement?): DemoReply = json(
            HttpStatusCode.OK,
            buildJsonObject { put("data", data ?: JsonNull) }.toString(),
        )

        /** GraphQL response with an `errors` array and no data. */
        fun graphQlErrors(message: String): DemoReply = json(
            HttpStatusCode.OK,
            buildJsonObject {
                put("data", JsonNull)
                put("errors", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject { put("message", JsonPrimitive(message)) })))
            }.toString(),
        )

        private fun json(status: HttpStatusCode, body: String): DemoReply =
            DemoReply(status, body.encodeToByteArray(), ContentType.Application.Json)
    }
}
