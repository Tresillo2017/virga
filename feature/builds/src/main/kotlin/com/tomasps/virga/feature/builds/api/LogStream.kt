package com.tomasps.virga.feature.builds.api

/** Most log lines held on screen. Older lines are dropped from the top, so memory stays bounded on long builds. */
const val MAX_LOG_LINES = 2_000

/**
 * The log as the screen holds it, plus the cursor for the next read.
 *
 * A read sent without a cursor returns the whole log, so its lines replace what is held. A read sent with a cursor
 * returns only the lines after it, so they are appended. A reply to a cursored read that omits the cursor is not
 * trusted: its lines are dropped and the cursor is cleared, so the next read is a full one that replaces the log.
 * That repeats the whole log on each poll for such a reply, but it never duplicates or skips a line.
 */
data class LogStream(val lines: List<String> = emptyList(), val cursor: String? = null) {

    /** The state after [page] came back for a read that was sent with [requestCursor]. */
    fun applied(page: LogPage, requestCursor: String?): LogStream {
        val combined = when {
            requestCursor == null -> page.lines
            page.cursor == null -> lines
            else -> lines + page.lines
        }
        return LogStream(lines = combined.takeLast(MAX_LOG_LINES), cursor = page.cursor)
    }
}
