package com.tomasps.virga.wear.surfaces

/** Counts update requests instead of asking the system. */
class FakeSurfaceUpdater : SurfaceUpdater {
    var requests = 0
    override fun requestUpdates() {
        requests++
    }
}
