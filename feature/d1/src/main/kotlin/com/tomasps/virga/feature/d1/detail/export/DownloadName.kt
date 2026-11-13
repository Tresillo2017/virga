package com.tomasps.virga.feature.d1.detail.export

internal const val DEFAULT_EXPORT_FILENAME = "d1-export.sql"

private const val MAX_NAME_CHARS = 120
private const val SAFE_SYMBOLS = "._-"

/**
 * The file name the API gave, reduced to a plain basename for the download manager. Any path part is dropped,
 * characters outside ASCII letters, digits and ._- become underscores, leading dots are removed (so no hidden or
 * relative names), and the result is capped. An empty result falls back to [DEFAULT_EXPORT_FILENAME].
 */
internal fun safeDownloadName(raw: String?): String {
    if (raw.isNullOrBlank()) return DEFAULT_EXPORT_FILENAME
    val basename = raw.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = basename
        .map { if (it.isAsciiLetterOrDigit() || it in SAFE_SYMBOLS) it else '_' }
        .joinToString(separator = "")
        .trimStart('.')
        .take(MAX_NAME_CHARS)
    return cleaned.ifBlank { DEFAULT_EXPORT_FILENAME }
}

private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
