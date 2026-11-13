package com.tomasps.virga.core.common

import java.util.Locale

private const val BINARY_BASE = 1024.0
private const val ONE_DECIMAL_LIMIT = 10.0
private val BYTE_UNITS = listOf("KB", "MB", "GB", "TB", "PB", "EB")

/** Formats a size in binary units, e.g. 1536 -> "1.5 KB". Locale-neutral. */
fun formatBytes(bytes: Long): String {
    require(bytes >= 0) { "Byte count must not be negative: $bytes" }
    if (bytes < BINARY_BASE) return "$bytes B"
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= BINARY_BASE && unitIndex < BYTE_UNITS.lastIndex) {
        value /= BINARY_BASE
        unitIndex++
    }
    val decimals = if (value < ONE_DECIMAL_LIMIT) 1 else 0
    return "${trimFixed(value, decimals)} ${BYTE_UNITS[unitIndex]}"
}

internal fun trimFixed(value: Double, decimals: Int): String {
    val text = String.format(Locale.ROOT, "%.${decimals}f", value)
    return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
}
