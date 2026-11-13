package com.tomasps.virga.core.navigation

import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The only way screens move between destinations. Provided through [LocalNavigator] by :app, so features
 * never hold a reference to the back stack itself.
 *
 * @param backStack the live stack. Navigator mutates it; NavDisplay renders its last key.
 * @param openUrl opens an external page, for example "Open in dashboard" links.
 */
class Navigator(
    private val backStack: MutableList<NavKey>,
    private val openUrl: (String) -> Unit,
) {
    val current: NavKey? get() = backStack.lastOrNull()

    fun navigate(key: NavKey) {
        backStack.add(key)
    }

    /** Pops the top key. Returns false when only the root is left, so the caller can leave the app. */
    fun back(): Boolean {
        if (backStack.size <= MIN_STACK_SIZE_FOR_POP) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Collapses the stack to [key]. Used for bottom tab switches and for sign-in or sign-out. */
    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    /**
     * Opens [url] in the browser. Only HTTPS links on Cloudflare's domains are allowed (see [ExternalLinks]).
     * Returns false, and opens nothing, for any other URL, so the caller can show that it was refused.
     */
    fun openInBrowser(url: String): Boolean {
        if (!ExternalLinks.isAllowed(url)) return false
        openUrl(url)
        return true
    }

    private companion object {
        const val MIN_STACK_SIZE_FOR_POP = 1
    }
}

/** Provided once by :app around the NavDisplay. Reading it outside that scope is a programming error. */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("Navigator was not provided. :app wraps NavDisplay in LocalNavigator.")
}
