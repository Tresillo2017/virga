package com.tomasps.virga.feature.d1.ui

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreatedOnFormatTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `an ISO timestamp with fractional seconds becomes a readable date`() {
        // Arrange
        val raw = "2026-11-10T20:28:54.029837Z"

        // Act
        val formatted = formatCreatedOn(raw, utc)

        // Assert
        assertEquals("Nov 10, 2026", formatted)
    }

    @Test
    fun `a value that is not an instant is returned unchanged`() {
        // Arrange
        val raw = "last Tuesday"

        // Act
        val formatted = formatCreatedOn(raw, utc)

        // Assert
        assertEquals("last Tuesday", formatted)
    }

    @Test
    fun `a missing value stays missing`() {
        // Act and Assert
        assertNull(formatCreatedOn(null, utc))
    }
}
