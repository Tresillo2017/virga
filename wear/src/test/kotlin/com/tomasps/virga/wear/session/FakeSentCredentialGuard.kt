package com.tomasps.virga.wear.session

import java.util.Collections

/** Every tracked call reports [sent] as its credential (null: nothing sent). Rejections are recorded, then [onReject] runs. */
class FakeSentCredentialGuard(
    private val sent: Long? = SENT_GENERATION,
    private val onReject: suspend (Long) -> Unit = {},
) : SentCredentialGuard {
    val rejected: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override suspend fun <T> track(call: suspend () -> T): Tracked<T> = Tracked(call(), sent)

    override suspend fun onRejected(generation: Long) {
        rejected += generation
        onReject(generation)
    }

    companion object {
        const val SENT_GENERATION = 7L
    }
}
