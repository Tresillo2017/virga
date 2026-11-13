package com.tomasps.virga.core.common

import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.Assert.assertSame

class DispatcherProviderTest {
    @Test
    fun `standard provider exposes the platform io and default dispatchers`() {
        val provider = StandardDispatcherProvider()
        assertSame(Dispatchers.IO, provider.io)
        assertSame(Dispatchers.Default, provider.default)
    }
}
