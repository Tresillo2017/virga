package com.tomasps.virga.core.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartPolylineTest {

    @Test
    fun `single point is centred horizontally`() {
        val coordinates = ChartPolyline.build(
            values = listOf(5.0),
            domain = ChartDomain(0.0, 10.0),
            left = 0f,
            top = 0f,
            right = 100f,
            bottom = 100f,
        )

        assertEquals(listOf(50f to 50f), coordinates)
    }

    @Test
    fun `empty input gives no coordinates`() {
        val coordinates = ChartPolyline.build(emptyList(), ChartDomain(0.0, 1.0), 0f, 0f, 100f, 100f)

        assertEquals(emptyList<Pair<Float, Float>>(), coordinates)
    }

    @Test
    fun `non finite values are skipped without shifting the rest`() {
        val coordinates = ChartPolyline.build(
            values = listOf(0.0, Double.NaN, 10.0),
            domain = ChartDomain(0.0, 10.0),
            left = 0f,
            top = 0f,
            right = 100f,
            bottom = 100f,
        )

        assertEquals(listOf(0f to 100f, 100f to 0f), coordinates)
    }
}
