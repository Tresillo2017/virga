package com.tomasps.virga.feature.pages

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Keys private to :feature:pages. The project and the Pages root are cross-feature keys in core:navigation. */
@Serializable
data class PagesDeploymentDetail(val projectName: String, val deploymentId: String) : NavKey
