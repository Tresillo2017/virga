package com.tomasps.virga.core.common.result

/** One entry from the envelope's `errors` or `messages` arrays. */
data class CloudflareMessage(val code: Int? = null, val message: String? = null)

/**
 * Typed failure. Carries Cloudflare error codes so features can branch on them (for example 10000 on a
 * Forbidden response). Messages are raw API text: UI code maps them to strings.xml, never shows them as-is.
 */
sealed interface ApiError {
    val messages: List<CloudflareMessage>

    val codes: List<Int>
        get() = messages.mapNotNull { it.code }

    data class Unauthorized(override val messages: List<CloudflareMessage> = emptyList()) : ApiError
    data class Forbidden(override val messages: List<CloudflareMessage> = emptyList()) : ApiError
    data class NotFound(override val messages: List<CloudflareMessage> = emptyList()) : ApiError
    data class RateLimited(
        val retryAfterMillis: Long,
        override val messages: List<CloudflareMessage> = emptyList(),
    ) : ApiError
    data class Rejected(val status: Int, override val messages: List<CloudflareMessage> = emptyList()) : ApiError
    data class GraphQl(override val messages: List<CloudflareMessage> = emptyList()) : ApiError
    data class Server(val status: Int, override val messages: List<CloudflareMessage> = emptyList()) : ApiError
    data class Network(val reason: String) : ApiError {
        override val messages: List<CloudflareMessage> get() = emptyList()
    }
    data class Decode(val reason: String) : ApiError {
        override val messages: List<CloudflareMessage> get() = emptyList()
    }

    /**
     * The app itself could not finish: a request it refused before sending, a local keystore or storage
     * failure, or a list it stopped early. No Cloudflare response describes it, so [reason] is for logs and
     * tests only; UI shows generic text.
     */
    data class Local(val reason: String) : ApiError {
        override val messages: List<CloudflareMessage> get() = emptyList()
    }
}
