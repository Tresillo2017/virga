package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi

private const val QUERY_PAGE = "page"
private const val QUERY_PER_PAGE = "per_page"
private const val QUERY_FORCE = "force"
private const val QUERY_ENV = "env"
private const val SEGMENT_RETRY = "retry"
private const val SEGMENT_ROLLBACK = "rollback"
private const val SEGMENT_LOGS = "history"
private const val SEGMENT_LOG_LINES = "logs"

/** Deployment calls. Retry and rollback are the only writes that are not destructive, so the screen confirms them. */
class PagesDeploymentsRepository(private val api: CloudflareApi) {

    /** One page of deployments, newest first, with the page info the caller uses for paging. [environment] filters. */
    suspend fun page(
        accountId: String,
        projectName: String,
        page: Int,
        perPage: Int,
        environment: String? = null,
    ): ApiResult<List<PagesDeployment>> {
        val query = mutableMapOf(QUERY_PAGE to page.toString(), QUERY_PER_PAGE to perPage.toString())
        if (environment != null) query[QUERY_ENV] = environment
        return api.get(path = deploymentsPath(accountId, projectName), query = query)
    }

    suspend fun deployment(accountId: String, projectName: String, deploymentId: String): ApiResult<PagesDeployment> =
        api.get(deploymentPath(accountId, projectName, deploymentId))

    suspend fun retry(accountId: String, projectName: String, deploymentId: String): ApiResult<PagesDeployment> =
        api.postEmpty(deploymentPath(accountId, projectName, deploymentId, SEGMENT_RETRY))

    /** Makes [deploymentId] the production deployment again. The server accepts only a previous successful one. */
    suspend fun rollback(accountId: String, projectName: String, deploymentId: String): ApiResult<PagesDeployment> =
        api.postEmpty(deploymentPath(accountId, projectName, deploymentId, SEGMENT_ROLLBACK))

    /** [force] lets the server delete a non-production deployment that still has an active alias. */
    suspend fun delete(accountId: String, projectName: String, deploymentId: String, force: Boolean): ApiResult<Unit> =
        api.delete(
            path = deploymentPath(accountId, projectName, deploymentId),
            query = if (force) mapOf(QUERY_FORCE to "true") else emptyMap(),
        )

    suspend fun logs(accountId: String, projectName: String, deploymentId: String): ApiResult<PagesDeploymentLogs> =
        api.get(deploymentPath(accountId, projectName, deploymentId, SEGMENT_LOGS, SEGMENT_LOG_LINES))
}
