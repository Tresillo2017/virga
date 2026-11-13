package com.tomasps.virga.feature.account.ui

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.account.accountKoinModule
import com.tomasps.virga.feature.account.registerAccountEntries
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A flow over the account feature's real graph and real screens. [session] replaces the demo session for one test,
 * for sign-in and empty-account states the demo session cannot reach. [extra] swaps in any other binding a test needs
 * (for example an account details repository that fails).
 */
fun accountFlow(
    compose: ComposeContentTestRule,
    session: SessionRepository? = null,
    extra: List<Module> = emptyList(),
): DemoFlow {
    val overrides = listOfNotNull(
        session?.let { fake -> module { single<SessionRepository> { fake } } },
    )
    return DemoFlow(compose, FeatureUnderTest(listOf(accountKoinModule) + overrides + extra, ::registerAccountEntries))
}
