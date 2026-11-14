package com.tomasps.virga.wear.summary

private const val TENTHS = 10L
private const val UNIT_STEP = 1_000L
private const val CAP_TEXT = "999T+"
private const val CAP_VALUE = 1_000L * 1_000 * 1_000 * 1_000 * 1_000
private val SUFFIXES = listOf("k", "M", "B", "T")

/**
 * A count short enough for a watch: `999`, `1.2k`, `12.3k`, `3.4M`. Digits are cut, never rounded up, so a value
 * cannot jump into the next unit (`999.9k`, not `1000k`). Negative values show `0`; values past 999T show `999T+`.
 */
fun compactCount(value: Long): String {
    if (value <= 0) return "0"
    if (value < UNIT_STEP) return value.toString()
    if (value >= CAP_VALUE) return CAP_TEXT
    var unit = UNIT_STEP
    var suffix = 0
    while (suffix < SUFFIXES.lastIndex && value >= unit * UNIT_STEP) {
        unit *= UNIT_STEP
        suffix++
    }
    val tenths = value / (unit / TENTHS)
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    val number = if (fraction == 0L) whole.toString() else "$whole.$fraction"
    return number + SUFFIXES[suffix]
}
