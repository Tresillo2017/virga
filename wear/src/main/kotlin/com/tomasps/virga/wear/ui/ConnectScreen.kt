package com.tomasps.virga.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.tomasps.virga.R

/** One message and an optional button, in a scaling list so it lays out on round and square screens. */
@Composable
fun MessageScreen(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { Text(message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            if (actionLabel != null) {
                item { Button(onClick = onAction, label = { Text(actionLabel) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Shown when the watch has no credential. The phone does the connecting, so there is nothing to type or tap. */
@Composable
fun ConnectScreen() = MessageScreen(stringResource(R.string.connect_not_connected))

/** Shown when the phone has not checked in for over 24 hours. No request is made from this state. */
@Composable
fun StaleScreen() = MessageScreen(stringResource(R.string.connect_stale))
