package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.CloudflareMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsIssueClassifierTest {

    @Test
    fun `a forbidden account query names the account analytics permission`() {
        val issue = AnalyticsIssueClassifier.classify(ApiError.Forbidden(), AnalyticsScope.ACCOUNT)

        assertEquals(AnalyticsIssue.MissingPermission(PERMISSION_ACCOUNT_ANALYTICS), issue)
    }

    @Test
    fun `an authorisation message in a GraphQL error names the zone permission for zone queries`() {
        val error = ApiError.GraphQl(listOf(CloudflareMessage(message = "Not authorized for this zone")))

        assertEquals(
            AnalyticsIssue.MissingPermission(PERMISSION_ZONE_ANALYTICS),
            AnalyticsIssueClassifier.classify(error, AnalyticsScope.ZONE),
        )
    }

    @Test
    fun `a rate limit message is reported as rate limited, not as a missing permission`() {
        val error = ApiError.GraphQl(listOf(CloudflareMessage(message = "Account has exceeded its rate limit")))

        assertEquals(AnalyticsIssue.RateLimited, AnalyticsIssueClassifier.classify(error, AnalyticsScope.ACCOUNT))
    }

    @Test
    fun `a RateLimited error is reported as rate limited`() {
        assertEquals(
            AnalyticsIssue.RateLimited,
            AnalyticsIssueClassifier.classify(ApiError.RateLimited(retryAfterMillis = 1_000), AnalyticsScope.ACCOUNT),
        )
    }

    @Test
    fun `an unfamiliar GraphQL error is a generic failure and never claims a permission is missing`() {
        val error = ApiError.GraphQl(listOf(CloudflareMessage(message = "unknown field httpRequests1hGroups")))

        assertEquals(AnalyticsIssue.Failed, AnalyticsIssueClassifier.classify(error, AnalyticsScope.ZONE))
    }

    @Test
    fun `a server error is a generic failure`() {
        assertEquals(AnalyticsIssue.Failed, AnalyticsIssueClassifier.classify(ApiError.Server(500), AnalyticsScope.ACCOUNT))
    }
}
