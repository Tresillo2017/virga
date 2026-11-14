package com.tomasps.virga.wear.builds

import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.feature.builds.api.TriggerRef
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun build(branch: String? = null, commit: String? = null) = BuildSummary(
    buildUuid = "b-1",
    scriptName = "api",
    status = BuildStatus.STOPPED,
    branch = branch,
    commitHash = commit,
    createdOn = Instant.parse("2026-11-11T10:00:00Z"),
)

private fun trigger(uuid: String, vararg branches: String) = TriggerRef(uuid, null, branches.toList())

class RetryPlannerTest {

    @Test
    fun `prefers commit over branch`() {
        val plan = RetryPlanner.plan(build(branch = "main", commit = "abc"), listOf(trigger("t-1", "main")))

        assertEquals(RetryPlan("t-1", BuildTarget.Commit("abc")), plan)
    }

    @Test
    fun `branch only uses branch`() {
        val plan = RetryPlanner.plan(build(branch = "main"), listOf(trigger("t-1", "main")))

        assertEquals(RetryPlan("t-1", BuildTarget.Branch("main")), plan)
    }

    @Test
    fun `no branch no commit is null`() {
        assertNull(RetryPlanner.plan(build(), listOf(trigger("t-1", "main"))))
    }

    @Test
    fun `single trigger is used`() {
        val plan = RetryPlanner.plan(build(branch = "dev"), listOf(trigger("t-1", "main")))

        assertEquals("t-1", plan?.triggerUuid)
    }

    @Test
    fun `multiple triggers pick branch match`() {
        val plan = RetryPlanner.plan(
            build(branch = "dev"),
            listOf(trigger("t-1", "main"), trigger("t-2", "dev"), trigger("t-3", "dev")),
        )

        assertEquals("t-2", plan?.triggerUuid)
    }

    @Test
    fun `multiple triggers no match is null`() {
        val plan = RetryPlanner.plan(
            build(branch = "dev"),
            listOf(trigger("t-1", "main"), trigger("t-2")),
        )

        assertNull(plan)
    }

    @Test
    fun `no triggers is null`() {
        assertNull(RetryPlanner.plan(build(branch = "main"), emptyList()))
    }
}
