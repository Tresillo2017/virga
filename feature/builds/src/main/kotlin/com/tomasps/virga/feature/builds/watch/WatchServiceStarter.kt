package com.tomasps.virga.feature.builds.watch

/** Starts the watch service. Called only from a user's Watch tap while the app is visible. */
fun interface WatchServiceStarter {
    fun start()
}
