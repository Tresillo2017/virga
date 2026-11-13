package com.tomasps.virga.feature.pages.ui

import androidx.annotation.StringRes
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.logic.PagesStageName
import com.tomasps.virga.feature.pages.logic.PagesStatus

/** Colour tone for a stage or deployment status string. Unknown values are neutral, never an error. */
internal fun statusTone(status: String?): StatusTone = when (status) {
    PagesStatus.SUCCESS -> StatusTone.Success
    PagesStatus.FAILURE -> StatusTone.Error
    PagesStatus.ACTIVE -> StatusTone.Pending
    else -> StatusTone.Neutral
}

@StringRes
internal fun statusLabelRes(status: String?): Int = when (status) {
    PagesStatus.SUCCESS -> R.string.pages_status_success
    PagesStatus.FAILURE -> R.string.pages_status_failure
    PagesStatus.ACTIVE -> R.string.pages_status_active
    PagesStatus.CANCELED -> R.string.pages_status_canceled
    PagesStatus.SKIPPED -> R.string.pages_status_skipped
    PagesStatus.IDLE -> R.string.pages_status_idle
    else -> R.string.pages_status_unknown
}

@StringRes
internal fun stageLabelRes(stageName: String): Int = when (stageName) {
    PagesStageName.QUEUED -> R.string.pages_stage_queued
    PagesStageName.INITIALIZE -> R.string.pages_stage_initialize
    PagesStageName.CLONE_REPO -> R.string.pages_stage_clone_repo
    PagesStageName.BUILD -> R.string.pages_stage_build
    PagesStageName.DEPLOY -> R.string.pages_stage_deploy
    else -> R.string.pages_stage_unknown
}
