package com.tomasps.virga.feature.workers.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.Deployment

private const val SHORT_ID_LENGTH = 8

/**
 * How a version and a split read to the user: "Version 2", "Version 2 at 80%". A version the snapshot does not list
 * falls back to its short id, so a deployment never shows a blank name.
 */
internal class VersionNames(
    private val snapshot: WorkerSnapshot,
    private val versionFormat: String,
    private val splitFormat: String,
) {
    fun version(versionId: String): String =
        snapshot.versions.firstOrNull { it.id == versionId }?.number?.let { versionFormat.format(it) }
            ?: versionId.take(SHORT_ID_LENGTH)

    /** One line for a whole deployment, for example "Version 2 at 90%, Version 3 at 10%". */
    fun split(deployment: Deployment): String = deployment.versions.joinToString(separator = ", ") { share ->
        splitFormat.format(version(share.versionId), DeploymentSplit.formatPercent(share.percentage))
    }
}

/** The version names for [snapshot], built from the screen's format strings. */
@Composable
internal fun rememberVersionNames(snapshot: WorkerSnapshot): VersionNames {
    val versionFormat = stringResource(R.string.workers_deploy_version)
    val splitFormat = stringResource(R.string.workers_deploy_split_item)
    return remember(snapshot, versionFormat, splitFormat) { VersionNames(snapshot, versionFormat, splitFormat) }
}
