package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.workers.data.WorkersRepository
import com.tomasps.virga.feature.workers.testing.FakeSessionRepository
import com.tomasps.virga.feature.workers.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private const val WAIT_MS = 2_000L
private const val LATEST_UUID = "build-latest"

/** Serves one active build and a watched-id set the test controls. Records the builds it was asked to stop. */
private class ScriptedBuildsGateway(private val latest: BuildSummary, watched: Set<String>) : BuildsGateway {
    val watchedIds = MutableStateFlow(watched)
    val stopped = mutableListOf<String>()

    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(listOf(latest))

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> = ApiResult.Success(emptyList())
    override fun watchedBuildIds(): Flow<Set<String>> = watchedIds
    override fun startWatching(accountId: String, build: BuildSummary) = Unit
    override fun stopWatching(buildUuid: String) {
        stopped += buildUuid
    }
}

class WorkerDetailWatchStateTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val runningBuild = BuildSummary(
        buildUuid = LATEST_UUID,
        scriptName = "edge-api",
        status = BuildStatus.RUNNING,
        createdOn = Instant.parse("2026-11-11T10:00:00Z"),
    )

    private suspend fun viewModelWith(gateway: BuildsGateway): WorkerDetailViewModel {
        val session = FakeSessionRepository()
        val viewModel = WorkerDetailViewModel(session, WorkersRepository(mockCloudflareApi { }), gateway, "edge-api")
        session.signedInAs("acc-1")
        withTimeout(WAIT_MS) { viewModel.state.first { it.latestBuild != null } }
        return viewModel
    }

    @Test
    fun `state reports the latest build as watched when the gateway pins it`() = runBlocking {
        // Arrange
        val gateway = ScriptedBuildsGateway(runningBuild, watched = setOf(LATEST_UUID))

        // Act
        val viewModel = viewModelWith(gateway)

        // Assert
        assertTrue(LATEST_UUID in viewModel.state.value.watchedBuildIds)
    }

    @Test
    fun `state drops the build once the gateway stops watching it`() = runBlocking {
        // Arrange
        val gateway = ScriptedBuildsGateway(runningBuild, watched = setOf(LATEST_UUID))
        val viewModel = viewModelWith(gateway)

        // Act
        gateway.watchedIds.value = emptySet()
        withTimeout(WAIT_MS) { viewModel.state.first { LATEST_UUID !in it.watchedBuildIds } }

        // Assert
        assertEquals(emptySet<String>(), viewModel.state.value.watchedBuildIds)
    }

    @Test
    fun `stop watching targets the latest build`() = runBlocking {
        // Arrange
        val gateway = ScriptedBuildsGateway(runningBuild, watched = setOf(LATEST_UUID))
        val viewModel = viewModelWith(gateway)

        // Act
        viewModel.stopWatchingLatestBuild()

        // Assert
        assertEquals(listOf(LATEST_UUID), gateway.stopped)
    }
}
