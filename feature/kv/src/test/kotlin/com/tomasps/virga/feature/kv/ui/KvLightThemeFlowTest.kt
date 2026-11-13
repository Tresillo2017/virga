package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.kv.registerKvEntries
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The same screen in the light theme. It renders the same content; the colours themselves are not asserted here. */
@RunWith(RobolectricTestRunner::class)
class KvLightThemeFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(
            compose,
            FeatureUnderTest(listOf(kvKoinModule), ::registerKvEntries),
            darkTheme = false,
        )
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `namespaces list renders in the light theme`() {
        flow.show(KvRoot)

        flow.assertTextShown("Session cache")
        flow.assertTextShown("Feature flags")
    }
}
