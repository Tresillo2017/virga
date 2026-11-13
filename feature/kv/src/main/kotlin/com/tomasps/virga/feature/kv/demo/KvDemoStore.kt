package com.tomasps.virga.feature.kv.demo

import com.tomasps.virga.core.network.demo.InMemoryStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Separates account, namespace and key in store keys. A unit separator cannot appear in a name the UI accepts. */
internal const val KV_DEMO_SEP = "\u001F"

internal const val KV_DEMO_ACCOUNT_PERSONAL = "demo-account-personal"
internal const val KV_DEMO_ACCOUNT_STAGING = "demo-account-staging"

internal const val KV_DEMO_NS_SESSIONS = "5f1c0a9e2b7d4c3a8e6f1b2d3c4e5f60"
internal const val KV_DEMO_NS_FLAGS = "9a2b7c4d1e3f4a5b8c6d7e8f9a0b1c2d"
internal const val KV_DEMO_NS_ASSETS = "3c8d2e1f0a9b4c7d8e5f6a1b2c3d4e5f"

private const val SESSION_TTL_SECONDS = 3_600L
private const val SECONDS_PER_MILLI = 1_000L

/** A namespace as the demo keeps it. [title] is what the rename and create routes change. */
internal data class KvDemoNamespace(val id: String, val title: String)

/** One stored key. [bytes] is the raw value, [metadata] and [expiresAtEpochSeconds] are what the PUT carried. */
internal class KvDemoEntry(
    val bytes: ByteArray,
    val metadata: JsonElement?,
    val expiresAtEpochSeconds: Long?,
)

/** Canned state for the demo account: namespaces and keys, held in memory and changed by the demo's writes. */
internal class KvDemoStore(nowEpochSeconds: Long = System.currentTimeMillis() / SECONDS_PER_MILLI) {
    val namespaces = InMemoryStore(seedNamespaces())
    val entries = InMemoryStore(seedEntries(nowEpochSeconds))

    fun namespaceKey(accountId: String, namespaceId: String) = accountId + KV_DEMO_SEP + namespaceId

    fun entryKey(accountId: String, namespaceId: String, keyName: String) =
        keyPrefix(accountId, namespaceId) + keyName

    fun keyPrefix(accountId: String, namespaceId: String) = accountId + KV_DEMO_SEP + namespaceId + KV_DEMO_SEP
}

private val DEMO_ACCOUNTS = listOf(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_ACCOUNT_STAGING)

private fun seedNamespaces(): Map<String, KvDemoNamespace> = buildMap {
    DEMO_ACCOUNTS.forEach { account ->
        listOf(
            KvDemoNamespace(KV_DEMO_NS_SESSIONS, "Session cache"),
            KvDemoNamespace(KV_DEMO_NS_FLAGS, "Feature flags"),
            KvDemoNamespace(KV_DEMO_NS_ASSETS, "Asset manifest"),
        ).forEach { ns -> put(account + KV_DEMO_SEP + ns.id, ns) }
    }
}

private fun seedEntries(now: Long): Map<String, KvDemoEntry> = buildMap {
    DEMO_ACCOUNTS.forEach { account ->
        val flagsPrefix = account + KV_DEMO_SEP + KV_DEMO_NS_FLAGS + KV_DEMO_SEP
        put(flagsPrefix + "checkout/new-flow", KvDemoEntry(jsonBytes(flagJson(enabled = true, rollout = 25)), null, null))
        put(flagsPrefix + "banner text", KvDemoEntry("Scheduled maintenance on Sunday".encodeToByteArray(), null, null))
        put(
            flagsPrefix + "users/42/profile?draft=1",
            KvDemoEntry(jsonBytes(profileJson()), buildJsonObject { put("owner", "demo-team") }, null),
        )
        put(flagsPrefix + "a b+c", KvDemoEntry("spaces and a plus sign".encodeToByteArray(), null, null))
        val sessionsPrefix = account + KV_DEMO_SEP + KV_DEMO_NS_SESSIONS + KV_DEMO_SEP
        put(
            sessionsPrefix + "session:7f3a0c",
            KvDemoEntry(
                jsonBytes(sessionJson()),
                buildJsonObject { put("user", "demo-user-17") },
                now + SESSION_TTL_SECONDS,
            ),
        )
        val assetsPrefix = account + KV_DEMO_SEP + KV_DEMO_NS_ASSETS + KV_DEMO_SEP
        put(assetsPrefix + "blobs/thumb.bin", KvDemoEntry(BINARY_SAMPLE, null, null))
    }
}

private fun flagJson(enabled: Boolean, rollout: Int): JsonObject = buildJsonObject {
    put("enabled", enabled)
    put("rollout", rollout)
}

private fun profileJson(): JsonObject = buildJsonObject {
    put("name", "Demo Person")
    put("plan", "trial")
    putJsonObject("preferences") {
        put("theme", "dark")
        put("digest", false)
    }
}

private fun sessionJson(): JsonObject = buildJsonObject {
    put("user", JsonPrimitive("demo-user-17"))
    put("issued", "2026-11-11T08:00:00Z")
}

private fun jsonBytes(element: JsonElement): ByteArray = element.toString().encodeToByteArray()

/** Not valid UTF-8 (0x89 and 0xFF cannot start a sequence), so the value shows as a hex preview. */
private val BINARY_SAMPLE: ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0xFF.toByte(), 0x00, 0x7F, 0x01,
)
