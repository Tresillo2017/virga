package com.tomasps.virga.wear.session

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * The token in use and its generation. The generation changes only when the token itself changes or is cleared, so a
 * same-token heartbeat does not hide a genuine rejection. [toString] leaves the token out: it must never reach a log.
 */
class IssuedCredential(val token: String, val generation: Long) {
    override fun toString(): String = "IssuedCredential(generation=$generation)"
}

/** A call's result and the generation of the credential it actually sent, or null when it sent none. */
class Tracked<T>(val value: T, val sentGeneration: Long?)

/**
 * Ties a 401 to the credential that was actually sent with the request, so a local "no token" failure, or a late 401
 * for a token that has since been replaced, never wipes the session that is in use now.
 */
interface SentCredentialGuard {
    suspend fun <T> track(call: suspend () -> T): Tracked<T>

    /** The API rejected the credential of [generation]. Implementations wipe only if it is still the one in use. */
    suspend fun onRejected(generation: Long)
}

/** The watch's guard: the credential provider records into the request's coroutine context what it handed out. */
class SessionSentCredentialGuard(private val session: WatchSessionRepository) : SentCredentialGuard {

    override suspend fun <T> track(call: suspend () -> T): Tracked<T> {
        val record = SentCredentialRecord()
        val value = withContext(record) { call() }
        return Tracked(value, record.generation)
    }

    override suspend fun onRejected(generation: Long) = session.onUnauthorized(generation)
}

/**
 * Per-request record of the credential the transport took from the provider. One record per tracked call; when a
 * call makes several requests the last one wins, which is the one whose failure ends the call.
 */
internal class SentCredentialRecord : AbstractCoroutineContextElement(Key) {
    @Volatile
    var generation: Long? = null
        private set

    fun record(sent: Long?) {
        generation = sent
    }

    companion object Key : CoroutineContext.Key<SentCredentialRecord>
}

/** Called by the credential provider: [generation] of the token it returned, or null when it returned none. */
internal suspend fun recordSentCredential(generation: Long?) {
    currentCoroutineContext()[SentCredentialRecord]?.record(generation)
}
