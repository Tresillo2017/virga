@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.wear.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.semantics.SemanticsActions
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.coreNetworkKoinModule
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes
import com.tomasps.virga.wear.WATCH_SESSION_STATE
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.builds.WearBuildsSource
import com.tomasps.virga.wear.demo.WearDemoRoutes
import com.tomasps.virga.wear.session.WatchSessionState
import com.tomasps.virga.wear.wearUiKoinModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val DEMO_ACCOUNT = "demo-account-personal"
private const val WAIT_MS = 10_000L

private object UnconfinedDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

/** The whole watch app over the demo engine: no network, no phone. */
// The real VirgaWearApp would start the production Koin graph (Keystore, DataStore); the test owns its own.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WearFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = MutableStateFlow<WatchSessionState>(WatchSessionState.Connected(DEMO_ACCOUNT, isDemo = true))
    private lateinit var koinApp: KoinApplication
    private var scripted: WearBuilds? = null

    @Before
    fun setUp() {
        koinApp = koinApplication {
            modules(
                coreNetworkKoinModule,
                module {
                    single<Context> { RuntimeEnvironment.getApplication() }
                    single<DispatcherProvider> { UnconfinedDispatchers }
                    single { DemoMode(initiallyEnabled = true) }
                    single { ActiveCredential() }
                    single<CredentialProvider> { get<ActiveCredential>() }
                    single { BuildsDemoRoutes() } bind DemoRouteContributor::class
                    single { WearDemoRoutes() } bind DemoRouteContributor::class
                    single { BuildsRepository(get(), get()) }
                    single<WearBuilds> { scripted ?: WearBuildsSource(get(), onUnauthorized = {}, hasToken = { true }) }
                    single<StateFlow<WatchSessionState>>(WATCH_SESSION_STATE) { session }
                },
                wearUiKoinModule,
            )
        }
    }

    @After
    fun tearDown() {
        koinApp.close()
    }

    private fun showApp() {
        compose.setContent { KoinIsolatedContext(koinApp) { WearApp() } }
        compose.waitForIdle()
    }

    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), WAIT_MS)

    private fun click(text: String) {
        waitForText(text)
        // The demo lists several builds for one Worker; the newest is first, and the live one is the newest.
        val target = compose.onAllNodes(hasText(text) and hasClickAction()).onFirst()
        runCatching { target.performScrollTo() }
        target.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+round")
    fun cancelARunningBuildThroughConfirmToDone() {
        showApp()
        waitForText("Builds")
        click("edge-api")
        waitForText("Running · now")

        click("Cancel build")
        waitForText("Cancel this build?")
        click("Confirm")

        waitForText("Done")
    }

    @Test
    @Config(qualifiers = "+notround")
    fun listLaysOutOnASquareScreen() {
        showApp()
        waitForText("Builds")
        compose.waitUntilAtLeastOneExists(hasText("edge-api"), WAIT_MS)
    }

    @Test
    fun notConnectedShowsConnectMessageWithNoInput() {
        session.value = WatchSessionState.NotConnected
        showApp()
        waitForText("Open Virga on your phone to connect")
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun serverErrorShowsSomethingWentWrongNotBlank() {
        scripted = FakeWearBuilds(recent = failure(ApiError.Server(503)))
        showApp()
        waitForText("Something went wrong")
    }

    @Test
    fun buildWithNoBranchAndNoCommitShowsWithoutRetry() {
        val bare = build().copy(branch = null, commitHash = null)
        scripted = FakeWearBuilds(recent = ApiResult.Success(listOf(bare)), plan = ApiResult.Success(null))
        showApp()
        click("api")
        // The detail status line is plain text; the list row it came from is a button.
        compose.waitUntilAtLeastOneExists(hasText("Passed", substring = true) and !hasClickAction(), WAIT_MS)
        compose.onAllNodes(hasText("Retry build")).assertCountEquals(0)
    }

    @Test
    fun staleShowsReconnectMessage() {
        session.value = WatchSessionState.Stale
        showApp()
        waitForText("Reconnect from phone")
    }
}
