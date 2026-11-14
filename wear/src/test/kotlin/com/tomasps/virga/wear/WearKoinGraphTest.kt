package com.tomasps.virga.wear

import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.builds.WearBuildsSource
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The production binding set, built the way VirgaWearApp builds it. Later tasks consume these types by key. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WearKoinGraphTest {

    @Test
    fun productionGraphResolvesTheInterfaceAndTheConcreteSource() {
        val koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(wearKoinModules)
        }.koin
        try {
            val concrete = koin.get<WearBuildsSource>()
            assertSame(concrete, koin.get<WearBuilds>())
            val state = koin.get<StateFlow<WatchSessionState>>(WATCH_SESSION_STATE)
            assertTrue(state.value is WatchSessionState.Restoring)
        } finally {
            koin.close()
        }
    }

    @Test
    fun alertsGraphResolvesWhatTheWatchServiceInjects() {
        val koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(wearKoinModules)
        }.koin
        try {
            koin.get<com.tomasps.virga.core.network.RateLimiter>()
            val registry = koin.get<com.tomasps.virga.feature.builds.watch.WatchRegistry>()
            assertSame(registry, koin.get<com.tomasps.virga.feature.builds.watch.WatchRegistry>())
            koin.get<com.tomasps.virga.feature.builds.watch.BuildFetcher>()
            koin.get<com.tomasps.virga.wear.alerts.WearBuildNotifier>()
            koin.get<com.tomasps.virga.wear.alerts.WatchStarter>()
            koin.get<StateFlow<WatchSessionState>>(WATCH_SESSION_STATE)
        } finally {
            koin.close()
        }
    }

    @Test
    fun surfaceGraphResolvesWhatTheTilesInject() {
        val koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(wearKoinModules)
        }.koin
        try {
            val summary = koin.get<com.tomasps.virga.wear.summary.SummaryRefresher>()
            assertSame(summary, koin.get<com.tomasps.virga.wear.summary.SummaryRefresher>())
            koin.get<com.tomasps.virga.wear.surfaces.BuildSurfaceRefresher>()
            // What VirgaWearApp and MainActivity resolve on start and on app open.
            koin.get<com.tomasps.virga.wear.surfaces.SurfaceUpdater>()
            koin.get<com.tomasps.virga.wear.session.PhoneItemBootstrap>()
        } finally {
            koin.close()
        }
    }
}
