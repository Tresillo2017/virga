package com.tomasps.virga.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadMoreStateTest {

    @Test
    fun `shows nothing once the list is complete`() {
        assertEquals(
            LoadMoreState.HIDDEN,
            loadMoreStateOf(hasMore = false, isLoading = false, hasFailed = false),
        )
    }

    @Test
    fun `offers a load more button when another page exists`() {
        assertEquals(
            LoadMoreState.AVAILABLE,
            loadMoreStateOf(hasMore = true, isLoading = false, hasFailed = false),
        )
    }

    @Test
    fun `shows a failure with retry when the last page failed`() {
        assertEquals(
            LoadMoreState.FAILED,
            loadMoreStateOf(hasMore = true, isLoading = false, hasFailed = true),
        )
    }

    @Test
    fun `loading takes precedence over an earlier failure`() {
        assertEquals(
            LoadMoreState.LOADING,
            loadMoreStateOf(hasMore = true, isLoading = true, hasFailed = true),
        )
    }
}
