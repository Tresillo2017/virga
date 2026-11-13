package com.tomasps.virga.feature.builds.watch

/**
 * Decides when the summary notification is reposted. It is reposted when the watched set or a stage changes, and
 * otherwise at most every [REFRESH_MS], so the elapsed time stays roughly current without a repost each tick.
 */
class SummaryPostPolicy(private val refreshMs: Long = REFRESH_MS) {
    private var lastKey: List<String>? = null
    private var lastPostedAt: Long = 0L

    fun shouldPost(watches: Collection<WatchedBuild>, nowMillis: Long): Boolean {
        val key = watches.map { watch ->
            "${watch.buildUuid}|${watch.lastRecord?.stage?.name}|${watch.lastRecord?.branch}"
        }.sorted()
        val changed = key != lastKey
        val due = nowMillis - lastPostedAt >= refreshMs
        if (!changed && !due) return false
        lastKey = key
        lastPostedAt = nowMillis
        return true
    }

    companion object {
        const val REFRESH_MS = 15_000L
    }
}
