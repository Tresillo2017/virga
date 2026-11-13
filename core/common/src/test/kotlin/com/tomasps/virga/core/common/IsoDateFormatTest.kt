package com.tomasps.virga.core.common

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoDateFormatTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `an ISO timestamp becomes a readable date in the given zone`() {
        // Arrange
        val raw = "2024-05-01T09:00:00Z"

        // Act
        val formatted = formatIsoDate(raw, utc)

        // Assert
        assertEquals("May 1, 2024", formatted)
    }

    @Test
    fun `a value that is not an instant is returned unchanged`() {
        // Arrange / Act
        val formatted = formatIsoDate("last Tuesday", utc)

        // Assert
        assertEquals("last Tuesday", formatted)
    }

    @Test
    fun `a null value stays null`() {
        // Arrange / Act / Assert
        assertNull(formatIsoDate(null, utc))
    }
}
