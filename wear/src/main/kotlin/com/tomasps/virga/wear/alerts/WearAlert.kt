package com.tomasps.virga.wear.alerts

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.feature.builds.watch.WatchEvent
import com.tomasps.virga.wear.builds.routeKeyOf
import com.tomasps.virga.wear.present.BuildVisualState
import com.tomasps.virga.wear.present.visualState

/** How a watched build ended, which picks the wording of its notification. */
enum class AlertKind { PASSED, FAILED, CANCELED, FINISHED, LOST, EXPIRED }

/**
 * What a notification says, with no Android types: the Worker name and build uuid (to open the build) and the kind.
 * It carries nothing else on purpose, so no token, commit message, author or account id can reach a notification.
 */
data class WearAlert(val scriptName: String, val buildUuid: String, val kind: AlertKind) {
    val routeKey: String get() = routeKeyOf(scriptName, buildUuid)
}

/**
 * The alert for a watch event, or null when the event is not worth interrupting for. Progress is silent. A failed
 * poll (the engine only reports one after it has given up on the build) reads as "lost track", never as finished,
 * except a 401, which raises no alert.
 */
fun alertFor(event: WatchEvent): WearAlert? {
    val build = event.build
    val kind = when (event) {
        is WatchEvent.Progress -> return null
        is WatchEvent.Finished -> finishedKind(event)
        // A rejected token is the session's story (it is wiped and the screens say Reconnect), not a lost build.
        is WatchEvent.Failed -> if (event.error is ApiError.Unauthorized) return null else AlertKind.LOST
        is WatchEvent.Expired -> AlertKind.EXPIRED
    }
    return WearAlert(build.scriptName, build.buildUuid, kind)
}

/** Reuses [visualState], so the notification and the detail screen name an outcome the same way. */
private fun finishedKind(event: WatchEvent.Finished): AlertKind {
    val summary: BuildSummary = event.record.toSummary()
    return when (summary.visualState()) {
        BuildVisualState.PASSED -> AlertKind.PASSED
        BuildVisualState.FAILED -> AlertKind.FAILED
        BuildVisualState.CANCELED -> AlertKind.CANCELED
        BuildVisualState.RUNNING, BuildVisualState.UNKNOWN -> AlertKind.FINISHED
    }
}

/** Stable id per build, derived from the route key (not the raw uuid), so a repeat post replaces the earlier one. */
fun alertNotificationId(routeKey: String): Int = ALERT_ID_BASE + (routeKey.hashCode() and ID_MASK)

/** The one foreground notification. Below [ALERT_ID_BASE], so no alert id can equal it. */
const val WATCHING_NOTIFICATION_ID = 40_001

private const val ALERT_ID_BASE = 50_000
private const val ID_MASK = 0xFFFFF
