package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuildDetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun detailVm(
        fake: FakeWearBuilds,
        build: BuildSummary,
        session: WatchSessionState = CONNECTED,
        watcher: FakeWatchStarter = FakeWatchStarter(),
    ) = detailVm(fake, build, MutableStateFlow(session), watcher)

    private fun detailVm(
        fake: FakeWearBuilds,
        build: BuildSummary,
        session: MutableStateFlow<WatchSessionState>,
        watcher: FakeWatchStarter = FakeWatchStarter(),
    ) = main.keep(BuildDetailViewModel::class.java) { BuildDetailViewModel(session, fake, watcher, build) }

    @Test
    fun runningBuildOffersCancelNotRetry() = main.runVmTest {
        val fake = FakeWearBuilds(plan = ApiResult.Success(PLAN))
        val vm = detailVm(fake, RUNNING)
        advanceUntilIdle()
        assertTrue(vm.state.value.canCancel)
        assertNull(vm.state.value.retryPlan)
        assertFalse(fake.calls.any { it.startsWith("retryPlan") })
    }

    @Test
    fun finishedBuildOffersRetryWhenPlanExists() = main.runVmTest {
        val vm = detailVm(FakeWearBuilds(plan = ApiResult.Success(PLAN)), build())
        advanceUntilIdle()
        assertFalse(vm.state.value.canCancel)
        assertEquals(PLAN, vm.state.value.retryPlan)
    }

    @Test
    fun finishedBuildWithoutPlanHidesRetry() = main.runVmTest {
        val vm = detailVm(FakeWearBuilds(plan = ApiResult.Success(null)), build())
        advanceUntilIdle()
        assertNull(vm.state.value.retryPlan)
        assertFalse(vm.state.value.canCancel)
    }

    @Test
    fun cancelRequiresConfirmBeforeCalling() = main.runVmTest {
        val fake = FakeWearBuilds()
        val vm = detailVm(fake, RUNNING)
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(ActionState.Confirming(ActionKind.Cancel), vm.state.value.action)
        assertFalse(fake.calls.any { it.startsWith("cancel") })

        vm.dismiss()
        assertEquals(ActionState.Idle, vm.state.value.action)
        assertFalse(fake.calls.any { it.startsWith("cancel") })

        vm.cancel()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, fake.calls.count { it == "cancel:b-1" })
        assertEquals(ActionState.Done, vm.state.value.action)
    }

    @Test
    fun retryRequiresConfirmThenStartsBuild() = main.runVmTest {
        val fake = FakeWearBuilds(plan = ApiResult.Success(PLAN))
        val vm = detailVm(fake, build())
        advanceUntilIdle()
        vm.retry()
        advanceUntilIdle()
        assertEquals(ActionState.Confirming(ActionKind.Retry), vm.state.value.action)
        assertFalse(fake.calls.any { it.startsWith("retry:") })
        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, fake.calls.count { it == "retry:b-1" })
        assertEquals(ActionState.Done, vm.state.value.action)
    }

    @Test
    fun failedActionShowsShortPhraseAndCanBeDismissed() = main.runVmTest {
        val fake = FakeWearBuilds(cancel = failure(ApiError.Network("down")))
        val vm = detailVm(fake, RUNNING)
        advanceUntilIdle()
        vm.cancel()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(ActionState.Failed(ErrorKind.Offline), vm.state.value.action)
        vm.dismiss()
        assertEquals(ActionState.Idle, vm.state.value.action)
    }

    @Test
    fun cancelIsIgnoredWhenBuildCannotBeCancelled() = main.runVmTest {
        val vm = detailVm(FakeWearBuilds(), build())
        advanceUntilIdle()
        vm.cancel()
        assertEquals(ActionState.Idle, vm.state.value.action)
    }

    @Test
    fun confirmWithoutAPendingActionDoesNothing() = main.runVmTest {
        val fake = FakeWearBuilds()
        val vm = detailVm(fake, RUNNING)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertFalse(fake.calls.any { it.startsWith("cancel") || it.startsWith("retry:") })
    }

    @Test
    fun staleSessionNeverCallsApi() = main.runVmTest {
        val fake = FakeWearBuilds()
        val vm = detailVm(fake, RUNNING, WatchSessionState.Stale)
        advanceUntilIdle()
        vm.cancel()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun refreshedBuildReplacesTheListedOne() = main.runVmTest {
        val finished = RUNNING.copy(status = BuildStatus.STOPPED)
        val vm = detailVm(FakeWearBuilds(detail = ApiResult.Success(finished)), RUNNING)
        advanceUntilIdle()
        assertEquals(BuildStatus.STOPPED, vm.state.value.build.status)
        assertFalse(vm.state.value.canCancel)
    }

    @Test
    fun confirmAfterAccountSwitchRefusesAndResetsToIdle() = main.runVmTest {
        val fake = FakeWearBuilds()
        val session = MutableStateFlow<WatchSessionState>(CONNECTED)
        val vm = detailVm(fake, RUNNING, session)
        advanceUntilIdle()
        vm.cancel()
        assertFalse(vm.isOrphaned.value)

        session.value = WatchSessionState.Connected("acc-2", isDemo = false)
        advanceUntilIdle()
        assertTrue(vm.isOrphaned.value)
        vm.confirm()
        advanceUntilIdle()

        assertEquals(ActionState.Idle, vm.state.value.action)
        assertFalse(fake.calls.any { it.startsWith("cancel") })
    }

    @Test
    fun confirmWhenSessionIsNoLongerConnectedResetsToIdle() = main.runVmTest {
        val fake = FakeWearBuilds()
        val session = MutableStateFlow<WatchSessionState>(CONNECTED)
        val vm = detailVm(fake, RUNNING, session)
        advanceUntilIdle()
        vm.cancel()
        session.value = WatchSessionState.Stale
        vm.confirm()
        advanceUntilIdle()
        assertEquals(ActionState.Idle, vm.state.value.action)
        assertFalse(fake.calls.any { it.startsWith("cancel") })
    }

    @Test
    fun sameAccountReconnectDoesNotOrphanTheScreen() = main.runVmTest {
        val session = MutableStateFlow<WatchSessionState>(CONNECTED)
        val vm = detailVm(FakeWearBuilds(), RUNNING, session)
        advanceUntilIdle()
        session.value = WatchSessionState.Connected(ACCOUNT, isDemo = true)
        advanceUntilIdle()
        assertFalse(vm.isOrphaned.value)
    }

    @Test
    fun transientRestoringDoesNotOrphanButADifferentAccountDoes() = main.runVmTest {
        val session = MutableStateFlow<WatchSessionState>(CONNECTED)
        val vm = detailVm(FakeWearBuilds(), RUNNING, session)
        advanceUntilIdle()
        session.value = WatchSessionState.Restoring
        advanceUntilIdle()
        assertFalse(vm.isOrphaned.value)
        session.value = CONNECTED
        advanceUntilIdle()
        assertFalse(vm.isOrphaned.value)
        session.value = WatchSessionState.Restoring
        session.value = WatchSessionState.Connected("acc-2", isDemo = false)
        advanceUntilIdle()
        assertTrue(vm.isOrphaned.value)
    }

    @Test
    fun staleOrNotConnectedOrphans() = main.runVmTest {
        val session = MutableStateFlow<WatchSessionState>(CONNECTED)
        val vm = detailVm(FakeWearBuilds(), RUNNING, session)
        advanceUntilIdle()
        session.value = WatchSessionState.Stale
        advanceUntilIdle()
        assertTrue(vm.isOrphaned.value)
    }

    @Test
    fun runningBuildCanBeWatchedAndStopped() = main.runVmTest {
        val watcher = FakeWatchStarter()
        val vm = detailVm(FakeWearBuilds(), RUNNING, watcher = watcher)
        advanceUntilIdle()
        assertTrue(vm.state.value.canWatch)
        assertFalse(vm.state.value.isWatching)

        vm.watch()
        advanceUntilIdle()
        assertEquals(listOf("start:$ACCOUNT:api/b-1"), watcher.calls)
        assertTrue(vm.state.value.isWatching)

        vm.stopWatching()
        advanceUntilIdle()
        assertEquals("stop:api/b-1", watcher.calls.last())
        assertFalse(vm.state.value.isWatching)
    }

    @Test
    fun watchingTwiceDoesNotStartTwice() = main.runVmTest {
        val watcher = FakeWatchStarter()
        val vm = detailVm(FakeWearBuilds(), RUNNING, watcher = watcher)
        advanceUntilIdle()
        vm.watch()
        advanceUntilIdle()
        vm.watch()
        advanceUntilIdle()
        assertEquals(1, watcher.calls.size)
    }

    @Test
    fun finishedBuildCannotBeWatched() = main.runVmTest {
        val watcher = FakeWatchStarter()
        val vm = detailVm(FakeWearBuilds(), build(), watcher = watcher)
        advanceUntilIdle()
        assertFalse(vm.state.value.canWatch)
        vm.watch()
        assertTrue(watcher.calls.isEmpty())
    }

    @Test
    fun watchIsRefusedWhenTheSessionIsNotConnected() = main.runVmTest {
        val watcher = FakeWatchStarter()
        val vm = detailVm(FakeWearBuilds(), RUNNING, WatchSessionState.Stale, watcher)
        advanceUntilIdle()
        vm.watch()
        assertTrue(watcher.calls.isEmpty())
    }

    @Test
    fun stateFollowsTheWatchEndingElsewhere() = main.runVmTest {
        val watcher = FakeWatchStarter()
        val vm = detailVm(FakeWearBuilds(), RUNNING, watcher = watcher)
        advanceUntilIdle()
        vm.watch()
        advanceUntilIdle()
        assertTrue(vm.state.value.isWatching)

        watcher.keys.value = emptySet()
        advanceUntilIdle()
        assertFalse(vm.state.value.isWatching)
    }

    @Test
    fun refusedStartIsReportedAndClearedByTheNextTry() = main.runVmTest {
        val watcher = FakeWatchStarter().apply { refuseStart = true }
        val vm = detailVm(FakeWearBuilds(), RUNNING, watcher = watcher)
        advanceUntilIdle()
        vm.watch()
        advanceUntilIdle()
        assertTrue(vm.state.value.watchFailed)
        assertFalse(vm.state.value.isWatching)

        watcher.refuseStart = false
        vm.watch()
        advanceUntilIdle()
        assertFalse(vm.state.value.watchFailed)
        assertTrue(vm.state.value.isWatching)
    }
}
