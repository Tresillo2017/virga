package com.tomasps.virga.feature.workers.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One Worker as GET /accounts/{id}/workers/scripts lists it. The script name is the id. */
@Serializable
data class WorkerScript(
    val id: String,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("usage_model") val usageModel: String? = null,
)

/** Result of GET .../deployments. Most recent first, as the API returns it. */
@Serializable
data class DeploymentPage(val deployments: List<Deployment> = emptyList())

@Serializable
data class Deployment(
    val id: String,
    @SerialName("created_on") val createdOn: String? = null,
    val versions: List<DeploymentVersion> = emptyList(),
    @SerialName("author_email") val authorEmail: String? = null,
    val annotations: DeploymentAnnotations? = null,
)

@Serializable
data class DeploymentAnnotations(
    @SerialName("workers/message") val message: String? = null,
    @SerialName("workers/triggered_by") val triggeredBy: String? = null,
)

@Serializable
data class DeploymentVersion(
    @SerialName("version_id") val versionId: String,
    val percentage: Double = 0.0,
)

/** One uploaded version. The API lists newest first; [number] is the sequential version number. */
@Serializable
data class WorkerVersion(
    val id: String,
    val number: Int? = null,
    val metadata: VersionMetadata? = null,
)

@Serializable
data class VersionMetadata(
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("author_email") val authorEmail: String? = null,
    val source: String? = null,
)

@Serializable
internal data class VersionsPage(val items: List<WorkerVersion> = emptyList())

/** The workers.dev subdomain state from GET .../subdomain. */
@Serializable
data class SubdomainState(
    val enabled: Boolean = false,
    @SerialName("previews_enabled") val previewsEnabled: Boolean = false,
)

/** A custom domain attached to a Worker, from GET /accounts/{id}/workers/domains. */
@Serializable
data class CustomDomain(
    val id: String,
    val hostname: String = "",
    val service: String? = null,
    val enabled: Boolean = true,
    @SerialName("zone_name") val zoneName: String? = null,
    @SerialName("previews_enabled") val previewsEnabled: Boolean = false,
)

/**
 * Script settings from GET .../settings. Only the fields Virga reads. Bindings stay as raw JSON because each type
 * has its own fields; [WorkerBinding.fromJson] reads them defensively.
 */
@Serializable
data class ScriptSettings(
    @SerialName("compatibility_date") val compatibilityDate: String? = null,
    @SerialName("compatibility_flags") val compatibilityFlags: List<String> = emptyList(),
    val logpush: Boolean? = null,
    val bindings: List<JsonObject> = emptyList(),
)

/** A binding in display form. [target] is the resource id the deep link opens, when Virga has a screen for it. */
data class WorkerBinding(
    val type: String,
    val name: String,
    val target: BindingTarget?,
    val text: String? = null,
) {
    override fun toString(): String = "WorkerBinding(type=$type, name=$name)"
}

/** Where a binding can link: R2 bucket name, D1 database id, or KV namespace id. */
sealed interface BindingTarget {
    data class R2(val bucketName: String) : BindingTarget
    data class D1(val databaseId: String) : BindingTarget
    data class Kv(val namespaceId: String) : BindingTarget
}
