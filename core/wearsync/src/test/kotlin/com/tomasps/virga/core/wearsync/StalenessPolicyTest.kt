package com.tomasps.virga.core.wearsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StalenessPolicyTest {
    private val now = 10_000_000_000L

    @Test
    fun nullCheckInIsStale() = assertTrue(StalenessPolicy.isStale(null, now))

    @Test
    fun exactly24hIsNotStale() =
        assertFalse(StalenessPolicy.isStale(now - StalenessPolicy.MAX_OFFLINE_MILLIS, now))

    @Test
    fun twentyFourHoursPlusOneMsIsStale() =
        assertTrue(StalenessPolicy.isStale(now - StalenessPolicy.MAX_OFFLINE_MILLIS - 1, now))

    @Test
    fun smallFutureSkewIsNotStale() = assertFalse(StalenessPolicy.isStale(now + 60_000, now))

    @Test
    fun futureSkewAtTheToleranceIsNotStale() =
        assertFalse(StalenessPolicy.isStale(now + StalenessPolicy.MAX_FUTURE_SKEW_MILLIS, now))

    /** A watch clock rolled back after the check-in must not keep the token usable past the 24-hour window. */
    @Test
    fun checkInFurtherInTheFutureThanTheToleranceIsStale() =
        assertTrue(StalenessPolicy.isStale(now + StalenessPolicy.MAX_FUTURE_SKEW_MILLIS + 1, now))

    @Test
    fun clockRolledBackByDaysIsStale() =
        assertTrue(StalenessPolicy.isStale(now + 3 * StalenessPolicy.MAX_OFFLINE_MILLIS, now))
}
