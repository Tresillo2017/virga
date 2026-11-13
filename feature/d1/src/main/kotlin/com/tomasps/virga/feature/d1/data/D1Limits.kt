package com.tomasps.virga.feature.d1.data

/** Limits the D1 REST API imposes, plus the app's own page caps. Named so no screen carries a bare number. */
object D1Limits {
    /** Cloudflare API requests must finish within 30 seconds, including every statement of a batch. */
    const val REQUEST_LIMIT_MILLIS = 30_000L

    /** Reason on the local failure returned when [REQUEST_LIMIT_MILLIS] runs out. */
    const val REQUEST_LIMIT_REASON = "D1 request limit reached"

    /** Substring of the API message for a result over the 50 MB cap (enforced from 2026-11-04). */
    const val RESULT_TOO_LARGE_MARKER = "maximum response size limit"

    /** Rows requested per table page, and the most a grid will ever ask for in one request. */
    const val TABLE_PAGE_ROWS = 50
    const val MAX_GRID_PAGE_ROWS = 200

    /** Console results show at most this many rows; the rest are counted but not rendered. */
    const val CONSOLE_MAX_ROWS_SHOWN = 500

    /** A database deleted less than this long ago can be restored with the undelete endpoint (7 days). */
    const val UNDELETE_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1_000L

    /** Databases per list page. The API accepts 10 to 10,000. */
    const val LIST_PAGE_SIZE = 50

    /** Export polling: how often to ask, and how long to wait before giving up on the UI side. */
    const val EXPORT_POLL_INTERVAL_MILLIS = 2_000L
    const val EXPORT_MAX_WAIT_MILLIS = 15 * 60 * 1_000L
}
