package com.tomasps.virga.core.ui.brand

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tomasps.virga.core.ui.theme.VirgaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Recreating the activity during the Compose splash (rotation, font scale) must resume at the finished picture. It
 * must not replay the animation from the start, and it must not blank the screen.
 *
 * The compose rule has no activity here, so recreation is emulated: the saved state of the first composition is taken
 * from its [SaveableStateRegistry] and handed to a new composition, which is what an Activity restart does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashGateTest {

    @get:Rule
    val compose = createComposeRule()

    private val splashVisible = MutableStateFlow(true)
    private var startedCount = 0

    @Test
    fun `a recreated activity resumes at the finished picture instead of replaying`() {
        // The saved state of an activity recreated mid-splash: the animation had started, and had not finished.
        // SplashGate saves animationDone first, then animationStarted, so the registry answers in that order.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides SavedFlags(done = false, started = true)) {
                gate()
            }
        }
        advanceFrames(FRAME_MS * 2)

        // Resuming needs no animation time: the app is up within a couple of frames. A replay would still be drawing.
        compose.onNodeWithText(APP_TEXT).assertIsDisplayed()
        assertEquals("the restored splash started and finished at once", 1, startedCount)
    }

    /** Answers rememberSaveable's restores in the order the splash asks for them: animationDone, then animationStarted. */
    private class SavedFlags(done: Boolean, started: Boolean) : SaveableStateRegistry {
        private val pending = ArrayDeque(listOf(done, started))

        override fun consumeRestored(key: String): Any? = pending.removeFirstOrNull()?.let { mutableStateOf(it) }

        override fun registerProvider(key: String, valueProvider: () -> Any?): SaveableStateRegistry.Entry =
            object : SaveableStateRegistry.Entry {
                override fun unregister() = Unit
            }

        override fun canBeSaved(value: Any): Boolean = true

        override fun performSave(): Map<String, List<Any?>> = emptyMap()
    }

    @Test
    fun `the app shows after the hold when the splash was never recreated`() {
        compose.mainClock.autoAdvance = false
        showGate()
        advanceUntilStarted()
        compose.onNodeWithText(APP_TEXT).assertDoesNotExist()

        advanceFrames(1200)
        compose.onNodeWithText(APP_TEXT).assertIsDisplayed()
    }

    private fun showGate() {
        compose.setContent { gate() }
    }

    @Composable
    private fun gate() {
        VirgaTheme(darkTheme = true) {
            SplashGate(splashVisible = splashVisible, onSplashStarted = { startedCount++ }) {
                Text(APP_TEXT)
            }
        }
    }

    private fun advanceUntilStarted() {
        repeat(MAX_FRAMES_TO_START) {
            if (startedCount > 0) return
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue("the splash never started", startedCount > 0)
    }

    private fun advanceFrames(millis: Long) {
        repeat((millis / FRAME_MS).toInt()) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private companion object {
        const val APP_TEXT = "App shell"
        const val FRAME_MS = 16L
        const val MAX_FRAMES_TO_START = 10
    }
}
