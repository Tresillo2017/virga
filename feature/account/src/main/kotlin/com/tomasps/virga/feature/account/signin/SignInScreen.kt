package com.tomasps.virga.feature.account.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.brand.VirgaLockup
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.core.ui.window.SecureWindow
import com.tomasps.virga.feature.account.R
import org.koin.compose.viewmodel.koinViewModel

private const val TOKEN_PAGE_URL = "https://dash.cloudflare.com/profile/api-tokens"
private val SCREEN_GUTTER = 16.dp
private val SCREEN_TOP_SPACE = 24.dp
private val LOCKUP_SIZE = 48.dp
private val BUTTON_PROGRESS_SIZE = 18.dp

/** Route entry point: wires the ViewModel and the navigator to the stateless screen. */
@Composable
fun SignInRoute(viewModel: SignInViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    SignInScreen(
        state = state,
        onTokenChanged = viewModel::onTokenChanged,
        onToggleVisibility = viewModel::onToggleTokenVisibility,
        onSignIn = viewModel::onSignIn,
        onExploreDemo = viewModel::onExploreDemo,
        onGuidanceToggled = viewModel::onGuidanceToggled,
        onOpenTokenPage = { navigator.openInBrowser(TOKEN_PAGE_URL) },
    )
}

@Composable
fun SignInScreen(
    state: SignInUiState,
    onTokenChanged: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSignIn: () -> Unit,
    onExploreDemo: () -> Unit,
    onGuidanceToggled: () -> Unit,
    onOpenTokenPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The token is on screen in plain text: keep it out of screenshots and the Recents preview.
    SecureWindow(enabled = state.isTokenVisible)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_GUTTER, vertical = SCREEN_TOP_SPACE),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VirgaLockup(markSize = LOCKUP_SIZE)
        Text(
            text = stringResource(R.string.account_sign_in_tagline),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.account_sign_in_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        TokenField(state, onTokenChanged, onToggleVisibility)
        state.error?.let { error ->
            Text(
                text = error.title.asString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error.message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SignInButtons(state, onSignIn, onExploreDemo)
        TokenGuidance(
            expanded = state.isGuidanceExpanded,
            onToggle = onGuidanceToggled,
            onOpenTokenPage = onOpenTokenPage,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TokenField(
    state: SignInUiState,
    onTokenChanged: (String) -> Unit,
    onToggleVisibility: () -> Unit,
) {
    val visibilityLabel = stringResource(
        if (state.isTokenVisible) R.string.account_sign_in_hide_token else R.string.account_sign_in_show_token,
    )
    OutlinedTextField(
        value = state.token,
        onValueChange = onTokenChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.account_sign_in_token_label)) },
        singleLine = true,
        enabled = !state.isVerifying,
        visualTransformation = if (state.isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (state.isTokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = visibilityLabel,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignInButtons(state: SignInUiState, onSignIn: () -> Unit, onExploreDemo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onSignIn, enabled = !state.isVerifying, modifier = Modifier.fillMaxWidth()) {
            if (state.isVerifying) {
                LoadingIndicator(modifier = Modifier.size(BUTTON_PROGRESS_SIZE), color = LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.account_sign_in_verify))
        }
        OutlinedButton(onClick = onExploreDemo, enabled = !state.isVerifying, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_sign_in_demo))
        }
        Text(
            text = stringResource(R.string.account_sign_in_demo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
