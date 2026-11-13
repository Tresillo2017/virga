package com.tomasps.virga.feature.builds.watch

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.tomasps.virga.core.common.formatDuration
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.DeepLinks
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildStage
import java.time.Duration
import java.time.Instant

/** Id of the one foreground notification that summarises every watch. */
const val SUMMARY_NOTIFICATION_ID = 40_001
private const val CHANNEL_ONGOING = "build_watch_ongoing"
private const val CHANNEL_RESULT = "build_watch_result"

/**
 * Builds and posts the watch notifications. One summary notification is shown while the service runs (promoted
 * to a Live Update on Android 16 when allowed), and one ordinary notification is posted per finished build.
 */
class WatchNotifier(
    private val context: Context,
    private val dismissal: SummaryDismissal,
) {

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.builds_channel_ongoing_name), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, context.getString(R.string.builds_channel_result_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** True when the user allows this app's Live Updates. Always false below Android 16. */
    fun liveUpdatesAllowed(): Boolean =
        Build.VERSION.SDK_INT >= LIVE_UPDATE_API_LEVEL && canPostPromoted()

    @RequiresApi(LIVE_UPDATE_API_LEVEL)
    private fun canPostPromoted(): Boolean = manager.canPostPromotedNotifications()

    /** The notification the foreground service shows for [watches]. The first watch is the one promoted. */
    @SuppressLint("NewApi")
    fun summary(watches: List<WatchedBuild>, nowMillis: Long): Notification {
        val primary = watches.firstOrNull()
        val title = if (watches.size == 1 && primary != null) {
            context.getString(R.string.builds_notification_title, primary.scriptName)
        } else {
            context.getString(R.string.builds_notification_title_many, watches.size)
        }
        val lines = watches.joinToString("\n") { watch -> summaryLine(watch, nowMillis) }
        val completed = completedSegments(primary?.lastRecord?.stage)
        val shortText = primary?.lastRecord?.stage?.let { shortStageText(it) }
            ?: context.getString(R.string.builds_stage_queued_short)
        val builder = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_build_watch))
            .setContentTitle(title)
            .setContentText(lines)
            .setStyle(Notification.BigTextStyle().bigText(lines))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setProgress(WATCH_SEGMENT_COUNT, completed, false)
            .setDeleteIntent(servicePending(BuildWatchService.ACTION_SUMMARY_DISMISSED, SUMMARY_KEY))
        if (primary != null) {
            val open = openIntent(primary.accountId, primary.buildUuid, primary.scriptName)
            builder.setContentIntent(open)
            builder.addAction(actionFor(R.string.builds_action_open_build, open))
            // Short label: a notification action gets about a third of the row, so "Stop watching" would truncate.
            builder.addAction(serviceAction(R.string.builds_notification_action_stop, primary.buildUuid))
            // Cancel opens the build with a flag that makes it ask in-app first. It never cancels from here.
            builder.addAction(actionFor(R.string.builds_action_cancel_build, cancelIntent(primary)))
            if (liveUpdatesAllowed()) builder.applyLiveUpdate(completed, shortText)
        }
        return builder.build()
    }

    /**
     * Refreshes the summary in place without starting or stopping the service. Skipped once the user has dismissed
     * it: the foreground service keeps running, but its notification is not brought back.
     */
    fun showSummary(watches: List<WatchedBuild>, nowMillis: Long) {
        if (!dismissal.mayRepost()) return
        manager.notify(SUMMARY_NOTIFICATION_ID, summary(watches, nowMillis))
    }

    /** Posts the one-off result for a finished watch. Never reposted, and posted at most once per build. */
    fun postResult(watch: WatchedBuild, kind: FinalKind, nowMillis: Long) {
        val text = when (kind) {
            FinalKind.SUCCEEDED -> R.string.builds_result_succeeded
            FinalKind.FAILED -> R.string.builds_result_failed
            FinalKind.CANCELED -> R.string.builds_result_canceled
            FinalKind.SKIPPED -> R.string.builds_result_skipped
            FinalKind.FINISHED -> R.string.builds_result_finished
            FinalKind.EXPIRED -> R.string.builds_result_expired
            FinalKind.LOST -> R.string.builds_result_lost
        }
        val notification = Notification.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_build_watch))
            .setContentTitle(context.getString(R.string.builds_notification_title, watch.scriptName))
            .setContentText(context.getString(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(watch.accountId, watch.buildUuid, watch.scriptName))
            .setWhen(nowMillis)
            .build()
        manager.notify(resultNotificationIdFor(watch.buildUuid), notification)
    }

    /** Posted when Android's background limit ends the service. The watches stay saved for the user to resume. */
    fun postPaused(watches: List<WatchedBuild>) {
        val first = watches.firstOrNull() ?: return
        val notification = Notification.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_build_watch))
            .setContentTitle(context.getString(R.string.builds_notification_title, first.scriptName))
            .setContentText(context.getString(R.string.builds_result_paused))
            .setAutoCancel(true)
            .setContentIntent(openIntent(first.accountId, first.buildUuid, first.scriptName))
            .build()
        manager.notify(resultNotificationIdFor(first.buildUuid), notification)
    }

    private fun summaryLine(watch: WatchedBuild, nowMillis: Long): String {
        val record = watch.lastRecord
        val stage = stageLabel(record?.stage)
        val elapsed = record?.let { formatDuration(elapsedMillis(it.createdOn, nowMillis)) }
            ?: formatDuration(elapsedMillis(Instant.ofEpochMilli(watch.startedAtMillis), nowMillis))
        val branch = record?.branch
        val detail = listOfNotNull(stage, branch, elapsed).joinToString(" · ")
        return context.getString(R.string.builds_summary_line, watch.scriptName, detail)
    }

    private fun actionFor(labelRes: Int, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_build_watch),
            context.getString(labelRes),
            intent,
        ).build()

    private fun serviceAction(labelRes: Int, buildUuid: String): Notification.Action =
        actionFor(labelRes, servicePending(ACTION_STOP_WATCH, buildUuid))

    /** Opens the build with [EXTRA_CONFIRM_CANCEL] set, so the screen asks before cancelling. Its own request code. */
    private fun cancelIntent(watch: WatchedBuild): PendingIntent {
        val intent = buildDetailIntent(watch.accountId, watch.buildUuid, watch.scriptName)
            .putExtra(EXTRA_CONFIRM_CANCEL, watch.buildUuid)
        return PendingIntent.getActivity(
            context,
            cancelRequestCodeFor(watch.buildUuid),
            intent,
            IMMUTABLE_FLAGS,
        )
    }

    private fun servicePending(action: String, buildUuid: String): PendingIntent =
        PendingIntent.getService(
            context,
            (action.hashCode() xor buildUuid.hashCode()),
            BuildWatchService.actionIntent(context, action, buildUuid),
            IMMUTABLE_FLAGS,
        )

    private fun openIntent(accountId: String, buildUuid: String, scriptName: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationIdFor(buildUuid),
            buildDetailIntent(accountId, buildUuid, scriptName),
            IMMUTABLE_FLAGS,
        )

    private fun buildDetailIntent(accountId: String, buildUuid: String, scriptName: String): Intent {
        val key = BuildDetail(accountId = accountId, buildUuid = buildUuid, workerName = scriptName)
        return Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.buildDetailUri(key)))
            .setPackage(context.packageName)
    }

    private fun stageLabel(stage: BuildStage?): String = context.getString(
        when (stage) {
            BuildStage.QUEUED, null -> R.string.builds_stage_queued
            BuildStage.INITIALIZING -> R.string.builds_stage_initializing
            BuildStage.RUNNING -> R.string.builds_stage_running
            BuildStage.DONE -> R.string.builds_stage_done
        },
    )

    private fun shortStageText(stage: BuildStage): String = context.getString(
        when (stage) {
            BuildStage.QUEUED -> R.string.builds_stage_queued_short
            BuildStage.INITIALIZING -> R.string.builds_stage_initializing_short
            BuildStage.RUNNING -> R.string.builds_stage_running_short
            BuildStage.DONE -> R.string.builds_stage_done_short
        },
    )

    private fun elapsedMillis(from: Instant, now: Long): Long =
        Duration.between(from, Instant.ofEpochMilli(now)).toMillis().coerceAtLeast(0L)

    companion object {
        /** Intent extra holding a build UUID. The build screen reads it once and asks to confirm the cancel. */
        const val EXTRA_CONFIRM_CANCEL = "com.tomasps.virga.feature.builds.extra.CONFIRM_CANCEL"

        private const val IMMUTABLE_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private const val ACTION_STOP_WATCH = BuildWatchService.ACTION_STOP_WATCH
        private const val SUMMARY_KEY = "summary"
    }
}

