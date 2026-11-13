package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/**
 * Typed Cloudflare REST client. Each helper returns [ApiResult] and never throws for HTTP or transport
 * failures. The one exception is an error thrown by the caller's own code (the `consume` of [download] or the
 * `write` of [uploadStream]); it propagates unchanged. Paths are relative to /client/v4 and must be built with
 * [cloudflarePath] when they contain user-supplied segments such as bucket keys.
 */
class CloudflareApi internal constructor(
    private val transport: CloudflareTransport,
    @PublishedApi internal val json: Json,
) {
    val graphQl: GraphQlClient = GraphQlClient(transport)

    suspend inline fun <reified T> get(path: String, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Get, path, query, null, serializer<T>())

    suspend inline fun <reified T> delete(path: String, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Delete, path, query, null, serializer<T>())

    suspend inline fun <reified B, reified T> post(path: String, body: B, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Post, path, query, json.encodeToJsonElement(serializer<B>(), body), serializer<T>())

    suspend inline fun <reified B, reified T> put(path: String, body: B, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Put, path, query, json.encodeToJsonElement(serializer<B>(), body), serializer<T>())

    suspend inline fun <reified B, reified T> patch(path: String, body: B, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Patch, path, query, json.encodeToJsonElement(serializer<B>(), body), serializer<T>())

    suspend inline fun <reified T> postEmpty(path: String, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Post, path, query, null, serializer<T>())

    suspend inline fun <reified T> putEmpty(path: String, query: QueryParams = emptyMap()): ApiResult<T> =
        execute(HttpMethod.Put, path, query, null, serializer<T>())

    /** Core envelope call with an explicit deserializer. The reified helpers above delegate here. */
    suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        query: QueryParams,
        body: JsonElement?,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> {
        val content = body?.let { TextBody(json.encodeToString(JsonElement.serializer(), it)) }
        return transport.callEnvelope(RateLane.REST, method, path, query, content, deserializer)
    }

    /**
     * Streams the response body to [consume] without buffering it. The channel is valid only inside
     * [consume]. Errors return before [consume] runs. The total request time is not capped; only silence
     * longer than [CloudflareEndpoints.STREAM_IDLE_TIMEOUT_MILLIS] fails the transfer.
     */
    suspend fun <R> download(
        path: String,
        query: QueryParams = emptyMap(),
        consume: suspend (ByteReadChannel, contentLength: Long?) -> R,
    ): ApiResult<R> = transport.call(RateLane.REST, HttpMethod.Get, path, query, null, streaming = true) { response ->
        if (isSuccessStatus(response.status.value)) {
            val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ApiResult.Success(guardConsumer { consume(response.bodyAsChannel(), length) })
        } else {
            errorResult(response.status.value, parseJsonObject(json, response.bodyAsText()), response.retryAfterMillis())
        }
    }

    /**
     * Streams [write]'s output as the request body, with a known length so no chunking is needed. Like
     * [download], the total time is not capped; only an idle stall fails the upload.
     */
    suspend fun <T> uploadStream(
        method: HttpMethod,
        path: String,
        contentType: ContentType,
        contentLength: Long,
        write: suspend (ByteWriteChannel) -> Unit,
        deserializer: DeserializationStrategy<T>,
        query: QueryParams = emptyMap(),
    ): ApiResult<T> = transport.callEnvelope(
        RateLane.REST, method, path, query, StreamingBody(contentType, contentLength, write), deserializer, streaming = true,
    )

    /** multipart/form-data POST. File parts are buffered in memory. */
    suspend fun <T> postMultipart(
        path: String,
        parts: List<FormPart>,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = transport.callEnvelope(
        RateLane.REST, HttpMethod.Post, path, emptyMap(), MultiPartFormDataContent(toFormData(parts)), deserializer,
    )

    /** For endpoints that return plain text rather than an envelope. */
    suspend fun getText(path: String, query: QueryParams = emptyMap()): ApiResult<String> =
        transport.call(RateLane.REST, HttpMethod.Get, path, query, null) { response ->
            if (isSuccessStatus(response.status.value)) {
                ApiResult.Success(response.bodyAsText())
            } else {
                errorResult(response.status.value, parseJsonObject(json, response.bodyAsText()), response.retryAfterMillis())
            }
        }

    companion object {
        /**
         * Builds a client. [liveEngine] null uses OkHttp; tests pass a MockEngine. Demo mode serves
         * [demoRoutes] through MockEngine whenever [demoMode] is enabled.
         */
        fun create(
            credentials: CredentialProvider,
            demoMode: DemoMode = DemoMode(),
            demoRoutes: DemoRouteRegistry = DemoRouteRegistry(),
            rateLimiter: RateLimiter = RateLimiter(),
            liveEngine: HttpClientEngine? = null,
            logger: ApiLogger = ApiLogger.None,
            json: Json = CloudflareJson,
        ): CloudflareApi {
            val live = if (liveEngine != null) {
                HttpClient(liveEngine) { configureClient() }
            } else {
                HttpClient(OkHttp) { configureClient() }
            }
            val demo = HttpClient(demoRoutes.toMockEngine())
            val transport = CloudflareTransport(live, demo, demoMode, credentials, rateLimiter, logger, json)
            return CloudflareApi(transport, json)
        }
    }
}

/**
 * Builds an escaped path, e.g. `cloudflarePath("accounts", id, "r2", "buckets", name, "objects", key)`.
 * Slashes, percent signs, question marks and hashes in a segment are escaped. A segment that is exactly `.` or
 * `..` cannot be escaped (OkHttp resolves it even when encoded), so the transport refuses such a path with
 * [ApiError.Local] before sending anything.
 */
fun cloudflarePath(vararg segments: String): String =
    segments.joinToString(separator = "/", prefix = "/") { it.encodeURLPathPart() }

private fun io.ktor.client.HttpClientConfig<*>.configureClient() {
    install(HttpTimeout) {
        connectTimeoutMillis = CloudflareEndpoints.CONNECT_TIMEOUT_MILLIS
        requestTimeoutMillis = CloudflareEndpoints.REQUEST_TIMEOUT_MILLIS
        socketTimeoutMillis = CloudflareEndpoints.STREAM_IDLE_TIMEOUT_MILLIS
    }
}

private fun toFormData(parts: List<FormPart>) = formData {
    parts.forEach { part ->
        when (part) {
            is FormPart.Text -> part.contentType?.let { type ->
                append(part.name, part.value, Headers.build { append(HttpHeaders.ContentType, type.toString()) })
            } ?: append(part.name, part.value)
            is FormPart.File -> append(
                part.name,
                part.bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, part.contentType.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${part.fileName}\"")
                },
            )
        }
    }
}

private class TextBody(text: String) : OutgoingContent.ByteArrayContent() {
    private val bytes = text.encodeToByteArray()
    override val contentType: ContentType = ContentType.Application.Json
    override val contentLength: Long = bytes.size.toLong()
    override fun bytes(): ByteArray = bytes
}

private class StreamingBody(
    override val contentType: ContentType,
    override val contentLength: Long,
    private val write: suspend (ByteWriteChannel) -> Unit,
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        guardConsumer { write(channel) }
    }
}
