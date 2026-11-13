package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.common.result.ApiError

/** Which token permission an analytics node needs. Names match the Cloudflare permission labels. */
enum class AnalyticsScope(val permission: String) {
    ACCOUNT(PERMISSION_ACCOUNT_ANALYTICS),
    ZONE(PERMISSION_ZONE_ANALYTICS),
}

const val PERMISSION_ACCOUNT_ANALYTICS = "Account Analytics: Read"
const val PERMISSION_ZONE_ANALYTICS = "Zone Analytics: Read"

private val AUTH_MARKERS = listOf(
    "not authorized",
    "unauthorized",
    "permission",
    "forbidden",
    "access denied",
    "authentication",
)
private const val RATE_LIMIT_MARKER = "rate limit"

/**
 * Maps a failed analytics call to what Home can say about it. A GraphQL error carries no HTTP status, so the
 * message text decides: "rate limit" wins, then authorisation wording names the missing permission. Everything
 * else is a generic failure, so an unfamiliar message never claims a permission is missing.
 */
object AnalyticsIssueClassifier {

    fun classify(error: ApiError, scope: AnalyticsScope): AnalyticsIssue {
        val text = error.messages.mapNotNull { it.message }.joinToString(separator = " ").lowercase()
        return when {
            error is ApiError.RateLimited -> AnalyticsIssue.RateLimited
            text.contains(RATE_LIMIT_MARKER) -> AnalyticsIssue.RateLimited
            error is ApiError.Forbidden || error is ApiError.Unauthorized -> AnalyticsIssue.MissingPermission(scope.permission)
            error is ApiError.GraphQl && AUTH_MARKERS.any(text::contains) -> AnalyticsIssue.MissingPermission(scope.permission)
            else -> AnalyticsIssue.Failed
        }
    }
}
