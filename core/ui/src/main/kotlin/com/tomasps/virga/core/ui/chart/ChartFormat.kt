package com.tomasps.virga.core.ui.chart

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Number formatting for chart labels and descriptions. Takes an explicit [Locale] so output is testable. */
object ChartFormat {
    private const val BILLION = 1_000_000_000.0
    private const val MILLION = 1_000_000.0
    private const val THOUSAND = 1_000.0
    private const val NON_FINITE_TEXT = "-"

    /**
     * Compact form for axis labels and summaries: 999, 1.2K, 3.4M, 5.6B.
     * Whole numbers below 1000 show no decimals; others show at most two. NaN or infinite gives "-".
     */
    fun compact(value: Double, locale: Locale): String {
        if (!value.isFinite()) return NON_FINITE_TEXT
        val magnitude = abs(value)
        val (scaled, suffix) = when {
            magnitude >= BILLION -> value / BILLION to "B"
            magnitude >= MILLION -> value / MILLION to "M"
            magnitude >= THOUSAND -> value / THOUSAND to "K"
            else -> value to ""
        }
        val decimals = if (suffix.isEmpty()) "%.2f" else "%.1f"
        return trimZeros(String.format(locale, decimals, scaled), locale) + suffix
    }

    /** Removes trailing zeros and a dangling decimal separator, keeping the locale's separator. */
    private fun trimZeros(text: String, locale: Locale): String {
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        if (!text.contains(separator)) return text
        return text.trimEnd('0').trimEnd(separator)
    }
}
