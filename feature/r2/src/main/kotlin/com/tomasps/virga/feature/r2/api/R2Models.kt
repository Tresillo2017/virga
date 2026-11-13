package com.tomasps.virga.feature.r2.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One bucket as the R2 bucket endpoints return it. Every field is optional on the wire, so all have defaults. */
@Serializable
data class R2BucketInfo(
    val name: String = "",
    @SerialName("creation_date") val creationDate: String? = null,
    val jurisdiction: String? = null,
    val location: String? = null,
    @SerialName("storage_class") val storageClass: String? = null,
)

/** One object from a listing. Built by [R2ListEnvelopeParser], which reads the raw envelope. */
data class R2ObjectItem(
    val key: String,
    val size: Long,
    val etag: String?,
    val lastModified: String?,
    val contentType: String?,
    val customMetadata: Map<String, String>,
    val storageClass: String?,
)

/** One page of objects. [folders] are common prefixes (the "directories" at this level). */
data class R2ObjectPage(
    val objects: List<R2ObjectItem>,
    val folders: List<String>,
    val nextCursor: String?,
)

/** One page of buckets. */
data class R2BucketPage(
    val buckets: List<R2BucketInfo>,
    val nextCursor: String?,
)

/** The r2.dev public development URL switch. */
@Serializable
data class R2ManagedDomain(
    val enabled: Boolean = false,
    val domain: String? = null,
)

@Serializable
data class R2CustomDomainList(
    val domains: List<R2CustomDomain> = emptyList(),
)

@Serializable
data class R2CustomDomain(
    val domain: String = "",
    val enabled: Boolean = true,
    val zoneId: String? = null,
    val zoneName: String? = null,
    @SerialName("minTLS") val minTls: String? = null,
    val status: R2DomainStatus? = null,
)

@Serializable
data class R2DomainStatus(
    val ownership: String? = null,
    val ssl: String? = null,
)

/** One CORS rule. Empty lists mean the field was absent on the wire. */
@Serializable
data class R2CorsRule(
    val id: String? = null,
    val allowed: R2CorsAllowed = R2CorsAllowed(),
    val exposeHeaders: List<String> = emptyList(),
    val maxAgeSeconds: Int? = null,
)

@Serializable
data class R2CorsAllowed(
    val origins: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    val headers: List<String> = emptyList(),
)

@Serializable
data class R2CorsConfig(
    val rules: List<R2CorsRule> = emptyList(),
)

/** One bucket lock rule. [condition] is one of Age (maxAgeSeconds), Date (date) or Indefinite. */
@Serializable
data class R2LockRule(
    val id: String = "",
    val enabled: Boolean = true,
    val prefix: String? = null,
    val condition: R2LockCondition = R2LockCondition(),
)

@Serializable
data class R2LockCondition(
    val type: String = "",
    val maxAgeSeconds: Long? = null,
    val date: String? = null,
)

@Serializable
data class R2LockConfig(
    val rules: List<R2LockRule> = emptyList(),
)
