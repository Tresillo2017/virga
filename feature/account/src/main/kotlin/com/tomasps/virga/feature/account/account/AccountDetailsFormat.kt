package com.tomasps.virga.feature.account.account

import com.tomasps.virga.core.common.formatIsoDate
import java.time.ZoneId

/**
 * The account type as a word a person reads, for example "standard" becomes "Standard". Null when there is no type,
 * so the row is left out rather than shown empty.
 */
fun friendlyAccountType(raw: String?): String? {
    val words = raw?.trim()?.replace('_', ' ')?.takeIf { it.isNotEmpty() } ?: return null
    return words.replaceFirstChar { it.uppercaseChar() }
}

/**
 * The creation time as a localised date. Null when there is no timestamp, or when it is not one: an unreadable
 * value is hidden rather than shown as the raw API string.
 */
fun friendlyCreatedOn(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (raw.isNullOrBlank()) return null
    val formatted = formatIsoDate(raw, zone) ?: return null
    return if (formatted == raw) null else formatted
}
