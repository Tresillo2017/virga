package com.tomasps.virga.core.testing

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

private const val POLL_INTERVAL_MS = 5_000L

/** The same mechanism the builds screens use: a loop on viewModelScope, which runs on Dispatchers.Main. */
class PollingViewModel : ViewModel() {
    private val mutableTicks = MutableStateFlow(0)
    val ticks: StateFlow<Int> = mutableTicks.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                mutableTicks.update { it + 1 }
            }
        }
    }
}

@Serializable
private data object PollingProbe : NavKey

@Composable
private fun PollingProbeScreen(viewModel: PollingViewModel = koinViewModel()) {
    val ticks by viewModel.ticks.collectAsState()
    Text("ticks $ticks")
}

/** The harness must neither hang on a polling screen nor miss a poll once the clock is advanced. */
@RunWith(RobolectricTestRunner::class)
class PollingScreenFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(
            compose,
            FeatureUnderTest(koinModules = listOf(module { viewModel { PollingViewModel() } })) { routes ->
                routes.route(PollingProbe.serializer()) { PollingProbeScreen() }
            },
        )
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `a polling screen does not stall the waits, and advancing the clock delivers its polls`() {
        flow.show(PollingProbe)

        flow.waitForText("ticks 0")
        flow.advanceClock(POLL_INTERVAL_MS)
        flow.waitForText("ticks 1")
        flow.advanceClock(POLL_INTERVAL_MS * 2)
        flow.waitForText("ticks 3")
    }
}
