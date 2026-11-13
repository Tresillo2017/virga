package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.feature.workers.R

/**
 * String resource for a binding type id as the API sends it (for example "kv_namespace"), or null when the type
 * is not one this table knows. Callers show the raw id for unknown types, so a new binding type never breaks the
 * screen.
 */
internal fun bindingTypeLabelRes(type: String): Int? = when (type) {
    "kv_namespace" -> R.string.workers_binding_kv
    "r2_bucket" -> R.string.workers_binding_r2
    "d1" -> R.string.workers_binding_d1
    "plain_text" -> R.string.workers_binding_plain_text
    "secret_text" -> R.string.workers_binding_secret
    "service" -> R.string.workers_binding_service
    "durable_object_namespace" -> R.string.workers_binding_durable_object
    "assets" -> R.string.workers_binding_assets
    "queue" -> R.string.workers_binding_queue
    "analytics_engine" -> R.string.workers_binding_analytics_engine
    "hyperdrive" -> R.string.workers_binding_hyperdrive
    "ai" -> R.string.workers_binding_ai
    "vectorize" -> R.string.workers_binding_vectorize
    "workflow" -> R.string.workers_binding_workflow
    "images" -> R.string.workers_binding_images
    else -> null
}
