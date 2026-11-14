package com.tomasps.virga.wear.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.tomasps.virga.R
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.watch.WatchEvent
import com.tomasps.virga.feature.builds.watch.WatchedBuild
import com.tomasps.virga.wear.ui.EXTRA_START_ROUTE
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val WATCH = WatchedBuild("acc-secret", "api", "b-1", startedAtMillis = 1L)

private fun record(outcome: BuildOutcome?, status: BuildStatus = BuildStatus.STOPPED) = BuildRecord(
    buildUuid = "b-1",
    scriptName = "api",
    status = status,
    outcome = outcome,
    commitMessage = "secret commit message",
    author = "secret author",
    createdOn = Instant.parse("2026-11-14T10:00:00Z"),
)

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WearBuildNotifierTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var notifier: WearBuildNotifier

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = WearBuildNotifier(context)
    }

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun Notification.text(): String = extras.getString(Notification.EXTRA_TEXT).orEmpty()

    @Test
    fun finishedEventPostsNotificationWithOutcomeText() {
        notifier.onEvent(WatchEvent.Finished(WATCH, record(BuildOutcome.SUCCESS)))

        val notification = posted().single()
        assertEquals(context.getString(R.string.status_passed), notification.text())
        assertEquals("api", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("build/api/b-1", shadowOf(notification.contentIntent).savedIntent.getStringExtra(EXTRA_START_ROUTE))
    }

    @Test
    fun finishedWithFailureAndCancelUseTheirOwnWording() {
        notifier.onEvent(WatchEvent.Finished(WATCH, record(BuildOutcome.FAIL)))
        notifier.onEvent(WatchEvent.Finished(WATCH.copy(buildUuid = "b-2"), record(BuildOutcome.CANCELED)))

        assertEquals(
            setOf(context.getString(R.string.status_failed), context.getString(R.string.status_canceled)),
            posted().map { it.text() }.toSet(),
        )
    }

    @Test
    fun failedPollDoesNotPostFinished() {
        notifier.onEvent(WatchEvent.Failed(WATCH, ApiError.NotFound()))

        val notification = posted().single()
        assertEquals(context.getString(R.string.alert_lost), notification.text())
        assertNotEquals(context.getString(R.string.status_passed), notification.text())
    }

    @Test
    fun progressPostsNothing() {
        notifier.onEvent(WatchEvent.Progress(WATCH, record(null, BuildStatus.RUNNING)))

        assertTrue(posted().isEmpty())
    }

    @Test
    fun notificationIsLocalOnly() {
        notifier.onEvent(WatchEvent.Finished(WATCH, record(BuildOutcome.SUCCESS)))

        assertTrue(NotificationCompat.getLocalOnly(posted().single()))
        assertTrue(NotificationCompat.getLocalOnly(notifier.watchingNotification(2)))
    }

    @Test
    fun theSameBuildReplacesItsNotificationInsteadOfDuplicating() {
        notifier.onEvent(WatchEvent.Failed(WATCH, ApiError.NotFound()))
        notifier.onEvent(WatchEvent.Finished(WATCH, record(BuildOutcome.SUCCESS)))

        assertEquals(1, posted().size)
    }

    @Test
    fun notificationCarriesNoCommitAuthorOrAccount() {
        notifier.onEvent(WatchEvent.Finished(WATCH, record(BuildOutcome.SUCCESS)))

        val dump = posted().single().extras.keySet().joinToString { key -> "${key}=${posted().single().extras.get(key)}" }
        assertFalse(dump.contains("secret"))
    }

    @Test
    fun channelIsCreatedOnceWithDefaultImportance() {
        notifier.onEvent(WatchEvent.Finished(WATCH, record(BuildOutcome.SUCCESS)))
        notifier.onEvent(WatchEvent.Finished(WATCH.copy(buildUuid = "b-2"), record(BuildOutcome.FAIL)))

        val channels = manager.notificationChannels.filter { it.id == RESULT_CHANNEL_ID }
        assertEquals(1, channels.size)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels.single().importance)
        assertNotNull(channels.single().name)
    }

    @Test
    fun alertModelMapsEventsAndSkipsProgress() {
        assertNull(alertFor(WatchEvent.Progress(WATCH, record(null, BuildStatus.RUNNING))))
        assertEquals(AlertKind.PASSED, alertFor(WatchEvent.Finished(WATCH, record(BuildOutcome.SUCCESS)))?.kind)
        assertEquals(AlertKind.FINISHED, alertFor(WatchEvent.Finished(WATCH, record(BuildOutcome.UNKNOWN)))?.kind)
        assertEquals(AlertKind.LOST, alertFor(WatchEvent.Failed(WATCH, ApiError.Forbidden()))?.kind)
        assertEquals(AlertKind.EXPIRED, alertFor(WatchEvent.Expired(WATCH))?.kind)
    }

    @Test
    fun notificationIdIsStableAndNotTheRawUuid() {
        val id = alertNotificationId("api/b-1")
        assertEquals(id, alertNotificationId("api/b-1"))
        assertNotEquals(id, alertNotificationId("api/b-2"))
        assertNotEquals(WATCHING_NOTIFICATION_ID, id)
    }
}
