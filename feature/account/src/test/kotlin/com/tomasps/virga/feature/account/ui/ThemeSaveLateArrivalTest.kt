package com.tomasps.virga.feature.account.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.tomasps.virga.core.navigation.Settings
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.account.settings.ThemeMode
import com.tomasps.virga.feature.account.settings.ThemeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val WRITER_HEAD_START_MS = 500L

/**
 * A theme save hops from the DataStore writer thread back to the main thread before it is written. On a loaded machine
 * that hop arrives after the tap has settled, so the flow's wait has to run the main looper itself. This pins that down.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeSaveLateArrivalTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = accountFlow(compose)
        runBlocking { flow.get<ThemeRepository>().set(ThemeMode.System) }
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `a save that reaches the main thread after the tap settled is still seen by the flow wait`() {
        val themes = flow.get<ThemeRepository>()
        flow.show(Settings)

        // Tap without letting the main looper run, then give the writer thread time to post back to it.
        compose.onNode(hasText("Dark") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        Thread.sleep(WRITER_HEAD_START_MS)

        flow.waitUntil { runBlocking { themes.mode.first() == ThemeMode.Dark } }
        assertEquals(ThemeMode.Dark, runBlocking { themes.mode.first() })
    }
}
