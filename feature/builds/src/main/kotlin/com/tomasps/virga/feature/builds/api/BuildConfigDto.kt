package com.tomasps.virga.feature.builds.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Build settings for a Worker, as GET /builds/workers/{tag} returns them. Only the fields Virga reads. */
@Serializable
data class BuildConfigDto(
    @SerialName("git_repository") val gitRepository: GitRepositoryDto? = null,
    @SerialName("production_settings") val productionSettings: ProductionSettingsDto? = null,
    @SerialName("previews_enabled") val previewsEnabled: Boolean? = null,
)

@Serializable
data class GitRepositoryDto(
    @SerialName("provider_type") val providerType: String? = null,
    @SerialName("provider_account_name") val providerAccountName: String? = null,
    @SerialName("repo_name") val repoName: String? = null,
    val branch: String? = null,
)

@Serializable
data class ProductionSettingsDto(
    @SerialName("build_command") val buildCommand: String? = null,
    @SerialName("deploy_command") val deployCommand: String? = null,
    @SerialName("root_directory") val rootDirectory: String? = null,
    @SerialName("build_caching_enabled") val buildCachingEnabled: Boolean? = null,
)

/** One Preview: a branch with its own build settings. */
@Serializable
data class PreviewDto(
    @SerialName("preview_id") val previewId: String? = null,
    val branch: String? = null,
    @SerialName("auto_build") val autoBuild: Boolean? = null,
    @SerialName("auto_delete") val autoDelete: Boolean? = null,
)
