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
}
