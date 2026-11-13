package com.tomasps.virga.feature.kv

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

/** One key's detail screen: value, metadata, expiration and the actions on them. Internal to :feature:kv. */
@Serializable
data class KvKeyDetail(val namespaceId: String, val keyName: String) : NavKey

/** The add-key screen for one namespace. */
@Serializable
data class KvAddKey(val namespaceId: String) : NavKey

/** The bulk import screen for one namespace. */
@Serializable
data class KvImport(val namespaceId: String) : NavKey
