package com.tomasps.virga.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Keys that cross feature boundaries. Each one is declared here so any feature can navigate to it without
 * depending on the owning feature. The owner is named in the comment; see docs/feature-module-guide.md.
 * A feature's own internal keys live in that feature's module and never appear here.
 */

/** Marker for the five bottom tabs. Only these keys show the bottom bar. */
interface TopLevelRoute : NavKey

// Bottom tabs. HomeRoot is owned by :feature:home, DomainsRoot by :feature:domains, AccountRoot by
// :feature:account. ComputeRoot and StorageRoot are hubs drawn by :app, linking to the list roots below.
@Serializable data object HomeRoot : TopLevelRoute
@Serializable data object ComputeRoot : TopLevelRoute
@Serializable data object DomainsRoot : TopLevelRoute
@Serializable data object StorageRoot : TopLevelRoute
@Serializable data object AccountRoot : TopLevelRoute

// Top-level lists opened from the Compute and Storage hubs, and the Domains tab's zone list. Owned by the feature
// named in the comment. Reuse these instead of adding WorkersList, PagesList, ZonesList, R2BucketsList,
// D1DatabasesList or KvNamespacesList: they are the same destinations.
@Serializable data object WorkersRoot : NavKey // :feature:workers
@Serializable data object PagesRoot : NavKey // :feature:pages
@Serializable data object R2Root : NavKey // :feature:r2
@Serializable data object D1Root : NavKey // :feature:d1
@Serializable data object KvRoot : NavKey // :feature:kv

// Detail and cross-feature destinations.
@Serializable data object SignIn : NavKey // :feature:account
@Serializable data object Settings : NavKey // :feature:account
@Serializable data class WorkerDetail(val scriptName: String) : NavKey // :feature:workers
/** Full build history of one Worker. Opened from a Worker detail screen. Owned by :feature:builds. */
@Serializable data class WorkerBuilds(val accountId: String, val scriptName: String) : NavKey // :feature:builds
/**
 * A build is only meaningful inside its account, so the key carries the account id. A watched build restored
 * after an account switch then opens the right build, or none, never another account's build with the same id.
 */
@Serializable data class BuildDetail(val accountId: String, val buildUuid: String, val workerName: String) : NavKey // :feature:builds
@Serializable data class PagesProject(val projectName: String) : NavKey // :feature:pages
@Serializable data class ZoneDetail(val zoneId: String) : NavKey // :feature:domains
@Serializable data class DnsRecords(val zoneId: String) : NavKey // :feature:domains
@Serializable data class R2Bucket(val bucketName: String) : NavKey // :feature:r2
@Serializable data class D1Database(val databaseId: String) : NavKey // :feature:d1
@Serializable data class KvNamespace(val namespaceId: String) : NavKey // :feature:kv
