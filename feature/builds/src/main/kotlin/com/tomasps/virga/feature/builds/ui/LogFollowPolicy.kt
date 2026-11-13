package com.tomasps.virga.feature.builds.ui

/**
 * Auto-scroll rule for the live log. Follow stays on while the view sits at the bottom. A user drag away from the
 * bottom pauses it. Reaching the bottom again, or pressing "Jump to latest", resumes it. A quiet frame keeps the
 * current choice.
 */
fun nextFollowState(following: Boolean, userScrolling: Boolean, atBottom: Boolean): Boolean = when {
    atBottom -> true
    userScrolling -> false
    else -> following
}
