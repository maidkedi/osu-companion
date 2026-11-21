package com.osuchat.app.ui.screens.login

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.osuchat.app.R
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.ui.components.OsuBannerTone
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.components.OsuInlineBanner
import com.osuchat.app.ui.components.OsuSheetTopBar
import com.osuchat.app.ui.components.PlayerAvatar
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import java.util.concurrent.atomic.AtomicBoolean

private const val LEGACY_IRC_PASSWORD_URL = "https://osu.ppy.sh/home/account/edit#legacy-api"

private enum class AuthAttemptType {
    OAUTH_EXTERNAL,
    OAUTH_IN_APP,
    STEP2_IRC,
    DIRECT_IRC
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showIrcOnlyFallback by rememberSaveable { mutableStateOf(false) }
    var showIrcTutorialModal by rememberSaveable { mutableStateOf(false) }
    var inAppOAuthUrl by remember { mutableStateOf<String?>(null) }
    var lastAttempt by rememberSaveable { mutableStateOf(AuthAttemptType.OAUTH_EXTERNAL) }
    var step2IrcPassword by remember { mutableStateOf("") }
    var ircUsername by rememberSaveable { mutableStateOf("") }
    var ircPassword by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val isStep2Onboarding = state.settings.authMode == AuthMode.OAUTH_V2 && !state.settings.hasIrcPassword
    val controlShape = RoundedCornerShape(OsuRadius.md)

    val openLegacyIrcSettings = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(state.legacyIrcSettingsUrl.ifBlank { LEGACY_IRC_PASSWORD_URL }))
            )
        }.onFailure {
            viewModel.reportError("No browser app found to open osu! Legacy API settings")
        }
        Unit
    }

    val launchExternalOAuth = {
        lastAttempt = AuthAttemptType.OAUTH_EXTERNAL
        viewModel.startOAuthLogin(context)
    }

    val launchInAppOAuth = {
        lastAttempt = AuthAttemptType.OAUTH_IN_APP
        inAppOAuthUrl = viewModel.prepareInAppOAuthUrl()
    }

    val submitStep2Irc = {
        if (!state.isIrcBusy && step2IrcPassword.isNotBlank()) {
            lastAttempt = AuthAttemptType.STEP2_IRC
            viewModel.completeIrcOnboarding(step2IrcPassword, onLoginSuccess)
        }
    }

    val submitDirectIrc = {
        if (!state.isIrcBusy && ircUsername.isNotBlank() && ircPassword.isNotBlank()) {
            lastAttempt = AuthAttemptType.DIRECT_IRC
            viewModel.loginWithIrc(ircUsername, ircPassword, onLoginSuccess)
        }
    }

    LaunchedEffect(state.isIrcAuthError, isStep2Onboarding) {
        if (state.isIrcAuthError && !isStep2Onboarding) {
            showIrcOnlyFallback = true
        }
    }

    inAppOAuthUrl?.let { authUrl ->
        InAppOAuthWebViewDialog(
            authorizeUrl = authUrl,
            isOAuthCallbackUri = viewModel::isOAuthCallbackUri,
            onRedirectUri = { uri ->
                inAppOAuthUrl = null
                viewModel.handleInAppOAuthRedirect(uri)
            },
            onDismiss = {
                inAppOAuthUrl = null
                viewModel.onInAppOAuthCancelled()
            }
        )
    }

    if (showIrcTutorialModal) {
        IrcPasswordTutorialDialog(
            onDismiss = { showIrcTutorialModal = false },
            onOpenLegacySettings = {
                showIrcTutorialModal = false
                openLegacyIrcSettings()
            }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            Spacer(modifier = Modifier.height(OsuSpacing.sm))

            // Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_osu_logo),
                    contentDescription = "osu! logo",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(OsuSpacing.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                ) {
                    Text(
                        text = "osu! Companion",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Bancho IRC, multiplayer lobbies & stats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(OsuSpacing.xs))

            if (!state.isNetworkOnline) {
                OsuInlineBanner(
                    text = "Offline — connect to sign in",
                    tone = OsuBannerTone.WARNING,
                    icon = Icons.Rounded.WifiOff
                )
            }

            if (state.isSessionExpired || !state.errorMessage.isNullOrBlank()) {
                val bannerText = state.errorMessage ?: "Session expired. Please sign in again."
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = controlShape,
                    border = osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    state.isIrcAuthError -> "Bancho IRC password rejected"
                                    state.isSessionExpired -> "Session expired"
                                    else -> "Sign-in failed"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::clearError,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = bannerText,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                state.isIrcAuthError -> {
                                    Button(
                                        onClick = openLegacyIrcSettings,
                                        shape = controlShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text("Legacy API settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.width(OsuSpacing.xs))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                state.isSessionExpired -> {
                                    Button(
                                        onClick = launchExternalOAuth,
                                        shape = controlShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text("Sign in again", maxLines = 1)
                                    }
                                }
                                else -> {
                                    OutlinedButton(
                                        onClick = {
                                            when {
                                                isStep2Onboarding && step2IrcPassword.isNotBlank() -> submitStep2Irc()
                                                lastAttempt == AuthAttemptType.OAUTH_IN_APP -> launchInAppOAuth()
                                                lastAttempt == AuthAttemptType.DIRECT_IRC &&
                                                    ircUsername.isNotBlank() &&
                                                    ircPassword.isNotBlank() -> submitDirectIrc()
                                                else -> launchExternalOAuth()
                                            }
                                        },
                                        shape = controlShape,
                                        border = osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = viewModel::clearError) {
                                Text(
                                    text = "Dismiss",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            if (isStep2Onboarding) {
                OsuGroupedContainer {
                    Column(modifier = Modifier.padding(OsuSpacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerAvatar(
                                name = state.settings.username.ifBlank { "osu!" },
                                avatarUrl = state.settings.avatarUrl,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(OsuSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.settings.username.ifBlank { "osu! Account" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "OAuth v2 connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = viewModel::resetOAuthAccount) {
                                Text("Switch")
                            }
                        }

                        OsuHairlineDivider(modifier = Modifier.padding(vertical = OsuSpacing.md))

                        Text(
                            text = "Bancho IRC password",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.xs))
                        Text(
                            text = "Required for live chat and DMs (Bancho legacy IRC uses unencrypted TCP on irc.ppy.sh:6667)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(OsuSpacing.md))

                        OutlinedTextField(
                            value = step2IrcPassword,
                            onValueChange = { step2IrcPassword = it },
                            label = { Text("Legacy IRC password") },
                            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            isError = state.isIrcAuthError,
                            shape = controlShape,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitStep2Irc() }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(OsuSpacing.sm))

                        OutlinedButton(
                            onClick = { showIrcTutorialModal = true },
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(OsuSpacing.sm))
                            Text(
                                text = "How to get IRC password",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.md))

                        Button(
                            onClick = submitStep2Irc,
                            enabled = !state.isIrcBusy && step2IrcPassword.isNotBlank(),
                            shape = controlShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (state.isIrcBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text(
                                    text = "Connecting to Bancho…",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Connect to chat",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                val isOAuthConnecting = state.isOAuthBusy
                OsuGroupedContainer {
                    Column(modifier = Modifier.padding(OsuSpacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sign in with osu!",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isOAuthConnecting) {
                                TextButton(onClick = viewModel::onInAppOAuthCancelled) {
                                    Text("Cancel")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(OsuSpacing.xs))
                        Text(
                            text = "OAuth v2 for profile stats, friends, and beatmaps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(OsuSpacing.lg))

                        Button(
                            onClick = launchExternalOAuth,
                            enabled = !isOAuthConnecting,
                            shape = controlShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isOAuthConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text(
                                    text = "Completing sign-in…",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Login with osu!",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.sm))

                        OutlinedButton(
                            onClick = launchInAppOAuth,
                            enabled = !isOAuthConnecting,
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(OsuSpacing.sm))
                            Text(
                                text = "Use in-app browser",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = {
                            showIrcOnlyFallback = !showIrcOnlyFallback
                            viewModel.clearError()
                        },
                        shape = controlShape
                    ) {
                        Text(
                            if (showIrcOnlyFallback) {
                                "Hide IRC-only login"
                            } else {
                                "IRC-only login (no OAuth)"
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showIrcOnlyFallback,
                    enter = OsuMotion.expandEnter(),
                    exit = OsuMotion.expandExit()
                ) {
                    OsuGroupedContainer {
                        Column(modifier = Modifier.padding(OsuSpacing.lg)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bancho IRC login",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "irc.ppy.sh:6667 · unencrypted TCP",
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(OsuSpacing.md))

                            OutlinedTextField(
                                value = ircUsername,
                                onValueChange = { ircUsername = it },
                                label = { Text("osu! username") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = controlShape,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(OsuSpacing.sm))

                            OutlinedTextField(
                                value = ircPassword,
                                onValueChange = { ircPassword = it },
                                label = { Text("Legacy IRC password") },
                                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                isError = state.isIrcAuthError,
                                shape = controlShape,
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { submitDirectIrc() }),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(OsuSpacing.md))

                            OutlinedButton(
                                onClick = { showIrcTutorialModal = true },
                                shape = controlShape,
                                border = osuHairlineBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("How to get IRC password", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Spacer(modifier = Modifier.height(OsuSpacing.sm))

                            Button(
                                onClick = submitDirectIrc,
                                enabled = !state.isIrcBusy && ircUsername.isNotBlank() && ircPassword.isNotBlank(),
                                shape = controlShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (state.isIrcBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text("Connecting…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text("Connect via IRC", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IrcPasswordTutorialDialog(
    onDismiss: () -> Unit,
    onOpenLegacySettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(OsuRadius.xl),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = osuHairlineBorder(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(OsuSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
            ) {
                Text(
                    text = "Get your Legacy IRC password",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Bancho chat uses your Legacy IRC password, separate from your web login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TutorialStepRow(
                    number = "1",
                    text = "Open osu! Legacy API settings and sign in."
                )
                TutorialStepRow(
                    number = "2",
                    text = "Scroll to the Legacy API section at the bottom."
                )
                TutorialStepRow(
                    number = "3",
                    text = "Reveal or reset your IRC password and paste it here."
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Button(
                        onClick = onOpenLegacySettings,
                        shape = RoundedCornerShape(OsuRadius.md)
                    ) {
                        Text("Open settings", maxLines = 1)
                        Spacer(Modifier.width(OsuSpacing.xs))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStepRow(
    number: String,
    text: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(OsuRadius.xs))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.tnum(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(OsuSpacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "RestrictedApi")
@Composable
private fun InAppOAuthWebViewDialog(
    authorizeUrl: String,
    isOAuthCallbackUri: (Uri?) -> Boolean,
    onRedirectUri: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var isPageLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val redirectHandled = remember(authorizeUrl) { AtomicBoolean(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(OsuRadius.xl),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = osuHairlineBorder(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xxl)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OsuSheetTopBar(
                    title = "Sign in with osu!",
                    subtitle = "osu.ppy.sh/oauth",
                    onClose = onDismiss
                )

                // Layout-stable 2dp indicator slot
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                ) {
                    if (isPageLoading && loadError == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                    }
                }

                if (loadError != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(OsuSpacing.xl),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Could not load osu! sign-in page",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.sm))
                        Text(
                            text = loadError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.lg))
                        Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(OsuRadius.md),
                                border = osuHairlineBorder()
                            ) {
                                Text("Close")
                            }
                            Button(
                                onClick = {
                                    loadError = null
                                    isPageLoading = true
                                    reloadTrigger++
                                },
                                shape = RoundedCornerShape(OsuRadius.md)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    userAgentString = userAgentString
                                        .replace("; wv", "")
                                        .replace("Version/4.0 ", "")
                                    runCatching {
                                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val uri = request?.url ?: return false
                                        if (isOAuthCallbackUri(uri)) {
                                            if (redirectHandled.compareAndSet(false, true)) {
                                                view?.stopLoading()
                                                onRedirectUri(uri)
                                            }
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                        if (uri != null && isOAuthCallbackUri(uri)) {
                                            if (redirectHandled.compareAndSet(false, true)) {
                                                view?.stopLoading()
                                                onRedirectUri(uri)
                                            }
                                            return
                                        }
                                        loadError = null
                                        isPageLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            isPageLoading = false
                                            loadError = "Check your connection and retry"
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: WebResourceResponse?
                                    ) {
                                        if (request?.isForMainFrame == true &&
                                            (errorResponse?.statusCode ?: 0) >= 500
                                        ) {
                                            isPageLoading = false
                                            loadError = "osu! server error — retry shortly"
                                        }
                                    }
                                }
                                loadUrl(authorizeUrl)
                            }
                        },
                        update = { webView ->
                            if (reloadTrigger > 0 && webView.tag != reloadTrigger) {
                                webView.tag = reloadTrigger
                                webView.loadUrl(authorizeUrl)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        }
                    )
                }
            }
        }
    }
}
