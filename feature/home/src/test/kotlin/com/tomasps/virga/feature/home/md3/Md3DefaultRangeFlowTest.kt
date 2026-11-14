package com.tomasps.virga.feature.home.md3

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.core.ui.components.DefaultTimeRangeSource
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.home.homeKoinModule
import com.tomasps.virga.feature.home.ui.FlowAnalytics
import com.tomasps.virga.feature.home.ui.FlowBuildsGateway
import com.tomasps.virga.feature.home.ui.FlowLists
import com.tomasps.virga.feature.home.ui.homeFlowModule
import com.tomasps.virga.feature.home.ui.registerHomeFlowEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

/** Material 3 audit, settings effect: Home opens on the analytics range chosen in Settings. */
@RunWith(RobolectricTestRunner::class)
class Md3DefaultRangeFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private var flow: DemoFlow? = null

    @After
    fun tearDown() {
        flow?.close()
    }

    private fun startWith(range: TimeRange?): DemoFlow {
        val source = range?.let { chosen ->
            module {
                single<DefaultTimeRangeSource> {
                    object : DefaultTimeRangeSource {
                        override val defaultRange: Flow<TimeRange> = flowOf(chosen)
                    }
                }
            }
        }
        val modules = listOfNotNull(homeKoinModule, homeFlowModule(FlowBuildsGateway(), FlowLists(), FlowAnalytics()), source)
        return DemoFlow(compose, FeatureUnderTest(modules, ::registerHomeFlowEntries)).also { flow = it }
    }

    @Test
    fun `Home opens on the default range from settings`() {
        val home = startWith(TimeRange.SEVEN_DAYS)

        home.show(HomeRoot)

        home.waitForText("7d")
    }

    @Test
    fun `Home without a saved default opens on 24 hours`() {
        val home = startWith(null)

        home.show(HomeRoot)

        home.waitForText("24h")
    }
}
