package com.tomasps.virga.core.common

import java.util.Locale
import kotlin.math.abs

private const val PERCENT_DECIMALS = 2
private const val ZERO_DELTA_EPSILON = 0.005
private const val PERCENT_SCALE = 100.0
private const val NON_FINITE_PLACEHOLDER = "--"

/** Formats an already-scaled percentage with two decimals, e.g. 14.1 -> "14.10%". */
fun formatPercent(percent: Double): String {
    if (!percent.isFinite()) return NON_FINITE_PLACEHOLDER
    return String.format(Locale.ROOT, "%.${PERCENT_DECIMALS}f%%", percent)
}

/** Relative change from [previous] to [current] as a percentage, or null when [previous] is zero. */
fun percentChange(previous: Double, current: Double): Double? {
    if (previous == 0.0) return null
    return (current - previous) / abs(previous) * PERCENT_SCALE
}

/** Signed delta such as "+12.50%" or "-3.00%". Values that round to zero carry no sign. */
fun formatSignedPercent(percent: Double): String {
    if (!percent.isFinite()) return NON_FINITE_PLACEHOLDER
    if (abs(percent) < ZERO_DELTA_EPSILON) return formatPercent(0.0)
    val sign = if (percent > 0) "+" else ""
    return sign + formatPercent(percent)
}
