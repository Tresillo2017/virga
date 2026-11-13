package com.tomasps.virga.core.network

import kotlinx.serialization.json.Json

/** Parsing is defensive: unknown keys are ignored and nulls fall back to defaults. */
val CloudflareJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
