package com.tomasps.virga.core.common

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class ByteFormatTest {
    @Test
    fun `formats small values in bytes without decimals`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `uses one decimal below ten units and trims a zero decimal`() {
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1 KB", formatBytes(1024))
    }

    @Test
    fun `uses no decimals at ten units and above`() {
        assertEquals("12 MB", formatBytes(12L * 1024 * 1024))
        assertEquals("5 GB", formatBytes(5L * 1024 * 1024 * 1024))
    }

    @Test
    fun `reaches terabytes for large values`() {
        assertEquals("2.5 TB", formatBytes((2.5 * 1024 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `rejects negative sizes`() {
        assertThrows(IllegalArgumentException::class.java) { formatBytes(-1) }
    }
}
