package com.tomasps.virga.core.network

/** Fixed endpoint and client settings. The user agent must never contain the word "Cloudflare". */
object CloudflareEndpoints {
    const val BASE_URL = "https://api.cloudflare.com/client/v4"
    const val BASE_PATH = "/client/v4"
    const val USER_AGENT = "Virga/0.1.0 (Android; unofficial client)"
    const val DEFAULT_PER_PAGE = 50
    const val MAX_PAGES = 200
    const val CONNECT_TIMEOUT_MILLIS = 15_000L
    const val REQUEST_TIMEOUT_MILLIS = 60_000L

    /** For streamed bodies: longest silence allowed between bytes, instead of a cap on the whole transfer. */
    const val STREAM_IDLE_TIMEOUT_MILLIS = 60_000L
}
