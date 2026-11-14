package com.tomasps.virga.wear.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tomasps.virga.core.network.RateLimiter
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.feature.builds.watch.WatchPolicy
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.wear.WATCH_SESSION_STATE
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "WearWatchService"

/**
 * Foreground service (dataSync) that polls the builds watched on the watch. It runs only while the registry has an
 * active watch and the session is Connected, and stops itself otherwise ([WearWatchRunner] decides). It starts only
 * from a Watch tap and promotes itself at once. Saved watches are not restored here: after process death they stay
 * paused until the wearer taps Watch again, because a foreground service cannot start from the background.
 * Notifications are local-only, so the phone does not mirror them.
 */
class WearWatchService : Service(), KoinComponent {

    private val registry: WatchRegistry by inject()
    private val fetcher: BuildFetcher by inject()
    private val rateLimiter: RateLimiter by inject()
    private val notifier: WearBuildNotifier by inject()
    private val session: StateFlow<WatchSessionState> by inject(WATCH_SESSION_STATE)

    private val crashGuard = WatchCrashGuard(registry, { Log.e(TAG, it) }) { stopNow() }
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> crashGuard.onFailure(error) },
    )
    private val startLock = Mutex()
    private var loop: Job? = null

    /** Id of the newest start Android delivered. A stop must name it, so a start that arrives later is not stopped. */
    @Volatile
    private var latestStartId = 0

    /** Set when this service stopped itself. Destroy after that must not pause watches. */
    @Volatile
    private var deliberateStop = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (!promoteToForeground()) return START_NOT_STICKY
        scope.launch {
            startLock.withLock { if (loop?.isActive != true) loop = scope.launch { serviceLoop() } }
        }
        return START_NOT_STICKY
    }

    /** Android calls this when the dataSync budget runs out. Watches are paused, not dropped, and the service stops. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        registry.pauseAll()
        stopNow()
    }

    override fun onDestroy() {
        loop?.cancel()
        if (!deliberateStop && registry.snapshot().isNotEmpty()) registry.pauseAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private suspend fun serviceLoop() {
        val runner = WearWatchRunner(
            registry = registry,
            session = session,
            fetcher = fetcher,
            budget = { rateLimiter.budget.value },
            onEvent = { event -> notifier.onEvent(event) },
        )
        val gate = ServiceStopGate(registry, { latestStartId }) { startId ->
            // Id read, registry check, then stopSelfResult(id): see ServiceStopGate. Android refuses a stale id.
            stopSelfResult(startId).also { if (it) markStopped() }
        }
        WatchServiceLoop(runner, gate) { delay(it) }.run()
    }

    /** Android may refuse to run a foreground service (for example from the background). Then nothing may poll. */
    private fun promoteToForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            WATCHING_NOTIFICATION_ID,
            notifier.watchingNotification(registry.snapshot().size.coerceAtLeast(1)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        true
    } catch (refused: IllegalStateException) {
        registry.pauseAll()
        stopNow()
        false
    }

    private fun stopNow() {
        markStopped()
        stopSelf()
    }

    private fun markStopped() {
        loop?.cancel()
        deliberateStop = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        /** Starts the service as a foreground service. Only call this from a Watch tap while the app is visible. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WearWatchService::class.java))
        }
    }
}
