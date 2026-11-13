package com.tomasps.virga.feature.workers.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

/** Worker reads and writes. Repositories wrap [CloudflareApi] and return [ApiResult]; they never map text for the UI. */
class WorkersRepository(private val api: CloudflareApi) {

    suspend fun listScripts(accountId: String): ApiResult<List<WorkerScript>> = api.allPages(
        cloudflarePath("accounts", accountId, "workers", "scripts"),
        deserializer = ListSerializer(WorkerScript.serializer()),
    )

    suspend fun settings(accountId: String, scriptName: String): ApiResult<ScriptSettings> =
        api.get(scriptPath(accountId, scriptName, "settings"))

    suspend fun deployments(accountId: String, scriptName: String): ApiResult<DeploymentPage> =
        api.get(scriptPath(accountId, scriptName, "deployments"), mapOf("per_page" to DEPLOYMENTS_PER_PAGE))

    /** Creates a percentage deployment. The caller checks the percentages sum to 100 before calling. */
    suspend fun deployPercentages(
        accountId: String,
        scriptName: String,
        versions: List<DeploymentVersion>,
    ): ApiResult<Deployment> = api.post(scriptPath(accountId, scriptName, "deployments"), DeploymentRequest(strategy = "percentage", versions = versions))

    /** Deployable versions, newest first, up to the 100 the rollback limit allows. */
    suspend fun versions(accountId: String, scriptName: String): ApiResult<List<WorkerVersion>> =
        when (val result = api.get<VersionsPage>(scriptPath(accountId, scriptName, "versions"), mapOf("deployable" to "true", "per_page" to VERSIONS_PER_PAGE))) {
            is ApiResult.Success -> ApiResult.Success(result.value.items)
            is ApiResult.Failure -> result
        }

    suspend fun subdomain(accountId: String, scriptName: String): ApiResult<SubdomainState> =
        api.get(scriptPath(accountId, scriptName, "subdomain"))

    suspend fun setSubdomain(
        accountId: String,
        scriptName: String,
        enabled: Boolean,
        previewsEnabled: Boolean,
    ): ApiResult<SubdomainState> = api.post(
        scriptPath(accountId, scriptName, "subdomain"),
        SubdomainRequest(enabled = enabled, previewsEnabled = previewsEnabled),
    )

    suspend fun customDomains(accountId: String, scriptName: String): ApiResult<List<CustomDomain>> =
        api.get(cloudflarePath("accounts", accountId, "workers", "domains"), mapOf("service" to scriptName))

    /** Attaches a hostname to a Worker. Cloudflare infers the zone from the hostname when zone fields are omitted. */
    suspend fun addCustomDomain(accountId: String, hostname: String, service: String): ApiResult<CustomDomain> =
        api.put(
            cloudflarePath("accounts", accountId, "workers", "domains"),
            AddCustomDomainRequest(hostname = hostname, service = service),
        )

    suspend fun deleteCustomDomain(accountId: String, domainId: String): ApiResult<Unit> =
        api.delete(cloudflarePath("accounts", accountId, "workers", "domains", domainId))

    /**
     * Saves secrets by name. A null value deletes that secret. The response may contain secret values, so it is
     * read as raw JSON and dropped; nothing from it reaches the caller.
     */
    suspend fun updateSecrets(accountId: String, scriptName: String, changes: Map<String, String?>): ApiResult<Unit> {
        val body = SecretsBulkRequest(
            secrets = changes.mapValues { (name, text) -> text?.let { SecretValue(name = name, text = it) } },
        )
        return when (val result = api.patch<SecretsBulkRequest, JsonElement>(scriptPath(accountId, scriptName, "secrets-bulk"), body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    suspend fun deleteWorker(accountId: String, scriptName: String): ApiResult<Unit> =
        api.delete(cloudflarePath("accounts", accountId, "workers", "scripts", scriptName))

    private fun scriptPath(accountId: String, scriptName: String, vararg rest: String): String =
        cloudflarePath("accounts", accountId, "workers", "scripts", scriptName, *rest)

    private companion object {
        const val DEPLOYMENTS_PER_PAGE = "50"
        const val VERSIONS_PER_PAGE = "100"
    }
}

@Serializable
internal data class DeploymentRequest(
    val strategy: String,
    val versions: List<DeploymentVersion>,
)

@Serializable
internal data class AddCustomDomainRequest(
    val hostname: String,
    val service: String,
)

@Serializable
internal data class SubdomainRequest(
    val enabled: Boolean,
    @SerialName("previews_enabled") val previewsEnabled: Boolean,
)

/** `type` is always sent: the reference example includes it, and defaults are not encoded unless marked. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SecretValue(
    val name: String,
    @EncodeDefault val type: String = "secret_text",
    val text: String,
) {
    override fun toString(): String = "SecretValue(name=$name, type=$type, text=<redacted>)"
}

@Serializable
internal data class SecretsBulkRequest(val secrets: Map<String, SecretValue?>)
