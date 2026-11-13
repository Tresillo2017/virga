package com.tomasps.virga.core.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun `empty values give a unit domain`() {
        val domain = ChartMath.domain(emptyList(), includeZero = false)

        assertEquals(ChartDomain(0.0, 1.0), domain)
    }

    @Test
    fun `NaN and infinite values are ignored when building the domain`() {
        val domain = ChartMath.domain(listOf(Double.NaN, 4.0, Double.POSITIVE_INFINITY, 10.0), includeZero = false)

        assertEquals(ChartDomain(4.0, 10.0), domain)
    }

    @Test
    fun `a single point gets padding so the domain has height`() {
        val domain = ChartMath.domain(listOf(50.0), includeZero = false)

        assertEquals(45.0, domain.min, 0.001)
        assertEquals(55.0, domain.max, 0.001)
    }

    @Test
    fun `all zeros without zero included still give a non-empty span`() {
        val domain = ChartMath.domain(listOf(0.0, 0.0), includeZero = false)

        assertTrue(domain.span > 0.0)
    }

    @Test
    fun `all zeros with zero included give a non-empty span`() {
        val domain = ChartMath.domain(listOf(0.0, 0.0), includeZero = true)

        assertTrue(domain.span > 0.0)
        assertEquals(0.5, ChartMath.fraction(0.0, domain), 0.001)
    }

    @Test
    fun `bar domain includes zero for negative and positive values`() {
        val domain = ChartMath.domain(listOf(-4.0, 12.0), includeZero = true)

        assertEquals(ChartDomain(-4.0, 12.0), domain)
    }

    @Test
    fun `bar domain for only positive values starts at zero`() {
        val domain = ChartMath.domain(listOf(3.0, 9.0), includeZero = true)

        assertEquals(0.0, domain.min, 0.001)
        assertEquals(9.0, domain.max, 0.001)
    }

    @Test
    fun `fraction is 0 at the minimum and 1 at the maximum`() {
        val domain = ChartDomain(10.0, 20.0)

        assertEquals(0.0, ChartMath.fraction(10.0, domain), 0.0001)
        assertEquals(1.0, ChartMath.fraction(20.0, domain), 0.0001)
        assertEquals(0.25, ChartMath.fraction(12.5, domain), 0.0001)
    }

    @Test
    fun `flat domain maps values to the middle`() {
        assertEquals(0.5, ChartMath.fraction(5.0, ChartDomain(5.0, 5.0)), 0.0001)
    }

    @Test
    fun `y pixel puts the maximum at the top edge`() {
        val domain = ChartDomain(0.0, 100.0)

        assertEquals(0f, ChartMath.yPixel(100.0, domain, top = 0f, bottom = 200f), 0.001f)
        assertEquals(200f, ChartMath.yPixel(0.0, domain, top = 0f, bottom = 200f), 0.001f)
        assertEquals(50f, ChartMath.yPixel(75.0, domain, top = 0f, bottom = 200f), 0.001f)
    }

    @Test
    fun `x positions spread evenly and centre a single point`() {
        assertEquals(listOf(0f, 50f, 100f), ChartMath.xPositions(3, 0f, 100f))
        assertEquals(listOf(50f), ChartMath.xPositions(1, 0f, 100f))
        assertEquals(emptyList<Float>(), ChartMath.xPositions(0, 0f, 100f))
    }

    @Test
    fun `positive bar spans from the value up to the baseline`() {
        val domain = ChartDomain(0.0, 10.0)

        val (top, bottom) = ChartMath.barSpan(5.0, domain, top = 0f, bottom = 100f)

        assertEquals(50f, top, 0.001f)
        assertEquals(100f, bottom, 0.001f)
    }

    @Test
    fun `negative bar hangs below the zero baseline`() {
        val domain = ChartDomain(-10.0, 10.0)

        val (top, bottom) = ChartMath.barSpan(-5.0, domain, top = 0f, bottom = 200f)

        assertEquals(100f, top, 0.001f)
        assertEquals(150f, bottom, 0.001f)
    }

    @Test
    fun `bar outside the domain is clamped to the plot`() {
        val domain = ChartDomain(0.0, 10.0)

        val (top, bottom) = ChartMath.barSpan(50.0, domain, top = 0f, bottom = 100f)

        assertEquals(0f, top, 0.001f)
        assertEquals(100f, bottom, 0.001f)
    }

    @Test
    fun `stacked segments are contiguous from the bottom`() {
        val domain = ChartDomain(0.0, 10.0)

        val spans = ChartMath.stackSpans(listOf(2.0, 3.0, 5.0), domain, top = 0f, bottom = 100f)

        assertEquals(listOf(80f to 100f, 50f to 80f, 0f to 50f), spans)
    }

    @Test
    fun `segments shorter than the legend are zero-filled and extras are dropped`() {
        val normalized = ChartMath.normalizeSegments(listOf(4.0, 2.0, 9.0), segmentCount = 2)

        assertEquals(listOf(4.0, 2.0), normalized)
        assertEquals(listOf(4.0, 0.0, 0.0), ChartMath.normalizeSegments(listOf(4.0), segmentCount = 3))
    }

    @Test
    fun `negative NaN and infinite stacked segments count as zero`() {
        val normalized = ChartMath.normalizeSegments(
            listOf(-3.0, Double.NaN, Double.POSITIVE_INFINITY, 7.0),
            segmentCount = 4,
        )

        assertEquals(listOf(0.0, 0.0, 0.0, 7.0), normalized)
    }

    @Test
    fun `stacked totals add each normalised column`() {
        val totals = ChartMath.stackedTotals(listOf(listOf(1.0, 2.0), listOf(0.0, 0.0)))

        assertEquals(listOf(3.0, 0.0), totals)
    }
}
