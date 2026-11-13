package com.tomasps.virga.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {

    @Test
    fun `https links on Cloudflare domains are allowed`() {
        assertTrue(ExternalLinks.isAllowed("https://dash.cloudflare.com/profile/api-tokens"))
        assertTrue(ExternalLinks.isAllowed("https://developers.cloudflare.com/api/"))
        assertTrue(ExternalLinks.isAllowed("https://cloudflare.com/"))
    }

    @Test
    fun `other schemes, hosts and lookalike domains are refused`() {
        assertFalse(ExternalLinks.isAllowed("http://dash.cloudflare.com/"))
        assertFalse(ExternalLinks.isAllowed("https://evil.example/cloudflare.com"))
        assertFalse(ExternalLinks.isAllowed("https://cloudflare.com.evil.example/"))
        assertFalse(ExternalLinks.isAllowed("https://notcloudflare.com/"))
        assertFalse(ExternalLinks.isAllowed("https://user@dash.cloudflare.com/"))
        assertFalse(ExternalLinks.isAllowed("intent://cloudflare.com/#Intent;end"))
        assertFalse(ExternalLinks.isAllowed("not a url"))
    }
}
