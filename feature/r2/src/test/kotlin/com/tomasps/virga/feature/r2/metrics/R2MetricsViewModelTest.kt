package com.tomasps.virga.feature.r2.metrics

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.r2.api.R2MetricsRepository
import com.tomasps.virga.feature.r2.demo.R2_BUCKET_METRICS_OPERATION
import com.tomasps.virga.feature.r2.demo.R2DemoRoutes
import com.tomasps.virga.feature.r2.demo.r2BucketMetricsReply
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val BUCKET = "demo-assets"

class R2MetricsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `the metrics tab loads the bucket on the default 24 hour window`() = runBlocking {
        // Arrange
        val viewModel = R2MetricsViewModel(
            FakeSessionRepository(),
            R2MetricsRepository(mockCloudflareApi { R2DemoRoutes().contribute(this) }),
            BUCKET,
        )

        // Act
        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.metrics is UiState.Content } }

        // Assert
        assertEquals(TimeRange.TWENTY_FOUR_HOURS, loaded.range)
        assertTrue((loaded.metrics as UiState.Content).data.operations.isNotEmpty())
    }

    @Test
    fun `changing the range asks the API again for the new window`() = runBlocking {
        // Arrange
        var requests = 0
        val api = mockCloudflareApi {
            graphQl(R2_BUCKET_METRICS_OPERATION) { request ->
                requests++
                r2BucketMetricsReply(request)
            }
        }
        val viewModel = R2MetricsViewModel(FakeSessionRepository(), R2MetricsRepository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.metrics is UiState.Content } }

        // Act
        viewModel.selectRange(TimeRange.SEVEN_DAYS)
        val reloaded = withTimeout(WAIT_MS) {
            viewModel.state.first { it.range == TimeRange.SEVEN_DAYS && it.metrics is UiState.Content }
        }

        // Assert
        assertEquals(2, requests)
        assertEquals(7, (reloaded.metrics as UiState.Content).data.storage.size)
    }

    @Test
    fun `a failed read shows a failure state and retry recovers`() = runBlocking {
        // Arrange
        var failing = true
        val api = mockCloudflareApi {
            graphQl(R2_BUCKET_METRICS_OPERATION) { request ->
                if (failing) DemoReply.graphQlErrors("down") else r2BucketMetricsReply(request)
            }
        }
        val viewModel = R2MetricsViewModel(FakeSessionRepository(), R2MetricsRepository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.metrics is UiState.Failure } }

        // Act
        failing = false
        viewModel.retry()
        val recovered = withTimeout(WAIT_MS) { viewModel.state.first { it.metrics is UiState.Content } }

        // Assert
        assertTrue(recovered.metrics is UiState.Content)
    }

    @Test
    fun `with no account selected there is no metrics state at all`() = runBlocking {
        val viewModel = R2MetricsViewModel(
            FakeSessionRepository(accountId = null),
            R2MetricsRepository(mockCloudflareApi { R2DemoRoutes().contribute(this) }),
            BUCKET,
        )

        assertNull(viewModel.state.value.metrics)
    }
}
