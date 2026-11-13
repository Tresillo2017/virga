package com.tomasps.virga.feature.kv.value

/** Minimum time-to-live and minimum distance into the future that KV accepts for an expiration. */
const val KV_MIN_EXPIRATION_SECONDS = 60L

/**
 * How a key expires. [Absolute] is epoch seconds; [Ttl] is seconds from now. [None] sends no expiration parameter,
 * which means the key never expires.
 */
sealed interface KvExpiration {
    data object None : KvExpiration

    data class Absolute(val epochSeconds: Long) : KvExpiration

    data class Ttl(val seconds: Long) : KvExpiration

    /** Query parameters for a single-value PUT. Empty for [None]. */
    fun toQuery(): Map<String, String> = when (this) {
        None -> emptyMap()
        is Absolute -> mapOf(QUERY_EXPIRATION to epochSeconds.toString())
        is Ttl -> mapOf(QUERY_EXPIRATION_TTL to seconds.toString())
    }

    /** The absolute epoch second the key expires at, or null when it never does. */
    fun absoluteEpochSeconds(nowEpochSeconds: Long): Long? = when (this) {
        None -> null
        is Absolute -> epochSeconds
        is Ttl -> nowEpochSeconds + seconds
    }

    private companion object {
        const val QUERY_EXPIRATION = "expiration"
        const val QUERY_EXPIRATION_TTL = "expiration_ttl"
    }
}

/** Why an expiration was rejected before anything was sent. */
enum class KvExpirationProblem { TTL_TOO_SHORT, ABSOLUTE_TOO_SOON }

/**
 * Checks an expiration against the 60 second floor. An absolute time that is too close to now is rejected rather
 * than silently dropped, so the user can choose a new one.
 */
fun validateExpiration(expiration: KvExpiration, nowEpochSeconds: Long): KvExpirationProblem? = when (expiration) {
    KvExpiration.None -> null
    is KvExpiration.Ttl -> KvExpirationProblem.TTL_TOO_SHORT.takeIf { expiration.seconds < KV_MIN_EXPIRATION_SECONDS }
    is KvExpiration.Absolute -> KvExpirationProblem.ABSOLUTE_TOO_SOON
        .takeIf { expiration.epochSeconds < nowEpochSeconds + KV_MIN_EXPIRATION_SECONDS }
}
