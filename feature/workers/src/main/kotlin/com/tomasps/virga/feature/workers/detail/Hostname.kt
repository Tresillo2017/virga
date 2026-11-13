package com.tomasps.virga.feature.workers.detail

/** Lowercase with no surrounding space or trailing dot, so the shape check and the request see the same value. */
internal fun normaliseHostname(raw: String): String = raw.trim().lowercase().removeSuffix(".")

private val HOSTNAME_PATTERN = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

/** True when [raw] has the shape of a hostname such as api.example.com. Cloudflare decides whether it is usable. */
internal fun isValidHostname(raw: String): Boolean = HOSTNAME_PATTERN.matches(normaliseHostname(raw))
