package com.tomasps.virga.feature.account

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Keys private to :feature:account. Cross-feature keys (SignIn, AccountRoot, Settings) live in core:navigation. */
@Serializable
data object AccountSwitcher : NavKey

@Serializable
data object AccountAbout : NavKey
