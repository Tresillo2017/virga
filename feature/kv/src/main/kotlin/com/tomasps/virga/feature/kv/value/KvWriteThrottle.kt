package com.tomasps.virga.feature.kv.value

/** KV accepts one write per second per key. Writes to the same key sooner than that come back as 429. */
const val KV_SAME_KEY_WRITE_INTERVAL_MILLIS = 1_000L

/**
 * Remembers when each key was last written in this process, so the UI can refuse a second save of the same key
 * inside the window instead of sending a request that will fail. Not shared across processes.
 */
class KvWriteThrottle(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private val lastWrites = mutableMapOf<Pair<String, String>, Long>()

    /** Milliseconds until [key] in [namespaceId] may be written again. Zero when it may be written now. */
    @Synchronized
    fun remainingMillis(namespaceId: String, key: String): Long {
        val last = lastWrites[namespaceId to key] ?: return 0L
        return (last + KV_SAME_KEY_WRITE_INTERVAL_MILLIS - clock()).coerceAtLeast(0L)
    }

    @Synchronized
    fun markWritten(namespaceId: String, key: String) {
        lastWrites[namespaceId to key] = clock()
    }
}
