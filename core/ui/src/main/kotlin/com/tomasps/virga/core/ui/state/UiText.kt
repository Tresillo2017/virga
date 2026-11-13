package com.tomasps.virga.core.ui.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text that a ViewModel or mapper can build without a Context. Resources are resolved at render time,
 * so localisation stays in strings.xml. Use [Resource] for anything the app wrote, and [Literal] only for
 * text that is already data (for example an API message), never for an English sentence in code.
 */
sealed interface UiText {
    /** A string resource with optional format arguments. [id] is an `R.string` id. */
    data class Resource(val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Literal(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Literal -> value
    is UiText.Resource ->
        if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Literal -> value
    is UiText.Resource ->
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
}
