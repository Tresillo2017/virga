package com.tomasps.virga.feature.r2.transfer

private const val MIN_STEP_BYTES = 256L * 1024L

/**
 * Decides when a copy loop should publish progress. Publishing on every 64 KB chunk floods the state flow, so a
 * report is made only after [MIN_STEP_BYTES] more bytes, or when the copy reaches the known total.
 */
internal class ProgressThrottle(private val totalBytes: Long?) {

    private var lastReported = 0L

    fun shouldReport(doneBytes: Long): Boolean {
        val reachedTotal = totalBytes != null && doneBytes >= totalBytes
        val enoughNew = doneBytes - lastReported >= MIN_STEP_BYTES
        if (!reachedTotal && !enoughNew) return false
        lastReported = doneBytes
        return true
    }
}
