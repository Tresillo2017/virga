package com.tomasps.virga.wear.present

import com.tomasps.virga.core.common.builds.BuildSummary
import java.time.Instant

private const val SHORT_RUNNING = "Run"
private const val SHORT_PASSED = "OK"
private const val SHORT_FAILED = "Fail"
private const val SHORT_CANCELED = "Stop"
private const val SHORT_UNKNOWN = "?"
private const val SHORT_NONE = "None"

internal const val NO_BUILDS_TEXT = "No builds"

/** Text for a watch-face complication. [shortText] stays under seven characters. */
data class ComplicationModel(val shortText: String, val title: String, val state: BuildVisualState)

@Suppress("UnusedParameter") // now is kept so callers can pass one clock for every surface
fun complicationFor(latest: BuildSummary?, now: Instant): ComplicationModel {
    if (latest == null) return ComplicationModel(SHORT_NONE, NO_BUILDS_TEXT, BuildVisualState.UNKNOWN)
    val state = latest.visualState()
    return ComplicationModel(shortTextFor(state), latest.scriptName, state)
}

private fun shortTextFor(state: BuildVisualState): String = when (state) {
    BuildVisualState.RUNNING -> SHORT_RUNNING
    BuildVisualState.PASSED -> SHORT_PASSED
    BuildVisualState.FAILED -> SHORT_FAILED
    BuildVisualState.CANCELED -> SHORT_CANCELED
    BuildVisualState.UNKNOWN -> SHORT_UNKNOWN
}
