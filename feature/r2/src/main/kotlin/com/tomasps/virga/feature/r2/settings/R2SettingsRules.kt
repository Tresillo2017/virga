package com.tomasps.virga.feature.r2.settings

import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2LockCondition
import com.tomasps.virga.feature.r2.api.R2LockRule

private const val SECONDS_PER_DAY = 86_400L
private const val MAX_RETENTION_DAYS = 36_500L
private val ORIGIN_SEPARATORS = Regex("[,\\n]")
private val VALID_DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")

/** Origins typed in one box: separated by commas or new lines, blanks dropped. */
fun parseOrigins(text: String): List<String> =
    text.split(ORIGIN_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

/** Origins a browser accepts: a wildcard, or an absolute http or https origin. Returns the bad ones. */
fun invalidOrigins(origins: List<String>): List<String> = origins.filterNot { origin ->
    origin == "*" || origin.startsWith("https://") || origin.startsWith("http://")
}

/**
 * Builds a lock rule from the form. An indefinite rule has no day count. A day rule becomes an Age condition
 * in seconds. A blank prefix means the whole bucket, so it is sent as no prefix at all.
 */
fun lockRuleFrom(id: String, prefix: String, indefinite: Boolean, days: String): R2LockRule {
    val condition = if (indefinite) {
        R2LockCondition(type = "Indefinite")
    } else {
        R2LockCondition(type = "Age", maxAgeSeconds = days.trim().toLong() * SECONDS_PER_DAY)
    }
    return R2LockRule(id = id.trim(), enabled = true, prefix = prefix.trim().takeIf { it.isNotEmpty() }, condition = condition)
}

/** An error for a lock rule id that is blank or already used by another rule. */
fun lockIdError(id: String, existing: List<R2LockRule>): UiText? = when {
    id.isBlank() -> UiText.Resource(R.string.r2_lock_id_required)
    existing.any { it.id == id.trim() } -> UiText.Resource(R.string.r2_lock_id_taken)
    else -> null
}

/** Retention days must be a whole number from 1 up to the largest the API accepts. */
fun lockDaysError(indefinite: Boolean, days: String): UiText? {
    if (indefinite) return null
    val value = days.trim().toLongOrNull()
    return if (value == null || value !in 1..MAX_RETENTION_DAYS) UiText.Resource(R.string.r2_lock_days_invalid) else null
}

/** A custom domain needs a dotted hostname and the id of the zone it sits in. */
fun domainError(domain: String, zoneId: String): UiText? = when {
    !VALID_DOMAIN.matches(domain.trim().lowercase()) -> UiText.Resource(R.string.r2_domain_invalid)
    zoneId.isBlank() -> UiText.Resource(R.string.r2_domain_zone_required)
    else -> null
}
