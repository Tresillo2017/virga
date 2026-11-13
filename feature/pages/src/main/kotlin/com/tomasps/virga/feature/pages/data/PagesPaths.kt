package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.network.cloudflarePath

private const val SEGMENT_ACCOUNTS = "accounts"
private const val SEGMENT_PAGES = "pages"
private const val SEGMENT_PROJECTS = "projects"
private const val SEGMENT_DEPLOYMENTS = "deployments"
private const val SEGMENT_DOMAINS = "domains"

/** Path for a project, optionally followed by more segments (for example "source" or "purge_build_cache"). */
internal fun projectPath(accountId: String, projectName: String, vararg more: String): String =
    cloudflarePath(SEGMENT_ACCOUNTS, accountId, SEGMENT_PAGES, SEGMENT_PROJECTS, projectName, *more)

internal fun projectsPath(accountId: String): String =
    cloudflarePath(SEGMENT_ACCOUNTS, accountId, SEGMENT_PAGES, SEGMENT_PROJECTS)

internal fun deploymentPath(accountId: String, projectName: String, deploymentId: String, vararg more: String): String =
    projectPath(accountId, projectName, SEGMENT_DEPLOYMENTS, deploymentId, *more)

internal fun deploymentsPath(accountId: String, projectName: String): String =
    projectPath(accountId, projectName, SEGMENT_DEPLOYMENTS)

internal fun domainsPath(accountId: String, projectName: String): String =
    projectPath(accountId, projectName, SEGMENT_DOMAINS)

internal fun domainPath(accountId: String, projectName: String, hostname: String): String =
    projectPath(accountId, projectName, SEGMENT_DOMAINS, hostname)
