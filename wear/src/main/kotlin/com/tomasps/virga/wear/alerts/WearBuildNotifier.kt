package com.tomasps.virga.wear.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tomasps.virga.R
import com.tomasps.virga.feature.builds.watch.WatchEvent
import com.tomasps.virga.wear.MainActivity
import com.tomasps.virga.wear.ui.EXTRA_START_ROUTE
import com.tomasps.virga.wear.ui.detailRoute

internal const val RESULT_CHANNEL_ID = "wear_build_alerts"
internal const val WATCHING_CHANNEL_ID = "wear_build_watching"
private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

/**
 * Posts the watch's own notifications. Every one is local-only: a build watched on the watch is polled by the watch,
 * and the phone must not mirror its alerts. One notification per build: the id comes from [alertNotificationId], so
 * a repeat post replaces the earlier one. The event-to-text decision lives in [alertFor]; this is the thin shell.
 */
class WearBuildNotifier(private val context: Context) {

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /** Channels are created on first use, once per notifier. Creating an existing channel is a no-op anyway. */
    private val channels: Unit by lazy {
        manager.createNotificationChannel(
            NotificationChannel(
                RESULT_CHANNEL_ID,
                context.getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WATCHING_CHANNEL_ID,
                context.getString(R.string.alert_watching_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /** Posts the alert for a finished, lost or expired watch. Progress posts nothing. */
    fun onEvent(event: WatchEvent) {
        val alert = alertFor(event) ?: return
        channels
        manager.notify(alertNotificationId(alert.routeKey), alertNotification(alert))
    }

    /** The foreground notification the service shows while it polls [watchCount] builds. */
    fun watchingNotification(watchCount: Int): Notification {
        channels
        val text = if (watchCount == 1) {
            context.getString(R.string.alert_watching_one)
        } else {
            context.getString(R.string.alert_watching_many, watchCount)
        }
        return NotificationCompat.Builder(context, WATCHING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_notification)
            .setContentTitle(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun alertNotification(alert: WearAlert): Notification =
        NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_notification)
            .setContentTitle(alert.scriptName)
            .setContentText(context.getString(textFor(alert.kind)))
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openBuild(alert))
            .build()

    private fun openBuild(alert: WearAlert): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        detailRoute(alert.scriptName, alert.buildUuid)?.let { intent.putExtra(EXTRA_START_ROUTE, it) }
        return PendingIntent.getActivity(context, alertNotificationId(alert.routeKey), intent, IMMUTABLE_UPDATE)
    }

    private fun textFor(kind: AlertKind): Int = when (kind) {
        AlertKind.PASSED -> R.string.status_passed
        AlertKind.FAILED -> R.string.status_failed
        AlertKind.CANCELED -> R.string.status_canceled
        AlertKind.FINISHED -> R.string.alert_finished
        AlertKind.LOST -> R.string.alert_lost
        AlertKind.EXPIRED -> R.string.alert_expired
    }
}
