package com.tomasps.virga.wear.builds

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.feature.builds.api.TriggerRef

/** The trigger and target a Retry would start a build from. */
data class RetryPlan(val triggerUuid: String, val target: BuildTarget)

/** Decides whether a build can be retried, and how. Pure: the UI calls it to hide Retry before offering it. */
object RetryPlanner {

    /**
     * The target is the build's commit if it has one, else its branch, else there is no plan. The trigger is the first
     * one whose branch includes the build's branch; failing that, the only trigger when exactly one exists.
     */
    fun plan(build: BuildSummary, triggers: List<TriggerRef>): RetryPlan? {
        val target = targetOf(build) ?: return null
        val trigger = triggerFor(build.branch, triggers) ?: return null
        return RetryPlan(trigger.triggerUuid, target)
    }

    internal fun targetOf(build: BuildSummary): BuildTarget? {
        val commit = build.commitHash
        val branch = build.branch
        return when {
            !commit.isNullOrBlank() -> BuildTarget.Commit(commit)
            !branch.isNullOrBlank() -> BuildTarget.Branch(branch)
            else -> null
        }
    }

    private fun triggerFor(branch: String?, triggers: List<TriggerRef>): TriggerRef? {
        val match = branch?.let { name -> triggers.firstOrNull { name in it.branchIncludes } }
        return match ?: triggers.singleOrNull()
    }
}
