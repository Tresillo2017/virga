package com.tomasps.virga.core.navigation

import java.net.URI

/**
 * Which external pages the app may open in a browser. Limited to HTTPS links on Cloudflare's own domains, so a
 * URL that comes from data (an API response, for example) can never send the user to an arbitrary site.
 */
object ExternalLinks {
    private const val SCHEME = "https"
    private const val ROOT_DOMAIN = "cloudflare.com"

    fun isAllowed(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (error: java.net.URISyntaxException) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == SCHEME &&
            uri.userInfo == null &&
            (host == ROOT_DOMAIN || host.endsWith(".$ROOT_DOMAIN"))
    }
}
