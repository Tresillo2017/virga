package com.tomasps.virga.feature.pages.logic

/** Hostname rules from the Pages domain schema: labels, then a 2 to 20 character top-level label. */
object DomainRules {
    private val HOSTNAME = Regex("^([a-z0-9][-a-z0-9]*\\.)+[-a-z0-9]{2,20}$")

    /** Trims, lowercases and drops a trailing dot, the form the API stores. */
    fun normalize(raw: String): String = raw.trim().lowercase().removeSuffix(".")

    fun isValidHostname(raw: String): Boolean = HOSTNAME.matches(normalize(raw))
}
