package com.tomasps.virga.wear.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SummaryTileModelTest {

    private val now = Instant.parse("2026-11-14T12:00:00Z")
    private val data = SummaryData(requests = 1_234, errors = 56)

    @Test
    fun freshShowsNumbersWithoutAge() {
        val model = summaryTileFor(SummarySurface(data, now, SummaryStatus.Fresh), now)

        assertEquals(SummaryTileKind.Numbers, model.kind)
        assertEquals("1.2k", model.requests)
        assertEquals("56", model.errors)
        assertNull(model.age)
        assertFalse(model.isStale)
    }

    @Test
    fun staleShowsTheLastNumbersWithTheAgeOfTheFetch() {
        val fetched = Instant.parse("2026-11-14T09:00:00Z")

        val model = summaryTileFor(SummarySurface(data, fetched, SummaryStatus.Stale), now)

        assertEquals(SummaryTileKind.Numbers, model.kind)
        assertEquals("3h", model.age)
        assertTrue(model.isStale)
    }

    @Test
    fun staleWithoutDataIsNoData() {
        assertEquals(SummaryTileKind.NoData, summaryTileFor(SummarySurface(null, null, SummaryStatus.Stale), now).kind)
    }

    @Test
    fun sessionAndAccessStatesHaveTheirOwnKinds() {
        assertEquals(SummaryTileKind.NotConnected, summaryTileFor(SummarySurface(null, null, SummaryStatus.NotConnected), now).kind)
        assertEquals(SummaryTileKind.Reconnect, summaryTileFor(SummarySurface(null, null, SummaryStatus.Reconnect), now).kind)
        assertEquals(
            SummaryTileKind.MissingAccess,
            summaryTileFor(SummarySurface(null, null, SummaryStatus.MissingAnalyticsAccess), now).kind,
        )
    }
}
