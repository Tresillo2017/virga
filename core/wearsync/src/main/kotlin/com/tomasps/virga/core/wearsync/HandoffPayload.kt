package com.tomasps.virga.core.wearsync

import kotlinx.serialization.Serializable

@Serializable
data class HandoffAccount(val id: String, val name: String)

@Serializable
data class HandoffPayload(
    val version: Int,
    val token: String?,
    val isDemo: Boolean,
    val accounts: List<HandoffAccount>,
    val selectedAccountId: String?,
    val sentAtMillis: Long,
) {
    // The token is a credential and must never reach logs.
    override fun toString(): String =
        "HandoffPayload(version=$version, token=${if (token == null) "null" else "<redacted>"}, " +
            "isDemo=$isDemo, accounts=$accounts, selectedAccountId=$selectedAccountId, " +
            "sentAtMillis=$sentAtMillis)"

    companion object {
        const val CURRENT_VERSION = 1
    }
}
