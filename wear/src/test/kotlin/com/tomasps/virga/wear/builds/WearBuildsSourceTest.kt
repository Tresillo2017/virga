package com.tomasps.virga.wear.builds

import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.builds.api.BuildsRepository
import io.ktor.http.HttpStatusCode
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val SCRIPTS = "/accounts/{accountId}/workers/scripts"
private const val BUILDS = "/accounts/{accountId}/builds/workers/{workerTag}/builds"
private const val BUILD = "/accounts/{accountId}/builds/builds/{buildUuid}"
private const val CANCEL = "/accounts/{accountId}/builds/builds/{buildUuid}/cancel"
private const val TRIGGERS = "/accounts/{accountId}/builds/workers/{workerTag}/triggers"
private const val START = "/accounts/{accountId}/builds/triggers/{triggerUuid}/builds"

private fun json(text: String) = Json.parseToJsonElement(text)

private fun buildJson(id: String, created: String, branch: String? = "main", commit: String? = "abc") =
    """{"build_uuid":"$id","status":"stopped","build_outcome":"success","created_on":"$created",
    "build_trigger_metadata":{${listOfNotNull(branch?.let { "\"branch\":\"$it\"" }, commit?.let { "\"commit_hash\":\"$it\"" }).joinToString(",")}}}"""

private fun scripts(vararg names: String) =
    json(names.joinToString(prefix = "[", postfix = "]") { """{"id":"$it","tag":"tag-$it"}""" })

private class Calls {
    var unauthorized = 0
}

private fun source(
    api: CloudflareApi,
    calls: Calls = Calls(),
    hasToken: Boolean = true,
    onUnauthorizedThrows: Boolean = false,
) = WearBuildsSource(
    repository = BuildsRepository(api),
    onUnauthorized = {
        calls.unauthorized++
        if (onUnauthorizedThrows) error("storage down")
    },
    hasToken = { hasToken },
    warn = {},
)

private fun summary(branch: String? = "main", commit: String? = "abc", tag: String? = "tag-api") = BuildSummary(
    buildUuid = "b-1",
    scriptName = "api",
    workerTag = tag,
    status = BuildStatus.STOPPED,
    branch = branch,
    commitHash = commit,
    createdOn = Instant.parse("2026-11-11T10:00:00Z"),
)

class WearBuildsSourceTest {

    @Test
    fun `recent merges workers newest first and caps at ten`() = runBlocking {
        val api = mockCloudflareApi {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { request ->
                val worker = request.pathParams["workerTag"]
                val base = if (worker == "tag-a") 0 else 1
                val items = (0 until 5).joinToString(prefix = "[", postfix = "]") { i ->
                    buildJson("$worker-$i", "2026-11-11T10:%02d:00Z".format(i * 2 + base))
                }
                DemoReply.ok(json(items))
            }
        }

        val list = (source(api).recent(ACCOUNT) as ApiResult.Success).value

        assertEquals(10, list.size)
        assertEquals(list.sortedByDescending { it.createdOn }, list)
        assertEquals("tag-b-4", list.first().buildUuid)
        assertEquals("tag-b", list.first().workerTag)
    }

    @Test
    fun `worker with no builds is skipped`() = runBlocking {
        val api = mockCloudflareApi {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { request ->
                if (request.pathParams["workerTag"] == "tag-a") DemoReply.ok(json("[]"))
                else DemoReply.ok(json("[${buildJson("b-1", "2026-11-11T10:00:00Z")}]"))
            }
        }

        val list = (source(api).recent(ACCOUNT) as ApiResult.Success).value

        assertEquals(listOf("b-1"), list.map { it.buildUuid })
    }

    @Test
    fun `one worker failing does not fail the list`() = runBlocking {
        val api = mockCloudflareApi {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { request ->
                if (request.pathParams["workerTag"] == "tag-a") {
                    DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "boom")
                } else {
                    DemoReply.ok(json("[${buildJson("b-1", "2026-11-11T10:00:00Z")}]"))
                }
            }
        }

        val list = (source(api).recent(ACCOUNT) as ApiResult.Success).value

        assertEquals(listOf("b-1"), list.map { it.buildUuid })
    }

    @Test
    fun `worker list failure fails`() = runBlocking {
        val api = mockCloudflareApi {
            get(SCRIPTS) { DemoReply.failure(HttpStatusCode.Forbidden, 10000, "no") }
        }

        assertTrue(source(api).recent(ACCOUNT) is ApiResult.Failure)
    }

    @Test
    fun `cancel calls cancel endpoint`() = runBlocking {
        var cancelled: String? = null
        val api = mockCloudflareApi {
            put(CANCEL) { request ->
                cancelled = request.pathParams["buildUuid"]
                DemoReply.ok(json("{}"))
            }
        }

        val result = source(api).cancel(ACCOUNT, "b-1")

        assertTrue(result is ApiResult.Success)
        assertEquals("b-1", cancelled)
    }

    @Test
    fun `detail keeps the worker tag`() = runBlocking {
        val api = mockCloudflareApi {
            get(BUILD) { DemoReply.ok(json(buildJson("b-1", "2026-11-11T10:00:00Z"))) }
        }

        val detail = (source(api).detail(ACCOUNT, summary()) as ApiResult.Success).value

        assertEquals("tag-api", detail.workerTag)
        assertEquals("b-1", detail.buildUuid)
    }

    @Test
    fun `retry starts a build from the planned trigger`() = runBlocking {
        var startedTrigger: String? = null
        var body = ""
        val api = retryApi { reg ->
            reg.post(START) { request ->
                startedTrigger = request.pathParams["triggerUuid"]
                body = request.bodyText.orEmpty()
                DemoReply.ok(json("""{"build_uuid":"b-9"}"""))
            }
        }

        val result = source(api).retry(ACCOUNT, summary())

        assertEquals("b-9", (result as ApiResult.Success).value)
        assertEquals("t-1", startedTrigger)
        assertTrue(body.contains("commit_hash"))
    }

    @Test
    fun `retry resolves the tag when the summary has none`() = runBlocking {
        var triggersTag: String? = null
        val api = mockCloudflareApi {
            get(SCRIPTS) { DemoReply.ok(scripts("api")) }
            get(TRIGGERS) { request ->
                triggersTag = request.pathParams["workerTag"]
                DemoReply.ok(json("""[{"trigger_uuid":"t-1","branch_includes":["main"]}]"""))
            }
            post(START) { DemoReply.ok(json("""{"build_uuid":"b-9"}""")) }
        }

        val result = source(api).retry(ACCOUNT, summary(tag = null))

        assertTrue(result is ApiResult.Success)
        assertEquals("tag-api", triggersTag)
    }

    @Test
    fun `retry without a plan fails locally and never starts a build`() = runBlocking {
        var started = false
        val api = retryApi { reg ->
            reg.post(START) {
                started = true
                DemoReply.ok(json("""{"build_uuid":"b-9"}"""))
            }
        }

        val result = source(api).retry(ACCOUNT, summary(branch = null, commit = null))

        assertTrue((result as ApiResult.Failure).error is ApiError.Local)
        assertTrue(!started)
    }

    @Test
    fun `retryPlan is null for a build with no branch and no commit`() = runBlocking {
        val api = retryApi { }

        val plan = (source(api).retryPlan(ACCOUNT, summary(branch = null, commit = null)) as ApiResult.Success).value

        assertEquals(null, plan)
    }

    @Test
    fun `a rejected token wipes the session`() = runBlocking {
        val calls = Calls()
        val api = unauthorizedApi()

        val result = source(api, calls, hasToken = true).recent(ACCOUNT)

        assertTrue((result as ApiResult.Failure).error is ApiError.Unauthorized)
        assertEquals(1, calls.unauthorized)
    }

    @Test
    fun `a missing local token does not wipe the session`() = runBlocking {
        val calls = Calls()

        source(unauthorizedApi(), calls, hasToken = false).recent(ACCOUNT)

        assertEquals(0, calls.unauthorized)
    }

    @Test
    fun `a failing onUnauthorized is swallowed`() = runBlocking {
        val result = source(unauthorizedApi(), hasToken = true, onUnauthorizedThrows = true).recent(ACCOUNT)

        assertTrue((result as ApiResult.Failure).error is ApiError.Unauthorized)
    }

    private fun unauthorizedApi() = mockCloudflareApi {
        get(SCRIPTS) { DemoReply.failure(HttpStatusCode.Unauthorized, 10000, "bad token") }
    }

    private fun retryApi(extra: (DemoRouteRegistry) -> Unit) = mockCloudflareApi {
        get(TRIGGERS) { DemoReply.ok(json("""[{"trigger_uuid":"t-1","branch_includes":["main"]}]""")) }
        extra(this)
    }
}
