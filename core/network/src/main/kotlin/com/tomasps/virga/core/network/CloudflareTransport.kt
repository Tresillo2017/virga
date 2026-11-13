package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoMode
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

internal typealias QueryParams = Map<String, String?>

private const val DOT_SEGMENT = "."
private const val DOT_DOT_SEGMENT = ".."
private const val DOT_SEGMENT_REASON = "Path contains a dot segment that the API would resolve to another resource"

/** A GET that was rate limited with a short Retry-After is retried this many times before the error is returned. */
internal const val MAX_GET_RETRIES = 1

/** Longest Retry-After the transport will wait out on its own. Longer waits go back to the caller as RateLimited. */
internal const val MAX_GET_RETRY_WAIT_MILLIS = 10_000L

/**
 * Single choke point for every request. Picks the live or demo client, attaches the bearer token, spends
 * rate budget, and logs one line. Nothing else touches Ktor directly.
 */
internal class CloudflareTransport(
    private val liveClient: HttpClient,
    private val demoClient: HttpClient,
    private val demoMode: DemoMode,
    private val credentials: CredentialProvider,
    private val rateLimiter: RateLimiter,
    private val logger: ApiLogger,
    val json: Json,
) {
    /**
     * Sends one request and passes the response to [handle]. [streaming] lifts the total request timeout so a
     * large body can take as long as it needs; only the inactivity timeout applies. Exceptions thrown by
     * [handle] itself propagate to the caller unchanged; only transport failures become [ApiError.Network].
     */
    suspend fun <R> call(
        lane: RateLane,
        method: HttpMethod,
        path: String,
        query: QueryParams,
        body: OutgoingContent?,
        streaming: Boolean = false,
        handle: suspend (HttpResponse) -> ApiResult<R>,
    ): ApiResult<R> {
        val isDemo = demoMode.enabled.value
        val bearer = if (isDemo) null else credentials.bearerToken()
        if (!isDemo && bearer == null) return ApiResult.Failure(ApiError.Unauthorized())
        if (path.hasDotSegment()) return ApiResult.Failure(ApiError.Local(DOT_SEGMENT_REASON))
        val request = OneRequest(lane, method, path, query, body, bearer, isDemo, streaming)
        return sendWithShortRetry(request, handle)
    }

    /** Envelope-decoding call, the common case for REST. */
    suspend fun <T> callEnvelope(
        lane: RateLane,
        method: HttpMethod,
        path: String,
        query: QueryParams,
        body: OutgoingContent?,
        deserializer: DeserializationStrategy<T>,
        streaming: Boolean = false,
    ): ApiResult<T> = call(lane, method, path, query, body, streaming) { response ->
        decodeEnvelope(json, response.status.value, response.bodyAsText(), response.retryAfterMillis(), deserializer)
    }

    private suspend fun <R> sendWithShortRetry(request: OneRequest, handle: suspend (HttpResponse) -> ApiResult<R>): ApiResult<R> {
        var retries = 0
        while (true) {
            val result = sendOnce(request, handle)
            if (request.method != HttpMethod.Get || retries >= MAX_GET_RETRIES) return result
            if (!isShortRateLimit(result)) return result
            retries++
        }
    }

    private fun isShortRateLimit(result: ApiResult<*>): Boolean {
        val error = (result as? ApiResult.Failure)?.error as? ApiError.RateLimited ?: return false
        return error.retryAfterMillis <= MAX_GET_RETRY_WAIT_MILLIS
    }

    private suspend fun <R> sendOnce(request: OneRequest, handle: suspend (HttpResponse) -> ApiResult<R>): ApiResult<R> {
        if (!request.isDemo) rateLimiter.acquire(request.lane)
        val client = if (request.isDemo) demoClient else liveClient
        return try {
            client.prepareRequest {
                this.method = request.method
                url(CloudflareEndpoints.BASE_URL + request.path)
                request.query.forEach { (key, value) -> if (value != null) parameter(key, value) }
                header(HttpHeaders.UserAgent, CloudflareEndpoints.USER_AGENT)
                accept(ContentType.Application.Json)
                if (request.bearer != null) header(HttpHeaders.Authorization, "Bearer ${request.bearer}")
                if (request.body != null) setBody(request.body)
                if (request.streaming && !request.isDemo) {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = CloudflareEndpoints.STREAM_IDLE_TIMEOUT_MILLIS
                    }
                }
            }.execute { response ->
                if (!request.isDemo && response.status == HttpStatusCode.TooManyRequests) {
                    rateLimiter.blockFor(request.lane, response.retryAfterMillis() ?: DEFAULT_RETRY_AFTER_MILLIS)
                }
                logger.log("${request.method.value} ${request.path} -> ${response.status.value}")
                handle(response)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ConsumerFailure) {
            throw error.original
        } catch (error: Exception) {
            ApiResult.Failure(ApiError.Network(reason = error::class.simpleName ?: "unknown"))
        }
    }

    private class OneRequest(
        val lane: RateLane,
        val method: HttpMethod,
        val path: String,
        val query: QueryParams,
        val body: OutgoingContent?,
        val bearer: String?,
        val isDemo: Boolean,
        val streaming: Boolean,
    )
}

/**
 * Wraps an exception thrown by caller code (a download consumer or an upload writer) so the transport can tell
 * it apart from a transport failure and rethrow the original. Only [sendOnce] unwraps it.
 */
internal class ConsumerFailure(val original: Exception) : RuntimeException(original)

/** Runs caller code, tagging any exception it throws so the transport lets it propagate unchanged. */
internal suspend fun <T> guardConsumer(block: suspend () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    throw ConsumerFailure(error)
}

/** True when any segment decodes to `.` or `..`. OkHttp resolves those even when they are percent-encoded. */
internal fun String.hasDotSegment(): Boolean = split('/').any { segment ->
    val decoded = runCatching { segment.decodeURLPart() }.getOrDefault(segment)
    decoded == DOT_SEGMENT || decoded == DOT_DOT_SEGMENT
}

internal fun HttpResponse.retryAfterMillis(): Long? = parseRetryAfterMillis(headers[HttpHeaders.RetryAfter])
