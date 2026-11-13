package com.tomasps.virga.feature.builds.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The Worker's connected repository and build settings, in the form the screen shows. */
data class BuildConfig(
    val isConnected: Boolean,
    val repoName: String?,
    val providerAccountName: String?,
    val productionBranch: String?,
    val buildCommand: String?,
    val deployCommand: String?,
    val rootDirectory: String?,
    val buildCachingEnabled: Boolean?,
    val previewsEnabled: Boolean?,
)

/**
 * Fields the user changed. A null field is left as it is and is not sent. Empty text clears a command, so the
 * screen sends it as the empty string.
 */
data class BuildConfigEdit(
    val productionBranch: String? = null,
    val buildCommand: String? = null,
    val deployCommand: String? = null,
    val rootDirectory: String? = null,
    val previewsEnabled: Boolean? = null,
)

data class PreviewSummary(
    val previewId: String,
    val branch: String,
    val autoBuild: Boolean?,
)

internal fun BuildConfigDto.toBuildConfig(): BuildConfig {
    val repo = gitRepository
    val production = productionSettings
    return BuildConfig(
        isConnected = repo != null,
        repoName = repo?.repoName?.takeIf { it.isNotBlank() },
        providerAccountName = repo?.providerAccountName?.takeIf { it.isNotBlank() },
        productionBranch = repo?.branch?.takeIf { it.isNotBlank() },
        buildCommand = production?.buildCommand,
        deployCommand = production?.deployCommand,
        rootDirectory = production?.rootDirectory,
        buildCachingEnabled = production?.buildCachingEnabled,
        previewsEnabled = previewsEnabled,
    )
}

/**
 * The PATCH body for an edit. Only changed fields appear, under the keys the API documents: git_repository.branch,
 * production_settings.*, previews_enabled. An unchanged edit gives an empty object.
 */
internal fun BuildConfigEdit.toPatchBody(): JsonObject = buildJsonObject {
    productionBranch?.let { branch ->
        putJsonObject("git_repository") { put("branch", branch) }
    }
    val production = listOfNotNull(
        buildCommand?.let { "build_command" to it },
        deployCommand?.let { "deploy_command" to it },
        rootDirectory?.let { "root_directory" to it },
    )
    if (production.isNotEmpty()) {
        putJsonObject("production_settings") {
            production.forEach { (key, value) -> put(key, value) }
        }
    }
    previewsEnabled?.let { put("previews_enabled", it) }
}

internal fun PreviewDto.toSummary(): PreviewSummary? {
    val id = previewId?.takeIf { it.isNotBlank() } ?: return null
    return PreviewSummary(previewId = id, branch = branch.orEmpty(), autoBuild = autoBuild)
}
