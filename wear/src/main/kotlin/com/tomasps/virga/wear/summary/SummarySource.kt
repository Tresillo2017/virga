package com.tomasps.virga.wear.summary

import android.util.Log
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.GraphQlClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.time.Instant

private const val TAG = "SummarySource"
private const val RATE_LIMIT_MARKER = "rate limit"

// The same wording Home's AnalyticsIssueClassifier uses to tell an authorization error from any other GraphQL error.
private val AUTH_MARKERS = listOf(
    "not authorized",
    "unauthorized",
    "permission",
    "forbidden",
    "access denied",
    "authentication",
)

sealed interface SummaryResult {
    data class Ok(val data: SummaryData) : SummaryResult

    /** The token is valid but cannot read analytics. Persistent, and not a reason to wipe the session. */
    data object MissingAnalyticsAccess : SummaryResult

    data class Failed(val error: ApiError) : SummaryResult
}

/**
 * Reads the last 24 hours of account requests and errors. Two kinds of authorization problem reach it:
 * - a real 401 means the token was rejected; when a token was present, [onUnauthorized] wipes the session (once, as
 *   this makes a single call). The local "no token" case (stale session, handoff in progress) does not wipe.
 * - a 403, or an authorization error inside the GraphQL `errors` (HTTP 200), means the token works but lacks analytics
 *   access: [SummaryResult.MissingAnalyticsAccess], with no wipe.
 */
class SummarySource(
    private val graphQl: GraphQlClient,
    private val onUnauthorized: suspend () -> Unit = {},
    private val hasToken: suspend () -> Boolean = { false },
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {

    suspend fun last24h(accountId: String, now: Instant): SummaryResult =
        when (val result = graphQl.query(SUMMARY_QUERY, summaryVariables(accountId, now), JsonObject.serializer())) {
            is ApiResult.Success -> parseSummary(result.value)?.let { SummaryResult.Ok(it) }
                ?: SummaryResult.Failed(ApiError.Decode("summary response has no viewer"))
            is ApiResult.Failure -> failed(result.error)
        }

    private suspend fun failed(error: ApiError): SummaryResult {
        if (error is ApiError.Unauthorized && tokenWasPresent()) wipeSession()
        return if (isMissingAccess(error)) SummaryResult.MissingAnalyticsAccess else SummaryResult.Failed(error)
    }

    private fun isMissingAccess(error: ApiError): Boolean {
        if (error is ApiError.Forbidden) return true
        if (error !is ApiError.GraphQl) return false
        val text = error.messages.mapNotNull { it.message }.joinToString(separator = " ").lowercase()
        return !text.contains(RATE_LIMIT_MARKER) && AUTH_MARKERS.any(text::contains)
    }

    /** A token check that fails (storage trouble) counts as no token: never wipe on a guess. */
    private suspend fun tokenWasPresent(): Boolean = try {
        hasToken()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        false
    }

    private suspend fun wipeSession() {
        try {
            onUnauthorized()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // Class name only: the message could carry storage paths or credential material.
            warn("Could not clear the session after a 401: ${failure.javaClass.simpleName}")
        }
    }
}
