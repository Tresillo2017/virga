package com.tomasps.virga.feature.builds.watch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.tomasps.virga.core.network.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground service (dataSync) that polls the watched builds. It runs only while at least one build is active, and
 * stops itself when the list empties. Polling and timing live in [WatchEngine]; this class wires them to Android:
 * foreground promotion, notifications, and onTimeout.
 *
 * It starts only from a Watch tap, which carries no action and promotes the service at once (Android allows five
 * seconds). A notification action never promotes the service, because that would bring back a dismissed summary.
 * Saved watches are not restored here: they stay paused until the user taps Watch on them.
 */
class BuildWatchService : Service(), KoinComponent {

    private val registry: WatchRegistry by inject()
    private val fetcher: BuildFetcher by inject()
    private val rateLimiter: RateLimiter by inject()
    private val notifier: WatchNotifier by inject()
    private val dismissal: SummaryDismissal by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startLock = Mutex()
    private var loop: Job? = null
    private var foregroundStarted = false

    /** Id of the newest start Android delivered. A stop must name it, so a start that arrives later is not stopped. */
    @Volatile
    private var latestStartId = 0

    /** Set when this service stopped itself because nothing is watched. Destroy after that must not pause watches. */
    private var deliberateStop = false

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val action = intent?.action
        val buildUuid = intent?.getStringExtra(EXTRA_BUILD_UUID)
        val isWatchTap = action == null
        if (isWatchTap) promoteToForeground()
        scope.launch {
            startLock.withLock { handleStart(action, buildUuid, isWatchTap) }
        }
        return START_NOT_STICKY
    }

    /** Android calls this when the dataSync budget runs out. Watches are paused, not dropped, and the service stops. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val watches = registry.snapshot().values.toList()
        registry.pauseAll()
        notifier.postPaused(watches)
        stopForegroundAndSelf()
    }

    override fun onDestroy() {
        loop?.cancel()
        // Android ended the service with watches still active: keep them visible as paused. A deliberate stop
        // leaves them active, because a start that raced the stop may already have added a watch.
        if (WatchServiceStopPolicy.shouldPauseOnDestroy(deliberateStop, registry.snapshot().size)) registry.pauseAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    /** Runs under [startLock], so two starts can never create two polling loops. */
    private fun handleStart(action: String?, buildUuid: String?, isWatchTap: Boolean) {
        handleAction(action, buildUuid)
        when {
            registry.snapshot().isEmpty() -> tryStopIfIdle()
            !isWatchTap && !foregroundStarted -> {
                // A notification action reached a service that is no longer foreground. Nothing may poll in the background.
                registry.pauseAll()
                stopForegroundAndSelf()
            }
            loop?.isActive != true -> loop = scope.launch { runLoop() }
        }
    }

    private fun handleAction(action: String?, buildUuid: String?) {
        when (action) {
            ACTION_STOP_WATCH -> buildUuid?.let { registry.remove(it) }
            ACTION_SUMMARY_DISMISSED -> dismissal.onDismissed()
        }
    }

    private suspend fun runLoop() {
        val engine = WatchEngine(
            registry = registry,
            fetcher = fetcher,
            budget = { rateLimiter.budget.value },
            onEvent = { event -> onEngineEvent(event) },
        )
        val summary = SummaryPostPolicy()
        while (true) {
            val now = System.currentTimeMillis()
            val nextDueInMillis = engine.tick(now)
            val watches = registry.snapshot().values.toList()
            if (watches.isNotEmpty() && summary.shouldPost(watches, now)) notifier.showSummary(watches, now)
            val step = WatchServiceStopPolicy.nextLoopStep(watches.size, nextDueInMillis)
            // A refused stop (a newer start is pending) retries after the shortest delay, never a busy spin.
            if (step == WatchLoopStep.Stop && startLock.withLock { tryStopIfIdle() }) return
            delay((step as? WatchLoopStep.Sleep)?.millis ?: WatchPolicy.MIN_LOOP_DELAY_MS)
        }
    }

    private fun onEngineEvent(event: WatchEvent) {
        val now = System.currentTimeMillis()
        when (event) {
            is WatchEvent.Progress -> Unit
            is WatchEvent.Finished -> notifier.postResult(event.build, finalKindOf(event.record), now)
            is WatchEvent.Expired -> notifier.postResult(event.build, FinalKind.EXPIRED, now)
            is WatchEvent.Failed -> notifier.postResult(event.build, FinalKind.LOST, now)
        }
    }

    private fun promoteToForeground() {
        val notification = notifier.summary(registry.snapshot().values.toList(), System.currentTimeMillis())
        ServiceCompat.startForeground(
            this,
            SUMMARY_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        foregroundStarted = true
    }

    /**
     * Stops the service only when nothing is watched. The start id is read before the registry is checked, so a
     * watch added after the check is caught by [stopSelfResult]: Android refuses a stop that names an older start.
     */
    private fun tryStopIfIdle(): Boolean {
        val startId = latestStartId
        if (!WatchServiceStopPolicy.mayStop(registry.snapshot().size)) return false
        if (!stopSelfResult(startId)) return false
        markStopped()
        return true
    }

    /** Stops whatever the state, because Android asked for it (dataSync timeout) or a stale start must end. */
    private fun stopForegroundAndSelf() {
        markStopped()
        stopSelf()
    }

    private fun markStopped() {
        loop?.cancel()
        deliberateStop = true
        foregroundStarted = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val ACTION_STOP_WATCH = "com.tomasps.virga.feature.builds.action.STOP_WATCH"
        const val ACTION_SUMMARY_DISMISSED = "com.tomasps.virga.feature.builds.action.SUMMARY_DISMISSED"
        const val EXTRA_BUILD_UUID = "build_uuid"

        fun startIntent(context: Context): Intent = Intent(context, BuildWatchService::class.java)

        fun actionIntent(context: Context, action: String, buildUuid: String): Intent =
            Intent(context, BuildWatchService::class.java)
                .setAction(action)
                .putExtra(EXTRA_BUILD_UUID, buildUuid)
    }
}
