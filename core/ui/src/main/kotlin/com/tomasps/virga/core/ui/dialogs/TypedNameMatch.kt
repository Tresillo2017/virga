package com.tomasps.virga.core.ui.dialogs

/**
 * True when the user has typed the exact resource name. Surrounding whitespace is ignored; case and
 * everything else must match. An empty expected name never matches, so the dialog cannot be confirmed by accident.
 */
fun isTypedNameMatch(typed: String, expected: String): Boolean =
    expected.isNotEmpty() && typed.trim() == expected
