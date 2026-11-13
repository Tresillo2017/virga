package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.feature.builds.testing.TEST_ACCOUNT
import com.tomasps.virga.feature.builds.testing.TEST_SCRIPT
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPostPolicyTest {

    private fun watch(uuid: String) = WatchedBuild(TEST_ACCOUNT, TEST_SCRIPT, uuid, startedAtMillis = 0L)

    @Test
    fun `the first summary is always posted`() {
        assertTrue(SummaryPostPolicy().shouldPost(listOf(watch("a")), nowMillis = 0L))
    }

    @Test
    fun `an unchanged summary is not reposted every tick`() {
        val policy = SummaryPostPolicy(refreshMs = 15_000L)
        policy.shouldPost(listOf(watch("a")), nowMillis = 0L)

        assertFalse(policy.shouldPost(listOf(watch("a")), nowMillis = 1_000L))
        assertFalse(policy.shouldPost(listOf(watch("a")), nowMillis = 14_000L))
    }

    @Test
    fun `an unchanged summary is refreshed once the refresh interval has passed`() {
        val policy = SummaryPostPolicy(refreshMs = 15_000L)
        policy.shouldPost(listOf(watch("a")), nowMillis = 0L)

        assertTrue(policy.shouldPost(listOf(watch("a")), nowMillis = 15_000L))
    }

    @Test
    fun `a change in the watched set or a stage is posted at once`() {
        val policy = SummaryPostPolicy(refreshMs = 15_000L)
        policy.shouldPost(listOf(watch("a")), nowMillis = 0L)

        assertTrue(policy.shouldPost(listOf(watch("a"), watch("b")), nowMillis = 1_000L))
    }
}
