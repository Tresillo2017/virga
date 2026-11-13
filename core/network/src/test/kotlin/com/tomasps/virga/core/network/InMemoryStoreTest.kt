package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.network.demo.InMemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryStoreTest {
    @Test
    fun `put get and remove replace the immutable map`() {
        val store = InMemoryStore<String>(initial = mapOf("a" to "1"))
        val before = store.items.value
        store.put("b", "2")
        assertEquals("2", store.get("b"))
        assertEquals("1", store.remove("a"))
        assertNull(store.get("a"))
        assertEquals(mapOf("a" to "1"), before)
        assertEquals(mapOf("b" to "2"), store.items.value)
    }
}
