package com.tomasps.virga.feature.builds.api

import com.tomasps.virga.feature.builds.demo.demoTagFor
import kotlinx.serialization.Serializable

/** One item of GET /workers/scripts. Only the name (`id`) and the tag are read here. */
@Serializable
internal data class ScriptDto(
    val id: String? = null,
    val tag: String? = null,
) {
    /**
     * The Worker with its Builds tag. A script without a tag is dropped, except in demo mode, where the canned
     * list carries no tags and a synthetic one is used. The demo routes accept it.
     */
    fun toWorkerRef(isDemo: Boolean): WorkerRef? {
        val name = id?.takeIf { it.isNotBlank() } ?: return null
        val workerTag = tag?.takeIf { it.isNotBlank() } ?: if (isDemo) demoTagFor(name) else return null
        return WorkerRef(name = name, tag = workerTag)
    }
}
