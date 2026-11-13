package com.tomasps.virga.core.navigation

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingNavigationTest {

    @Test
    fun `keys offered before anyone collects are delivered in order`() = runBlocking {
        val pending = PendingNavigation()
        val first = BuildDetail("a", "1", "w")
        val second = BuildDetail("a", "2", "w")
        pending.offer(first)
        pending.offer(second)

        val delivered = pending.keys.take(2).toList()

        assertEquals(listOf(first, second), delivered)
    }
}
