package com.tomasps.virga.wear.present

import java.time.Duration
import java.time.Instant

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val NOW_TEXT = "now"

/** Short age such as `now`, `5m`, `3h` or `2d`. Under a minute, or a future [from], gives `now`. */
fun ageText(from: Instant, now: Instant): String {
    val age = Duration.between(from, now)
    return when {
        age.toMinutes() < 1 -> NOW_TEXT
        age.toMinutes() < MINUTES_PER_HOUR -> "${age.toMinutes()}m"
        age.toHours() < HOURS_PER_DAY -> "${age.toHours()}h"
        else -> "${age.toDays()}d"
    }
}
