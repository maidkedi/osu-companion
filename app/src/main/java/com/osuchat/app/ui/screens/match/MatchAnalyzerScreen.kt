package com.osuchat.app.ui.screens.match

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.domain.model.MatchAnalysisGame
import com.osuchat.app.domain.model.MatchAnalysisPlayerScore
import com.osuchat.app.domain.model.MatchAnalyzedGameItem
import com.osuchat.app.domain.model.MatchComputedAnalytics
import com.osuchat.app.domain.model.MatchPlayerStanding
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.ui.components.GradeBadge
import com.osuchat.app.ui.components.OsuAccentBarContainer
import com.osuchat.app.ui.components.OsuEmptyState
import com.osuchat.app.ui.components.OsuFilterChip
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.components.OsuMicroTag
import com.osuchat.app.ui.components.OsuSegmentedBar
import com.osuchat.app.ui.components.PlayerAvatar
import com.osuchat.app.ui.components.PlayerStatsBottomSheet
import com.osuchat.app.ui.components.TrackedPlayerAlertsDialog
import com.osuchat.app.ui.screens.home.BeatmapStatsBottomSheet
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.TeamBlue
import com.osuchat.app.ui.theme.TeamRed
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.MatchAnalyzerCalculator
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchAnalyzerScreen(
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: MatchAnalyzerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trackedAlertsDialogProfile by remember { mutableStateOf<PlayerProfile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MatchAnalyzerEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is MatchAnalyzerEffect.OpenDirectMessageChannel -> onOpenChannel(effect.channelId)
            }
        }
    }

    if (state.selectedProfile != null || state.isProfileLoading || state.profileError != null) {
        PlayerStatsBottomSheet(
            profile = state.selectedProfile,
            isLoading = state.isProfileLoading,
            onDismiss = viewModel::dismissPlayerProfile,
            onSaveAlias = viewModel::saveUserAlias,
            onStartDirectMessage = viewModel::startDirectMessage,
            onToggleTrackPp = { prof ->
                if (prof.isTrackedForPp) {
                    viewModel.toggleTrackFriendPp(prof)
                } else {
                    trackedAlertsDialogProfile = prof
                }
            },
            selfUsername = state.selfUsername,
            onToggleFriend = viewModel::toggleFriendStatus,
            isIgnored = state.selectedProfile?.let { prof ->
                BeatmapUrlParser.normalizeOsuNick(prof.username) in state.ignoredUsers
            } == true,
            onToggleIgnore = { prof -> viewModel.toggleIgnoreUser(prof.username) },
            pinnedScores = state.selectedProfilePinnedScores,
            bestScores = state.selectedProfileBestScores,
            recentScores = state.selectedProfileRecentScores,
            isScoresLoading = state.isProfileScoresLoading,
            isLoadingMoreBestScores = state.isLoadingMoreBestScores,
            canLoadMoreBestScores = state.canLoadMoreBestScores,
            onLoadMoreBestScores = viewModel::loadMoreBestScores,
            selectedMode = state.selectedProfileMode,
            onSelectMode = viewModel::selectProfileMode,
            onEstimateRankForPp = viewModel::estimateRankForPp,
            profileHistory = state.selectedProfileLocalHistory,
            osuTrackHistory = state.selectedProfileOsuTrack,
            isOsuTrackLoading = state.isSelectedProfileOsuTrackLoading,
            errorMessage = state.profileError,
            onRetry = viewModel::retryLoadPlayerProfile,
            trackedAlertConfig = state.selectedProfile?.let { prof ->
                state.trackedAlertConfigs[
                    TrackedPlayerAlertConfig.configKey(prof.userId, state.selectedProfileMode)
                ]
            },
            defaultTrackedTopPlayLimit = state.defaultTrackedTopPlayLimit,
            onConfigureTrackedAlerts = { prof ->
                trackedAlertsDialogProfile = prof
            }
        )
    }

    trackedAlertsDialogProfile?.let { targetProf ->
        val mode = state.selectedProfileMode
        val configKey = TrackedPlayerAlertConfig.configKey(targetProf.userId, mode)
        val initialConfig = state.trackedAlertConfigs[configKey] ?: TrackedPlayerAlertConfig()
        TrackedPlayerAlertsDialog(
            username = targetProf.username,
            mode = mode,
            currentPp = targetProf.pp,
            currentRank = targetProf.globalRank,
            initialConfig = initialConfig,
            defaultTopPlayLimit = state.defaultTrackedTopPlayLimit,
            isAlreadyTracked = targetProf.isTrackedForPp,
            onUntrack = if (targetProf.isTrackedForPp) {
                {
                    viewModel.toggleTrackFriendPp(targetProf)
                    trackedAlertsDialogProfile = null
                }
            } else {
                null
            },
            onSave = { updated ->
                viewModel.saveTrackedPlayerAlertConfig(
                    userId = targetProf.userId,
                    mode = mode,
                    config = updated,
                    username = targetProf.username,
                    avatarUrl = targetProf.avatarUrl
                )
                trackedAlertsDialogProfile = null
            },
            onDismiss = { trackedAlertsDialogProfile = null }
        )
    }

    state.selectedBeatmapSet?.let { beatmapSet ->
        BeatmapStatsBottomSheet(
            beatmapSet = beatmapSet,
            initialDiffIndex = 0,
            onDismiss = viewModel::dismissBeatmapDetails,
            onResolveDetails = viewModel::resolveBeatmapDetails,
            onLoadScores = viewModel::loadBeatmapScores,
            onOpenPlayerProfile = viewModel::openPlayerProfile,
            onShowMessage = viewModel::notifyMessage,
            savedBeatmapIds = state.savedBeatmapIds,
            onToggleSaveBeatmap = viewModel::toggleSaveBeatmap
        )
    }

    val analytics = state.analytics
    val surfaces = LocalOsuSurfaces.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = analytics?.roomName ?: "Match #${state.matchId}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            Text(
                                text = "#${state.matchId}",
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (analytics != null) {
                                OsuMicroTag(
                                    text = if (analytics.isLive) "Live" else "Finished",
                                    containerColor = if (analytics.isLive) {
                                        StatusOnline.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (analytics.isLive) {
                                        StatusOnline
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (analytics != null) {
                        IconButton(
                            onClick = {
                                val summary = MatchAnalyzerCalculator.formatDiscordSummary(analytics)
                                clipboardManager.setText(AnnotatedString(summary))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Match summary copied for Discord")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Discord summary",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val url = "https://osu.ppy.sh/mp/${state.matchId}"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open in browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh match",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OsuHairlineDivider()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            when {
                state.isLoading && analytics == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Loading match #${state.matchId}…",
                                style = MaterialTheme.typography.bodySmall.tnum(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                state.errorMessage != null && analytics == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(OsuSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        OsuGroupedContainer {
                            OsuEmptyState(
                                icon = Icons.Rounded.EmojiEvents,
                                title = "Match analyzer unavailable",
                                subtitle = state.errorMessage.orEmpty(),
                                primaryActionLabel = "Retry",
                                onPrimaryAction = viewModel::refresh
                            )
                        }
                    }
                }
                analytics != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(OsuSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        item(key = "summary_header", contentType = "summary_header") {
                            MatchSummaryHeaderCard(
                                analytics = analytics,
                                selectedWarmupPreset = state.selectedWarmupPreset,
                                onSelectWarmupPreset = viewModel::applyWarmupSkipPreset
                            )
                        }

                        item(key = "analyzer_tabs", contentType = "analyzer_tabs") {
                            OsuSegmentedBar(
                                items = MatchAnalyzerTab.entries,
                                selectedIndex = MatchAnalyzerTab.entries.indexOf(state.activeTab),
                                onSelect = { viewModel.selectTab(MatchAnalyzerTab.entries[it]) },
                                labelProvider = { tab ->
                                    val count = if (tab == MatchAnalyzerTab.PLAYERS) {
                                        analytics.playerStandings.size
                                    } else {
                                        analytics.games.size
                                    }
                                    "${tab.label} ($count)"
                                },
                                isNumeric = true
                            )
                        }

                        if (analytics.games.isEmpty()) {
                            item(key = "no_games_empty", contentType = "empty") {
                                OsuGroupedContainer {
                                    OsuEmptyState(
                                        icon = Icons.Rounded.EmojiEvents,
                                        title = "No completed maps",
                                        subtitle = "No completed maps recorded in this match yet"
                                    )
                                }
                            }
                        } else {
                            when (state.activeTab) {
                                MatchAnalyzerTab.PLAYERS -> {
                                    if (analytics.playerStandings.isEmpty()) {
                                        item(key = "no_counted_players", contentType = "empty") {
                                            OsuGroupedContainer {
                                                OsuEmptyState(
                                                    icon = Icons.Rounded.EmojiEvents,
                                                    title = "No counted maps",
                                                    subtitle = "All played maps are currently marked as warmup"
                                                )
                                            }
                                        }
                                    } else {
                                        item(key = "player_standings_group", contentType = "player_standings") {
                                            OsuGroupedContainer {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = OsuRadius.accentBar + OsuSpacing.md + 28.dp + 36.dp + OsuSpacing.sm * 2,
                                                            end = OsuSpacing.md,
                                                            top = OsuSpacing.sm,
                                                            bottom = OsuSpacing.sm
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                                ) {
                                                    Text(
                                                        text = "Player",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = surfaces.textMuted,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "Avg / Acc",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = surfaces.textMuted,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.width(68.dp)
                                                    )
                                                    Text(
                                                        text = "Cost",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = surfaces.textMuted,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.width(44.dp)
                                                    )
                                                }
                                                OsuHairlineDivider()
                                                analytics.playerStandings.forEachIndexed { idx, standing ->
                                                    key(idx, standing.userId) {
                                                        if (idx > 0) OsuHairlineDivider()
                                                        PlayerStandingRow(
                                                            standing = standing,
                                                            onClick = { viewModel.openPlayerProfile(standing.userId.toString()) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                MatchAnalyzerTab.MAPS -> {
                                    itemsIndexed(
                                        items = analytics.games,
                                        key = { idx, it -> "game_${idx}_${it.game.gameId}" },
                                        contentType = { _, _ -> "game_item" }
                                    ) { _, item ->
                                        MatchGameBreakdownCard(
                                            item = item,
                                            isExpanded = item.game.gameId in state.expandedGameIds,
                                            onToggleExpand = { viewModel.toggleGameExpanded(item.game.gameId) },
                                            onToggleWarmup = { viewModel.toggleGameWarmup(item.game.gameId) },
                                            onOpenBeatmap = { viewModel.openBeatmapDetails(item.game) },
                                            onOpenPlayer = { uid -> viewModel.openPlayerProfile(uid.toString()) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchSummaryHeaderCard(
    analytics: MatchComputedAnalytics,
    selectedWarmupPreset: Int?,
    onSelectWarmupPreset: (Int) -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val intFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    OsuGroupedContainer {
        Column(
            modifier = Modifier.padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            if (analytics.isTeamSeries) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Red team",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TeamRed
                    )
                    Surface(
                        shape = RoundedCornerShape(OsuRadius.md),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Text(
                            text = "${analytics.redSeriesWins}  –  ${analytics.blueSeriesWins}",
                            style = MaterialTheme.typography.titleLarge.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                        )
                    }
                    Text(
                        text = "Blue team",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TeamBlue
                    )
                }
                OsuHairlineDivider()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("${analytics.countedMapCount} maps counted")
                        if (analytics.warmupMapCount > 0) {
                            append(" · ${analytics.warmupMapCount} warmup")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium.tnum(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Skip warmups",
                    style = MaterialTheme.typography.labelSmall,
                    color = surfaces.textMuted
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                listOf(0, 1, 2).forEach { preset ->
                    OsuFilterChip(
                        label = "Skip $preset",
                        selected = selectedWarmupPreset == preset,
                        onClick = { onSelectWarmupPreset(preset) },
                        isNumeric = true
                    )
                }
            }

            analytics.mvpPlayer?.let { mvp ->
                OsuAccentBarContainer(
                    showAccentBar = true,
                    accentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        PlayerAvatar(
                            name = mvp.username,
                            avatarUrl = mvp.avatarUrl,
                            size = 40.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                OsuMicroTag(
                                    text = "Match MVP",
                                    containerColor = surfaces.accentSubtle,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                            Text(
                                text = mvp.username,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Avg %s · %.2f%% · %d maps",
                                    intFmt.format(mvp.averageScore),
                                    mvp.averageAccuracy,
                                    mvp.mapsPlayed
                                ),
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "%.2f", mvp.matchCost),
                                style = MaterialTheme.typography.titleMedium.tnum(),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Match cost",
                                style = MaterialTheme.typography.labelSmall,
                                color = surfaces.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStandingRow(
    standing: MatchPlayerStanding,
    onClick: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val intFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val barColor = when {
        standing.isMvp -> MaterialTheme.colorScheme.primary
        standing.primaryTeam == MpTeam.RED -> TeamRed
        standing.primaryTeam == MpTeam.BLUE -> TeamBlue
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(OsuRadius.accentBar)
                .fillMaxHeight()
                .background(barColor)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Text(
                text = "#${standing.rank}",
                style = MaterialTheme.typography.labelMedium.tnum(),
                fontWeight = FontWeight.SemiBold,
                color = if (standing.isMvp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
            PlayerAvatar(
                name = standing.username,
                avatarUrl = standing.avatarUrl,
                size = 36.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    Text(
                        text = standing.username,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (standing.isMvp) {
                        OsuMicroTag(
                            text = "MVP",
                            containerColor = surfaces.accentSubtle,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "${standing.mapsPlayed} maps",
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = surfaces.textMuted,
                    maxLines = 1
                )
            }
            Column(
                modifier = Modifier.width(68.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = intFmt.format(standing.averageScore),
                    style = MaterialTheme.typography.labelMedium.tnum(),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = String.format(Locale.US, "%.2f%%", standing.averageAccuracy),
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = surfaces.textMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = String.format(Locale.US, "%.2f", standing.matchCost),
                style = MaterialTheme.typography.titleSmall.tnum(),
                fontWeight = FontWeight.SemiBold,
                color = if (standing.isMvp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp)
            )
        }
    }
}

@Composable
private fun MatchGameBreakdownCard(
    item: MatchAnalyzedGameItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleWarmup: () -> Unit,
    onOpenBeatmap: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val game: MatchAnalysisGame = item.game
    val intFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    OsuGroupedContainer(
        borderColor = if (item.isWarmup) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = game.beatmapId != null, onClick = onOpenBeatmap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
            ) {
                if (game.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = game.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(OsuRadius.md))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        Text(
                            text = "#${game.gameIndex}",
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (game.starRating > 0.0) {
                            Text(
                                text = String.format(Locale.US, "★ %.2f", game.starRating),
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = surfaces.textMuted
                            )
                        }
                        if (game.mods.isNotEmpty()) {
                            OsuMicroTag(
                                text = "+${game.mods.joinToString("")}",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = game.scoringTypeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = surfaces.textMuted
                        )
                    }
                    Text(
                        text = game.formattedMapLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OsuFilterChip(
                    label = if (item.isWarmup) "Warmup" else "Counted",
                    selected = item.isWarmup,
                    onClick = onToggleWarmup
                )
            }

            if (game.isTeamMatch) {
                val sumTotal = (item.redTeamTotal + item.blueTeamTotal).coerceAtLeast(1L)
                val redFraction = (item.redTeamTotal.toFloat() / sumTotal.toFloat()).coerceIn(0.05f, 0.95f)
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Red ${intFmt.format(item.redTeamTotal)}",
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            fontWeight = if (item.winnerTeam == MpTeam.RED) FontWeight.SemiBold else FontWeight.Normal,
                            color = TeamRed
                        )
                        Text(
                            text = when (item.winnerTeam) {
                                MpTeam.RED -> "Red +${intFmt.format(item.scoreDelta)}"
                                MpTeam.BLUE -> "Blue +${intFmt.format(item.scoreDelta)}"
                                MpTeam.NONE -> "Tie"
                            },
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = surfaces.textMuted
                        )
                        Text(
                            text = "Blue ${intFmt.format(item.blueTeamTotal)}",
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            fontWeight = if (item.winnerTeam == MpTeam.BLUE) FontWeight.SemiBold else FontWeight.Normal,
                            color = TeamBlue
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(OsuSpacing.xs)
                            .clip(RoundedCornerShape(OsuRadius.xs))
                            .background(TeamBlue)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(redFraction)
                                .background(TeamRed)
                        )
                    }
                }
            } else if (!item.winnerUsername.isNullOrBlank()) {
                val topScore = item.sortedScores.firstOrNull()?.score ?: 0L
                Text(
                    text = "Winner: ${item.winnerUsername} (${intFmt.format(topScore)})",
                    style = MaterialTheme.typography.labelMedium.tnum(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OsuRadius.md))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = OsuSpacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.sortedScores.size} player scores",
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = OsuMotion.expandEnter(),
                exit = OsuMotion.expandExit()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                    OsuHairlineDivider()
                    item.sortedScores.forEach { score ->
                        MatchGamePlayerRow(
                            score = score,
                            onClick = { onOpenPlayer(score.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchGamePlayerRow(
    score: MatchAnalysisPlayerScore,
    onClick: () -> Unit
) {
    val intFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val teamTint = when (score.team) {
        MpTeam.RED -> TeamRed
        MpTeam.BLUE -> TeamBlue
        MpTeam.NONE -> MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OsuRadius.md))
            .clickable(onClick = onClick)
            .padding(vertical = OsuSpacing.xs, horizontal = OsuSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .width(OsuRadius.accentBar)
                .height(24.dp)
                .background(teamTint, RoundedCornerShape(OsuRadius.xs))
        )
        score.rank?.takeIf { it.isNotBlank() }?.let { r ->
            GradeBadge(grade = r)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                Text(
                    text = score.username,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!score.passed) {
                    OsuMicroTag(
                        text = "Fail",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            val modsLabel = if (score.mods.isEmpty()) "" else " +${score.mods.joinToString("")}"
            Text(
                text = String.format(
                    Locale.US,
                    "%.2f%% · %dx%s",
                    score.accuracy,
                    score.maxCombo,
                    modsLabel
                ),
                style = MaterialTheme.typography.labelSmall.tnum(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = intFmt.format(score.score),
            style = MaterialTheme.typography.bodySmall.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
