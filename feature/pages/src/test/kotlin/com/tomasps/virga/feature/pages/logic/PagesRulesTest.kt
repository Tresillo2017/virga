package com.tomasps.virga.feature.pages.logic

import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.data.PagesProject
import com.tomasps.virga.feature.pages.data.PagesStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagesRulesTest {

    private fun stage(name: String, status: String) = PagesStage(name = name, status = status)

    private fun deployment(
        id: String,
        environment: String = PagesRules.ENVIRONMENT_PRODUCTION,
        stages: List<PagesStage>,
    ) = PagesDeployment(id = id, environment = environment, stages = stages, latestStage = stages.lastOrNull())

    private val finished = listOf(
        stage("queued", "success"), stage("initialize", "success"), stage("clone_repo", "success"),
        stage("build", "success"), stage("deploy", "success"),
    )

    @Test
    fun `stage views always cover the five stages in pipeline order, filling missing ones as idle`() {
        // Arrange
        val running = deployment("d1", stages = listOf(stage("queued", "success"), stage("build", "active")))

        // Act
        val views = running.stageViews()

        // Assert
        assertEquals(listOf("queued", "initialize", "clone_repo", "build", "deploy"), views.map { it.name })
        assertEquals(listOf("success", "idle", "idle", "active", "idle"), views.map { it.status })
    }

    @Test
    fun `a deployment whose deploy stage succeeded is not in progress`() {
        assertFalse(deployment("d1", stages = finished).isInProgress())
    }

    @Test
    fun `a deployment still building is in progress`() {
        val building = deployment("d1", stages = listOf(stage("queued", "success"), stage("build", "active")))

        assertTrue(building.isInProgress())
    }

    @Test
    fun `a failed or canceled deployment is terminal, not in progress`() {
        val failed = deployment("d1", stages = listOf(stage("build", "failure")))
        val canceled = deployment("d2", stages = listOf(stage("build", "canceled")))

        assertFalse(failed.isInProgress())
        assertFalse(canceled.isInProgress())
    }

    @Test
    fun `retry is offered only for a failed deployment`() {
        assertTrue(deployment("d1", stages = listOf(stage("build", "failure"))).canRetry())
        assertFalse(deployment("d2", stages = finished).canRetry())
    }

    @Test
    fun `current production is the newest successful production deployment`() {
        val list = listOf(
            deployment("preview-1", PagesRules.ENVIRONMENT_PREVIEW, finished),
            deployment("prod-2", stages = listOf(stage("deploy", "failure"))),
            deployment("prod-1", stages = finished),
        )

        assertEquals("prod-1", PagesRules.currentProductionId(list))
        assertNull(PagesRules.currentProductionId(emptyList()))
    }

    @Test
    fun `rollback targets only older successful production deployments`() {
        val current = "prod-2"
        val older = deployment("prod-1", stages = finished)
        val preview = deployment("preview-1", PagesRules.ENVIRONMENT_PREVIEW, finished)

        assertTrue(older.canRollbackTo(current))
        assertFalse(deployment(current, stages = finished).canRollbackTo(current))
        assertFalse(preview.canRollbackTo(current))
        assertFalse(deployment("prod-0", stages = listOf(stage("deploy", "failure"))).canRollbackTo(current))
    }

    @Test
    fun `delete is never offered for the current production deployment or one in progress`() {
        val current = deployment("prod-2", stages = finished)
        val building = deployment("prod-3", stages = listOf(stage("build", "active")))
        val old = deployment("prod-1", stages = finished)

        assertFalse(current.canDelete(current.id))
        assertFalse(building.canDelete(current.id))
        assertTrue(old.canDelete(current.id))
    }

    @Test
    fun `project filter matches name, subdomain, branch and repository case-insensitively`() {
        val projects = listOf(
            PagesProject(name = "lantern-docs", subdomain = "lantern-docs.pages.dev", productionBranch = "main"),
            PagesProject(name = "tidewater", subdomain = "tidewater.pages.dev", productionBranch = "release"),
        )

        assertEquals(listOf("lantern-docs"), PagesRules.filterProjects(projects, "LANTERN").map { it.name })
        assertEquals(listOf("tidewater"), PagesRules.filterProjects(projects, "release").map { it.name })
        assertEquals(2, PagesRules.filterProjects(projects, "  ").size)
    }

    @Test
    fun `merging a refreshed first page keeps older deployments that were loaded later`() {
        val existing = listOf(deployment("new", stages = finished), deployment("old", stages = finished))
        val fresh = listOf(deployment("new", stages = listOf(stage("build", "active"))))

        val merged = PagesRules.mergeDeployments(fresh, existing)

        assertEquals(listOf("new", "old"), merged.map { it.id })
        assertEquals("active", merged.first().latestStage?.status)
    }

    @Test
    fun `more pages exist while the loaded count is below the reported total`() {
        assertTrue(PagesRules.hasMorePages(page = 1, perPage = 25, totalCount = 60, received = 25))
        assertFalse(PagesRules.hasMorePages(page = 3, perPage = 25, totalCount = 60, received = 10))
    }

    @Test
    fun `without a total, a full page means there may be more`() {
        assertTrue(PagesRules.hasMorePages(page = 1, perPage = 25, totalCount = null, received = 25))
        assertFalse(PagesRules.hasMorePages(page = 1, perPage = 25, totalCount = null, received = 3))
    }
}
