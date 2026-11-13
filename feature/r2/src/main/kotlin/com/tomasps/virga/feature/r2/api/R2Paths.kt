package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.network.cloudflarePath
import io.ktor.http.encodeURLPathPart

private const val OBJECT_KEY_SEPARATOR = "/"

/** Largest object one single PUT may carry (5 GiB). Larger uploads need multipart, which Virga does not do yet. */
const val R2_SINGLE_PUT_MAX_BYTES: Long = 5L * 1024 * 1024 * 1024

/** Bucket names are 3 to 64 characters of lowercase letters, digits and hyphens. */
val R2_BUCKET_NAME_PATTERN = Regex("^[a-z0-9-]{3,64}$")

const val R2_BUCKET_NAME_MAX_LENGTH = 64

/** Characters a bucket name may use. Length is checked separately, so a name can be checked while it is still short. */
val R2_BUCKET_NAME_CHARACTERS = Regex("^[a-z0-9-]*$")

fun bucketsPath(accountId: String): String = cloudflarePath("accounts", accountId, "r2", "buckets")

fun bucketPath(accountId: String, bucketName: String): String =
    cloudflarePath("accounts", accountId, "r2", "buckets", bucketName)

/** True when every key segment is usable. A segment of "." or ".." would be resolved by the HTTP stack, so it is refused. */
fun isSafeObjectKey(objectKey: String): Boolean =
    objectKey.isNotEmpty() && objectKey.split(OBJECT_KEY_SEPARATOR).none { it == "." || it == ".." }

fun objectsPath(accountId: String, bucketName: String): String =
    cloudflarePath("accounts", accountId, "r2", "buckets", bucketName, "objects")

/**
 * Path of one object. The API needs slashes in the key sent literally (not as %2F), so each key segment is
 * escaped on its own and the separators are kept. Dot segments are refused by the transport.
 */
fun objectPath(accountId: String, bucketName: String, objectKey: String): String {
    val encodedKey = objectKey.split(OBJECT_KEY_SEPARATOR).joinToString(OBJECT_KEY_SEPARATOR) { it.encodeURLPathPart() }
    return objectsPath(accountId, bucketName) + OBJECT_KEY_SEPARATOR + encodedKey
}

fun managedDomainPath(accountId: String, bucketName: String): String =
    bucketPath(accountId, bucketName) + "/domains/managed"

fun customDomainsPath(accountId: String, bucketName: String): String =
    bucketPath(accountId, bucketName) + "/domains/custom"

fun customDomainPath(accountId: String, bucketName: String, domain: String): String =
    cloudflarePath("accounts", accountId, "r2", "buckets", bucketName, "domains", "custom", domain)

fun corsPath(accountId: String, bucketName: String): String = bucketPath(accountId, bucketName) + "/cors"

fun lockPath(accountId: String, bucketName: String): String = bucketPath(accountId, bucketName) + "/lock"

fun lifecyclePath(accountId: String, bucketName: String): String = bucketPath(accountId, bucketName) + "/lifecycle"
