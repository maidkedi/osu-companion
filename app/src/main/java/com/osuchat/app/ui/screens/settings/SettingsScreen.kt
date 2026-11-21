package com.osuchat.app.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osuchat.app.BuildConfig
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.OfficialOsuChannels
import com.osuchat.app.domain.model.ParsedBackup
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ThemeMode
import com.osuchat.app.domain.model.WidgetTheme
import com.osuchat.app.ui.components.ConnectionStatusChip
import com.osuchat.app.ui.components.MappoolManagerDialog
import com.osuchat.app.ui.components.OsuBannerTone
import com.osuchat.app.ui.components.OsuFilterChip
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.components.OsuInlineBanner
import com.osuchat.app.ui.components.OsuMicroTag
import com.osuchat.app.ui.components.OsuSectionHeader
import com.osuchat.app.ui.components.OsuSegmentedBar
import com.osuchat.app.ui.components.PlayerAvatar
import com.osuchat.app.ui.components.WallpaperCustomizerPanel
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.WidgetPreviewColors
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context, uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupForRestore(context, uri)
        }
    }

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    var newKeyword by rememberSaveable { mutableStateOf("") }
    var keywordError by rememberSaveable { mutableStateOf<String?>(null) }
    var newIgnoredUser by rememberSaveable { mutableStateOf("") }
    var ignoredUserError by rememberSaveable { mutableStateOf<String?>(null) }
    var newAutoJoinChannel by rememberSaveable { mutableStateOf("") }
    var autoJoinChannelError by rememberSaveable { mutableStateOf<String?>(null) }
    var showMappoolManager by rememberSaveable { mutableStateOf(false) }

    if (showMappoolManager) {
        MappoolManagerDialog(
            mappools = state.mappools,
            onSavePool = viewModel::saveMappool,
            onDeletePool = viewModel::deleteMappool,
            onDismiss = { showMappoolManager = false },
            onGetCachedBeatmaps = viewModel::getCachedBeatmaps,
            onResolveBeatmapDetails = viewModel::resolveBeatmapDetails
        )
    }

    state.pendingRestoreBackup?.let { parsed ->
        BackupRestoreConfirmationDialog(
            parsed = parsed,
            isBusy = state.isBackupBusy,
            onConfirm = { viewModel.confirmRestoreBackup(context) },
            onDismiss = viewModel::dismissRestoreConfirmation
        )
    }

    val surfaces = LocalOsuSurfaces.current
    val controlShape = RoundedCornerShape(OsuRadius.md)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                OsuHairlineDivider()
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(OsuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.lg)
        ) {
            if (!state.actionError.isNullOrBlank()) {
                OsuInlineBanner(
                    text = state.actionError.orEmpty(),
                    tone = OsuBannerTone.ERROR,
                    icon = Icons.Rounded.ErrorOutline,
                    onDismiss = viewModel::clearActionError
                )
            }

            // 1. Account & connection (account identity, background sync, battery optimization, auto-join channels)
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                OsuSectionHeader(title = "Account & connection")
                OsuGroupedContainer {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(
                                name = settings.username.ifBlank { "osu!" },
                                avatarUrl = state.selfProfile?.avatarUrl ?: settings.avatarUrl,
                                size = 48.dp
                            )
                            Spacer(Modifier.width(OsuSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.username.ifBlank { "Not signed in" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (settings.authMode) {
                                        AuthMode.OAUTH_V2 -> "osu! OAuth v2 + Bancho IRC (irc.ppy.sh:6667 TCP)"
                                        AuthMode.IRC -> "Bancho IRC (irc.ppy.sh:6667 · unencrypted TCP)"
                                        AuthMode.NONE -> "Disconnected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = surfaces.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(OsuSpacing.xs))
                                ConnectionStatusChip(status = state.connectionStatus)
                            }
                        }

                        val canReconnect = settings.authMode != AuthMode.NONE &&
                            (state.connectionStatus == ConnectionStatus.OFFLINE ||
                                state.connectionStatus == ConnectionStatus.ERROR)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            if (canReconnect) {
                                OutlinedButton(
                                    onClick = { viewModel.reconnect(context) },
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text("Reconnect", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.logout(context, onLoggedOut) },
                                enabled = !state.isLoggingOut,
                                shape = controlShape,
                                border = osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isLoggingOut) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text("Signing out…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text("Log Out", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                            SwitchRow(
                                title = "Keep connection alive",
                                subtitle = "Foreground service keeps Bancho connected",
                                checked = settings.keepAliveService,
                                onCheckedChange = { viewModel.setKeepAliveService(context, it) }
                            )
                            SwitchRow(
                                title = "Auto-connect on boot",
                                subtitle = "Reconnect after device restart",
                                checked = settings.autoConnectOnBoot,
                                onCheckedChange = viewModel::setAutoConnectOnBoot
                            )
                        }

                        OsuHairlineDivider()

                        BatteryOptimizationSection()
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        SwitchRow(
                            title = "Auto-join on connect",
                            subtitle = "Join these #channels when Bancho connects",
                            checked = settings.autoJoinEnabled,
                            onCheckedChange = viewModel::setAutoJoinEnabled
                        )

                        if (settings.autoJoinChannels.isEmpty()) {
                            Text(
                                text = "No startup channels configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textMuted
                            )
                        } else {
                            Column {
                                settings.autoJoinChannels.forEachIndexed { index, channel ->
                                    if (index > 0) {
                                        OsuHairlineDivider()
                                    }
                                    val isOfficial = OfficialOsuChannels.isOfficial(channel)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = OsuSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.labelMedium.tnum(),
                                            color = surfaces.textMuted,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isOfficial) "Official channel" else "Custom channel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = surfaces.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveAutoJoinChannel(index, -1) },
                                            enabled = index > 0 && settings.autoJoinEnabled
                                        ) {
                                            Icon(
                                                Icons.Rounded.KeyboardArrowUp,
                                                contentDescription = "Move $channel up"
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveAutoJoinChannel(index, 1) },
                                            enabled = index < settings.autoJoinChannels.lastIndex && settings.autoJoinEnabled
                                        ) {
                                            Icon(
                                                Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Move $channel down"
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeAutoJoinChannel(channel) }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove $channel"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val quickOfficialSuggestions = remember(settings.autoJoinChannels) {
                            val existing = settings.autoJoinChannels.map { it.lowercase() }.toSet()
                            (OfficialOsuChannels.PRIORITY + listOf("#english", "#japanese"))
                                .distinct()
                                .filter { it.lowercase() !in existing }
                                .take(6)
                        }
                        if (quickOfficialSuggestions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Quick add official channels",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = surfaces.textSecondary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    quickOfficialSuggestions.forEach { suggestion ->
                                        OsuFilterChip(
                                            label = suggestion,
                                            selected = false,
                                            leadingIcon = Icons.Rounded.Add,
                                            onClick = {
                                                autoJoinChannelError = viewModel.addAutoJoinChannel(suggestion)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newAutoJoinChannel,
                                onValueChange = {
                                    newAutoJoinChannel = it
                                    autoJoinChannelError = null
                                },
                                label = { Text("Add #channel (e.g. #osu, #mapping)") },
                                singleLine = true,
                                isError = autoJoinChannelError != null,
                                supportingText = autoJoinChannelError?.let { err -> { Text(err) } },
                                shape = controlShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val err = viewModel.addAutoJoinChannel(newAutoJoinChannel)
                                        autoJoinChannelError = err
                                        if (err == null) {
                                            newAutoJoinChannel = ""
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(OsuSpacing.sm))
                            IconButton(
                                onClick = {
                                    val err = viewModel.addAutoJoinChannel(newAutoJoinChannel)
                                    autoJoinChannelError = err
                                    if (err == null) {
                                        newAutoJoinChannel = ""
                                    }
                                },
                                enabled = newAutoJoinChannel.isNotBlank()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add startup channel")
                            }
                        }
                    }
                }
            }

            // 2. Appearance (app theme, AMOLED black, global chat wallpaper)
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                OsuSectionHeader(title = "Appearance")
                OsuGroupedContainer {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        val baseThemeModes = remember {
                            listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                        }
                        val selectedThemeIdx = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> 0
                            ThemeMode.LIGHT -> 1
                            ThemeMode.DARK, ThemeMode.AMOLED -> 2
                        }
                        OsuSegmentedBar(
                            items = baseThemeModes,
                            selectedIndex = selectedThemeIdx,
                            onSelect = { viewModel.setThemeMode(baseThemeModes[it]) },
                            labelProvider = { mode ->
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK, ThemeMode.AMOLED -> "Dark"
                                }
                            }
                        )

                        SwitchRow(
                            title = "AMOLED pure black",
                            subtitle = "Pure #000000 canvas with hairline-separated surfaces",
                            checked = settings.themeMode == ThemeMode.AMOLED,
                            onCheckedChange = { enabled ->
                                viewModel.setThemeMode(if (enabled) ThemeMode.AMOLED else ThemeMode.DARK)
                            }
                        )
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)) {
                            Text(
                                text = "Global chat wallpaper",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Preset, seasonal art, photo framing, dim, and blur",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textSecondary
                            )
                        }
                        WallpaperCustomizerPanel(
                            config = settings.globalWallpaper,
                            onConfigChange = viewModel::setGlobalWallpaper,
                            seasonalBackgrounds = state.seasonalBackgrounds,
                            isSeasonalLoading = state.isSeasonalLoading,
                            downloadingSeasonalUrl = state.downloadingSeasonalUrl,
                            onSelectSeasonalBackground = { item ->
                                viewModel.selectSeasonalWallpaper(item.url)
                            }
                        )
                    }
                }
            }

            // 3. Home-screen widget (live preview, size, theme, opacity)
            run {
                val widgetContext = LocalContext.current
                val isPreviewDark = when (settings.themeMode) {
                    ThemeMode.DARK, ThemeMode.AMOLED -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                var previewCompact by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    OsuSectionHeader(
                        title = "Home-screen widget",
                        trailingMeta = if (previewCompact) "2x2" else "4x2"
                    )
                    OsuGroupedContainer {
                        Column(
                            modifier = Modifier.padding(OsuSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                        ) {
                            val sizeModes = remember { listOf(false to "4x2", true to "2x2") }
                            OsuSegmentedBar(
                                items = sizeModes,
                                selectedIndex = if (previewCompact) 1 else 0,
                                onSelect = { previewCompact = sizeModes[it].first },
                                labelProvider = { it.second },
                                isNumeric = true
                            )

                            BanchoWidgetPreviewCard(
                                username = settings.username,
                                avatarUrl = state.selfProfile?.avatarUrl ?: settings.avatarUrl,
                                profile = state.selfProfile,
                                onlineFriendsCount = state.onlineFriendsCount,
                                isLoggedIn = settings.authMode != AuthMode.NONE,
                                isLoading = state.isPreviewLoading,
                                updatedAtMillis = state.previewUpdatedAtMillis,
                                theme = settings.widgetTheme,
                                translucent = settings.widgetTranslucent,
                                compact = previewCompact,
                                isDark = isPreviewDark,
                                context = widgetContext,
                                onRefresh = viewModel::refreshWidgetPreviewProfile
                            )
                        }

                        OsuHairlineDivider()

                        Column(
                            modifier = Modifier.padding(OsuSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Widget style",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = surfaces.textSecondary
                                )
                                val widgetThemes = WidgetTheme.entries
                                OsuSegmentedBar(
                                    items = widgetThemes,
                                    selectedIndex = widgetThemes.indexOf(settings.widgetTheme).coerceAtLeast(0),
                                    onSelect = { viewModel.setWidgetTheme(widgetContext, widgetThemes[it]) },
                                    labelProvider = { it.label }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Background opacity",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = surfaces.textSecondary
                                )
                                val opacityOptions = remember {
                                    listOf(false to "Solid (100%)", true to "Translucent (80%)")
                                }
                                OsuSegmentedBar(
                                    items = opacityOptions,
                                    selectedIndex = if (settings.widgetTranslucent) 1 else 0,
                                    onSelect = { viewModel.setWidgetTranslucent(widgetContext, opacityOptions[it].first) },
                                    labelProvider = { it.second },
                                    isNumeric = true
                                )
                            }
                        }
                    }
                }
            }

            // 4. Notifications & chat filters (alerts, keywords, ignored users, local nicknames)
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                OsuSectionHeader(title = "Notifications & chat filters")
                OsuGroupedContainer {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        SwitchRow(
                            title = "Notify on direct messages",
                            subtitle = "Direct message alerts with inline reply",
                            checked = settings.notifyDirectMessages,
                            onCheckedChange = {
                                viewModel.setNotificationPrefs(it, settings.notifyKeywordMentions)
                            }
                        )
                        SwitchRow(
                            title = "Notify on keyword mentions",
                            subtitle = "Alert when username or keywords match in channels",
                            checked = settings.notifyKeywordMentions,
                            onCheckedChange = {
                                viewModel.setNotificationPrefs(settings.notifyDirectMessages, it)
                            }
                        )
                        SwitchRow(
                            title = "Tracked player alerts",
                            subtitle = "Custom PP or rank targets and new Top-N plays",
                            checked = settings.trackedAlertsEnabled,
                            onCheckedChange = viewModel::setTrackedAlertsEnabled
                        )

                        AnimatedVisibility(
                            visible = settings.trackedAlertsEnabled,
                            enter = OsuMotion.expandEnter(),
                            exit = OsuMotion.expandExit()
                        ) {
                            var defaultTopInput by rememberSaveable(settings.defaultTrackedTopPlayLimit) {
                                mutableStateOf(settings.defaultTrackedTopPlayLimit.toString())
                            }
                            val parsedTopLimit = defaultTopInput.trim().toIntOrNull()
                            val topLimitError = if (
                                parsedTopLimit == null ||
                                parsedTopLimit !in TrackedPlayerAlertConfig.MIN_TOP_PLAY_LIMIT..TrackedPlayerAlertConfig.MAX_TOP_PLAY_LIMIT
                            ) {
                                "Enter a number from 1 to 100"
                            } else {
                                null
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = OsuSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                OutlinedTextField(
                                    value = defaultTopInput,
                                    onValueChange = { next ->
                                        val digits = next.filter { it.isDigit() }.take(3)
                                        defaultTopInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null &&
                                            parsed in TrackedPlayerAlertConfig.MIN_TOP_PLAY_LIMIT..TrackedPlayerAlertConfig.MAX_TOP_PLAY_LIMIT
                                        ) {
                                            viewModel.setDefaultTrackedTopPlayLimit(parsed)
                                        }
                                    },
                                    label = { Text("Default top plays tracked (1–100)") },
                                    singleLine = true,
                                    isError = topLimitError != null,
                                    textStyle = MaterialTheme.typography.bodyMedium.tnum(),
                                    shape = controlShape,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    supportingText = {
                                        Text(
                                            text = topLimitError ?: "Applies unless overridden per player",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (topLimitError != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                surfaces.textMuted
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                    Text(
                                        text = "Check interval",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = surfaces.textSecondary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                    ) {
                                        TrackedPlayerAlertConfig.INTERVAL_OPTIONS_MINUTES.forEach { intervalMins ->
                                            val selected = settings.trackedAlertsIntervalMinutes == intervalMins
                                            val bg by animateColorAsState(
                                                targetValue = if (selected) {
                                                    surfaces.accentSubtle
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                },
                                                animationSpec = OsuMotion.colorSpec(),
                                                label = "intervalBg"
                                            )
                                            val fg by animateColorAsState(
                                                targetValue = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                animationSpec = OsuMotion.colorSpec(),
                                                label = "intervalFg"
                                            )
                                            val border by animateColorAsState(
                                                targetValue = if (selected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                animationSpec = OsuMotion.colorSpec(),
                                                label = "intervalBorder"
                                            )
                                            Surface(
                                                shape = controlShape,
                                                color = bg,
                                                border = osuHairlineBorder(border),
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clip(controlShape)
                                                    .clickable(role = Role.Tab) {
                                                        viewModel.setTrackedAlertsIntervalMinutes(intervalMins)
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${intervalMins}m",
                                                        style = MaterialTheme.typography.labelLarge.tnum(),
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                        color = fg,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Text(
                            text = "Highlight keywords",
                            style = MaterialTheme.typography.labelMedium,
                            color = surfaces.textSecondary
                        )
                        if (settings.username.isBlank() && settings.highlightKeywords.isEmpty()) {
                            Text(
                                text = "No highlight keywords configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textMuted
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                if (settings.username.isNotBlank()) {
                                    OsuMicroTag(
                                        text = "${settings.username} (You)",
                                        containerColor = surfaces.accentSubtle,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                                settings.highlightKeywords.forEach { kw ->
                                    OsuFilterChip(
                                        label = kw,
                                        selected = false,
                                        leadingIcon = Icons.Rounded.Close,
                                        onClick = { viewModel.removeHighlightKeyword(kw) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newKeyword,
                                onValueChange = {
                                    newKeyword = it
                                    keywordError = null
                                },
                                label = { Text("Add highlight keyword") },
                                singleLine = true,
                                isError = keywordError != null,
                                supportingText = keywordError?.let { err -> { Text(err) } },
                                shape = controlShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val err = viewModel.addHighlightKeyword(newKeyword)
                                        keywordError = err
                                        if (err == null) {
                                            newKeyword = ""
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(OsuSpacing.sm))
                            IconButton(
                                onClick = {
                                    val err = viewModel.addHighlightKeyword(newKeyword)
                                    keywordError = err
                                    if (err == null) {
                                        newKeyword = ""
                                    }
                                },
                                enabled = newKeyword.isNotBlank()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add keyword")
                            }
                        }
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)) {
                            Text(
                                text = "Ignored users",
                                style = MaterialTheme.typography.labelMedium,
                                color = surfaces.textSecondary
                            )
                            Text(
                                text = "Suppress notifications and hide or collapse in public channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textMuted
                            )
                        }
                        val ignoreModes = remember { listOf("HIDE" to "Hide", "COLLAPSE" to "Collapse") }
                        OsuSegmentedBar(
                            items = ignoreModes,
                            selectedIndex = if (settings.ignoreDisplayMode == "COLLAPSE") 1 else 0,
                            onSelect = { viewModel.setIgnoreDisplayMode(ignoreModes[it].first) },
                            labelProvider = { it.second }
                        )

                        val sortedIgnored = remember(settings.ignoredUsers) {
                            settings.ignoredUsers.sorted()
                        }
                        if (sortedIgnored.isEmpty()) {
                            Text(
                                text = "No ignored users",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textMuted
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                sortedIgnored.forEach { ignored ->
                                    OsuFilterChip(
                                        label = ignored,
                                        selected = false,
                                        leadingIcon = Icons.Rounded.Close,
                                        onClick = { viewModel.removeIgnoredUser(ignored) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newIgnoredUser,
                                onValueChange = {
                                    newIgnoredUser = it
                                    ignoredUserError = null
                                },
                                label = { Text("Ignore username") },
                                singleLine = true,
                                isError = ignoredUserError != null,
                                supportingText = ignoredUserError?.let { err -> { Text(err) } },
                                shape = controlShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val err = viewModel.addIgnoredUser(newIgnoredUser)
                                        ignoredUserError = err
                                        if (err == null) {
                                            newIgnoredUser = ""
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(OsuSpacing.sm))
                            IconButton(
                                onClick = {
                                    val err = viewModel.addIgnoredUser(newIgnoredUser)
                                    ignoredUserError = err
                                    if (err == null) {
                                        newIgnoredUser = ""
                                    }
                                },
                                enabled = newIgnoredUser.isNotBlank()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add ignored user")
                            }
                        }
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Text(
                            text = "Player nicknames",
                            style = MaterialTheme.typography.labelMedium,
                            color = surfaces.textSecondary
                        )
                        if (state.aliases.isEmpty()) {
                            Text(
                                text = "No local aliases set — assign from any player profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textMuted
                            )
                        } else {
                            state.aliases.forEachIndexed { index, alias ->
                                if (index > 0) {
                                    OsuHairlineDivider()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = OsuSpacing.xs),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alias.customNickname.ifBlank { alias.originalUsername },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${alias.originalUsername}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = surfaces.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (alias.note.isNotBlank()) {
                                            Text(
                                                text = alias.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = surfaces.textMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.removeAlias(alias.originalUsername) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove alias")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Tournament & backup (mappools, JSON backup/restore)
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                OsuSectionHeader(title = "Tournament & backup")
                OsuGroupedContainer {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)) {
                            Text(
                                text = "Tournament mappools",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${state.mappools.size} saved for #mp_ referee panel",
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = surfaces.textSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { showMappoolManager = true },
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(OsuSpacing.sm))
                            Text("Manage Mappools")
                        }
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                            Text(
                                text = "Backup & restore",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Exports mappools, Play Later maps, aliases, bookmarks, tracked friends, history, and preferences. Login tokens and IRC passwords are never included",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textSecondary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportBackupLauncher.launch("osucompanion-backup-$stamp.json")
                                },
                                enabled = !state.isBackupBusy,
                                shape = controlShape,
                                border = osuHairlineBorder(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text(
                                    text = "Export (.json)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    restoreBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                enabled = !state.isBackupBusy,
                                shape = controlShape,
                                border = osuHairlineBorder(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text(
                                    text = "Restore (.json)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 6. App updates (installed version, manual GitHub Releases check)
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                OsuSectionHeader(title = "App updates")
                OsuGroupedContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OsuSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(OsuRadius.md))
                                .background(surfaces.accentSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                        ) {
                            Text(
                                text = "osu! Companion",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = surfaces.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OsuHairlineDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OsuSpacing.lg)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::checkForUpdates,
                            enabled = !state.isCheckingUpdates,
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Checking for updates…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Check for updates")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreConfirmationDialog(
    parsed: ParsedBackup,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val summary = parsed.summary
    val exportedText = remember(summary.exportedAt) {
        if (summary.exportedAt > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(summary.exportedAt))
        } else {
            "Unknown"
        }
    }
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        shape = RoundedCornerShape(OsuRadius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "Restore backup?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                Text(
                    text = "Existing chat messages and login credentials will not be modified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OsuRadius.md))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    BackupSummaryCountRow("Schema", "v${summary.schemaVersion}")
                    BackupSummaryCountRow("Exported", exportedText)
                    BackupSummaryCountRow("Mappools", summary.mappoolsCount.toString())
                    BackupSummaryCountRow("Play Later maps", summary.savedBeatmapsCount.toString())
                    BackupSummaryCountRow("Aliases & notes", summary.userAliasesCount.toString())
                    BackupSummaryCountRow("Tracked friends", summary.trackedFriendsCount.toString())
                    BackupSummaryCountRow("Bookmarks", summary.bookmarkedMessagesCount.toString())
                    BackupSummaryCountRow("Profile history", summary.profileHistoryCount.toString())
                    BackupSummaryCountRow("Referee macros", summary.refereeMacrosCount.toString())
                    BackupSummaryCountRow("Match setup presets", summary.matchSetupTemplatesCount.toString())
                    BackupSummaryCountRow("Ignored users", summary.ignoredUsersCount.toString())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
                shape = RoundedCornerShape(OsuRadius.md)
            ) {
                Text(if (isBusy) "Restoring…" else "Restore")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
                shape = RoundedCornerShape(OsuRadius.md)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BackupSummaryCountRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun BatteryOptimizationSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }

    var isIgnoring by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    DisposableEffect(lifecycleOwner, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isIgnoring) Icons.Rounded.CheckCircle else Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                tint = if (isIgnoring) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(OsuSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isIgnoring) "Unrestricted background battery" else "Battery optimization active",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isIgnoring) {
                        "Background Bancho sync stays active when locked"
                    } else {
                        "Allow unrestricted battery so Android does not pause Bancho sync"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalOsuSurfaces.current.textSecondary
                )
            }
        }

        if (!isIgnoring) {
            Spacer(Modifier.height(OsuSpacing.md))
            OutlinedButton(
                onClick = {
                    val reqIntent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    runCatching { context.startActivity(reqIntent) }.onFailure {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                },
                shape = RoundedCornerShape(OsuRadius.md),
                border = osuHairlineBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disable Battery Optimization")
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OsuSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = OsuSpacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalOsuSurfaces.current.textSecondary
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private data class WidgetPreviewPalette(
    val surface: Color,
    val border: Color,
    val tile: Color,
    val tileBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val ppAccent: Color,
    val dotColor: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val fontFamily: FontFamily
)

@Composable
private fun BanchoWidgetPreviewCard(
    username: String,
    avatarUrl: String?,
    profile: PlayerProfile?,
    onlineFriendsCount: Int,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    updatedAtMillis: Long,
    theme: WidgetTheme,
    translucent: Boolean,
    compact: Boolean,
    isDark: Boolean,
    context: Context,
    onRefresh: () -> Unit
) {
    val surfaceAlpha = if (translucent) 0.80f else 1f
    val palette = when (theme) {
        WidgetTheme.NOTHING_OS -> if (isDark) {
            WidgetPreviewPalette(
                surface = WidgetPreviewColors.NothingDarkSurface,
                border = WidgetPreviewColors.NothingDarkBorder,
                tile = WidgetPreviewColors.NothingDarkTile,
                tileBorder = WidgetPreviewColors.NothingDarkTileBorder,
                primaryText = WidgetPreviewColors.NothingDarkText,
                secondaryText = WidgetPreviewColors.NothingDarkSecondary,
                accent = WidgetPreviewColors.NothingDarkText,
                ppAccent = WidgetPreviewColors.NothingAccentRed,
                dotColor = WidgetPreviewColors.NothingAccentRed,
                badgeBg = WidgetPreviewColors.NothingDarkBadgeBg,
                badgeText = WidgetPreviewColors.NothingAccentRed,
                fontFamily = FontFamily.Monospace
            )
        } else {
            WidgetPreviewPalette(
                surface = WidgetPreviewColors.NothingLightSurface,
                border = WidgetPreviewColors.NothingLightBorder,
                tile = WidgetPreviewColors.NothingLightTile,
                tileBorder = WidgetPreviewColors.NothingLightTileBorder,
                primaryText = WidgetPreviewColors.NothingLightText,
                secondaryText = WidgetPreviewColors.NothingLightSecondary,
                accent = WidgetPreviewColors.NothingLightText,
                ppAccent = WidgetPreviewColors.NothingAccentRed,
                dotColor = WidgetPreviewColors.NothingAccentRed,
                badgeBg = WidgetPreviewColors.NothingLightBadgeBg,
                badgeText = WidgetPreviewColors.NothingAccentRed,
                fontFamily = FontFamily.Monospace
            )
        }
        WidgetTheme.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dyn = if (isDark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
                WidgetPreviewPalette(
                    surface = dyn.surface,
                    border = dyn.outlineVariant,
                    tile = dyn.secondaryContainer,
                    tileBorder = dyn.outlineVariant.copy(alpha = 0.65f),
                    primaryText = dyn.onSurface,
                    secondaryText = dyn.onSurfaceVariant,
                    accent = dyn.primary,
                    ppAccent = dyn.primary,
                    dotColor = dyn.primary,
                    badgeBg = dyn.primary.copy(alpha = 0.16f),
                    badgeText = dyn.primary,
                    fontFamily = FontFamily.Default
                )
            } else if (isDark) {
                WidgetPreviewPalette(
                    surface = WidgetPreviewColors.OsuDarkSurface,
                    border = WidgetPreviewColors.OsuDarkBorder,
                    tile = WidgetPreviewColors.OsuDarkTile,
                    tileBorder = WidgetPreviewColors.OsuDarkTileBorder,
                    primaryText = WidgetPreviewColors.OsuDarkText,
                    secondaryText = WidgetPreviewColors.OsuDarkSecondary,
                    accent = WidgetPreviewColors.OsuDarkAccent,
                    ppAccent = WidgetPreviewColors.OsuDarkAccent,
                    dotColor = WidgetPreviewColors.OsuDarkAccent,
                    badgeBg = WidgetPreviewColors.OsuDarkBadgeBg,
                    badgeText = WidgetPreviewColors.OsuDarkAccent,
                    fontFamily = FontFamily.Default
                )
            } else {
                WidgetPreviewPalette(
                    surface = WidgetPreviewColors.OsuLightSurface,
                    border = WidgetPreviewColors.OsuLightBorder,
                    tile = WidgetPreviewColors.OsuLightTile,
                    tileBorder = WidgetPreviewColors.OsuLightBorder,
                    primaryText = WidgetPreviewColors.OsuLightText,
                    secondaryText = WidgetPreviewColors.OsuLightSecondary,
                    accent = WidgetPreviewColors.OsuLightAccent,
                    ppAccent = WidgetPreviewColors.OsuLightAccent,
                    dotColor = WidgetPreviewColors.OsuLightAccent,
                    badgeBg = WidgetPreviewColors.OsuLightBadgeBg,
                    badgeText = WidgetPreviewColors.OsuLightAccent,
                    fontFamily = FontFamily.Default
                )
            }
        }
        WidgetTheme.OSU_DARK -> WidgetPreviewPalette(
            surface = WidgetPreviewColors.OsuDarkSurface,
            border = WidgetPreviewColors.OsuDarkBorder,
            tile = WidgetPreviewColors.OsuDarkTile,
            tileBorder = WidgetPreviewColors.OsuDarkTileBorder,
            primaryText = WidgetPreviewColors.OsuDarkText,
            secondaryText = WidgetPreviewColors.OsuDarkSecondary,
            accent = WidgetPreviewColors.OsuDarkAccent,
            ppAccent = WidgetPreviewColors.OsuDarkText,
            dotColor = WidgetPreviewColors.OsuGreenDot,
            badgeBg = WidgetPreviewColors.OsuGreenBadgeBg,
            badgeText = WidgetPreviewColors.OsuGreenDot,
            fontFamily = FontFamily.Default
        )
    }

    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val displayUsername = if (!isLoggedIn || username.isBlank()) "osu! Companion" else username
    val globalRankText = if (!isLoggedIn) {
        if (compact && theme == WidgetTheme.NOTHING_OS) "SIGN IN" else if (compact) "Sign in" else "—"
    } else {
        profile?.globalRank?.takeIf { it > 0 }?.let { "#${numFmt.format(it)}" } ?: "—"
    }
    val validCountryCode = if (isLoggedIn) {
        profile?.countryCode
            ?.takeIf {
                it.isNotBlank() &&
                    !it.equals("XX", ignoreCase = true) &&
                    !it.equals("null", ignoreCase = true)
            }
            ?.uppercase(Locale.US)
    } else {
        null
    }
    val countryLabel = when {
        validCountryCode != null && theme == WidgetTheme.NOTHING_OS -> validCountryCode
        validCountryCode != null -> "COUNTRY ($validCountryCode)"
        theme == WidgetTheme.NOTHING_OS -> "LOCAL"
        else -> "COUNTRY"
    }
    val countryRankText = if (!isLoggedIn) {
        "—"
    } else {
        profile?.countryRank?.takeIf { it > 0 }?.let { "#${numFmt.format(it)}" } ?: "—"
    }
    val ppText = if (!isLoggedIn) {
        "—"
    } else {
        profile?.pp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            "${numFmt.format(it.roundToInt())} pp"
        } ?: "—"
    }
    val ppCompactValue = if (!isLoggedIn) {
        "—"
    } else {
        profile?.pp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            numFmt.format(it.roundToInt())
        } ?: "—"
    }
    val accuracyText = if (!isLoggedIn) {
        "—%"
    } else {
        profile?.accuracy?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            String.format(Locale.US, "%.2f%%", it)
        } ?: "—%"
    }
    val statusFooterText = when {
        !isLoggedIn && theme == WidgetTheme.NOTHING_OS -> "TAP TO SIGN IN"
        !isLoggedIn -> "Tap to sign in"
        isLoading && theme == WidgetTheme.NOTHING_OS -> "SYNCING..."
        isLoading -> "Refreshing…"
        updatedAtMillis > 0L && theme == WidgetTheme.NOTHING_OS ->
            "SYNC ${timeFmt.format(Date(updatedAtMillis))}"
        updatedAtMillis > 0L -> "Updated ${timeFmt.format(Date(updatedAtMillis))}"
        theme == WidgetTheme.NOTHING_OS -> "OSU.STATS"
        else -> "Cached preview"
    }
    val onlineCount = onlineFriendsCount.coerceAtLeast(0)
    val friendsText = if (theme == WidgetTheme.NOTHING_OS) {
        "$onlineCount ON"
    } else {
        val suffix = if (onlineCount == 1) "friend" else "friends"
        "$onlineCount $suffix online"
    }

    val stageBg = if (isDark) WidgetPreviewColors.StageDarkBg else WidgetPreviewColors.StageLightBg
    val gridLineColor = if (isDark) WidgetPreviewColors.StageDarkGrid else WidgetPreviewColors.StageLightGrid

    // Flat matte grid backdrop so Solid (100%) vs Translucent (80%) is visibly clear without gradients
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OsuRadius.md))
            .background(stageBg)
            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.md),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stepPx = 16.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += stepPx
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += stepPx
            }
        }

        val widgetModifier = if (compact) Modifier.width(178.dp) else Modifier.fillMaxWidth()
        val outerShape = RoundedCornerShape(OsuRadius.lg)
        val innerShape = RoundedCornerShape(OsuRadius.sm)

        Column(
            modifier = widgetModifier
                .clip(outerShape)
                .background(palette.surface.copy(alpha = surfaceAlpha))
                .border(OsuRadius.hairline, palette.border, outerShape)
                .padding(OsuSpacing.md)
        ) {
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (theme == WidgetTheme.NOTHING_OS && displayUsername == "osu! Companion") {
                            "BANCHO"
                        } else {
                            displayUsername
                        },
                        color = palette.primaryText,
                        fontFamily = palette.fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetPreviewRefreshButton(
                        enabled = isLoggedIn && !isLoading,
                        isLoading = isLoading,
                        compact = true,
                        shape = innerShape,
                        palette = palette,
                        onClick = onRefresh
                    )
                }
                Spacer(Modifier.height(OsuSpacing.sm))
                WidgetPreviewTile(
                    label = if (theme == WidgetTheme.NOTHING_OS) "RANK" else "GLOBAL RANK",
                    value = globalRankText,
                    labelColor = palette.secondaryText,
                    valueColor = palette.accent,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(OsuSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PP",
                        color = if (theme == WidgetTheme.NOTHING_OS) palette.ppAccent else palette.secondaryText,
                        fontFamily = palette.fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = ppText,
                        color = if (theme == WidgetTheme.NOTHING_OS) palette.primaryText else palette.ppAccent,
                        fontFamily = palette.fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium.tnum()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerAvatar(
                        name = displayUsername,
                        avatarUrl = if (isLoggedIn) avatarUrl else null,
                        size = 30.dp
                    )
                    Spacer(Modifier.width(OsuSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayUsername,
                            color = palette.primaryText,
                            fontFamily = palette.fontFamily,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusFooterText,
                            color = palette.secondaryText,
                            fontFamily = palette.fontFamily,
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isLoggedIn) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(OsuRadius.xs))
                                .background(palette.badgeBg)
                                .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onlineCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(palette.dotColor)
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                            }
                            Text(
                                text = friendsText,
                                color = palette.badgeText,
                                fontFamily = palette.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(OsuSpacing.xs))
                    }
                    WidgetPreviewRefreshButton(
                        enabled = isLoggedIn && !isLoading,
                        isLoading = isLoading,
                        compact = false,
                        shape = innerShape,
                        palette = palette,
                        onClick = onRefresh
                    )
                }

                Spacer(Modifier.height(OsuSpacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    WidgetPreviewTile(
                        label = "GLOBAL",
                        value = globalRankText,
                        labelColor = palette.secondaryText,
                        valueColor = palette.accent,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetPreviewTile(
                        label = countryLabel,
                        value = countryRankText,
                        labelColor = palette.secondaryText,
                        valueColor = palette.primaryText,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetPreviewTile(
                        label = "PP",
                        value = ppCompactValue,
                        labelColor = if (theme == WidgetTheme.NOTHING_OS) palette.ppAccent else palette.secondaryText,
                        valueColor = if (theme == WidgetTheme.NOTHING_OS) palette.primaryText else palette.ppAccent,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetPreviewTile(
                        label = "ACC",
                        value = accuracyText,
                        labelColor = palette.secondaryText,
                        valueColor = palette.primaryText,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewRefreshButton(
    enabled: Boolean,
    isLoading: Boolean,
    compact: Boolean,
    shape: RoundedCornerShape,
    palette: WidgetPreviewPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(if (compact) 24.dp else 28.dp)
            .clip(shape)
            .background(palette.tile)
            .border(OsuRadius.hairline, palette.tileBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                strokeWidth = 1.5.dp,
                color = palette.secondaryText
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh preview",
                tint = palette.secondaryText.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
        }
    }
}

@Composable
private fun WidgetPreviewTile(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    palette: WidgetPreviewPalette,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(OsuRadius.sm)
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette.tile)
            .border(OsuRadius.hairline, palette.tileBorder, shape)
            .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
    ) {
        Text(
            text = label,
            color = labelColor,
            fontFamily = palette.fontFamily,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = palette.fontFamily,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium.tnum(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
