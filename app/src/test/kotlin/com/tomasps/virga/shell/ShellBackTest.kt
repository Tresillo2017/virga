package com.tomasps.virga.shell

import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.SignIn
import com.tomasps.virga.core.navigation.WorkersRoot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellBackTest {

    @Test
    fun `back on a signed-in tab root other than Home returns to Home`() {
        assertTrue(shouldReturnToHome(signedInState(), WorkersRoot, 1))
    }

    @Test
    fun `back on Home does not intercept, so the system leaves the app`() {
        assertFalse(shouldReturnToHome(signedInState(), HomeRoot, 1))
    }

    @Test
    fun `back on a deeper screen is left to the navigator`() {
        assertFalse(shouldReturnToHome(signedInState(), WorkersRoot, 2))
    }

    @Test
    fun `back on the sign-in root does not jump to Home`() {
        assertFalse(shouldReturnToHome(SessionState.SignedOut, SignIn, 1))
    }

    @Test
    fun `back with no screen on the stack is not intercepted`() {
        assertFalse(shouldReturnToHome(signedInState(), null, 0))
    }

    private fun signedInState(): SessionState =
        SessionState.SignedIn(accounts = emptyList(), selectedAccountId = null, isDemo = true)
}
