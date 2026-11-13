package com.tomasps.virga.feature.builds

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Build configuration (repository and build settings) of one Worker. Internal to this feature. */
@Serializable
data class BuildConfigScreen(val accountId: String, val scriptName: String) : NavKey
