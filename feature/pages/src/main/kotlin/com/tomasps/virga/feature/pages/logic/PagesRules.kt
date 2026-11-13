package com.tomasps.virga.feature.pages.logic

import com.tomasps.virga.feature.pages.data.PagesDeployment
import com.tomasps.virga.feature.pages.data.PagesProject

/** Stage and status strings as the Pages API sends them. */
object PagesStatus {
    const val IDLE = "idle"
    const val ACTIVE = "active"
    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val CANCELED = "canceled"
    const val SKIPPED = "skipped"
}

object PagesStageName {
    const val QUEUED = "queued"
    const val INITIALIZE = "initialize"
    const val CLONE_REPO = "clone_repo"
    const val BUILD = "build"
    const val DEPLOY = "deploy"

    /** The order a deployment passes through its stages. */
    val PIPELINE = listOf(QUEUED, INITIALIZE, CLONE_REPO, BUILD, DEPLOY)
}

/** One pipeline step as the progress view draws it. A stage missing from the response is idle. */
data class StageView(val name: String, val status: String)

object PagesRules {
    const val ENVIRONMENT_PRODUCTION = "production"
    const val ENVIRONMENT_PREVIEW = "preview"

    /** The newest successful production deployment. The list is newest first, so this is the first match. */
    fun currentProductionId(deployments: List<PagesDeployment>): String? =
        deployments.firstOrNull { it.isProductionSuccess() }?.id

    fun filterProjects(projects: List<PagesProject>, query: String): List<PagesProject> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return projects
        return projects.filter { project -> project.searchableText().any { it.lowercase().contains(needle) } }
    }

    /** Keeps the refreshed first page at the top and any older pages that were loaded after it. */
    fun mergeDeployments(fresh: List<PagesDeployment>, existing: List<PagesDeployment>): List<PagesDeployment> {
        val freshIds = fresh.map { it.id }.toSet()
        return fresh + existing.filterNot { it.id in freshIds }
    }

    /** True while another page exists. Uses the reported total when there is one, otherwise a full page. */
    fun hasMorePages(page: Int, perPage: Int, totalCount: Int?, received: Int): Boolean =
        totalCount?.let { page * perPage < it } ?: (received >= perPage)

    private fun PagesDeployment.isProductionSuccess(): Boolean =
        environment == ENVIRONMENT_PRODUCTION && latestStage?.status == PagesStatus.SUCCESS &&
            latestStage?.name == PagesStageName.DEPLOY
}

private fun PagesProject.searchableText(): List<String> = listOfNotNull(
    name,
    subdomain,
    productionBranch,
    source?.config?.repoName,
    source?.config?.owner,
) + domains.orEmpty()

fun PagesDeployment.stageViews(): List<StageView> = PagesStageName.PIPELINE.map { name ->
    StageView(name = name, status = stages.orEmpty().firstOrNull { it.name == name }?.status ?: PagesStatus.IDLE)
}

/** Still moving: a stage is running. A finished, failed, cancelled or skipped deployment has no active stage. */
fun PagesDeployment.isInProgress(): Boolean {
    val latest = latestStage ?: return stages.orEmpty().any { it.status == PagesStatus.ACTIVE }
    return latest.status == PagesStatus.ACTIVE
}

fun PagesDeployment.canRetry(): Boolean = latestStage?.status == PagesStatus.FAILURE

fun PagesDeployment.canRollbackTo(currentProductionId: String?): Boolean =
    environment == PagesRules.ENVIRONMENT_PRODUCTION &&
        latestStage?.status == PagesStatus.SUCCESS &&
        latestStage?.name == PagesStageName.DEPLOY &&
        id != currentProductionId &&
        !isInProgress()

fun PagesDeployment.canDelete(currentProductionId: String?): Boolean =
    id != currentProductionId && !isInProgress()

