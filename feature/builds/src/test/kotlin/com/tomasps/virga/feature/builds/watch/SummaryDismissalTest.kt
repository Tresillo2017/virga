package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.feature.builds.ui.nextFollowState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryDismissalTest {

    @Test
    fun `a summary may be posted until the user dismisses it`() {
        val dismissal = SummaryDismissal()

        assertTrue(dismissal.mayRepost())
    }

    @Test
    fun `once dismissed the summary is never reposted until a new watch starts`() {
        val dismissal = SummaryDismissal()

        dismissal.onDismissed()
        assertFalse(dismissal.mayRepost())
        assertFalse(dismissal.mayRepost())

        dismissal.onNewWatch()
        assertTrue(dismissal.mayRepost())
    }
}

class LogFollowPolicyTest {

    @Test
    fun `follow stays on at the bottom and resumes when the user reaches it again`() {
        assertTrue(nextFollowState(following = false, userScrolling = false, atBottom = true))
    }

    @Test
    fun `a user drag away from the bottom pauses following`() {
        assertFalse(nextFollowState(following = true, userScrolling = true, atBottom = false))
    }

    @Test
    fun `a quiet frame keeps the current choice`() {
        assertTrue(nextFollowState(following = true, userScrolling = false, atBottom = false))
        assertFalse(nextFollowState(following = false, userScrolling = false, atBottom = false))
    }
}
