package com.tomasps.virga.core.navigation

import androidx.navigation3.runtime.NavKey
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * `virga://` links, used by notifications (for example the watch-build Open action) to reach a screen. Only
 * keys listed here can be reached from outside the app. Anything else parses to null.
 */
object DeepLinks {
    private const val PREFIX = "virga://"
    private const val BUILD_HOST = "build"
    private const val BUILD_PARTS = 4
    private const val UTF_8 = "UTF-8"

    /** `virga://build/{accountId}/{workerName}/{buildUuid}`. Each part is percent-encoded. */
    fun buildDetailUri(key: BuildDetail): String =
        PREFIX + listOf(BUILD_HOST, key.accountId, key.workerName, key.buildUuid).joinToString("/") { encode(it) }

    /** Parses a link into a key, or null when it is not a recognised Virga link. Never throws. */
    fun parse(uri: String?): NavKey? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        val parts = uri.removePrefix(PREFIX).split('/')
        if (parts.size != BUILD_PARTS || parts[0] != BUILD_HOST) return null
        val values = parts.drop(1).map { decode(it) ?: return null }
        if (values.any { it.isBlank() }) return null
        return BuildDetail(accountId = values[0], workerName = values[1], buildUuid = values[2])
    }

    private fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, UTF_8)
    } catch (error: IllegalArgumentException) {
        null
    }
}
