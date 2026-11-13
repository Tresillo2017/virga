package com.tomasps.virga.feature.builds.watch

/** What the watch loop does after a tick: stop the service, or sleep before the next tick. */
internal sealed interface WatchLoopStep {
    data object Stop : WatchLoopStep
    data class Sleep(val millis: Long) : WatchLoopStep
}

/** Stop and pause decisions for [BuildWatchService], kept pure so the JVM tests cover them. */
internal object WatchServiceStopPolicy {

    /** The service may stop only when no build is watched. */
    fun mayStop(activeWatches: Int): Boolean = activeWatches == 0

    /**
     * Decides the step after a tick. An empty registry stops at once: sleeping first would keep the service in the
     * foreground for up to a loop delay after the last watch finished.
     */
    fun nextLoopStep(activeWatches: Int, nextDueInMillis: Long?): WatchLoopStep =
        if (mayStop(activeWatches)) WatchLoopStep.Stop else WatchLoopStep.Sleep(WatchPolicy.loopDelayMillis(nextDueInMillis))

    /**
     * Destroy pauses the watches only when Android ended the service while some were still active. After a deliberate
     * stop the watches stay active: a start that raced the stop may already have added one, and its service instance
     * polls the same registry.
     */
    fun shouldPauseOnDestroy(deliberateStop: Boolean, activeWatches: Int): Boolean =
        !deliberateStop && activeWatches > 0
}
