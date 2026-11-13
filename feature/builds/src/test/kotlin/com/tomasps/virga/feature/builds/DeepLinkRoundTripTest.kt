package com.tomasps.virga.feature.builds

import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Test

/** A notification opens the build it names: the link the watch notifier builds parses back to the same key. */
class DeepLinkRoundTripTest {

    @Test
    fun `a build link parses back to the same key, even with spaces and reserved characters`() {
        val key = BuildDetail(accountId = "acc 1", buildUuid = "b/1?x=2", workerName = "edge api/v2")

        val uri = DeepLinks.buildDetailUri(key)

        assertEquals(key, DeepLinks.parse(uri))
    }

    @Test
    fun `a build link keeps each part in its own path segment`() {
        val uri = DeepLinks.buildDetailUri(BuildDetail(accountId = "acc", buildUuid = "b1", workerName = "edge/api"))

        assertEquals(4, uri.removePrefix("virga://").split('/').size)
    }
}
