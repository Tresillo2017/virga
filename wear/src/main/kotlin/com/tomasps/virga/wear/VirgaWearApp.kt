package com.tomasps.virga.wear

import android.app.Application
import android.util.Log
import com.tomasps.virga.wear.session.WatchSessionRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
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
        }
    }
}
