package com.tomasps.virga.core.ui.brand

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.tomasps.virga.core.ui.R
import com.tomasps.virga.core.ui.theme.VirgaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * The animated splash on the JVM, with the test clock. Time is read from the first frame the splash draws, so every
 * "at N ms" below means N ms after the splash started. Pixels are read from the real render, so a wordmark that is
 * laid out but not painted fails here, not on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimatedSplashTest {

    @get:Rule
    val compose = createComposeRule()

    private var startWhen by mutableStateOf(true)
    private var startedAtMs = -1L
    private var finishedCount = 0
    private var finishedAtMs = -1L

    /** Left of the mark: only streak 0 reaches this far. Right of the mark: only streak 2 does. */
    private val leftBandGrid = 0f..31f
    private val rightBandGrid = 64f..100f

    @Test
    fun `streaks draw one after another, each starting 100 milliseconds after the last`() {
        play()
        advanceToElapsed(50)
        assertTrue("first streak should be drawing", litPixels(capture(), leftBand()) > 0)
        assertEquals("third streak should not have started", 0, litPixels(capture(), rightBand()))

        advanceToElapsed(150)
        assertEquals("third streak starts at 200 ms", 0, litPixels(capture(), rightBand()))

        advanceToElapsed(260)
        assertTrue("third streak should be drawing by 260 ms", litPixels(capture(), rightBand()) > 0)
    }

    @Test
    fun `the wordmark is absent while the streaks draw and fades up after them`() {
        play()
        advanceToElapsed(300)
        assertEquals("no wordmark before its 450 ms start", 0, wordmarkBrightness(capture()))

        advanceToElapsed(500)
        val partial = wordmarkBrightness(capture())
        advanceToElapsed(1000)
        val full = wordmarkBrightness(capture())

        assertTrue("wordmark should be partly visible at 500 ms, was $partial", partial in 1 until full)
        assertTrue("wordmark should be fully opaque by 1000 ms, brightest channel $full", full >= WORDMARK_FULL_MIN)
    }

    @Test
    fun `the finished picture is held before the hand-off`() {
        play()
        advanceToElapsed(800)
        assertEquals("hand-off must wait for the hold after the animation", 0, finishedCount)

        advanceToElapsed(1000)
        assertTrue("the wordmark is visible during the hold", wordmarkBrightness(capture()) >= WORDMARK_FULL_MIN)
        advanceToElapsed(1060)
        assertEquals("still holding at 1060 ms", 0, finishedCount)

        advanceToElapsed(1140)
        assertEquals("hands off once after the hold", 1, finishedCount)
        assertTrue("hand-off at ${finishedAtMs - startedAtMs} ms", finishedAtMs - startedAtMs >= SplashTimeline.handOffMs(3))

        advanceToElapsed(3000)
        assertEquals("hand-off fires only once", 1, finishedCount)
    }

    @Test
    fun `the clock does not start until the caller releases it`() {
        startWhen = false
        play()
        advanceToElapsed(0)
        advanceByMillis(1000)
        assertEquals("nothing is drawn behind the system splash", 0, litPixels(capture(), leftBand()))
        assertEquals(0, finishedCount)

        compose.runOnIdle { startWhen = true }
        advanceByMillis(300)
        assertTrue("streaks draw once released", litPixels(capture(), leftBand()) > 0)
    }

    @Test
    fun `the clock gives up waiting after MAX_WAIT_FOR_START_MS so the splash cannot hang`() {
        startWhen = false
        play()
        advanceToElapsed(0)
        advanceByMillis(SplashTimeline.MAX_WAIT_FOR_START_MS + SplashTimeline.handOffMs(3) + 200L)

        assertEquals("the splash plays anyway and hands off", 1, finishedCount)
    }

    @Test
    fun `reduced motion draws the finished picture at once and hands off`() {
        play(holdFinalFrame = true)
        compose.waitForIdle()

        assertEquals("hands off without a clock", 1, finishedCount)
        assertTrue("all streaks drawn", litPixels(capture(), leftBand()) > 0 && litPixels(capture(), rightBand()) > 0)
        assertTrue("wordmark painted", wordmarkBrightness(capture()) >= WORDMARK_FULL_MIN)
    }

    @Test
    fun `the mid-draw frame of the splash is saved`() {
        play()
        advanceToElapsed(240)
        saveScreenshot(capture(), "splash-streaks-mid-draw-dark.png")
    }

    @Test
    fun `the fully visible wordmark frame is saved`() {
        play()
        advanceToElapsed(1000)
        saveScreenshot(capture(), "splash-wordmark-visible-dark.png")
    }

    @Test
    fun `the reduced-motion final frame is saved`() {
        play(holdFinalFrame = true)
        compose.waitForIdle()
        saveScreenshot(capture(), "splash-reduced-motion-final-dark.png")
    }

    private fun play(holdFinalFrame: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VirgaTheme(darkTheme = true) {
                AnimatedSplash(
                    modifier = Modifier.fillMaxSize(),
                    holdFinalFrame = holdFinalFrame,
                    startWhen = startWhen,
                    onStarted = { startedAtMs = compose.mainClock.currentTime },
                    onFinished = {
                        finishedCount++
                        finishedAtMs = compose.mainClock.currentTime
                    },
                )
            }
        }
        if (!holdFinalFrame) awaitStarted()
    }

    /** Runs frames until the splash has drawn its first one. */
    private fun awaitStarted() {
        repeat(MAX_FRAMES_TO_START) {
            if (startedAtMs >= 0L) return
            compose.mainClock.advanceTimeByFrame()
        }
        if (startedAtMs < 0L) fail("the splash never drew its first frame")
    }

    private fun advanceToElapsed(elapsedMs: Long) {
        advanceByMillis(startedAtMs + elapsedMs - compose.mainClock.currentTime)
    }

    /**
     * Steps frame by frame, as a device does. A single advanceTimeBy delivers one frame at the far end, so a clock that
     * starts late would skip straight to its final state, and a coroutine resumed by a state change needs an idle
     * step before it asks for its next frame.
     */
    private fun advanceByMillis(millis: Long) {
        var remaining = millis
        while (remaining > 0L) {
            compose.mainClock.advanceTimeByFrame()
            // Lets the splash's coroutines resume and request their next frame before the next step.
            compose.waitForIdle()
            remaining -= FRAME_MS
        }
    }

    private fun capture(): Bitmap {
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    /** The mark's bounds in the window, split into the streak bands the stagger tests read. */
    private fun markBounds(): Rect =
        compose.onNodeWithContentDescription(string(R.string.core_ui_logo_description)).fetchSemanticsNode().boundsInRoot

    /** Pixel band that holds only the first streak, in window pixels. */
    private fun leftBand(): Rect = band(leftBandGrid)

    /** Pixel band that holds only the last streak, in window pixels. */
    private fun rightBand(): Rect = band(rightBandGrid)

    private fun band(grid: ClosedFloatingPointRange<Float>): Rect {
        val mark = markBounds()
        val scale = mark.width / VirgaMarkGeometry.GRID_SIZE
        return Rect(
            left = mark.left + grid.start * scale,
            top = mark.top,
            right = mark.left + grid.endInclusive * scale,
            bottom = mark.bottom,
        )
    }

    /** The largest channel distance from the background inside the wordmark's bounds: 0 when nothing is painted. */
    private fun wordmarkBrightness(bitmap: Bitmap): Int {
        val bounds = compose.onNodeWithText(string(R.string.core_ui_brand_name)).fetchSemanticsNode().boundsInRoot
        var widest = 0
        forEachPixel(bitmap, bounds) { pixel ->
            widest = maxOf(widest, channelDistance(pixel))
        }
        return widest
    }

    private fun channelDistance(pixel: Int): Int =
        maxOf(abs(red(pixel) - BACKGROUND_R), abs(green(pixel) - BACKGROUND_G), abs(blue(pixel) - BACKGROUND_B))

    private fun litPixels(bitmap: Bitmap, bounds: Rect): Int {
        var count = 0
        forEachPixel(bitmap, bounds) { pixel -> if (differsFromBackground(pixel)) count++ }
        return count
    }

    private fun differsFromBackground(pixel: Int): Boolean =
        abs(red(pixel) - BACKGROUND_R) + abs(green(pixel) - BACKGROUND_G) + abs(blue(pixel) - BACKGROUND_B) > LIT_THRESHOLD

    private fun forEachPixel(bitmap: Bitmap, bounds: Rect, action: (Int) -> Unit) {
        val left = bounds.left.toInt().coerceIn(0, bitmap.width)
        val right = bounds.right.toInt().coerceIn(0, bitmap.width)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height)
        val bottom = bounds.bottom.toInt().coerceIn(0, bitmap.height)
        for (y in top until bottom) for (x in left until right) action(bitmap.getPixel(x, y))
    }

    private fun saveScreenshot(bitmap: Bitmap, name: String) {
        val file = File(featureScreenshotsDir(), name)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun red(pixel: Int) = (pixel shr 16) and 0xFF
    private fun green(pixel: Int) = (pixel shr 8) and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF

    private companion object {
        /** Background of the dark scheme, #0B0E14. */
        const val BACKGROUND_R = 0x0B
        const val BACKGROUND_G = 0x0E
        const val BACKGROUND_B = 0x14

        /** Summed channel difference that counts as painted. */
        const val LIT_THRESHOLD = 24

        /**
         * A fully opaque wordmark reaches the on-background colour #E4E8F2, 222 channel steps from #0B0E14 at the
         * widest; allow for anti-aliasing.
         */
        const val WORDMARK_FULL_MIN = 200

        const val MAX_FRAMES_TO_START = 10

        /** The test clock steps one 60 Hz frame at a time. */
        const val FRAME_MS = 16L

        /** Walks up from the module directory to the project root, which holds settings.gradle.kts. */
        fun featureScreenshotsDir(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
            return File(checkNotNull(dir) { "project root not found" }, "docs/screenshots/features")
        }
    }
}
