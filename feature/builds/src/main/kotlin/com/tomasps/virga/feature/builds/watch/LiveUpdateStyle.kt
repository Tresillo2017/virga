package com.tomasps.virga.feature.builds.watch

import android.app.Notification
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi

/** First API level with promoted ongoing notifications (Android 16). Below it the ordinary progress bar is used. */
const val LIVE_UPDATE_API_LEVEL = 36

private val FILLED_SEGMENT: Int = Color.rgb(0x8E, 0x9B, 0xFF)
private val PENDING_SEGMENT: Int = Color.rgb(0x3A, 0x41, 0x50)

/**
 * Makes a notification a promoted Live Update: a ProgressStyle with one segment per stage, and [shortText] as the
 * status-bar chip. Requires the POST_PROMOTED_NOTIFICATIONS permission, which the app manifest declares.
 */
@RequiresApi(LIVE_UPDATE_API_LEVEL)
internal fun Notification.Builder.applyLiveUpdate(completed: Int, shortText: String): Notification.Builder {
    val segments = (0 until WATCH_SEGMENT_COUNT).map { index ->
        Notification.ProgressStyle.Segment(1).setColor(if (index < completed) FILLED_SEGMENT else PENDING_SEGMENT)
    }
    setShortCriticalText(shortText)
    // setRequestPromotedOngoing arrived in API 36.1. On 36.0 the notification stays an ordinary ProgressStyle.
    if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) setRequestPromotedOngoing(true)
    setStyle(
        Notification.ProgressStyle()
            .setProgressSegments(segments)
            .setProgress(completed),
    )
    return this
}
