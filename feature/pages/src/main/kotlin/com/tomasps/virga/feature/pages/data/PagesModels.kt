package com.tomasps.virga.feature.pages.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A Pages project as GET /accounts/{id}/pages/projects/{name} returns it. Unknown fields are ignored. */
@Serializable
data class PagesProject(
    val id: String = "",
    val name: String = "",
    @SerialName("production_branch") val productionBranch: String? = null,
    val subdomain: String? = null,
    val domains: List<String>? = null,
    val framework: String? = null,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("latest_deployment") val latestDeployment: PagesDeployment? = null,
    @SerialName("canonical_deployment") val canonicalDeployment: PagesDeployment? = null,
    val source: PagesSource? = null,
    @SerialName("build_config") val buildConfig: PagesBuildConfig? = null,
    @SerialName("deployment_configs") val deploymentConfigs: PagesDeploymentConfigs? = null,
)

@Serializable
data class PagesDeployment(
    val id: String = "",
    @SerialName("short_id") val shortId: String? = null,
    val environment: String? = null,
    val url: String? = null,
    val aliases: List<String>? = null,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("latest_stage") val latestStage: PagesStage? = null,
    val stages: List<PagesStage>? = null,
    @SerialName("deployment_trigger") val trigger: PagesTrigger? = null,
    @SerialName("is_skipped") val isSkipped: Boolean? = null,
    @SerialName("skip_reason") val skipReason: String? = null,
)

@Serializable
data class PagesStage(
    val name: String? = null,
    val status: String? = null,
    @SerialName("started_on") val startedOn: String? = null,
    @SerialName("ended_on") val endedOn: String? = null,
)

@Serializable
data class PagesTrigger(
    val type: String? = null,
    val metadata: PagesTriggerMetadata? = null,
)

@Serializable
data class PagesTriggerMetadata(
    val branch: String? = null,
    @SerialName("commit_hash") val commitHash: String? = null,
    @SerialName("commit_message") val commitMessage: String? = null,
    @SerialName("commit_dirty") val commitDirty: Boolean? = null,
)

@Serializable
data class PagesSource(
    val type: String? = null,
    val config: PagesSourceConfig? = null,
)

@Serializable
data class PagesSourceConfig(
    @SerialName("repo_name") val repoName: String? = null,
    val owner: String? = null,
    @SerialName("production_branch") val productionBranch: String? = null,
)

@Serializable
data class PagesBuildConfig(
    @SerialName("build_command") val buildCommand: String? = null,
    @SerialName("destination_dir") val destinationDir: String? = null,
    @SerialName("root_dir") val rootDir: String? = null,
    @SerialName("build_caching") val buildCaching: Boolean? = null,
)

@Serializable
data class PagesDeploymentConfigs(
    val production: PagesDeploymentConfigValues? = null,
    val preview: PagesDeploymentConfigValues? = null,
)

/**
 * Per-environment settings. Bindings are read-only in Virga, so they are kept as name lists: the map keys are the
 * binding names and the values are ignored.
 */
@Serializable
data class PagesDeploymentConfigValues(
    @SerialName("env_vars") val envVars: Map<String, PagesEnvVar?>? = null,
    @SerialName("compatibility_date") val compatibilityDate: String? = null,
    @SerialName("compatibility_flags") val compatibilityFlags: List<String>? = null,
    @SerialName("usage_model") val usageModel: String? = null,
    @SerialName("kv_namespaces") val kvNamespaces: Map<String, JsonElement?>? = null,
    @SerialName("d1_databases") val d1Databases: Map<String, JsonElement?>? = null,
    @SerialName("r2_buckets") val r2Buckets: Map<String, JsonElement?>? = null,
    val services: Map<String, JsonElement?>? = null,
)

/** Plain values carry their value. Secrets come back with an empty or missing value, and Virga never shows them. */
@Serializable
data class PagesEnvVar(
    val type: String? = null,
    val value: String? = null,
)

@Serializable
data class PagesDomain(
    val id: String = "",
    val name: String = "",
    val status: String? = null,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("zone_tag") val zoneTag: String? = null,
    @SerialName("validation_data") val validation: PagesDomainValidation? = null,
)

@Serializable
data class PagesDomainValidation(
    val method: String? = null,
    val status: String? = null,
    @SerialName("txt_name") val txtName: String? = null,
    @SerialName("txt_value") val txtValue: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class PagesDeploymentLogs(
    val total: Int? = null,
    val data: List<PagesLogLine>? = null,
)

@Serializable
data class PagesLogLine(
    val ts: String? = null,
    val line: String? = null,
)

/** Request body for adding a custom domain. */
@Serializable
data class PagesDomainRequest(val name: String)
