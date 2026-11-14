package com.tomasps.virga.wear.surfaces

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant

/** The newest build the surfaces last showed, the account it belongs to and when it was fetched. */
data class CachedBuild(val accountId: String, val isDemo: Boolean, val build: BuildSummary, val fetchedAt: Instant)

/**
 * Keeps the last good build so a tile or complication can still show something when the network fails. Holds only
 * what the surfaces display plus the account id, never a token, commit text or author. A value that cannot be
 * read back reads as no cache.
 */
class LatestBuildCache(private val store: DataStore<Preferences>) {

    suspend fun save(accountId: String, isDemo: Boolean, build: BuildSummary, fetchedAt: Instant) {
        store.edit { prefs ->
            prefs.clear()
            prefs[ACCOUNT] = accountId
            prefs[IS_DEMO] = isDemo
            prefs[SCRIPT] = build.scriptName
            build.workerTag?.let { prefs[WORKER_TAG] = it }
            prefs[BUILD_UUID] = build.buildUuid
            prefs[STATUS] = build.status.name
            build.outcome?.let { prefs[OUTCOME] = it.name }
            build.branch?.let { prefs[BRANCH] = it }
            prefs[CREATED_ON] = build.createdOn.toEpochMilli()
            build.startedOn?.let { prefs[STARTED_ON] = it.toEpochMilli() }
            build.stoppedOn?.let { prefs[STOPPED_ON] = it.toEpochMilli() }
            prefs[FETCHED_AT] = fetchedAt.toEpochMilli()
        }
    }

    suspend fun load(): CachedBuild? = try {
        read(store.data.first())
    } catch (error: CancellationException) {
        throw error
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        null
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun read(prefs: Preferences): CachedBuild? {
        val accountId = prefs[ACCOUNT] ?: return null
        val isDemo = prefs[IS_DEMO] ?: return null
        val build = BuildSummary(
            buildUuid = prefs[BUILD_UUID] ?: return null,
            scriptName = prefs[SCRIPT] ?: return null,
            workerTag = prefs[WORKER_TAG],
            status = BuildStatus.valueOf(prefs[STATUS] ?: return null),
            outcome = prefs[OUTCOME]?.let { BuildOutcome.valueOf(it) },
            branch = prefs[BRANCH],
            createdOn = Instant.ofEpochMilli(prefs[CREATED_ON] ?: return null),
            startedOn = prefs[STARTED_ON]?.let(Instant::ofEpochMilli),
            stoppedOn = prefs[STOPPED_ON]?.let(Instant::ofEpochMilli),
        )
        return CachedBuild(accountId, isDemo, build, Instant.ofEpochMilli(prefs[FETCHED_AT] ?: return null))
    }

    private companion object {
        val ACCOUNT = stringPreferencesKey("account_id")
        val IS_DEMO = booleanPreferencesKey("is_demo")
        val SCRIPT = stringPreferencesKey("script_name")
        val WORKER_TAG = stringPreferencesKey("worker_tag")
        val BUILD_UUID = stringPreferencesKey("build_uuid")
        val STATUS = stringPreferencesKey("status")
        val OUTCOME = stringPreferencesKey("outcome")
        val BRANCH = stringPreferencesKey("branch")
        val CREATED_ON = longPreferencesKey("created_on")
        val STARTED_ON = longPreferencesKey("started_on")
        val STOPPED_ON = longPreferencesKey("stopped_on")
        val FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
