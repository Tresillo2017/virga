package com.tomasps.virga.core.ui.brand

import org.junit.Assert.assertEquals
import org.junit.Test

class VirgaMarkGeometryTest {

    private val firstStreak = VirgaMarkGeometry.STREAKS[0]

    @Test
    fun `has three streaks in the brand order`() {
        assertEquals(3, VirgaMarkGeometry.STREAKS.size)
        assertEquals(Streak(34f, 16f, 20f, 70f), VirgaMarkGeometry.STREAKS[0])
        assertEquals(Streak(56f, 16f, 38f, 88f), VirgaMarkGeometry.STREAKS[1])
        assertEquals(Streak(78f, 16f, 66f, 62f), VirgaMarkGeometry.STREAKS[2])
    }

    @Test
    fun `progress zero keeps the end at the start point`() {
        val (x, y) = VirgaMarkGeometry.partialEnd(firstStreak, 0f)

        assertEquals(34f, x, 0.001f)
        assertEquals(16f, y, 0.001f)
    }

    @Test
    fun `progress one reaches the full end point`() {
        val (x, y) = VirgaMarkGeometry.partialEnd(firstStreak, 1f)

        assertEquals(20f, x, 0.001f)
        assertEquals(70f, y, 0.001f)
    }

    @Test
    fun `progress half is the midpoint of the streak`() {
        val (x, y) = VirgaMarkGeometry.partialEnd(firstStreak, 0.5f)

        assertEquals(27f, x, 0.001f)
        assertEquals(43f, y, 0.001f)
    }

    @Test
    fun `progress outside zero to one is clamped`() {
        assertEquals(VirgaMarkGeometry.partialEnd(firstStreak, 0f), VirgaMarkGeometry.partialEnd(firstStreak, -2f))
        assertEquals(VirgaMarkGeometry.partialEnd(firstStreak, 1f), VirgaMarkGeometry.partialEnd(firstStreak, 3f))
    }

    @Test
    fun `NaN progress draws nothing`() {
        assertEquals(0f, VirgaMarkGeometry.clampProgress(Float.NaN), 0f)
    }

    @Test
    fun `scale maps the 100 grid onto the canvas`() {
        assertEquals(2f, VirgaMarkGeometry.gridScale(200f), 0.001f)
        assertEquals(22f, VirgaMarkGeometry.strokeWidthPx(200f), 0.001f)
    }
}
