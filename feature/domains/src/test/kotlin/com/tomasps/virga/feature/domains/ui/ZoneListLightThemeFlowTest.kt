package com.tomasps.virga.feature.domains.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.testing.DemoFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The domain list in the light theme: the same screen renders and its rows are reachable. Colours are not asserted. */
@RunWith(RobolectricTestRunner::class)
class ZoneListLightThemeFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = domainsFlow(compose, darkTheme = false)
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `the domain list renders in the light theme`() {
        flow.show(DomainsRoot)

        flow.assertTextShown("example.com")
        flow.assertTextShown("Active · Pro Plan")
    }
}
