package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.wear.builds.WearBuildsSource
import com.tomasps.virga.wear.session.FakeSentCredentialGuard
import com.tomasps.virga.wear.session.WatchSessionState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuildListViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun listVm(session: MutableStateFlow<WatchSessionState>, builds: com.tomasps.virga.wear.builds.WearBuilds) =
        main.keep(BuildListViewModel::class.java) { BuildListViewModel(session, builds) {} }

    @Test
    fun listShowsLoadingThenBuilds() = main.runVmTest {
        val gate = CompletableDeferred<Unit>()
        val builds = object : com.tomasps.virga.wear.builds.WearBuilds by FakeWearBuilds() {
            override suspend fun recent(accountId: String): ApiResult<List<com.tomasps.virga.core.common.builds.BuildSummary>> {
                gate.await()
                return ApiResult.Success(listOf(RUNNING))
            }
        }
        val vm = listVm(MutableStateFlow(CONNECTED), builds)
        advanceUntilIdle()
        assertEquals(ListState.Loading, vm.state.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ListState.Content(listOf(RUNNING)), vm.state.value)
    }

    @Test
    fun emptyAccountShowsEmptyState() = main.runVmTest {
        val vm = listVm(MutableStateFlow(CONNECTED), FakeWearBuilds(recent = ApiResult.Success(emptyList())))
        advanceUntilIdle()
        assertEquals(ListState.Empty, vm.state.value)
    }

    @Test
    fun offlineShowsRetryableError() = main.runVmTest {
        val fake = FakeWearBuilds(recent = failure(ApiError.Network("down")))
        val vm = listVm(MutableStateFlow(CONNECTED), fake)
        advanceUntilIdle()
        assertEquals(ListState.Error(ErrorKind.Offline), vm.state.value)

        fake.recent = ApiResult.Success(listOf(RUNNING))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(ListState.Content(listOf(RUNNING)), vm.state.value)
    }

    @Test
    fun rateLimitedAndRejectedUseTheirOwnPhrases() = main.runVmTest {
        val fake = FakeWearBuilds(recent = failure(ApiError.RateLimited(1_000)))
        val vm = listVm(MutableStateFlow(CONNECTED), fake)
        advanceUntilIdle()
        assertEquals(ListState.Error(ErrorKind.RateLimited), vm.state.value)

        fake.recent = failure(ApiError.Forbidden())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(ListState.Error(ErrorKind.TokenRejected), vm.state.value)
    }

    @Test
    fun unauthorizedCallsSessionOnUnauthorized() = main.runVmTest {
        var wipes = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                DemoReply.failure(HttpStatusCode.Unauthorized, 10000, "bad token")
            }
        }
        val source = WearBuildsSource(
            repository = BuildsRepository(api),
            credentials = FakeSentCredentialGuard { wipes++ },
        )
        val vm = listVm(MutableStateFlow(CONNECTED), source)
        advanceUntilIdle()
        assertEquals(1, wipes)
        assertEquals(ListState.Error(ErrorKind.TokenRejected), vm.state.value)
    }

    @Test
    fun notConnectedShowsConnectScreen() = main.runVmTest {
        val fake = FakeWearBuilds()
        val session = MutableStateFlow<WatchSessionState>(WatchSessionState.NotConnected)
        val vm = listVm(session, fake)
        advanceUntilIdle()
        assertEquals(ListState.NotConnected, vm.state.value)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun staleShowsReconnectAndNeverCallsApi() = main.runVmTest {
        val fake = FakeWearBuilds()
        val session = MutableStateFlow<WatchSessionState>(WatchSessionState.Stale)
        val vm = listVm(session, fake)
        advanceUntilIdle()
        assertEquals(ListState.Stale, vm.state.value)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun sessionChangeReloadsForTheNewAccount() = main.runVmTest {
        val fake = FakeWearBuilds(recent = ApiResult.Success(listOf(RUNNING)))
        val session = MutableStateFlow<WatchSessionState>(WatchSessionState.Restoring)
        val vm = listVm(session, fake)
        advanceUntilIdle()
        assertEquals(ListState.Loading, vm.state.value)
        session.value = WatchSessionState.Connected("acc-2", isDemo = false)
        advanceUntilIdle()
        assertEquals(listOf("recent:acc-2"), fake.calls)
        session.value = WatchSessionState.NotConnected
        advanceUntilIdle()
        assertEquals(ListState.NotConnected, vm.state.value)
    }

    @Test
    fun serverErrorShowsSomethingWentWrongNeverBlank() = main.runVmTest {
        val vm = listVm(MutableStateFlow(CONNECTED), FakeWearBuilds(recent = failure(ApiError.Server(503))))
        advanceUntilIdle()
        assertEquals(ListState.Error(ErrorKind.SomethingWrong), vm.state.value)
    }
}
