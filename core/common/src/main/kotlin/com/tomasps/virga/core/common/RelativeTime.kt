package com.tomasps.virga.core.common

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

enum class RelativeUnit { MINUTE, HOUR, DAY }

/**
 * Structured relative time so composables can localise it through strings.xml (plurals included).
 * [JustNow] covers anything under one minute either side of now.
 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Past(val amount: Long, val unit: RelativeUnit) : RelativeTime
    data class Future(val amount: Long, val unit: RelativeUnit) : RelativeTime
}

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

/** Relative position of [instant] against [now]. Pure, so tests pass a fixed [now]. */
fun relativeTime(instant: Instant, now: Instant): RelativeTime {
    val seconds = Duration.between(instant, now).seconds
    val magnitude = abs(seconds)
    if (magnitude < SECONDS_PER_MINUTE) return RelativeTime.JustNow
    val (amount, unit) = when {
        magnitude < SECONDS_PER_MINUTE * MINUTES_PER_HOUR ->
            magnitude / SECONDS_PER_MINUTE to RelativeUnit.MINUTE
        magnitude < SECONDS_PER_MINUTE * MINUTES_PER_HOUR * HOURS_PER_DAY ->
            magnitude / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR) to RelativeUnit.HOUR
        else -> magnitude / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR * HOURS_PER_DAY) to RelativeUnit.DAY
    }
    return if (seconds > 0) RelativeTime.Past(amount, unit) else RelativeTime.Future(amount, unit)
}
