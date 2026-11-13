package com.tomasps.virga.feature.r2.api

/** Location hints the bucket create call accepts (locationHint). */
val R2_LOCATION_HINTS: List<String> = listOf("enam", "wnam", "weur", "eeur", "apac", "oc")

/** Default storage classes for new objects. */
val R2_STORAGE_CLASSES: List<String> = listOf("Standard", "InfrequentAccess")

/** Minimum TLS versions a custom domain can require. */
val R2_MIN_TLS_VERSIONS: List<String> = listOf("1.0", "1.1", "1.2", "1.3")

/** HTTP methods a CORS rule can allow. */
val R2_CORS_METHODS: List<String> = listOf("GET", "PUT", "POST", "DELETE", "HEAD")

/** Object listings are paged at this many items per request. */
const val R2_LIST_PAGE_SIZE: Int = 50
