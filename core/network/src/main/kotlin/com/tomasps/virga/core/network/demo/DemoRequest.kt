package com.tomasps.virga.core.network.demo

import io.ktor.http.HttpMethod

/**
 * What a demo handler sees. Request headers are deliberately absent so a bearer token can never reach a
 * handler, a log line or a debugger view through this type.
 */
class DemoRequest internal constructor(
    val method: HttpMethod,
    val path: String,
    val pathParams: Map<String, String>,
    val query: Map<String, String>,
    /** The raw request body, or null when the request had none. Binary uploads arrive here unchanged. */
    val bodyBytes: ByteArray?,
) {
    /** The body decoded as UTF-8, or null when there is none. Use [bodyBytes] for binary content. */
    val bodyText: String? get() = bodyBytes?.decodeToString()

    override fun toString(): String = "DemoRequest(${method.value} $path)"
}
