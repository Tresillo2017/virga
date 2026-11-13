package com.tomasps.virga.feature.d1.detail.timetravel

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.data.TimeTravelRepository
import com.tomasps.virga.feature.d1.testing.FakeSessionRepository
import com.tomasps.virga.feature.d1.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val DB = "/accounts/{accountId}/d1/database/{databaseId}"
private const val WAIT_MS = 5_000L

class TimeTravelLookupTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `editing the timestamp drops the bookmark that was looked up for the old one`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get("$DB/time_travel/bookmark") { DemoReply.ok(buildJsonObject { put("bookmark", "bm-now") }) }
            get(DB) { DemoReply.ok(buildJsonObject { put("uuid", "db-1"); put("name", "orders") }) }
        }
        val viewModel = TimeTravelViewModel(
            session = FakeSessionRepository(
                SessionState.SignedIn(listOf(CloudflareAccount(id = "acc-1", name = "acc-1")), selectedAccountId = "acc-1", isDemo = false),
            ),
            databases = DatabasesRepository(api),
            timeTravel = TimeTravelRepository(api),
            databaseId = "db-1",
        )
        viewModel.onLookupChanged("2026-11-10T08:00:00Z")
        viewModel.lookUp()
        withTimeout(WAIT_MS) { viewModel.state.first { it.lookupBookmark != null } }

        viewModel.onLookupChanged("2026-11-10T09:00:00Z")

        assertNull(viewModel.state.value.lookupBookmark)
        assertEquals("2026-11-10T09:00:00Z", viewModel.state.value.lookupInput)
    }
}
