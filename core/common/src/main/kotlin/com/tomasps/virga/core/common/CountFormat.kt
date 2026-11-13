package com.tomasps.virga.core.common

private const val COUNT_BASE = 1000.0
private const val COUNT_TWO_DECIMAL_DIGITS = 2
private const val ROLLOVER_THRESHOLD = 1000.0
private val COUNT_UNITS = listOf("k", "M", "B", "T")

/**
 * Compact count with up to two decimals, e.g. 125560 -> "125.56k".
 * When rounding reaches 1000 of one unit, the value rolls over to "1" of the next unit.
 */
fun formatCompactCount(count: Long): String {
    if (count < 0) return "-" + formatCompactCount(-count)
    if (count < COUNT_BASE) return count.toString()
    var unitIndex = 0
    var scaled = count / COUNT_BASE
    while (scaled >= COUNT_BASE && unitIndex < COUNT_UNITS.lastIndex) {
        scaled /= COUNT_BASE
        unitIndex++
    }
    val text = trimFixed(scaled, COUNT_TWO_DECIMAL_DIGITS)
    val rolledOver = text.toDouble() >= ROLLOVER_THRESHOLD && unitIndex < COUNT_UNITS.lastIndex
    return if (rolledOver) "1" + COUNT_UNITS[unitIndex + 1] else text + COUNT_UNITS[unitIndex]
}
