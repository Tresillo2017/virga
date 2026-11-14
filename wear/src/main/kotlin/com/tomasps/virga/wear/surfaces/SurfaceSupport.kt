package com.tomasps.virga.wear.surfaces

import android.util.Log
import kotlinx.coroutines.CancellationException

private const val TAG = "VirgaSurface"

/** [BuildSurfaceRefresher.refresh] that never throws: a storage failure is logged by class name and reads as no data. */
internal suspend fun BuildSurfaceRefresher.refreshOrNull(): SurfaceData? = try {
    refresh()
} catch (error: CancellationException) {
    throw error
} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
    Log.e(TAG, "Refresh failed: ${error::class.java.name}")
    null
}
