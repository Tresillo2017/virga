package com.tomasps.virga.feature.builds.watch

import android.content.Context
import androidx.core.content.ContextCompat

/** Starts [BuildWatchService] as a foreground service. Only called from a Watch tap while the app is visible. */
class AndroidWatchServiceStarter(private val context: Context) : WatchServiceStarter {
    override fun start() {
        ContextCompat.startForegroundService(context, BuildWatchService.startIntent(context))
    }
}
