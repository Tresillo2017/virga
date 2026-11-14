package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.builds.BuildSummary

/**
 * A build's identity on the watch: its Worker's name and its uuid. The uuid alone can repeat across Workers (the demo
 * does) and the Worker tag may be missing, so neither is a safe key by itself.
 */
internal fun BuildSummary.routeKey(): String = "$scriptName/$buildUuid"

internal fun List<BuildSummary>.findByKey(scriptName: String, buildUuid: String): BuildSummary? =
    firstOrNull { it.scriptName == scriptName && it.buildUuid == buildUuid }
