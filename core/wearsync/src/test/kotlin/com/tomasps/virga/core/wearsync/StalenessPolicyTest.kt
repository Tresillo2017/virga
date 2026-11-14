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
    fun futureCheckInIsNotStale() = assertFalse(StalenessPolicy.isStale(now + 5_000, now))
}
