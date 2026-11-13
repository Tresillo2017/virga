package com.tomasps.virga.feature.builds.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.tomasps.virga.feature.builds.R

/**
 * Characters of log text that go into a copy or share. A share travels in one binder transaction, which Android
 * limits to roughly a megabyte in total, so the text is kept well below that.
 */
internal const val SHARE_CHAR_BUDGET = 100_000

/** The newest lines that fit in [budget] characters, oldest first. */
internal fun logTailForShare(lines: List<String>, budget: Int = SHARE_CHAR_BUDGET): List<String> {
    var used = 0
    var keepFrom = lines.size
    for (index in lines.indices.reversed()) {
        used += lines[index].length + 1
        if (used > budget) break
        keepFrom = index
    }
    return lines.subList(keepFrom, lines.size)
}

/**
 * The log as plain text, one line per row, limited to the newest lines that fit. Build logs hold no credentials;
 * the API never returns the token.
 */
internal fun logAsText(lines: List<String>): String = logTailForShare(lines).joinToString(separator = "\n")

/** Puts the log on the clipboard and returns the message to show, or null when there is nothing to copy. */
internal fun copyLog(context: Context, lines: List<String>): Int? {
    if (lines.isEmpty()) return null
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.builds_log_share_title), logAsText(lines)))
    return R.string.builds_log_copied
}

/** Opens the system share sheet with the log as text. Does nothing when there is no log yet. */
internal fun shareLog(context: Context, lines: List<String>) {
    if (lines.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.builds_log_share_title))
        .putExtra(Intent.EXTRA_TEXT, logAsText(lines))
    context.startActivity(Intent.createChooser(send, context.getString(R.string.builds_share_log)))
}
