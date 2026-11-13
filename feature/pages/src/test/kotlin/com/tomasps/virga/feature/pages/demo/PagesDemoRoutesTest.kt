package com.tomasps.virga.feature.pages.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.data.PagesDomain
import com.tomasps.virga.feature.pages.data.PagesDomainsRepository
import com.tomasps.virga.feature.pages.data.PagesProject
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.data.PagesDeploymentsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagesDemoRoutesTest {

    private fun api() = mockCloudflareApi { PagesDemoRoutes().contribute(this) }

    private fun projects(api: com.tomasps.virga.core.network.CloudflareApi) = runBlocking {
        (PagesProjectsRepository(api).projects("demo-account-personal") as ApiResult.Success).value
    }

    @Test
    fun `the demo account lists two invented projects with a git source on one of them`() {
        val list = projects(api())

        assertEquals(setOf("lantern-docs", "tidewater-blog"), list.map { it.name }.toSet())
        assertTrue(list.single { it.name == "lantern-docs" }.source != null)
        assertTrue(list.single { it.name == "tidewater-blog" }.source == null)
    }

    @Test
    fun `the project list status comes from the newest deployment, and demo commit ids are hex`() {
        val list = projects(api())
        val lantern = list.single { it.name == "lantern-docs" }

        assertEquals("lantern-prev-5", lantern.latestDeployment?.id)
        val commit = lantern.latestDeployment?.trigger?.metadata?.commitHash.orEmpty()
        assertTrue(commit.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun `a settings patch is visible on the next read of the project`() = runBlocking {
        val api = api()
        val patched = PagesProjectsRepository(api).updateSettings(
            "demo-account-personal",
            "lantern-docs",
            buildJsonObject { put("production_branch", "release") },
        )
        val read = PagesProjectsRepository(api).project("demo-account-personal", "lantern-docs")

        assertTrue(patched is ApiResult.Success)
        assertEquals("release", (read as ApiResult.Success<PagesProject>).value.productionBranch)
    }

    @Test
    fun `a null key in a patch removes that environment variable from the demo project`() = runBlocking {
        val api = api()
        val withVar = buildJsonObject {
            putJsonObject("deployment_configs") { putJsonObject("production") { putJsonObject("env_vars") { putJsonObject("MODE") { put("type", "plain_text"); put("value", "x") } } } }
        }
        PagesProjectsRepository(api).updateSettings("demo-account-personal", "lantern-docs", withVar)
        PagesProjectsRepository(api).updateSettings(
            "demo-account-personal",
            "lantern-docs",
            buildJsonObject { putJsonObject("deployment_configs") { putJsonObject("production") { putJsonObject("env_vars") { put("MODE", JsonNull) } } } },
        )

        val project = (PagesProjectsRepository(api).project("demo-account-personal", "lantern-docs") as ApiResult.Success).value
        assertFalse(project.deploymentConfigs?.production?.envVars.orEmpty().containsKey("MODE"))
    }

    @Test
    fun `retrying a failed deployment moves it back to queued`() = runBlocking {
        val api = api()
        val failed = PagesDeploymentsRepository(api).page("demo-account-personal", "lantern-docs", 1, 25)
            .let { (it as ApiResult.Success<List<PagesDeployment>>).value }
            .single { it.latestStage?.status == "failure" }

        val retried = PagesDeploymentsRepository(api).retry("demo-account-personal", "lantern-docs", failed.id)

        assertEquals("queued", (retried as ApiResult.Success).value.latestStage?.name)
    }

    @Test
    fun `a domain added in the demo appears in the list and can be removed`() = runBlocking {
        val api = api()
        val repository = PagesDomainsRepository(api)
        repository.add("demo-account-personal", "tidewater-blog", "news.tidewater.example")

        val afterAdd = (repository.domains("demo-account-personal", "tidewater-blog") as ApiResult.Success<List<PagesDomain>>).value
        repository.remove("demo-account-personal", "tidewater-blog", "news.tidewater.example")
        val afterRemove = (repository.domains("demo-account-personal", "tidewater-blog") as ApiResult.Success<List<PagesDomain>>).value

        assertTrue(afterAdd.any { it.name == "news.tidewater.example" })
        assertFalse(afterRemove.any { it.name == "news.tidewater.example" })
    }

    @Test
    fun `a project deleted in the demo leaves the list on the next read`() = runBlocking {
        val api = api()

        val deleted = com.tomasps.virga.feature.pages.data.PagesProjectsRepository(api).deleteProject("demo-account-personal", "tidewater-blog")
        val names = projects(api).map { it.name }

        assertTrue(deleted is ApiResult.Success)
        assertEquals(listOf("lantern-docs"), names)
    }
}
