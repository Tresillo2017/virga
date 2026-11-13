package com.tomasps.virga.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {

    @Test
    fun `a build link round trips through its URI`() {
        // Arrange
        val key = BuildDetail(accountId = "acc-1", buildUuid = "b-9", workerName = "api worker/v2")

        // Act
        val parsed = DeepLinks.parse(DeepLinks.buildDetailUri(key))

        // Assert
        assertEquals(key, parsed)
    }

    @Test
    fun `unknown hosts, schemes and missing parts parse to null`() {
        assertNull(DeepLinks.parse(null))
        assertNull(DeepLinks.parse("https://build/a/b/c"))
        assertNull(DeepLinks.parse("virga://worker/acc/name"))
        assertNull(DeepLinks.parse("virga://build/acc/name"))
        assertNull(DeepLinks.parse("virga://build/acc//uuid"))
    }

    @Test
    fun `malformed percent escapes parse to null instead of throwing`() {
        assertNull(DeepLinks.parse("virga://build/acc/%zz/uuid"))
    }
}
