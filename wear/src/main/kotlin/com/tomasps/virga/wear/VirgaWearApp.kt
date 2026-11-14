package com.tomasps.virga.wear

import android.app.Application
import android.util.Log
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.wear.session.PhoneItemBootstrap
import com.tomasps.virga.wear.session.WatchSessionRepository
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

private const val TAG = "VirgaWear"

class VirgaWearApp : Application() {

    // Logged by exception type only: a message could in principle carry token-related data.
    private val startupErrors = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Startup failed: ${error::class.java.name}")
    }
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + startupErrors)

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@VirgaWearApp)
            modules(wearKoinModules)
        }.koin
        startupScope.launch {
            val session = koin.get<WatchSessionRepository>()
            session.restore()
            session.refreshStaleness()
            // An item published before this app could hear about it (installed later, or reset) connects now.
            koin.get<PhoneItemBootstrap>().syncFromPhone()
        }
        startupScope.launch { keepOnlyConnectedAccountsWatches(koin.get<WatchRegistry>(), koin.get<kotlinx.coroutines.flow.StateFlow<WatchSessionState>>(WATCH_SESSION_STATE)) }
    }

    /** Called when the app comes to the foreground. Cheap: no network, and nothing at all while connected. */
    fun onAppOpened() {
        startupScope.launch { GlobalContext.get().get<PhoneItemBootstrap>().syncFromPhone() }
    }

    /**
     * Loads the saved watches (paused, never resumed automatically) and then drops every watch that does not belong
     * to the connected account, including all of them once the session ends. Restoring is skipped: it settles first.
     */
    private suspend fun keepOnlyConnectedAccountsWatches(
        registry: WatchRegistry,
        session: kotlinx.coroutines.flow.StateFlow<WatchSessionState>,
    ) {
        registry.restore()
        session.filter { it != WatchSessionState.Restoring }.collect { state ->
            registry.retainAccount((state as? WatchSessionState.Connected)?.accountId)
        }
    }
}
