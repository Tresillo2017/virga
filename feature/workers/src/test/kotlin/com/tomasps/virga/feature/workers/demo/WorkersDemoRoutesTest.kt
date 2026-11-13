package com.tomasps.virga.feature.workers.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.workers.data.WorkersRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkersDemoRoutesTest {

    @Test
    fun `every demo route registers without a shape clash`() {
        WorkersDemoRoutes().contribute(DemoRouteRegistry())
    }

    @Test
    fun `the demo account lists the seeded Workers`() = runBlocking {
        val api = mockCloudflareApi { WorkersDemoRoutes().contribute(this) }

        val scripts = (WorkersRepository(api).listScripts("demo-account-personal") as ApiResult.Success).value

        assertEquals(setOf(DEMO_SCRIPT_EDGE_API, DEMO_SCRIPT_RESIZER, DEMO_SCRIPT_CLEANUP), scripts.map { it.id }.toSet())
    }

    @Test
    fun `a deleted demo Worker disappears from the next list`() = runBlocking {
        val api = mockCloudflareApi { WorkersDemoRoutes().contribute(this) }
        val repository = WorkersRepository(api)

        repository.deleteWorker("demo-account-personal", DEMO_SCRIPT_CLEANUP)
        val scripts = (repository.listScripts("demo-account-personal") as ApiResult.Success).value

        assertTrue(scripts.none { it.id == DEMO_SCRIPT_CLEANUP })
    }

    @Test
    fun `a demo deployment is added on top of the history`() = runBlocking {
        val api = mockCloudflareApi { WorkersDemoRoutes().contribute(this) }
        val repository = WorkersRepository(api)

        repository.deployPercentages("demo-account-personal", DEMO_SCRIPT_EDGE_API, listOf(
            com.tomasps.virga.feature.workers.data.DeploymentVersion(DEMO_VERSION_OLD_FOR_TEST, 100.0),
        ))
        val page = (repository.deployments("demo-account-personal", DEMO_SCRIPT_EDGE_API) as ApiResult.Success).value

        assertEquals(DEMO_VERSION_OLD_FOR_TEST, page.deployments.first().versions.single().versionId)
        assertEquals(3, page.deployments.size)
    }
}

private const val DEMO_VERSION_OLD_FOR_TEST = "9f1c0e2a-0000-4000-8000-000000000001"
