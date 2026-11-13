package com.tomasps.virga.core.ui.brand

/** One slanted streak of the mark, on the 100x100 design grid. Draws from start to end. */
data class Streak(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/** Pure geometry for the Virga mark. Kept free of Android types so it can be unit tested on the JVM. */
object VirgaMarkGeometry {
    const val GRID_SIZE = 100f
    const val STROKE_WIDTH = 11f
    const val STREAK_COUNT = 3

    /** The three streaks of mark A, ordered left to right. */
    val STREAKS: List<Streak> = listOf(
        Streak(startX = 34f, startY = 16f, endX = 20f, endY = 70f),
        Streak(startX = 56f, startY = 16f, endX = 38f, endY = 88f),
        Streak(startX = 78f, startY = 16f, endX = 66f, endY = 62f),
    )

    /** Clamps a draw progress into 0..1 and maps NaN to 0 (nothing drawn). */
    fun clampProgress(progress: Float): Float =
        if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

    /**
     * End point of a streak after it has been drawn [progress] of the way from its start.
     * Returns (x, y) on the design grid.
     */
    fun partialEnd(streak: Streak, progress: Float): Pair<Float, Float> {
        val t = clampProgress(progress)
        val x = streak.startX + (streak.endX - streak.startX) * t
        val y = streak.startY + (streak.endY - streak.startY) * t
        return x to y
    }

    /** Uniform scale from the design grid to a canvas of [canvasMinDimension] pixels. */
    fun gridScale(canvasMinDimension: Float): Float = canvasMinDimension / GRID_SIZE

    /** Stroke width in canvas pixels for the given canvas size. */
    fun strokeWidthPx(canvasMinDimension: Float): Float = STROKE_WIDTH * gridScale(canvasMinDimension)
}
