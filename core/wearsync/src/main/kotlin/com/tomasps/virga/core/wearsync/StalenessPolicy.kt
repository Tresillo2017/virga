package com.tomasps.virga.core.wearsync

object StalenessPolicy {
    const val MAX_OFFLINE_MILLIS: Long = 24 * 60 * 60 * 1000

    /** How far in the future a check-in may lie (small clock corrections) before it counts as stale. */
    const val MAX_FUTURE_SKEW_MILLIS: Long = 5 * 60 * 1000

    /**
     * A missing check-in is stale. A check-in slightly in the future (small clock correction) is not; one more than
     * [MAX_FUTURE_SKEW_MILLIS] ahead is, so rolling the watch clock back cannot keep the token usable past 24 hours.
     */
    fun isStale(lastCheckInMillis: Long?, nowMillis: Long): Boolean {
        if (lastCheckInMillis == null) return true
        val age = nowMillis - lastCheckInMillis
        return age > MAX_OFFLINE_MILLIS || age < -MAX_FUTURE_SKEW_MILLIS
    }
}
