package com.tomasps.virga

import android.app.Application
import android.util.Log
import com.tomasps.virga.core.auth.coreAuthKoinModule
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.common.coreCommonKoinModule
import com.tomasps.virga.core.network.coreNetworkKoinModule
import com.tomasps.virga.feature.account.accountKoinModule
import com.tomasps.virga.feature.builds.buildsKoinModule
import com.tomasps.virga.feature.d1.d1KoinModule
import com.tomasps.virga.feature.domains.domainsKoinModule
import com.tomasps.virga.feature.home.homeKoinModule
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.pages.pagesKoinModule
import com.tomasps.virga.feature.r2.r2KoinModule
import com.tomasps.virga.feature.workers.workersKoinModule
import com.tomasps.virga.wearsync.HandoffPublisher
import com.tomasps.virga.wearsync.startWearSync
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

private const val TAG = "Virga"

class VirgaApplication : Application() {

    /**
     * restore() settles the session itself, so nothing should reach this handler. If something does, it is logged
     * by exception type only, because the message or stack could in principle carry token-related data.
     */
    private val startupErrors = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Startup failed: ${error::class.java.name}")
    }
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + startupErrors)

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@VirgaApplication)
            modules(virgaKoinModules)
        }.koin
        // Restores the session once per process, not once per Activity. The splash waits on it. Building the session
        // graph (the HTTP client, the Keystore-backed vault) happens off the main thread, so the first frames are not
        // held up by it.
        startupScope.launch {
            // Starts before restore so the first SignedIn state is published to the watch.
            startWearSync(startupScope, this@VirgaApplication, koin.get(), koin.get<HandoffPublisher>())
            koin.get<SessionRepository>().restore()
        }
    }
}
