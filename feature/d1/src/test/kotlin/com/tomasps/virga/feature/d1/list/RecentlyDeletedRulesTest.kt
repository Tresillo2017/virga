package com.tomasps.virga.feature.d1.list

import com.tomasps.virga.feature.d1.data.D1Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 2_000_000_000_000L

class RecentlyDeletedRulesTest {

    private val recent = RecentlyDeletedDatabase(uuid = "a", name = "orders", deletedAtMillis = NOW - 1_000)

    @Test
    fun `an entry is restorable until the seven-day window closes`() {
        val justInside = RecentlyDeletedDatabase("b", "b", NOW - D1Limits.UNDELETE_WINDOW_MILLIS + 1)
        val justOutside = RecentlyDeletedDatabase("c", "c", NOW - D1Limits.UNDELETE_WINDOW_MILLIS)

        val live = RecentlyDeletedRules.restorable(listOf(recent, justInside, justOutside), NOW)

        assertEquals(listOf("a", "b"), live.map { it.uuid })
    }

    @Test
    fun `adding the same database twice keeps one entry, the latest`() {
        val again = recent.copy(name = "orders-renamed")

        val entries = RecentlyDeletedRules.added(listOf(recent), again, NOW)

        assertEquals(listOf("orders-renamed"), entries.map { it.name })
    }

    @Test
    fun `the restore deadline is seven days after deletion`() {
        assertEquals(recent.deletedAtMillis + D1Limits.UNDELETE_WINDOW_MILLIS, RecentlyDeletedRules.restoreDeadlineMillis(recent))
    }

    @Test
    fun `the stored list round-trips, and unreadable content reads as empty`() {
        val encoded = RecentlyDeletedCodec.encode(listOf(recent))

        assertEquals(listOf(recent), RecentlyDeletedCodec.decode(encoded))
        assertTrue(RecentlyDeletedCodec.decode("not json").isEmpty())
        assertTrue(RecentlyDeletedCodec.decode(null).isEmpty())
    }
}
