package com.tomasps.virga.feature.kv.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.kv.data.KvDailyRequests
import com.tomasps.virga.feature.kv.data.KvDailyStorage
import com.tomasps.virga.feature.kv.data.KvMetrics
import com.tomasps.virga.feature.kv.data.KvMetricsSource
import java.time.LocalDate

private val DEMO_ACTIONS = listOf("read", "write", "delete", "list")
private const val BASE_KEY_COUNT = 40L
private const val KEYS_PER_DAY = 3L
private const val BYTES_PER_KEY = 2_048L
private const val REQUEST_BASE = 120L
private const val REQUEST_STEP = 17L
private const val ACTION_STEP = 9L

/**
 * Canned analytics for the demo account. Numbers are deterministic for a given date range, so the chart looks the same
 * each time the screen opens. The live source reads GraphQL; this one stands in for it when demo mode is on.
 */
class DemoKvMetricsSource : KvMetricsSource {

    override suspend fun load(
        accountId: String,
        namespaceId: String,
        start: LocalDate,
        end: LocalDate,
    ): ApiResult<KvMetrics> {
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        val operations = days.flatMapIndexed { index, day ->
            DEMO_ACTIONS.mapIndexed { actionIndex, action ->
                val requests = REQUEST_BASE + (index * REQUEST_STEP) % REQUEST_BASE + actionIndex * ACTION_STEP
                KvDailyRequests(date = day.toString(), action = action, requests = requests)
            }
        }
        val storage = days.mapIndexed { index, day ->
            val keys = BASE_KEY_COUNT + index * KEYS_PER_DAY
            KvDailyStorage(date = day.toString(), keyCount = keys, byteCount = keys * BYTES_PER_KEY)
        }
        return ApiResult.Success(KvMetrics(operations = operations, storage = storage))
    }
}
