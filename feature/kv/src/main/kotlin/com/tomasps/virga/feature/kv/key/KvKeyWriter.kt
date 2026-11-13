package com.tomasps.virga.feature.kv.key

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.value.KV_MIN_EXPIRATION_SECONDS
import com.tomasps.virga.feature.kv.value.KvExpiration
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import kotlinx.serialization.json.JsonElement

/** Outcome of one single-value write. [Throttled] is checked before any request, so nothing is sent. */
sealed interface KvWriteResult {
    data object Written : KvWriteResult

    data class Throttled(val remainingMillis: Long) : KvWriteResult

    data object ExpiresTooSoon : KvWriteResult

    data class Failed(val error: ApiError) : KvWriteResult
}

/** The expiration an edit must re-send: the key's current one, or [TooSoon] when it would expire inside the minimum. */
sealed interface KvExpirationKeep {
    data class Keep(val expiration: KvExpiration) : KvExpirationKeep

    data object TooSoon : KvExpirationKeep
}

/**
 * Single-value writes with the two rules an edit needs. A PUT replaces the whole entry, so an edit must re-send the
 * metadata and the expiration it wants to keep. And KV allows one write per second per key, so a write that would
 * break that is refused here, before a request is sent.
 */
class KvKeyWriter(
    private val repository: KvRepository,
    private val throttle: KvWriteThrottle,
    private val clock: () -> Long,
) {

    suspend fun write(
        accountId: String,
        namespaceId: String,
        key: String,
        bytes: ByteArray,
        metadata: JsonElement?,
        expiration: KvExpiration,
    ): KvWriteResult {
        val wait = throttle.remainingMillis(namespaceId, key)
        if (wait > 0) return KvWriteResult.Throttled(wait)
        throttle.markWritten(namespaceId, key)
        return when (val result = repository.writeValue(accountId, namespaceId, key, bytes, metadata, expiration)) {
            is ApiResult.Success -> KvWriteResult.Written
            is ApiResult.Failure -> KvWriteResult.Failed(result.error)
        }
    }

    /** Keeps an existing expiration (epoch seconds, or null for none) across an edit. */
    fun expirationToKeep(expiresAtEpochSeconds: Long?): KvExpirationKeep {
        if (expiresAtEpochSeconds == null) return KvExpirationKeep.Keep(KvExpiration.None)
        val tooSoon = expiresAtEpochSeconds < clock() + KV_MIN_EXPIRATION_SECONDS
        return if (tooSoon) KvExpirationKeep.TooSoon else KvExpirationKeep.Keep(KvExpiration.Absolute(expiresAtEpochSeconds))
    }
}
