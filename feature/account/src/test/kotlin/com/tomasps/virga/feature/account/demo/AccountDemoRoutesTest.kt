package com.tomasps.virga.feature.account.demo

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDemoRoutesTest {

    @Serializable
    private data class Verification(val status: String)

    @Test
    fun `demo token verification reports an active token`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi { AccountDemoRoutes("Demo account", "Demo staging").contribute(this) }

        // Act
        val result = api.get<Verification>("/user/tokens/verify")

        // Assert
        assertEquals("active", (result as ApiResult.Success).value.status)
    }

    @Test
    fun `demo accounts list has two accounts with stable ids`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi { AccountDemoRoutes("Demo account", "Demo staging").contribute(this) }

        // Act
        val result = api.allPages(
            "/accounts",
            deserializer = ListSerializer(CloudflareAccount.serializer()),
        )

        // Assert
        val accounts = (result as ApiResult.Success).value
        assertEquals(DEMO_ACCOUNT_IDS, accounts.map { it.id })
        assertTrue(accounts.all { it.name.isNotBlank() })
    }
}
