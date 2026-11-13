package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.workers.data.BindingTarget
import com.tomasps.virga.feature.workers.data.CustomDomain
import com.tomasps.virga.feature.workers.data.Deployment
import com.tomasps.virga.feature.workers.data.ScriptSettings
import com.tomasps.virga.feature.workers.data.SubdomainState
import com.tomasps.virga.feature.workers.data.WorkerBinding
import com.tomasps.virga.feature.workers.data.WorkerVersion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

enum class DetailTab { OVERVIEW, METRICS, DEPLOYMENTS, DOMAINS, SETTINGS }

/** Everything the detail tabs read. Loaded together, so one refresh updates every tab at once. */
data class WorkerSnapshot(
    val settings: ScriptSettings,
    val bindings: List<WorkerBinding>,
    val subdomain: SubdomainState,
    val domains: List<CustomDomain>,
    val deployments: List<Deployment>,
    /** Deployable versions, newest first. Rollback targets come from here. */
    val versions: List<WorkerVersion> = emptyList(),
) {
    /** Plain-text variables, shown read-only. */
    val plainTextVariables: List<WorkerBinding> get() = bindings.filter { it.type == PLAIN_TEXT_TYPE }

    /** Secret names only. Secret values are never read back from the API. */
    val secretNames: List<String> get() = bindings.filter { it.type == SECRET_TEXT_TYPE }.map { it.name }

    /** The most recent deployment, which is the active one. */
    val activeDeployment: Deployment? get() = deployments.firstOrNull()
}

/**
 * Detail state for one Worker. [snapshot] is null until the first load starts. [latestBuild] comes from the builds
 * gateway and stays null when the Worker has no connected build.
 */
data class WorkerDetailUiState(
    val scriptName: String,
    val accountId: String? = null,
    val tab: DetailTab = DetailTab.OVERVIEW,
    val snapshot: UiState<WorkerSnapshot>? = null,
    val latestBuild: BuildSummary? = null,
    val watchedBuildIds: Set<String> = emptySet(),
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val deleted: Boolean = false,
) {
    override fun toString(): String = "WorkerDetailUiState(scriptName=$scriptName, tab=$tab, busy=$isBusy)"
}

/** Pure helpers for the deployment split, kept separate so they can be tested without Android. */
object DeploymentSplit {
    const val TOTAL = 100.0
    private const val TOLERANCE = 0.001
    private const val PERCENT_SCALE = 100.0

    /** A split is valid when every share is above zero, at most 100, and the shares sum to exactly 100. */
    fun isValid(percentages: List<Double>): Boolean {
        if (percentages.isEmpty()) return false
        if (percentages.any { it <= 0.0 || it > TOTAL }) return false
        return kotlin.math.abs(percentages.sum() - TOTAL) < TOLERANCE
    }

    /** Shows a share without a trailing ".0" and without binary rounding noise, so 33.5 stays 33.5. */
    fun formatPercent(value: Double): String {
        val rounded = kotlin.math.round(value * PERCENT_SCALE) / PERCENT_SCALE
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else "%.2f".format(Locale.US, rounded).trimEnd('0').trimEnd('.')
    }

    /** The split the rollback button sends: one version at 100 percent. */
    fun rollbackTo(versionId: String): List<Pair<String, Double>> = listOf(versionId to TOTAL)
}

private const val PLAIN_TEXT_TYPE = "plain_text"
private const val SECRET_TEXT_TYPE = "secret_text"

/**
 * Turns the raw settings bindings into display rows. Unknown or malformed entries are dropped instead of failing
 * the whole screen. Only resources Virga has a screen for get a [BindingTarget].
 */
internal fun bindingsFrom(raw: List<JsonObject>): List<WorkerBinding> = raw.mapNotNull { obj ->
    val type = stringField(obj, "type") ?: return@mapNotNull null
    val name = stringField(obj, "name").orEmpty()
    WorkerBinding(
        type = type,
        name = name,
        target = targetFor(type, obj),
        text = if (type == PLAIN_TEXT_TYPE) stringField(obj, "text") else null,
    )
}

private fun targetFor(type: String, obj: JsonObject): BindingTarget? = when (type) {
    "r2_bucket" -> stringField(obj, "bucket_name")?.let { BindingTarget.R2(it) }
    "d1" -> (stringField(obj, "database_id") ?: stringField(obj, "id"))?.let { BindingTarget.D1(it) }
    "kv_namespace" -> stringField(obj, "namespace_id")?.let { BindingTarget.Kv(it) }
    else -> null
}

private fun stringField(obj: JsonObject, key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull

/**
 * True only while the build is still running. A build that has an outcome or a stop time has finished, even when the
 * status in a snapshot loaded earlier still says it is active, so it is never offered as watchable.
 */
internal fun BuildSummary.isBuildingNow(): Boolean = status.isActive && outcome == null && stoppedOn == null
