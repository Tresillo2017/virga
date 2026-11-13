package com.tomasps.virga.feature.kv.metrics

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.kv.data.KvMetrics
import com.tomasps.virga.feature.kv.data.KvMetricsSource
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_SESSIONS
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.awaitTrue
import com.tomasps.virga.feature.kv.testing.signedInSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val TODAY: LocalDate = LocalDate.of(2026, 11, 11)
private const val WINDOW_DAYS = 30L

class KvMetricsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class RecordingSource : KvMetricsSource {
        var start: LocalDate? = null
        var end: LocalDate? = null

        override suspend fun load(accountId: String, namespaceId: String, start: LocalDate, end: LocalDate): ApiResult<KvMetrics> {
            this.start = start
            this.end = end
            return ApiResult.Success(KvMetrics(operations = emptyList(), storage = emptyList()))
        }
    }

    @Test
    fun `metrics cover the 30 days that end today`() = runBlocking {
        // Arrange
        val source = RecordingSource()
        val vm = KvMetricsViewModel(
            namespaceId = KV_DEMO_NS_SESSIONS,
            session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
            source = source,
            zone = ZoneId.of("UTC"),
            today = { TODAY },
        )

        // Act
        awaitTrue { vm.state.value.metrics is UiState.Content }

        // Assert
        assertEquals(TODAY, source.end)
        assertEquals(TODAY.minusDays(WINDOW_DAYS - 1), source.start)
    }
}
