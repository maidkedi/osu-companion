package com.osuchat.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.osuchat.app.data.network.CloudflareChallengeManager
import com.osuchat.app.data.network.OAuthPkceManager
import com.osuchat.app.data.repository.AppUpdateRepository
import com.osuchat.app.data.repository.UpdateCheckResult
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.service.ActiveMpTimer
import com.osuchat.app.service.ChatConnectionService
import com.osuchat.app.service.MpTimerManager
import com.osuchat.app.ui.components.AppUpdateDialog
import com.osuchat.app.ui.screens.chat.ChatScreen
import com.osuchat.app.ui.screens.home.HomeScreen
import com.osuchat.app.ui.screens.login.LoginScreen
import com.osuchat.app.ui.screens.match.MatchAnalyzerScreen
import com.osuchat.app.ui.screens.settings.SettingsScreen
import com.osuchat.app.ui.theme.OsuChatTheme
import com.osuchat.app.ui.theme.OsuPink
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.StatusOffline
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.AppShortcutManager
import com.osuchat.app.util.ExternalOsuTarget
import com.osuchat.app.util.OsuIntentRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var cloudflareChallengeManager: CloudflareChallengeManager

    @Inject
    lateinit var oauthPkceManager: OAuthPkceManager

    @Inject
    lateinit var mpTimerManager: MpTimerManager

    @Inject
    lateinit var appUpdateRepository: AppUpdateRepository

    private var deepLinkChannelId = mutableStateOf<String?>(null)
    private var pendingShortcutAction = mutableStateOf<String?>(null)
    private var pendingExternalTarget = mutableStateOf<ExternalOsuTarget?>(null)
    private var lastHandledOAuthUri: String? = null
    private var shutdownReceiverRegistered = false

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ChatConnectionService.ACTION_APP_SHUTDOWN) {
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastHandledOAuthUri = savedInstanceState?.getString(STATE_LAST_OAUTH_URI)
        enableEdgeToEdge()
        handleIntent(intent)

        ContextCompat.registerReceiver(
            this,
            shutdownReceiver,
            IntentFilter(ChatConnectionService.ACTION_APP_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        shutdownReceiverRegistered = true

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                runCatching { appUpdateRepository.cleanupOldApks() }
                val checkResult = runCatching {
                    appUpdateRepository.checkForUpdate(force = false)
                }.getOrNull()
                if (checkResult is UpdateCheckResult.UpdateAvailable) {
                    appUpdateRepository.presentUpdate(checkResult.update)
                }
            }
        }

        setContent {
            val settingsState by chatRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val activeChallengeUrl by cloudflareChallengeManager.challengeUrl.collectAsStateWithLifecycle()
            val updateDialogState by appUpdateRepository.dialogState.collectAsStateWithLifecycle()
            val connectionStatus by chatRepository.connectionStatus.collectAsStateWithLifecycle()
            val connectionError by chatRepository.connectionError.collectAsStateWithLifecycle()
            val isIrcAuthError by chatRepository.isIrcAuthError.collectAsStateWithLifecycle()
            val isSessionExpired by chatRepository.isSessionExpired.collectAsStateWithLifecycle()
            val activeMpTimers by mpTimerManager.activeTimers.collectAsStateWithLifecycle()
            val currentSettings = settingsState

            if (currentSettings == null) {
                OsuChatTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        }
                    }
                }
            } else {
                OsuChatTheme(themeMode = currentSettings.themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        OsuChatRootNavigation(
                            settings = currentSettings,
                            connectionStatus = connectionStatus,
                            connectionError = connectionError,
                            isIrcAuthError = isIrcAuthError,
                            isSessionExpired = isSessionExpired,
                            activeMpTimers = activeMpTimers,
                            pendingOpenChannelId = deepLinkChannelId.value,
                            onConsumedOpenChannel = { deepLinkChannelId.value = null },
                            pendingShortcutAction = pendingShortcutAction.value,
                            onConsumedShortcutAction = { pendingShortcutAction.value = null },
                            pendingExternalTarget = pendingExternalTarget.value,
                            onConsumedExternalTarget = { pendingExternalTarget.value = null },
                            onJoinAndOpenMpLobby = { matchId ->
                                lifecycleScope.launch {
                                    chatRepository.joinMultiplayerLobby(matchId.toString())
                                        .onSuccess { ch ->
                                            deepLinkChannelId.value = ch.id
                                        }
                                        .onFailure {
                                            deepLinkChannelId.value = "#mp_$matchId"
                                        }
                                }
                            },
                            onEnsureBackgroundService = {
                                val ready = currentSettings.authMode == AuthMode.IRC ||
                                    (currentSettings.authMode == AuthMode.OAUTH_V2 && currentSettings.hasIrcPassword)
                                if (currentSettings.keepAliveService &&
                                    ready &&
                                    !currentSettings.isExplicitlyDisconnected
                                ) {
                                    ChatConnectionService.start(this)
                                }
                            },
                            onReconnect = {
                                lifecycleScope.launch {
                                    chatRepository.clearConnectionError()
                                    if (currentSettings.isExplicitlyDisconnected) {
                                        chatRepository.setExplicitlyDisconnected(false)
                                    }
                                    chatRepository.reconnectIfAuthenticated()
                                }
                            },
                            onSignInAgain = {
                                lifecycleScope.launch {
                                    chatRepository.clearConnectionError()
                                    chatRepository.logout()
                                }
                            },
                            onDismissConnectionError = {
                                chatRepository.clearConnectionError()
                            }
                        )

                        activeChallengeUrl?.let { challengeUrl ->
                            CloudflareVerificationDialog(
                                challengeUrl = challengeUrl,
                                onVerified = { cloudflareChallengeManager.onVerified() },
                                onCancelled = { cloudflareChallengeManager.onCancelled() }
                            )
                        }

                        AppUpdateDialog(
                            state = updateDialogState,
                            onStartDownload = appUpdateRepository::startDownload,
                            onCancelDownload = appUpdateRepository::cancelDownload,
                            onDismiss = appUpdateRepository::dismissDialog,
                            onSkipVersion = appUpdateRepository::skipCurrentUpdate,
                            onAwaitingPermission = appUpdateRepository::markAwaitingInstallPermission,
                            onReadyToInstall = appUpdateRepository::markReadyToInstall,
                            onInstallError = appUpdateRepository::markFailed
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastHandledOAuthUri?.let { outState.putString(STATE_LAST_OAUTH_URI, it) }
    }

    override fun onDestroy() {
        if (shutdownReceiverRegistered) {
            runCatching { unregisterReceiver(shutdownReceiver) }
            shutdownReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val launchedFromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (!launchedFromHistory && intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)) {
            intent.removeExtra(EXTRA_AUTO_CONNECT)
            setIntent(intent)
            lifecycleScope.launch {
                chatRepository.clearConnectionError()
                chatRepository.setExplicitlyDisconnected(false)
                val settings = chatRepository.settings.first()
                val ready = settings.authMode == AuthMode.IRC ||
                    (settings.authMode == AuthMode.OAUTH_V2 && settings.hasIrcPassword)
                if (ready) {
                    ChatConnectionService.start(this@MainActivity)
                    chatRepository.reconnectIfAuthenticated()
                }
            }
        }

        val data = intent.data
        var handledSpecialIntent = false
        if (data != null && oauthPkceManager.isOAuthCallbackUri(data)) {
            handledSpecialIntent = true
            val uriStr = data.toString()
            intent.data = null
            setIntent(intent)
            if (!launchedFromHistory && uriStr != lastHandledOAuthUri) {
                lastHandledOAuthUri = uriStr
                lifecycleScope.launch {
                    try {
                        val res = chatRepository.handleOAuthRedirect(data)
                        if (res.isSuccess) {
                            val settings = chatRepository.settings.first()
                            val ready = settings.authMode == AuthMode.IRC ||
                                (settings.authMode == AuthMode.OAUTH_V2 && settings.hasIrcPassword)
                            if (settings.keepAliveService && ready && !settings.isExplicitlyDisconnected) {
                                ChatConnectionService.start(this@MainActivity)
                            }
                        } else {
                            oauthPkceManager.stopLoopbackServer()
                        }
                    } finally {
                        if (!oauthPkceManager.isExchangeInFlight()) {
                            chatRepository.clearPendingOAuthCode()
                        }
                    }
                }
            }
        }

        val openChannel = intent.getStringExtra(EXTRA_OPEN_CHANNEL_ID)
            ?: intent.getStringExtra(AppShortcutManager.EXTRA_CHANNEL_ID)
        if (!openChannel.isNullOrBlank()) {
            handledSpecialIntent = true
            intent.removeExtra(EXTRA_OPEN_CHANNEL_ID)
            intent.removeExtra(AppShortcutManager.EXTRA_CHANNEL_ID)
            setIntent(intent)
            deepLinkChannelId.value = openChannel
        }

        val shortcutAction = intent.getStringExtra(AppShortcutManager.EXTRA_SHORTCUT_ACTION)
        if (!shortcutAction.isNullOrBlank()) {
            handledSpecialIntent = true
            intent.removeExtra(AppShortcutManager.EXTRA_SHORTCUT_ACTION)
            setIntent(intent)
            pendingShortcutAction.value = shortcutAction
        }

        if (!handledSpecialIntent) {
            val externalTarget = OsuIntentRouter.parseIntent(intent)
            if (externalTarget != null) {
                intent.action = Intent.ACTION_MAIN
                intent.data = null
                intent.removeExtra(Intent.EXTRA_TEXT)
                setIntent(intent)
                pendingExternalTarget.value = externalTarget
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_CHANNEL_ID = "extra_open_channel_id"
        const val EXTRA_AUTO_CONNECT = "extra_auto_connect"
        private const val STATE_LAST_OAUTH_URI = "state_last_oauth_uri"
    }
}

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{channelId}?focusMessageId={focusMessageId}"
    const val MATCH = "match/{matchId}"

    fun chatRoute(channelId: String, focusMessageId: String? = null): String {
        val base = "chat/${Uri.encode(channelId)}"
        return if (!focusMessageId.isNullOrBlank()) {
            "$base?focusMessageId=${Uri.encode(focusMessageId)}"
        } else {
            base
        }
    }

    fun matchRoute(matchId: Long): String = "match/$matchId"
}

@Composable
private fun OsuChatRootNavigation(
    settings: AppSettings,
    connectionStatus: ConnectionStatus,
    connectionError: String?,
    isIrcAuthError: Boolean,
    isSessionExpired: Boolean,
    activeMpTimers: Map<String, ActiveMpTimer>,
    pendingOpenChannelId: String?,
    onConsumedOpenChannel: () -> Unit,
    pendingShortcutAction: String?,
    onConsumedShortcutAction: () -> Unit,
    pendingExternalTarget: ExternalOsuTarget?,
    onConsumedExternalTarget: () -> Unit,
    onJoinAndOpenMpLobby: (Long) -> Unit,
    onEnsureBackgroundService: () -> Unit,
    onReconnect: () -> Unit,
    onSignInAgain: () -> Unit,
    onDismissConnectionError: () -> Unit
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    var twoPaneChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var twoPaneFocusMessageId by rememberSaveable { mutableStateOf<String?>(null) }

    var hasPromptedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Request POST_NOTIFICATIONS at runtime on first launch (Android 13+)
    LaunchedEffect(Unit) {
        if (!hasPromptedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPromptedNotificationPermission = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val isAuthenticated = settings.authMode == AuthMode.IRC ||
        (settings.authMode == AuthMode.OAUTH_V2 && settings.hasIrcPassword)

    LaunchedEffect(isAuthenticated, settings.isExplicitlyDisconnected) {
        if (isAuthenticated && !settings.isExplicitlyDisconnected) {
            onEnsureBackgroundService()
        }
    }

    LaunchedEffect(isAuthenticated) {
        val currentRoute = navController.currentDestination?.route
        if (isAuthenticated && currentRoute == Routes.LOGIN) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
                launchSingleTop = true
            }
        } else if (!isAuthenticated && currentRoute != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pendingOpenChannelId, isAuthenticated, isWideScreen) {
        if (isAuthenticated && !pendingOpenChannelId.isNullOrBlank()) {
            if (isWideScreen) {
                twoPaneChannelId = pendingOpenChannelId
                twoPaneFocusMessageId = null
                val destRoute = navController.currentDestination?.route
                if (destRoute != null && destRoute != Routes.HOME) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            } else {
                navController.navigate(Routes.chatRoute(pendingOpenChannelId)) {
                    launchSingleTop = true
                }
            }
            onConsumedOpenChannel()
        }
    }

    LaunchedEffect(pendingShortcutAction, isAuthenticated) {
        if (isAuthenticated && !pendingShortcutAction.isNullOrBlank()) {
            val destRoute = navController.currentDestination?.route
            if (destRoute != null && destRoute != Routes.HOME) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(pendingExternalTarget, isAuthenticated) {
        val target = pendingExternalTarget ?: return@LaunchedEffect
        if (!isAuthenticated) return@LaunchedEffect
        val destRoute = navController.currentDestination?.route
        if (destRoute != null && destRoute != Routes.HOME) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    val startDest = rememberSaveable {
        if (isAuthenticated) Routes.HOME else Routes.LOGIN
    }

    var lastNavClickMs by remember { mutableLongStateOf(0L) }
    fun navigateDebounced(block: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNavClickMs >= 300L) {
            lastNavClickMs = now
            block()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(isWideScreen, currentRoute) {
        if (isWideScreen && currentRoute == Routes.CHAT) {
            val ch = navBackStackEntry?.arguments?.getString("channelId")?.let(Uri::decode)?.trim()
            val focusMsg = navBackStackEntry?.arguments?.getString("focusMessageId")?.let(Uri::decode)?.takeIf { it.isNotBlank() }
            if (!ch.isNullOrBlank()) {
                twoPaneChannelId = ch
                twoPaneFocusMessageId = focusMsg
            }
            navController.popBackStack(Routes.HOME, inclusive = false)
        } else if (!isWideScreen && currentRoute == Routes.HOME && !twoPaneChannelId.isNullOrBlank()) {
            val ch = twoPaneChannelId!!
            val focusMsg = twoPaneFocusMessageId
            twoPaneChannelId = null
            twoPaneFocusMessageId = null
            navController.navigate(Routes.chatRoute(ch, focusMsg)) {
                launchSingleTop = true
            }
        }
    }

    val currentChatChannelId = remember(navBackStackEntry, currentRoute, isWideScreen, twoPaneChannelId) {
        when {
            currentRoute == Routes.CHAT ->
                navBackStackEntry?.arguments?.getString("channelId")?.let(Uri::decode)
            isWideScreen && currentRoute == Routes.HOME ->
                twoPaneChannelId
            else -> null
        }
    }
    var dismissedTimerKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val errorStateKey = "$isSessionExpired:$isIrcAuthError:$connectionStatus:${connectionError.orEmpty()}"
    var dismissedErrorKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(connectionStatus, isSessionExpired, isIrcAuthError) {
        if (connectionStatus == ConnectionStatus.ONLINE && !isSessionExpired && !isIrcAuthError) {
            dismissedErrorKey = null
        }
    }

    val bannerState = if (isAuthenticated && currentRoute != null && currentRoute != Routes.LOGIN) {
        when {
            isSessionExpired && dismissedErrorKey != errorStateKey -> GlobalBannerState(
                message = stringResource(R.string.conn_session_expired),
                actionLabel = stringResource(R.string.action_sign_in),
                isError = true,
                onAction = onSignInAgain,
                onDismiss = {
                    dismissedErrorKey = errorStateKey
                    onDismissConnectionError()
                }
            )
            isIrcAuthError && dismissedErrorKey != errorStateKey -> GlobalBannerState(
                message = stringResource(R.string.conn_irc_auth_error),
                actionLabel = if (currentRoute == Routes.SETTINGS) {
                    stringResource(R.string.action_retry)
                } else {
                    stringResource(R.string.action_settings)
                },
                isError = true,
                onAction = {
                    if (currentRoute == Routes.SETTINGS) {
                        onReconnect()
                    } else {
                        navigateDebounced {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onDismiss = {
                    dismissedErrorKey = errorStateKey
                    onDismissConnectionError()
                }
            )
            connectionStatus == ConnectionStatus.ERROR && dismissedErrorKey != errorStateKey -> GlobalBannerState(
                message = sanitizeConnectionError(
                    connectionError,
                    fallback = stringResource(R.string.conn_error_generic)
                ),
                actionLabel = stringResource(R.string.action_retry),
                isError = true,
                onAction = onReconnect,
                onDismiss = {
                    dismissedErrorKey = errorStateKey
                    onDismissConnectionError()
                }
            )
            connectionStatus == ConnectionStatus.OFFLINE -> GlobalBannerState(
                message = if (settings.isExplicitlyDisconnected) {
                    stringResource(R.string.conn_disconnected_cached)
                } else {
                    stringResource(R.string.conn_offline_cached)
                },
                actionLabel = if (settings.isExplicitlyDisconnected) {
                    stringResource(R.string.action_connect)
                } else {
                    stringResource(R.string.action_reconnect)
                },
                isError = false,
                onAction = onReconnect,
                onDismiss = null
            )
            else -> null
        }
    } else {
        null
    }

    val activeBannerTimer = remember(
        activeMpTimers,
        currentChatChannelId,
        dismissedTimerKeys,
        isAuthenticated,
        currentRoute
    ) {
        if (!isAuthenticated || currentRoute == null || currentRoute == Routes.LOGIN) {
            null
        } else {
            activeMpTimers.values
                .filter { timer ->
                    timer.remainingSeconds > 0 &&
                        !timer.channelId.equals(currentChatChannelId, ignoreCase = true) &&
                        "${timer.channelId}_${timer.endTimeMillis}" !in dismissedTimerKeys
                }
                .minByOrNull { it.remainingSeconds }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (bannerState != null) {
            GlobalConnectionBanner(state = bannerState)
        }
        if (activeBannerTimer != null) {
            GlobalMpTimerBanner(
                timer = activeBannerTimer,
                applyStatusBarsPadding = bannerState == null,
                onOpenLobby = {
                    navigateDebounced {
                        if (isWideScreen) {
                            twoPaneChannelId = activeBannerTimer.channelId
                            twoPaneFocusMessageId = null
                            if (currentRoute != Routes.HOME) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            navController.navigate(Routes.chatRoute(activeBannerTimer.channelId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onDismiss = {
                    dismissedTimerKeys = dismissedTimerKeys +
                        "${activeBannerTimer.channelId}_${activeBannerTimer.endTimeMillis}"
                }
            )
        }

        val hasTopBanner = bannerState != null || activeBannerTimer != null
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .let { mod ->
                    if (hasTopBanner) {
                        mod.consumeWindowInsets(WindowInsets.statusBars)
                    } else {
                        mod
                    }
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = startDest,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onLoginSuccess = {
                            onEnsureBackgroundService()
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenChannel = { channelId ->
                            navigateDebounced {
                                if (isWideScreen) {
                                    twoPaneChannelId = channelId
                                    twoPaneFocusMessageId = null
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.HOME) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(Routes.chatRoute(channelId)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onOpenChannelMessage = { channelId, messageId ->
                            navigateDebounced {
                                if (isWideScreen) {
                                    twoPaneChannelId = channelId
                                    twoPaneFocusMessageId = messageId
                                    if (currentRoute != Routes.HOME) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                } else {
                                    navController.navigate(Routes.chatRoute(channelId, messageId)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onOpenMatchAnalyzer = { matchId ->
                            navigateDebounced {
                                navController.navigate(Routes.matchRoute(matchId)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenSettings = {
                            navigateDebounced {
                                navController.navigate(Routes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        pendingExternalTarget = pendingExternalTarget,
                        onConsumedExternalTarget = onConsumedExternalTarget,
                        pendingShortcutAction = pendingShortcutAction,
                        onConsumedShortcutAction = onConsumedShortcutAction,
                        selectedTwoPaneChannelId = twoPaneChannelId,
                        selectedTwoPaneFocusMessageId = twoPaneFocusMessageId,
                        onSelectTwoPaneChannel = { chId, msgId ->
                            twoPaneChannelId = chId
                            twoPaneFocusMessageId = msgId
                        }
                    )
                }

                composable(
                    route = Routes.CHAT,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("focusMessageId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) {
                    ChatScreen(
                        onBack = {
                            navigateDebounced { navController.popBackStack() }
                        },
                        onNavigateToChannel = { targetChannelId ->
                            navigateDebounced {
                                navController.navigate(Routes.chatRoute(targetChannelId)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenMatchAnalyzer = { matchId ->
                            navigateDebounced {
                                navController.navigate(Routes.matchRoute(matchId)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenSettings = {
                            navigateDebounced {
                                navController.navigate(Routes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }

                composable(
                    route = Routes.MATCH,
                    arguments = listOf(navArgument("matchId") { type = NavType.LongType })
                ) {
                    MatchAnalyzerScreen(
                        onBack = {
                            navigateDebounced { navController.popBackStack() }
                        },
                        onOpenChannel = { channelId ->
                            navigateDebounced {
                                if (isWideScreen) {
                                    twoPaneChannelId = channelId
                                    twoPaneFocusMessageId = null
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.HOME) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(Routes.chatRoute(channelId)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = {
                            navigateDebounced { navController.popBackStack() }
                        },
                        onLoggedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class GlobalBannerState(
    val message: String,
    val actionLabel: String,
    val isError: Boolean,
    val onAction: () -> Unit,
    val onDismiss: (() -> Unit)?
)

@Composable
private fun GlobalConnectionBanner(state: GlobalBannerState) {
    val bgColor = if (state.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val fgColor = if (state.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (state.isError) {
        MaterialTheme.colorScheme.error
    } else {
        StatusOffline
    }
    val borderColor = if (state.isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (state.isError) Icons.Default.ErrorOutline else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = fgColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = state.onAction,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = state.actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isError) fgColor else MaterialTheme.colorScheme.primary
                    )
                }
                if (state.onDismiss != null) {
                    IconButton(
                        onClick = state.onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = fgColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = OsuRadius.hairline, color = borderColor)
        }
    }
}

@Composable
private fun GlobalMpTimerBanner(
    timer: ActiveMpTimer,
    applyStatusBarsPadding: Boolean,
    onOpenLobby: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUrgent = timer.remainingSeconds <= 15
    val bgColor = if (isUrgent) {
        OsuPink.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val accentColor = if (isUrgent) OsuPink else MaterialTheme.colorScheme.primary
    val borderColor = if (isUrgent) {
        OsuPink.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val mins = timer.remainingSeconds / 60
    val secs = timer.remainingSeconds % 60
    val timeText = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLobby),
        color = bgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (applyStatusBarsPadding) it.statusBarsPadding() else it }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${timer.roomName} · ${timer.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(OsuRadius.xs),
                    color = accentColor.copy(alpha = 0.15f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(OsuRadius.hairline, accentColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium.tnum(),
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss timer banner",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            HorizontalDivider(thickness = OsuRadius.hairline, color = borderColor)
        }
    }
}

private fun sanitizeConnectionError(raw: String?, fallback: String): String {
    if (raw.isNullOrBlank()) return fallback
    val trimmed = raw.trim()
    val looksTechnical = trimmed.contains("Exception", ignoreCase = true) ||
        trimmed.contains("java.") ||
        trimmed.contains("javax.") ||
        trimmed.contains("okhttp", ignoreCase = true) ||
        trimmed.contains("http://", ignoreCase = true) ||
        trimmed.contains("https://", ignoreCase = true) ||
        trimmed.length > 96
    if (!looksTechnical) return trimmed

    return when {
        trimmed.contains("timeout", ignoreCase = true) ||
            trimmed.contains("timed out", ignoreCase = true) -> "Connection timed out. Retry."
        trimmed.contains("ssl", ignoreCase = true) ||
            trimmed.contains("tls", ignoreCase = true) ||
            trimmed.contains("certificate", ignoreCase = true) -> "Secure connection failed. Retry."
        trimmed.contains("host", ignoreCase = true) ||
            trimmed.contains("connect", ignoreCase = true) ||
            trimmed.contains("network", ignoreCase = true) -> "Unable to reach Bancho. Check network."
        else -> fallback
    }
}

@SuppressLint("SetJavaScriptEnabled", "RestrictedApi")
@Composable
private fun CloudflareVerificationDialog(
    challengeUrl: String,
    onVerified: () -> Unit,
    onCancelled: () -> Unit
) {
    val targetUrl = remember(challengeUrl) {
        if (challengeUrl.isBlank() ||
            challengeUrl.contains("/api/", ignoreCase = true) ||
            CloudflareChallengeManager.isOAuthEndpoint(challengeUrl)
        ) {
            CloudflareChallengeManager.DEFAULT_OSU_URL
        } else {
            challengeUrl
        }
    }
    val resolvedOnce = remember(challengeUrl) { AtomicBoolean(false) }
    val initialCfClearance = remember(challengeUrl) {
        CloudflareChallengeManager.extractCfClearance(
            runCatching {
                CookieManager.getInstance().getCookie("https://osu.ppy.sh")
            }.getOrNull()
        )
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewLoading by remember(challengeUrl) { mutableStateOf(true) }

    fun completeVerified() {
        if (resolvedOnce.compareAndSet(false, true)) {
            runCatching { CookieManager.getInstance().flush() }
            onVerified()
        }
    }

    fun completeCancelled() {
        if (resolvedOnce.compareAndSet(false, true)) {
            onCancelled()
        }
    }

    fun hasFreshCfClearance(): Boolean {
        val cookieStr = runCatching {
            CookieManager.getInstance().getCookie("https://osu.ppy.sh")
        }.getOrNull()
        val cfValue = CloudflareChallengeManager.extractCfClearance(cookieStr)
        return !cfValue.isNullOrBlank() && cfValue != initialCfClearance
    }

    LaunchedEffect(challengeUrl) {
        while (isActive && !resolvedOnce.get()) {
            delay(500L)
            if (hasFreshCfClearance()) {
                completeVerified()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.destroy()
                }
            }
            webViewRef = null
        }
    }

    val dialogShape = RoundedCornerShape(OsuRadius.lg)
    val webViewShape = RoundedCornerShape(OsuRadius.xs)

    Dialog(
        onDismissRequest = { completeCancelled() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(OsuRadius.hairline, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.cf_verify_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cf_verify_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(webViewShape)
                        .border(OsuRadius.hairline, MaterialTheme.colorScheme.outlineVariant, webViewShape)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                runCatching {
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                    }
                                }
                                settings.userAgentString = settings.userAgentString
                                    .replace("; wv", "")
                                    .replace(Regex("""Version/\d+\.\d+\s*"""), "")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isWebViewLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                        if (hasFreshCfClearance()) {
                                            completeVerified()
                                        }
                                    }
                                }
                                webViewRef = this
                                loadUrl(targetUrl)
                            }
                        }
                    )

                    if (isWebViewLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = { completeCancelled() },
                        shape = RoundedCornerShape(OsuRadius.md)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    OutlinedButton(
                        onClick = { completeVerified() },
                        shape = RoundedCornerShape(OsuRadius.md)
                    ) {
                        Text(stringResource(R.string.cf_verify_retry))
                    }
                }
            }
        }
    }
}
