package com.tomasps.virga.core.wearsync

object StalenessPolicy {
    const val MAX_OFFLINE_MILLIS: Long = 24 * 60 * 60 * 1000

    /** A missing check-in is stale; a check-in in the future (clock skew) is not. */
    fun isStale(lastCheckInMillis: Long?, nowMillis: Long): Boolean =
        lastCheckInMillis == null || nowMillis - lastCheckInMillis > MAX_OFFLINE_MILLIS
}
