package com.tomasps.virga.feature.builds.api

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.builds.demo.demoTagFor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val TAG = "tag-1"
private const val SCRIPT = "api-worker"
private const val BUILD_PATH = "/accounts/{accountId}/builds/builds/{buildUuid}"
private const val LOGS_PATH = "/accounts/{accountId}/builds/builds/{buildUuid}/logs"
private const val CANCEL_PATH = "/accounts/{accountId}/builds/builds/{buildUuid}/cancel"
private const val SCRIPTS_PATH = "/accounts/{accountId}/workers/scripts"
private const val BUILDS_PATH = "/accounts/{accountId}/builds/workers/{workerTag}/builds"
private const val TRIGGERS_PATH = "/accounts/{accountId}/builds/workers/{workerTag}/triggers"
private const val START_PATH = "/accounts/{accountId}/builds/triggers/{triggerUuid}/builds"
private const val SETTINGS_PATH = "/accounts/{accountId}/builds/workers/{workerTag}"

private const val BUILD_JSON = """{"build_uuid":"b-1","status":"stopped","build_outcome":"cancelled",
  "created_on":"2026-11-11T10:00:00Z","initializing_on":"2026-11-11T10:00:01Z",
  "running_on":"2026-11-11T10:00:02Z","stopped_on":"2026-11-11T10:00:30Z",
  "build_trigger_metadata":{"branch":"main","commit_hash":"abc1234","commit_message":"Fix","author":"dev@example.test"}}"""

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

class BuildsRepositoryTest {

    @Test
    fun `recent builds read the tag path with page and per_page and drop a build without a uuid`() = runBlocking {
        var query: Map<String, String> = emptyMap()
        var workerTag = ""
        val api = mockCloudflareApi {
            get(BUILDS_PATH) { request ->
                query = request.query
                workerTag = request.pathParams["workerTag"].orEmpty()
                DemoReply.ok(json("[$BUILD_JSON, {\"status\":\"queued\",\"created_on\":\"2026-11-11T10:00:00Z\"}]"))
            }
        }

        val result = BuildsRepository(api).builds(ACCOUNT, TAG, SCRIPT, page = 2, perPage = 50)

        val records = (result as ApiResult.Success).value
        assertEquals(TAG, workerTag)
        assertEquals("2", query["page"])
        assertEquals("50", query["per_page"])
        assertEquals(1, records.size)
        assertEquals(BuildOutcome.CANCELED, records.single().outcome)
        assertEquals("main", records.single().branch)
    }

    @Test
    fun `a build detail reads the build by uuid and keeps its timestamps and trigger metadata`() = runBlocking {
        val api = mockCloudflareApi {
            get(BUILD_PATH) { request ->
                assertEquals("b-1", request.pathParams["buildUuid"])
                DemoReply.ok(json(BUILD_JSON))
            }
        }

        val record = (BuildsRepository(api).build(ACCOUNT, "b-1", SCRIPT) as ApiResult.Success).value

        assertEquals(BuildStatus.STOPPED, record.status)
        assertEquals(BuildOutcome.CANCELED, record.outcome)
        assertEquals("abc1234", record.commitHash)
        assertEquals("2026-11-11T10:00:30Z", record.stoppedOn.toString())
        assertTrue(record.isTerminal)
    }

    @Test
    fun `a build detail without a creation time is a decode failure`() = runBlocking {
        val api = mockCloudflareApi {
            get(BUILD_PATH) { DemoReply.ok(json("""{"build_uuid":"b-1","status":"queued"}""")) }
        }

        val failure = BuildsRepository(api).build(ACCOUNT, "b-1", SCRIPT) as ApiResult.Failure

        assertTrue(failure.error is ApiError.Decode)
    }

    @Test
    fun `logs send the cursor only when one is given and read timestamped line pairs`() = runBlocking {
        val queries = mutableListOf<Map<String, String>>()
        val api = mockCloudflareApi {
            get(LOGS_PATH) { request ->
                queries += request.query
                DemoReply.ok(json("""{"lines":[[1700000000,"Cloning"],[1700000001,"Done"]],"cursor":"c2"}"""))
            }
        }
        val repository = BuildsRepository(api)

        val first = (repository.logs(ACCOUNT, "b-1", cursor = null) as ApiResult.Success).value
        repository.logs(ACCOUNT, "b-1", cursor = "c2")

        assertEquals(listOf("Cloning", "Done"), first.lines)
        assertEquals("c2", first.cursor)
        assertFalse(queries[0].containsKey("cursor"))
        assertEquals("c2", queries[1]["cursor"])
    }

    @Test
    fun `cancel is a PUT to the cancel path and succeeds on an object reply`() = runBlocking {
        var cancelled = false
        val api = mockCloudflareApi {
            put(CANCEL_PATH) { request ->
                cancelled = request.pathParams["buildUuid"] == "b-1"
                DemoReply.ok(json("{}"))
            }
        }

        val result = BuildsRepository(api).cancel(ACCOUNT, "b-1")

        assertTrue(result is ApiResult.Success)
        assertTrue(cancelled)
    }

    @Test
    fun `tagFor returns not found when the account has no Worker of that name`() = runBlocking {
        val api = mockCloudflareApi {
            get(SCRIPTS_PATH) { DemoReply.ok(json("""[{"id":"other","tag":"t-9"}]""")) }
        }

        val failure = BuildsRepository(api).tagFor(ACCOUNT, SCRIPT) as ApiResult.Failure

        assertTrue(failure.error is ApiError.NotFound)
    }

    @Test
    fun `demo mode gives a script without a tag a synthetic tag, and live mode drops it`() = runBlocking {
        val api = mockCloudflareApi {
            get(SCRIPTS_PATH) { DemoReply.ok(json("""[{"id":"edge-api"}]""")) }
        }

        val demoTag = BuildsRepository(api, DemoMode(initiallyEnabled = true)).tagFor(ACCOUNT, "edge-api")
        val liveTag = BuildsRepository(api, DemoMode(initiallyEnabled = false)).tagFor(ACCOUNT, "edge-api")

        assertEquals(demoTagFor("edge-api"), (demoTag as ApiResult.Success).value)
        assertTrue(liveTag is ApiResult.Failure)
    }

    @Test
    fun `triggers read the list path and keep the trigger uuid and branch includes`() = runBlocking {
        val api = mockCloudflareApi {
            get(TRIGGERS_PATH) { request ->
                assertEquals(TAG, request.pathParams["workerTag"])
                DemoReply.ok(json("""[{"trigger_uuid":"t-1","trigger_name":"main","branch_includes":["main"]}]"""))
            }
        }

        val triggers = (BuildsRepository(api).triggers(ACCOUNT, TAG) as ApiResult.Success).value

        assertEquals("t-1", triggers.single().triggerUuid)
        assertEquals(listOf("main"), triggers.single().branchIncludes)
    }

    @Test
    fun `start build posts the branch as JSON and returns the new build uuid`() = runBlocking {
        var body = ""
        val api = mockCloudflareApi {
            post(START_PATH) { request ->
                body = request.bodyText.orEmpty()
                DemoReply.ok(json("""{"build_uuid":"b-9","branch":"main"}"""))
            }
        }

        val result = BuildsRepository(api).startBuild(ACCOUNT, "t-1", BuildTarget.Branch("main"))

        assertEquals("b-9", (result as ApiResult.Success).value)
        assertEquals("main", Json.parseToJsonElement(body).jsonObject["branch"]?.jsonPrimitive?.content)
    }

    @Test
    fun `start build without a build uuid in the reply is a decode failure`() = runBlocking {
        val api = mockCloudflareApi {
            post(START_PATH) { DemoReply.ok(json("""{"branch":"main"}""")) }
        }

        val failure = BuildsRepository(api).startBuild(ACCOUNT, "t-1", BuildTarget.Commit("abc1234")) as ApiResult.Failure

        assertTrue(failure.error is ApiError.Decode)
    }

    @Test
    fun `config read maps the repository and production settings`() = runBlocking {
        val api = mockCloudflareApi {
            get(SETTINGS_PATH) {
                DemoReply.ok(json("""{"git_repository":{"repo_name":"api","branch":"main","provider_account_name":"owner"},
                  "production_settings":{"build_command":"npm run build","deploy_command":"npx wrangler deploy"},
                  "previews_enabled":true}"""))
            }
        }

        val config = (BuildsRepository(api).config(ACCOUNT, TAG) as ApiResult.Success).value

        assertTrue(config.isConnected)
        assertEquals("api", config.repoName)
        assertEquals("main", config.productionBranch)
        assertEquals("npm run build", config.buildCommand)
        assertEquals(true, config.previewsEnabled)
    }

    @Test
    fun `config update sends only the fields that changed`() = runBlocking {
        var body = ""
        val api = mockCloudflareApi {
            patch(SETTINGS_PATH) { request ->
                body = request.bodyText.orEmpty()
                DemoReply.ok(json("""{"git_repository":{"branch":"release"}}"""))
            }
        }

        val result = BuildsRepository(api).updateConfig(
            ACCOUNT,
            TAG,
            BuildConfigEdit(productionBranch = "release", buildCommand = "npm run build"),
        )

        val sent = Json.parseToJsonElement(body).jsonObject
        assertEquals("release", sent["git_repository"]?.jsonObject?.get("branch")?.jsonPrimitive?.content)
        assertEquals(setOf("build_command"), sent["production_settings"]?.jsonObject?.keys)
        assertNull(sent["previews_enabled"])
        assertEquals("release", (result as ApiResult.Success).value.productionBranch)
    }
}
