package com.tomasps.virga.feature.domains.zones

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneFiltersTest {

    private val zones = listOf(
        Zone(id = "1", name = "beta.example", status = "active"),
        Zone(id = "2", name = "Alpha.example", status = "pending"),
        Zone(id = "3", name = "gamma.example", status = "active"),
    )

    @Test
    fun `search is case-insensitive and results are sorted by name`() {
        // Arrange
        val query = "EXAMPLE"

        // Act
        val result = filterZones(zones, query, status = null)

        // Assert
        assertEquals(listOf("Alpha.example", "beta.example", "gamma.example"), result.map { it.name })
    }

    @Test
    fun `status filter keeps only zones with that status`() {
        // Arrange / Act
        val result = filterZones(zones, query = "", status = "active")

        // Assert
        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test
    fun `blank search and no status filter return every zone`() {
        // Arrange / Act
        val result = filterZones(zones, query = "   ", status = null)

        // Assert
        assertEquals(3, result.size)
    }
}
