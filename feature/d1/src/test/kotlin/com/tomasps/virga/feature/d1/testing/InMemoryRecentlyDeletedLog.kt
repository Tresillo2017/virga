package com.tomasps.virga.feature.d1.testing

import com.tomasps.virga.feature.d1.list.RecentlyDeletedDatabase
import com.tomasps.virga.feature.d1.list.RecentlyDeletedLog
import com.tomasps.virga.feature.d1.list.RecentlyDeletedRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Keeps recently deleted entries in memory, per account, with the same rules as the DataStore log. */
class InMemoryRecentlyDeletedLog(initial: Map<String, List<RecentlyDeletedDatabase>> = emptyMap()) : RecentlyDeletedLog {

    private val entries = MutableStateFlow(initial)

    override fun observe(accountId: String): Flow<List<RecentlyDeletedDatabase>> = entries.map { it[accountId].orEmpty() }

    override suspend fun remember(accountId: String, entry: RecentlyDeletedDatabase, nowMillis: Long) {
        entries.update { map -> map + (accountId to RecentlyDeletedRules.added(map[accountId].orEmpty(), entry, nowMillis)) }
    }

    override suspend fun forget(accountId: String, uuid: String) {
        entries.update { map -> map + (accountId to RecentlyDeletedRules.without(map[accountId].orEmpty(), uuid)) }
    }

    fun current(accountId: String): List<RecentlyDeletedDatabase> = entries.value[accountId].orEmpty()
}
