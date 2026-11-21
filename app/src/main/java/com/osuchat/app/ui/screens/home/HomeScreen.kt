package com.osuchat.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osuchat.app.domain.model.AudioQueueTrack
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChannelType
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.FriendContact
import com.osuchat.app.domain.model.FriendLeaderboardEntry
import com.osuchat.app.domain.model.FriendScoreEventItem
import com.osuchat.app.domain.model.FriendsRankingSort
import com.osuchat.app.domain.model.GlobalMessageSearchHit
import com.osuchat.app.domain.model.OfficialOsuChannels
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OszDownloadState
import com.osuchat.app.domain.model.matchesNormalizedChannelSet
import com.osuchat.app.ui.components.BeatmapMiniPlayerBar
import com.osuchat.app.ui.components.BookmarksBottomSheet
import com.osuchat.app.ui.components.ConnectionStatusChip
import com.osuchat.app.ui.components.GradeBadge
import com.osuchat.app.ui.components.OsuAccentBarContainer
import com.osuchat.app.ui.components.OsuEmptyState
import com.osuchat.app.ui.components.OsuFilterChip
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.components.OsuMicroTag
import com.osuchat.app.ui.components.PlayerAvatar
import com.osuchat.app.ui.components.PlayerStatsBottomSheet
import com.osuchat.app.ui.components.ScoreCardShareDialog
import com.osuchat.app.ui.components.countryCodeToFlagEmoji
import com.osuchat.app.ui.screens.chat.ChatScreen
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.PodiumBronze
import com.osuchat.app.ui.theme.PodiumGold
import com.osuchat.app.ui.theme.PodiumSilver
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.AppShortcutManager
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.ExternalOsuTarget
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenChannel: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChannelMessage: (channelId: String, messageId: String?) -> Unit = { ch, _ -> onOpenChannel(ch) },
    onOpenMatchAnalyzer: (Long) -> Unit = {},
    pendingExternalTarget: ExternalOsuTarget? = null,
    onConsumedExternalTarget: () -> Unit = {},
    pendingShortcutAction: String? = null,
    onConsumedShortcutAction: () -> Unit = {},
    selectedTwoPaneChannelId: String? = null,
    selectedTwoPaneFocusMessageId: String? = null,
    onSelectTwoPaneChannel: (channelId: String?, focusMessageId: String?) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val isTwoPane = LocalConfiguration.current.screenWidthDp >= 600
    var showNewChatDialog by rememberSaveable { mutableStateOf(false) }
    var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var dmPendingDelete by remember { mutableStateOf<ChatChannel?>(null) }
    var channelOptionsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var miniPlayerStatsTrack by remember { mutableStateOf<AudioQueueTrack?>(null) }
    var externalMpTargetMatchId by rememberSaveable { mutableStateOf<Long?>(null) }
    val channelForOptions = channelOptionsTargetId?.let { targetId ->
        state.allChannels.firstOrNull { it.id == targetId }
    }

    val openChannelTarget: (String) -> Unit = { channelId ->
        if (isTwoPane) {
            viewModel.onCompanionTabSelected(CompanionTab.CHATS)
            onSelectTwoPaneChannel(channelId, null)
        } else {
            onOpenChannel(channelId)
        }
    }
    val openChannelMessageTarget: (String, String?) -> Unit = { channelId, messageId ->
        if (isTwoPane) {
            viewModel.onCompanionTabSelected(CompanionTab.CHATS)
            onSelectTwoPaneChannel(channelId, messageId)
        } else {
            onOpenChannelMessage(channelId, messageId)
        }
    }

    LaunchedEffect(pendingShortcutAction) {
        val action = pendingShortcutAction ?: return@LaunchedEffect
        onConsumedShortcutAction()
        when (action) {
            AppShortcutManager.ACTION_BEATMAP_SEARCH -> viewModel.openBeatmapSearchDestination()
            AppShortcutManager.ACTION_MP_SETUP -> viewModel.openMpSetupWizardDestination()
        }
    }

    var lastObservedTwoPaneChannelId by remember { mutableStateOf(selectedTwoPaneChannelId) }
    LaunchedEffect(selectedTwoPaneChannelId, isTwoPane) {
        val changed = selectedTwoPaneChannelId != lastObservedTwoPaneChannelId
        lastObservedTwoPaneChannelId = selectedTwoPaneChannelId
        if (changed && isTwoPane && !selectedTwoPaneChannelId.isNullOrBlank()) {
            viewModel.onCompanionTabSelected(CompanionTab.CHATS)
        }
    }

    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val controlShape = RoundedCornerShape(OsuRadius.md)
    val chipShape = RoundedCornerShape(OsuRadius.md)

    LaunchedEffect(state.actionError) {
        val err = state.actionError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(pendingExternalTarget) {
        val target = pendingExternalTarget ?: return@LaunchedEffect
        onConsumedExternalTarget()
        when (target) {
            is ExternalOsuTarget.BeatmapTarget -> {
                val mapId = target.beatmapId
                val setId = target.beatmapsetId
                if (mapId != null && mapId > 0L) {
                    miniPlayerStatsTrack = AudioQueueTrack(
                        beatmapId = mapId,
                        beatmapsetId = setId ?: 0L,
                        title = "Beatmap #$mapId"
                    )
                }
                viewModel.resolveExternalBeatmap(mapId, setId)
                    .onSuccess { meta ->
                        miniPlayerStatsTrack = AudioQueueTrack(
                            beatmapId = meta.beatmapId,
                            beatmapsetId = meta.beatmapsetId,
                            title = meta.title,
                            artist = meta.artist,
                            version = meta.version,
                            creator = meta.creator,
                            starRating = meta.starRating,
                            bpm = meta.bpm,
                            totalLengthSeconds = meta.totalLengthSeconds,
                            coverUrl = meta.coverUrl,
                            previewAudioUrl = meta.previewAudioUrl,
                            mode = meta.mode
                        )
                    }
                    .onFailure {
                        if (mapId == null || mapId <= 0L) {
                            viewModel.showMessage("Could not load beatmap details.")
                        }
                    }
            }
            is ExternalOsuTarget.UserTarget -> {
                viewModel.openPlayerProfile(target.userIdOrName)
            }
            is ExternalOsuTarget.MultiplayerTarget -> {
                externalMpTargetMatchId = target.matchId
            }
            is ExternalOsuTarget.UnrecognizedText -> {
                viewModel.showMessage("No osu! link detected in shared text.")
            }
        }
    }

    externalMpTargetMatchId?.let { targetMatchId ->
        AlertDialog(
            onDismissRequest = { externalMpTargetMatchId = null },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Multiplayer match #$targetMatchId",
                    style = MaterialTheme.typography.titleMedium.tnum()
                )
            },
            text = {
                Text(
                    text = "Open Match Analyzer breakdown or join live IRC lobby #mp_$targetMatchId.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        externalMpTargetMatchId = null
                        onOpenMatchAnalyzer(targetMatchId)
                    },
                    shape = controlShape
                ) {
                    Text("Open analyzer")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    TextButton(
                        onClick = { externalMpTargetMatchId = null },
                        shape = controlShape
                    ) {
                        Text("Cancel")
                    }
                    OutlinedButton(
                        onClick = {
                            externalMpTargetMatchId = null
                            viewModel.joinMultiplayerLobby(targetMatchId.toString())
                            openChannelTarget("#mp_$targetMatchId")
                        },
                        shape = controlShape,
                        border = osuHairlineBorder()
                    ) {
                        Text("Join #mp_$targetMatchId", style = MaterialTheme.typography.labelLarge.tnum())
                    }
                }
            }
        )
    }

    if (state.selectedProfile != null || state.isProfileLoading || state.selectedProfileError != null) {
        PlayerStatsBottomSheet(
            profile = state.selectedProfile,
            isLoading = state.isProfileLoading,
            onDismiss = viewModel::dismissPlayerProfile,
            onSaveAlias = viewModel::saveUserAlias,
            onStartDirectMessage = { target ->
                viewModel.startDirectMessage(target, openChannelTarget)
            },
            onToggleTrackPp = { prof ->
                viewModel.toggleTrackFriendPp(prof.username, prof.userId, prof.avatarUrl)
            },
            selfUsername = state.settings.username,
            onToggleFriend = viewModel::toggleFriendStatus,
            isIgnored = state.selectedProfile?.let { prof ->
                BeatmapUrlParser.normalizeOsuNick(prof.username) in state.settings.ignoredUsers
            } == true,
            onToggleIgnore = { prof -> viewModel.toggleIgnoreUser(prof.username) },
            pinnedScores = state.selectedProfilePinnedScores,
            bestScores = state.selectedProfileBestScores,
            recentScores = state.selectedProfileRecentScores,
            isScoresLoading = state.isProfileScoresLoading,
            isLoadingMoreBestScores = state.isLoadingMoreSelectedBestScores,
            canLoadMoreBestScores = state.canLoadMoreSelectedBestScores,
            onLoadMoreBestScores = viewModel::loadMoreSelectedBestScores,
            onShareScore = viewModel::promptShareScore,
            selectedMode = state.selectedProfileMode,
            onSelectMode = viewModel::selectProfileMode,
            onEstimateRankForPp = viewModel::estimateRankForPp,
            profileHistory = state.selectedProfileLocalHistory,
            osuTrackHistory = state.selectedProfileOsuTrack,
            isOsuTrackLoading = state.isSelectedProfileOsuTrackLoading,
            errorMessage = state.selectedProfileError,
            onRetry = viewModel::retrySelectedProfile
        )
    }

    channelForOptions?.let { targetCh ->
        ChannelOptionsBottomSheet(
            channel = targetCh,
            isDotOnly = targetCh.matchesNormalizedChannelSet(state.dotOnlyChannels),
            onDismiss = { channelOptionsTargetId = null },
            onTogglePin = {
                viewModel.togglePinChannel(targetCh)
                channelOptionsTargetId = null
            },
            onToggleMute = {
                viewModel.toggleChannelMute(targetCh)
                channelOptionsTargetId = null
            },
            onToggleDotOnly = {
                viewModel.toggleChannelDotOnly(targetCh)
                channelOptionsTargetId = null
            },
            onSelectNotificationMode = { mode ->
                viewModel.setChannelNotificationMode(targetCh, mode)
            },
            onCloseOrDelete = {
                channelOptionsTargetId = null
                if (targetCh.type == ChannelType.DIRECT) {
                    dmPendingDelete = targetCh
                } else {
                    viewModel.leaveChannel(targetCh)
                }
            }
        )
    }

    dmPendingDelete?.let { targetDm ->
        AlertDialog(
            onDismissRequest = { dmPendingDelete = null },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = { Text("Delete conversation?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = "Delete stored messages and remove chat with ${targetDm.displayTitle}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDirectConversation(targetDm)
                        dmPendingDelete = null
                    },
                    shape = controlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dmPendingDelete = null },
                    shape = controlShape
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    state.pendingShareScore?.let { shareScore ->
        ScoreCardShareDialog(
            score = shareScore,
            defaultChannelId = null,
            onSendTextFallback = viewModel::promptShareScoreTextOnly,
            onNotifyMessage = viewModel::showActionMessage,
            onOpenedChannel = openChannelTarget,
            onDismiss = viewModel::dismissScoreShareDialog
        )
    }

    state.pendingShareText?.let { sharePreview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSharePicker,
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = { Text("Share to chat", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    Text(
                        text = sharePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    OsuHairlineDivider()
                    if (state.allChannels.isEmpty()) {
                        Text(
                            text = "No open chats yet. Start a DM or join a channel first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = OsuSpacing.md)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(
                                items = state.allChannels,
                                key = { it.id },
                                contentType = { "share_channel" }
                            ) { ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { viewModel.sendShareToChannel(ch.id, openChannelTarget) }
                                        .padding(vertical = OsuSpacing.md, horizontal = OsuSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ch.displayTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text(
                                        text = "Send",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OsuHairlineDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissSharePicker,
                    shape = controlShape
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNewChatDialog) {
        NewChatOrChannelDialog(
            onDismiss = { showNewChatDialog = false },
            onStartDm = { target ->
                showNewChatDialog = false
                viewModel.startDirectMessage(target, openChannelTarget)
            },
            onJoinChannel = { channel ->
                showNewChatDialog = false
                viewModel.joinPublicChannel(channel, openChannelTarget)
            }
        )
    }

    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            bookmarks = state.bookmarkedMessages,
            onDismiss = { showBookmarksSheet = false },
            onOpenChannel = { targetChannelId ->
                showBookmarksSheet = false
                openChannelTarget(targetChannelId)
            },
            onDeleteBookmark = viewModel::removeBookmark,
            onCopyText = { text ->
                clipboardManager.setText(AnnotatedString(text))
                viewModel.showMessage("Message copied")
            }
        )
    }

    miniPlayerStatsTrack?.let { track ->
        val matchingResult: BeatmapSetSearchResult? = if (track.beatmapsetId > 0L) {
            state.beatmapResults.firstOrNull { it.beatmapsetId == track.beatmapsetId }
        } else {
            null
        }
        val sheetSet = matchingResult ?: track.toBeatmapSetSearchResult()
        val initialDiffIdx = if (track.beatmapId > 0L) {
            sheetSet.difficulties.indexOfFirst { it.beatmapId == track.beatmapId }.takeIf { it >= 0 }
                ?: (sheetSet.difficulties.lastIndex).coerceAtLeast(0)
        } else {
            (sheetSet.difficulties.lastIndex).coerceAtLeast(0)
        }
        BeatmapStatsBottomSheet(
            beatmapSet = sheetSet,
            initialDiffIndex = initialDiffIdx,
            onDismiss = { miniPlayerStatsTrack = null },
            onResolveDetails = viewModel::resolveBeatmapDetails,
            onLoadScores = viewModel::loadBeatmapLeaderboard,
            onOpenPlayerProfile = { target ->
                miniPlayerStatsTrack = null
                viewModel.openPlayerProfile(target)
            },
            onShowMessage = viewModel::showMessage,
            savedBeatmapIds = state.savedBeatmapIds,
            onToggleSaveBeatmap = viewModel::toggleSaveBeatmap,
            oszDownloadState = state.oszDownloadStates[sheetSet.beatmapsetId] ?: OszDownloadState.Idle,
            onDownloadOsz = {
                viewModel.downloadBeatmapSetOsz(sheetSet.beatmapsetId, sheetSet.artist, sheetSet.title)
            },
            onShareOsz = { ctx ->
                viewModel.shareBeatmapSetOsz(ctx, sheetSet.beatmapsetId, sheetSet.artist, sheetSet.title)
            },
            hasActiveMpLobby = (state.activeMpLobby?.isClosed == false) || state.mpChannels.isNotEmpty(),
            onSelectForMp = { mapId, modeInt ->
                miniPlayerStatsTrack = null
                viewModel.selectBeatmapForMpLobby(mapId, modeInt)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = 52.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(enabled = state.settings.username.isNotBlank() || state.settings.userId > 0L) {
                                    viewModel.openSelfProfileDestination()
                                }
                        ) {
                            PlayerAvatar(
                                name = state.settings.username.ifBlank { "osu!" },
                                avatarUrl = state.settings.avatarUrl,
                                size = 36.dp,
                                showOnlineDot = false
                            )
                            Spacer(modifier = Modifier.width(OsuSpacing.md))
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)) {
                                Text(
                                    text = state.settings.username.ifBlank { "osu! Chat" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                ConnectionStatusChip(status = state.connectionStatus)
                            }
                        }
                    },
                    actions = {
                        if (state.activeTab == CompanionTab.CHATS) {
                            if (isTwoPane) {
                                IconButton(onClick = { showNewChatDialog = true }) {
                                    Icon(
                                        Icons.Rounded.AddComment,
                                        contentDescription = "New DM or join channel",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { showBookmarksSheet = true }) {
                                if (state.bookmarkedMessages.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    text = if (state.bookmarkedMessages.size > 99) "99+" else state.bookmarkedMessages.size.toString(),
                                                    style = MaterialTheme.typography.labelSmall.tnum()
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.Bookmarks,
                                            contentDescription = "Saved bookmarks",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Rounded.Bookmarks,
                                        contentDescription = "Saved bookmarks",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = viewModel::refreshConnection,
                            enabled = !state.isRefreshingConnection
                        ) {
                            if (state.isRefreshingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Sync / Reconnect",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                OsuHairlineDivider()
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = state.audioState.currentTrack != null,
                    enter = slideInVertically(
                        animationSpec = tween(OsuMotion.StandardMs, easing = OsuMotion.EaseOut),
                        initialOffsetY = { it }
                    ) + fadeIn(tween(OsuMotion.FastMs, easing = OsuMotion.EaseOut)),
                    exit = slideOutVertically(
                        animationSpec = tween(OsuMotion.StandardMs, easing = OsuMotion.EaseOut),
                        targetOffsetY = { it }
                    ) + fadeOut(tween(OsuMotion.FastMs, easing = OsuMotion.EaseOut))
                ) {
                    val currentTrack = state.audioState.currentTrack
                    val isBookmarked = currentTrack != null &&
                        currentTrack.beatmapId > 0L &&
                        currentTrack.beatmapId in state.savedBeatmapIds
                    BeatmapMiniPlayerBar(
                        audioState = state.audioState,
                        isBookmarked = isBookmarked,
                        onTogglePlayPause = viewModel::toggleCurrentAudioTrack,
                        onPrevious = viewModel::playPreviousAudioTrack,
                        onNext = viewModel::playNextAudioTrack,
                        onToggleBookmark = viewModel::toggleSaveFromQueueTrack,
                        onClose = viewModel::stopAndClearAudio,
                        onOpenTrackStats = { track -> miniPlayerStatsTrack = track }
                    )
                }
                OsuHairlineDivider()
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    val surfaces = LocalOsuSurfaces.current
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = surfaces.accentSubtle,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CompanionTab.entries.forEach { tab ->
                        val selected = state.activeTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.onCompanionTabSelected(tab) },
                            colors = navItemColors,
                            icon = {
                                val iconVector = when (tab) {
                                    CompanionTab.CHATS -> Icons.Rounded.Forum
                                    CompanionTab.FRIENDS -> Icons.Rounded.People
                                    CompanionTab.MULTIPLAYER -> Icons.Rounded.Groups
                                    CompanionTab.EXPLORE -> Icons.Rounded.Explore
                                }
                                if (tab == CompanionTab.CHATS && (state.unreadTotal > 0 || state.hasDotOnlyUnread)) {
                                    BadgedBox(
                                        badge = {
                                            if (state.unreadTotal > 0) {
                                                Badge {
                                                    Text(
                                                        text = if (state.unreadTotal > 99) "99+" else state.unreadTotal.toString(),
                                                        style = MaterialTheme.typography.labelSmall.tnum()
                                                    )
                                                }
                                            } else {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(iconVector, contentDescription = tab.label)
                                    }
                                } else if (tab == CompanionTab.MULTIPLAYER && state.mpChannels.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    text = state.mpChannels.size.toString(),
                                                    style = MaterialTheme.typography.labelSmall.tnum()
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(iconVector, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(iconVector, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.activeTab == CompanionTab.CHATS && !isTwoPane) {
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    shape = cardShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    modifier = Modifier.border(
                        width = OsuRadius.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = cardShape
                    )
                ) {
                    Icon(Icons.Rounded.AddComment, contentDescription = "New DM or join channel")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val isConnecting = state.connectionStatus == ConnectionStatus.CONNECTING ||
                state.connectionStatus == ConnectionStatus.RECONNECTING ||
                state.isRefreshingConnection
            if (isConnecting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (state.activeTab) {
                    CompanionTab.CHATS -> {
                        val channelsListPane: @Composable (Modifier) -> Unit = { paneModifier ->
                            var isChatSearchFocused by remember { mutableStateOf(false) }
                            val showSearchScopeToggle = isChatSearchFocused ||
                                state.chatSearchQuery.isNotEmpty() ||
                                state.chatSearchScope == ChatSearchScope.ALL_MESSAGES
                            Column(modifier = paneModifier) {
                                OutlinedTextField(
                                    value = state.chatSearchQuery,
                                    onValueChange = viewModel::onChatSearchQueryChange,
                                    placeholder = {
                                        Text(
                                            if (state.chatSearchScope == ChatSearchScope.ALL_MESSAGES) {
                                                "Search all messages"
                                            } else {
                                                "Search chats or channels"
                                            }
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (state.chatSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.onChatSearchQueryChange("") }) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    shape = controlShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm)
                                        .onFocusChanged { isChatSearchFocused = it.isFocused }
                                )

                                AnimatedVisibility(
                                    visible = showSearchScopeToggle,
                                    enter = OsuMotion.expandEnter(),
                                    exit = OsuMotion.expandExit()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs),
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ChatSearchScope.entries.forEach { scopeOption ->
                                            OsuFilterChip(
                                                label = scopeOption.label,
                                                selected = state.chatSearchScope == scopeOption,
                                                onClick = { viewModel.onChatSearchScopeSelected(scopeOption) }
                                            )
                                        }
                                    }
                                }

                                if (state.chatSearchScope == ChatSearchScope.ALL_MESSAGES) {
                                    val trimmedQuery = state.chatSearchQuery.trim()
                                    if (state.isGlobalMessageSearchLoading) {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    }
                                    if (trimmedQuery.length < 2) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(OsuSpacing.xxl),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Type at least 2 characters to search messages",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else if (state.globalMessageSearchHits.isEmpty() && !state.isGlobalMessageSearchLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(OsuSpacing.xxl),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            OsuGroupedContainer {
                                                OsuEmptyState(
                                                    icon = Icons.Rounded.Search,
                                                    title = "No matching messages",
                                                    subtitle = "No stored messages match \"$trimmedQuery\""
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                start = OsuSpacing.lg,
                                                end = OsuSpacing.lg,
                                                top = OsuSpacing.sm,
                                                bottom = if (!isTwoPane) 80.dp else OsuSpacing.lg
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                        ) {
                                            items(
                                                items = state.globalMessageSearchHits,
                                                key = { "${it.channelId}_${it.messageId}" },
                                                contentType = { "global_msg_hit" }
                                            ) { hit ->
                                                GlobalMessageSearchHitCard(
                                                    hit = hit,
                                                    query = trimmedQuery,
                                                    onClick = { openChannelMessageTarget(hit.channelId, hit.messageId) }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs),
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                    ) {
                                        items(
                                            items = ChatFilterTab.entries,
                                            key = { it.name },
                                            contentType = { "filter_tab" }
                                        ) { tab ->
                                            val selected = state.filterTab == tab
                                            val showUnreadCount = tab == ChatFilterTab.UNREAD && state.unreadTotal > 0
                                            val label = if (showUnreadCount) {
                                                "${tab.label} (${state.unreadTotal})"
                                            } else {
                                                tab.label
                                            }
                                            OsuFilterChip(
                                                label = label,
                                                selected = selected,
                                                onClick = { viewModel.onFilterTabSelected(tab) },
                                                isNumeric = showUnreadCount
                                            )
                                        }
                                    }

                                    if (state.onlineFriends.isNotEmpty()) {
                                        OnlineFriendsBar(
                                            friends = state.onlineFriends,
                                            onFriendClick = { friend -> viewModel.openFriendDmFast(friend, openChannelTarget) },
                                            onSeeAllClick = { viewModel.onCompanionTabSelected(CompanionTab.FRIENDS) }
                                        )
                                        OsuHairlineDivider()
                                    }

                                    if (state.channels.isEmpty()) {
                                        val isFiltered = state.allChannels.isNotEmpty()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(OsuSpacing.xxl),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            OsuGroupedContainer {
                                                OsuEmptyState(
                                                    icon = Icons.Rounded.Forum,
                                                    title = if (isFiltered) "No matching conversations" else "No conversations yet",
                                                    subtitle = if (isFiltered) {
                                                        "No chats match your current search or filter"
                                                    } else {
                                                        "Start a direct message or join an official Bancho channel"
                                                    },
                                                    primaryActionLabel = if (isFiltered) "Reset filters" else "New chat",
                                                    onPrimaryAction = {
                                                        if (isFiltered) {
                                                            viewModel.resetChatSearchAndFilter()
                                                        } else {
                                                            showNewChatDialog = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                bottom = if (!isTwoPane) 80.dp else OsuSpacing.lg
                                            )
                                        ) {
                                            items(
                                                items = state.channels,
                                                key = { it.id },
                                                contentType = { it.type }
                                            ) { channel ->
                                                val isDotOnly = channel.notificationMode == ChannelNotificationMode.MUTED ||
                                                    channel.matchesNormalizedChannelSet(state.mutedChannels) ||
                                                    channel.matchesNormalizedChannelSet(state.dotOnlyChannels)
                                                val isSelectedInTwoPane = isTwoPane &&
                                                    !selectedTwoPaneChannelId.isNullOrBlank() &&
                                                    channel.id.equals(selectedTwoPaneChannelId, ignoreCase = true)
                                                ChannelListRow(
                                                    channel = channel,
                                                    isDotOnlyBadge = isDotOnly,
                                                    isSelected = isSelectedInTwoPane,
                                                    onClick = { openChannelTarget(channel.id) },
                                                    onLongClick = { channelOptionsTargetId = channel.id },
                                                    onAvatarClick = {
                                                        if (channel.type == ChannelType.DIRECT) {
                                                            viewModel.openPlayerProfile(channel.name)
                                                        } else {
                                                            openChannelTarget(channel.id)
                                                        }
                                                    }
                                                )
                                                OsuHairlineDivider(modifier = Modifier.padding(start = 76.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isTwoPane) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                channelsListPane(
                                    Modifier
                                        .width(328.dp)
                                        .fillMaxHeight()
                                )
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    thickness = OsuRadius.hairline,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    if (!selectedTwoPaneChannelId.isNullOrBlank()) {
                                        ChatScreen(
                                            onBack = { onSelectTwoPaneChannel(null, null) },
                                            onNavigateToChannel = { targetCh ->
                                                onSelectTwoPaneChannel(targetCh, null)
                                            },
                                            onOpenMatchAnalyzer = onOpenMatchAnalyzer,
                                            onOpenSettings = onOpenSettings,
                                            overrideChannelId = selectedTwoPaneChannelId,
                                            overrideFocusMessageId = selectedTwoPaneFocusMessageId,
                                            isEmbeddedInTwoPane = true
                                        )
                                    } else {
                                        TwoPaneEmptyChatPlaceholder(
                                            onStartNewChat = { showNewChatDialog = true },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        } else {
                            channelsListPane(Modifier.fillMaxSize())
                        }
                    }

                    CompanionTab.FRIENDS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = state.friendSearchQuery,
                                onValueChange = viewModel::onFriendSearchQueryChange,
                                placeholder = {
                                    Text(
                                        if (state.friendsFilterTab == FriendsFilterTab.SCORE_FEED) {
                                            "Filter score feed"
                                        } else {
                                            "Filter friends"
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (state.friendSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = viewModel::clearFriendSearch) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                shape = controlShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                items(
                                    items = FriendsFilterTab.entries,
                                    key = { it.name },
                                    contentType = { "friend_filter_tab" }
                                ) { tab ->
                                    val selected = state.friendsFilterTab == tab
                                    val countSuffix = when (tab) {
                                        FriendsFilterTab.ONLINE -> state.onlineFriends.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                                        FriendsFilterTab.MUTUAL -> state.mutualFriendsCount.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                                        FriendsFilterTab.TRACKED_PP -> state.trackedFriendsCount.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                                        FriendsFilterTab.RANKING -> state.friendsLeaderboard.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                                        FriendsFilterTab.SCORE_FEED -> state.friendScoreEvents.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                                        FriendsFilterTab.ALL -> ""
                                    }
                                    OsuFilterChip(
                                        label = "${tab.label}$countSuffix",
                                        selected = selected,
                                        onClick = { viewModel.onFriendsFilterTabSelected(tab) },
                                        isNumeric = countSuffix.isNotEmpty()
                                    )
                                }
                            }

                            if (state.friendsFilterTab == FriendsFilterTab.RANKING) {
                                FriendsRankingLeaderboardSection(
                                    entries = state.friendsLeaderboard,
                                    searchQuery = state.friendSearchQuery,
                                    selectedMode = state.friendsRankingMode,
                                    selectedSort = state.friendsRankingSort,
                                    isLoading = state.isFriendsRankingLoading,
                                    onSelectMode = viewModel::selectFriendsRankingMode,
                                    onSelectSort = viewModel::selectFriendsRankingSort,
                                    onRefresh = viewModel::refreshFriendsRanking,
                                    onOpenProfile = { username -> viewModel.openPlayerProfile(username) }
                                )
                            } else if (state.friendsFilterTab == FriendsFilterTab.SCORE_FEED) {
                                FriendScoreFeedSection(
                                    events = state.friendScoreEvents,
                                    authMode = state.settings.authMode,
                                    isRefreshing = state.isScoreFeedRefreshing,
                                    onRefresh = viewModel::refreshFriendScoreFeed,
                                    onOpenProfile = { username -> viewModel.openPlayerProfile(username) },
                                    onShareScoreCard = { event ->
                                        viewModel.promptShareScore(event.toOsuScoreItem())
                                    },
                                    onOpenBeatmap = { event ->
                                        if (event.beatmapId > 0L) {
                                            miniPlayerStatsTrack = AudioQueueTrack(
                                                beatmapId = event.beatmapId,
                                                beatmapsetId = 0L,
                                                title = event.beatmapTitle,
                                                version = event.beatmapVersion,
                                                starRating = event.starRating
                                            )
                                        }
                                    }
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val trackedCount = state.friends.count { it.isTrackedForPp }
                                            val onlineAlertsCount = state.friends.count { it.notifyOnline }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Friend alerts",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "$onlineAlertsCount online alerts · $trackedCount Top 5 PP tracked",
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(
                                                onClick = viewModel::checkTrackedFriendsTopPlaysNow,
                                                enabled = !state.isPpCheckRunning &&
                                                    trackedCount > 0 &&
                                                    state.settings.authMode == AuthMode.OAUTH_V2,
                                                shape = controlShape
                                            ) {
                                                if (state.isPpCheckRunning) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Text("Check PP")
                                                }
                                            }
                                        }
                                        OsuHairlineDivider()
                                    }
                                }

                                FriendsListSection(
                                    friends = state.friends,
                                    allFriendsCount = state.allFriendsCount,
                                    searchQuery = state.friendSearchQuery,
                                    authMode = state.settings.authMode,
                                    isLoading = state.isFriendsLoading,
                                    errorMessage = state.friendsError,
                                    onOpenDm = { friend -> viewModel.openFriendDmFast(friend, openChannelTarget) },
                                    onOpenProfile = { friend -> viewModel.openPlayerProfile(friend.username) },
                                    onToggleOnlineAlert = { friend -> viewModel.toggleFriendOnlineAlert(friend) },
                                    onToggleTrackPp = { friend ->
                                        viewModel.toggleTrackFriendPp(friend.username, friend.userId, friend.avatarUrl)
                                    },
                                    onRefresh = viewModel::refreshFriends,
                                    onClearSearch = viewModel::clearFriendSearch,
                                    onBrowsePlayers = viewModel::openRankingsDestination
                                )
                            }
                        }
                    }

                    CompanionTab.MULTIPLAYER -> {
                        MultiplayerHubSection(
                            mpChannels = state.mpChannels,
                            activeLobby = state.activeMpLobby,
                            recentMessages = state.activeMpMessages,
                            matchHistory = state.activeMpMatchHistory,
                            isHistoryLoading = state.isMpHistoryLoading,
                            historyError = state.activeMpMatchHistoryError,
                            isBusy = state.isMpActionBusy,
                            connectionStatus = state.connectionStatus,
                            selfUsername = state.settings.username,
                            onSelectMpChannel = viewModel::selectMpChannel,
                            onCreateLobby = viewModel::createMultiplayerLobby,
                            onJoinLobbyByInput = viewModel::joinMultiplayerLobby,
                            onSendCommandOrChat = viewModel::sendMpChannelInput,
                            onRefreshMatchHistory = viewModel::refreshMatchHistory,
                            onOpenBeatmapSearch = viewModel::openBeatmapSearchForMp,
                            onOpenFullChat = openChannelTarget,
                            onOpenPlayerProfile = viewModel::openPlayerProfile,
                            onDismissClosedLobby = viewModel::dismissClosedMpLobby,
                            onReconnect = viewModel::refreshConnection,
                            mappools = state.mappools,
                            onSaveMappool = viewModel::saveMappool,
                            onDeleteMappool = viewModel::deleteMappool,
                            onSelectMappoolSlot = viewModel::applyMappoolSlotToActiveLobby,
                            seriesState = state.activeMpSeriesState,
                            refereeMacros = state.refereeMacros,
                            onSetSeriesBestOf = viewModel::setActiveMpBestOf,
                            onSetSeriesTeamNames = viewModel::setActiveMpTeamNames,
                            onAdjustSeriesScore = viewModel::adjustActiveMpScore,
                            onUndoLastSeriesRound = viewModel::undoLastActiveMpRound,
                            onResetSeries = viewModel::resetActiveMpSeries,
                            onSetAutoScoreTeamVs = viewModel::setActiveMpAutoScore,
                            onSetWarmupActive = viewModel::setActiveMpWarmup,
                            onSetSelectedPoolId = viewModel::setActiveMpSelectedPoolId,
                            onSetPickBanMode = viewModel::setActiveMpPickBanMode,
                            onUpdatePoolSlotState = viewModel::updateActiveMpPoolSlotState,
                            onSendMacro = viewModel::sendActiveMpMacro,
                            onSaveMacros = viewModel::saveRefereeMacros,
                            onResetMacros = viewModel::resetRefereeMacros,
                            onNotifyMessage = viewModel::notifyMessage,
                            onGetCachedBeatmaps = viewModel::getCachedBeatmaps,
                            onResolveBeatmapDetails = viewModel::resolveBeatmapDetails,
                            onOpenMatchAnalyzer = onOpenMatchAnalyzer,
                            matchSetupTemplates = state.matchSetupTemplates,
                            isWizardRunning = state.isWizardRunning,
                            wizardStepMessage = state.wizardStepMessage,
                            wizardError = state.wizardError,
                            onSaveMatchSetupTemplates = viewModel::saveMatchSetupTemplates,
                            onRunMatchSetupWizard = viewModel::runMatchSetupWizard,
                            onCancelMatchSetupWizard = viewModel::cancelMatchSetupWizard,
                            onClearMatchSetupWizardError = viewModel::clearMatchSetupWizardError,
                            shouldOpenMatchWizard = state.pendingOpenMatchWizard,
                            onConsumedOpenMatchWizard = viewModel::consumePendingOpenMatchWizard
                        )
                    }

                    CompanionTab.EXPLORE -> {
                        ExploreAndProfileSection(
                            subTab = state.exploreSubTab,
                            onSelectSubTab = viewModel::onExploreSubTabSelected,
                            beatmapFilter = state.beatmapFilter,
                            onBeatmapQueryChange = viewModel::onBeatmapQueryChange,
                            onBeatmapFilterChange = viewModel::onBeatmapFilterChange,
                            onResetBeatmapFilters = viewModel::resetBeatmapFilters,
                            onClearBeatmapQueryAndFilters = viewModel::clearBeatmapQueryAndFilters,
                            beatmapResults = state.beatmapResults,
                            isBeatmapsLoading = state.isBeatmapsLoading,
                            hasSearchedBeatmaps = state.hasSearchedBeatmaps,
                            beatmapsError = state.beatmapsError,
                            onSearchBeatmaps = viewModel::searchBeatmaps,
                            isForYouMode = state.isForYouMode,
                            onToggleForYouMode = viewModel::setBeatmapsForYouMode,
                            recommendationPreset = state.recommendationPreset,
                            recommendationProfile = state.recommendationProfile,
                            recommendedBeatmaps = state.recommendedBeatmaps,
                            isRecommendationsLoading = state.isRecommendationsLoading,
                            recommendationsError = state.recommendationsError,
                            onSelectRecommendationPreset = viewModel::selectRecommendationPreset,
                            onRefreshRecommendations = viewModel::refreshRecommendations,
                            onPlayRecommendedQueue = viewModel::playRecommendedQueue,
                            hasActiveMpLobby = state.activeMpLobby?.isClosed == false,
                            onSelectBeatmapForMp = viewModel::selectBeatmapForMpLobby,
                            onResolveBeatmapDetails = viewModel::resolveBeatmapDetails,
                            onLoadBeatmapScores = viewModel::loadBeatmapLeaderboard,
                            onShowMessage = viewModel::showMessage,
                            audioState = state.audioState,
                            onToggleAudio = viewModel::toggleBeatmapAudio,
                            onPlaySavedQueue = viewModel::playSavedBeatmapsQueue,
                            onPlaySearchResultsQueue = viewModel::playSearchResultsQueue,
                            rankingMode = state.rankingMode,
                            onRankingModeChange = viewModel::onRankingModeChange,
                            rankingCountry = state.rankingCountry,
                            onRankingCountryChange = viewModel::onRankingCountryChange,
                            playerSearchQuery = state.playerSearchQuery,
                            onPlayerSearchQueryChange = viewModel::onPlayerSearchQueryChange,
                            onClearPlayerSearch = viewModel::clearPlayerSearch,
                            isPlayerSearchActive = state.isPlayerSearchActive,
                            players = state.players,
                            isPlayersLoading = state.isPlayersLoading,
                            playersError = state.playersError,
                            onLoadRankings = viewModel::loadRankings,
                            onSearchPlayers = viewModel::searchPlayers,
                            onOpenPlayerProfile = viewModel::openPlayerProfile,
                            onToggleTrackPp = { p ->
                                viewModel.toggleTrackFriendPp(p.username, p.userId, p.avatarUrl)
                            },
                            selfProfile = state.selfProfile,
                            isLoadingSelfProfile = state.isLoadingSelfProfile,
                            selfProfileError = state.selfProfileError,
                            selfProfileMode = state.selfProfileMode,
                            selfPinnedScores = state.selfPinnedScores,
                            selfBestScores = state.selfBestScores,
                            selfRecentScores = state.selfRecentScores,
                            isLoadingSelfScores = state.isLoadingSelfScores,
                            isLoadingMoreSelfBestScores = state.isLoadingMoreSelfBestScores,
                            canLoadMoreSelfBestScores = state.canLoadMoreSelfBestScores,
                            onLoadMoreSelfBestScores = viewModel::loadMoreSelfBestScores,
                            selfUsername = state.settings.username,
                            selfProfileHistory = state.selfProfileHistory,
                            selfProfileOsuTrack = state.selfProfileOsuTrack,
                            isLoadingSelfOsuTrack = state.isSelfProfileOsuTrackLoading,
                            selfDailySession = state.selfDailySession,
                            isLoadingDailySession = state.isLoadingDailySession,
                            onRefreshDailySession = { viewModel.refreshDailySession() },
                            onSelectSelfProfileMode = viewModel::selectSelfProfileMode,
                            onRefreshSelfProfile = viewModel::refreshSelfProfile,
                            onShareSelfScore = viewModel::promptShareScore,
                            onEstimateRankForPp = viewModel::estimateRankForPp,
                            onEstimatePpForRank = viewModel::estimatePpForRank,
                            savedBeatmaps = state.savedBeatmaps,
                            filteredSavedBeatmaps = state.filteredSavedBeatmaps,
                            availablePlayLaterTags = state.availablePlayLaterTags,
                            playLaterSelectedTag = state.playLaterSelectedTag,
                            playLaterSortOption = state.playLaterSortOption,
                            playLaterSortDescending = state.playLaterSortDescending,
                            savedBeatmapIds = state.savedBeatmapIds,
                            isSendingQueueToInGame = state.isSendingQueueToInGame,
                            oszDownloadStates = state.oszDownloadStates,
                            onToggleSaveBeatmap = viewModel::toggleSaveBeatmap,
                            onRemoveSavedBeatmap = viewModel::removeSavedBeatmap,
                            onSelectPlayLaterTag = viewModel::selectPlayLaterTag,
                            onSelectPlayLaterSortOption = viewModel::selectPlayLaterSortOption,
                            onTogglePlayLaterSortDirection = viewModel::togglePlayLaterSortDirection,
                            onUpdateSavedBeatmapTags = viewModel::updateSavedBeatmapTags,
                            onDownloadOsz = viewModel::downloadBeatmapSetOsz,
                            onShareOsz = viewModel::shareBeatmapSetOsz,
                            onSendBeatmapToInGame = viewModel::sendSavedBeatmapToInGame,
                            onSendAllSavedBeatmapsToInGame = viewModel::sendAllSavedBeatmapsToInGame,
                            rankPpMode = state.rankPpMode,
                            isConvertingRankPp = state.isConvertingRankPp,
                            rankPpResult = state.rankPpResult,
                            rankPpError = state.rankPpError,
                            onRankPpModeChange = viewModel::onRankPpModeChange,
                            onLookupPpForRank = viewModel::lookupPpForRank,
                            onLookupRankForPp = viewModel::lookupRankForPp,
                            newsFeed = state.newsFeed,
                            isNewsLoading = state.isNewsLoading,
                            newsError = state.newsError,
                            onRefreshNews = { viewModel.loadNewsAndSpotlights(forceRefresh = true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineFriendsBar(
    friends: List<FriendContact>,
    onFriendClick: (FriendContact) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Online friends (${friends.size})",
                style = MaterialTheme.typography.labelLarge.tnum(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onSeeAllClick,
                shape = RoundedCornerShape(OsuRadius.md)
            ) {
                Text("See all", maxLines = 1)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(start = OsuSpacing.lg, end = OsuSpacing.lg, bottom = OsuSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            items(
                items = friends,
                key = { it.username.lowercase() },
                contentType = { "online_friend" }
            ) { friend ->
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(OsuRadius.md))
                        .clickable { onFriendClick(friend) }
                        .padding(vertical = OsuSpacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerAvatar(
                        name = friend.username,
                        avatarUrl = friend.avatarUrl,
                        size = 44.dp,
                        showOnlineDot = true,
                        isOnline = friend.isOnline
                    )
                    Spacer(modifier = Modifier.height(OsuSpacing.xs))
                    Text(
                        text = friend.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsListSection(
    friends: List<FriendContact>,
    allFriendsCount: Int,
    searchQuery: String,
    authMode: AuthMode,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenDm: (FriendContact) -> Unit,
    onOpenProfile: (FriendContact) -> Unit,
    onToggleOnlineAlert: (FriendContact) -> Unit,
    onToggleTrackPp: (FriendContact) -> Unit,
    onRefresh: () -> Unit,
    onClearSearch: () -> Unit,
    onBrowsePlayers: () -> Unit
) {
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    PullToRefreshBox(
        isRefreshing = isLoading && allFriendsCount > 0,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && allFriendsCount == 0) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(count = 6, key = { "friend_skel_$it" }, contentType = { "skeleton" }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(OsuRadius.md))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Spacer(modifier = Modifier.width(OsuSpacing.md))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(OsuRadius.xs))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(OsuRadius.xs))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                    }
                    OsuHairlineDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        } else if (errorMessage != null && allFriendsCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(OsuSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRefresh,
                            shape = controlShape
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        } else if (friends.isEmpty()) {
            val isFiltered = allFriendsCount > 0 && searchQuery.isNotBlank()
            val needsOAuth = !isFiltered && authMode != AuthMode.OAUTH_V2
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(OsuSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                OsuGroupedContainer {
                    OsuEmptyState(
                        icon = Icons.Rounded.People,
                        title = when {
                            isFiltered -> "No matching friends"
                            needsOAuth -> "osu! OAuth required"
                            else -> "No friends synced"
                        },
                        subtitle = when {
                            isFiltered -> "No friends match \"${searchQuery.trim()}\""
                            needsOAuth -> "Sign in with OAuth for friends sync, or search players in Rankings"
                            else -> "Sync your osu! friends list or find players in Rankings"
                        },
                        primaryActionLabel = when {
                            isFiltered -> "Clear filter"
                            needsOAuth -> "Find players"
                            else -> "Sync friends"
                        },
                        onPrimaryAction = when {
                            isFiltered -> onClearSearch
                            needsOAuth -> onBrowsePlayers
                            else -> onRefresh
                        },
                        secondaryActionLabel = if (!isFiltered && !needsOAuth) "Find players" else null,
                        onSecondaryAction = if (!isFiltered && !needsOAuth) onBrowsePlayers else null
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = friends,
                    key = { it.username.lowercase() },
                    contentType = { "friend_row" }
                ) { friend ->
                    val subtitle = buildList {
                        add(friend.statusText)
                        friend.globalRank?.takeIf { it > 0 }?.let { add("#${numFmt.format(it)}") }
                        friend.pp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                            add("${numFmt.format(kotlin.math.round(it).toInt())}pp")
                        }
                    }.joinToString(" · ")

                    val alertTint by animateColorAsState(
                        targetValue = if (friend.notifyOnline) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "friendAlertTint"
                    )
                    val trackTint by animateColorAsState(
                        targetValue = if (friend.isTrackedForPp) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "friendTrackTint"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDm(friend) }
                            .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(
                            name = friend.username,
                            avatarUrl = friend.avatarUrl,
                            size = 48.dp,
                            showOnlineDot = true,
                            isOnline = friend.isOnline,
                            modifier = Modifier.clickable { onOpenProfile(friend) }
                        )
                        Spacer(modifier = Modifier.width(OsuSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.tnum(),
                                color = if (friend.isOnline) {
                                    StatusOnline
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (friend.userId > 0L) {
                            IconButton(
                                onClick = { onToggleOnlineAlert(friend) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (friend.notifyOnline) {
                                        Icons.Rounded.NotificationsActive
                                    } else {
                                        Icons.Rounded.NotificationsNone
                                    },
                                    contentDescription = "Online Alert",
                                    tint = alertTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onToggleTrackPp(friend) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = "Track Top 5 PP Plays",
                                    tint = trackTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        TextButton(
                            onClick = { onOpenDm(friend) },
                            shape = controlShape
                        ) {
                            Text("DM")
                        }
                    }
                    OsuHairlineDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun FriendsRankingLeaderboardSection(
    entries: List<FriendLeaderboardEntry>,
    searchQuery: String,
    selectedMode: String,
    selectedSort: FriendsRankingSort,
    isLoading: Boolean,
    onSelectMode: (String) -> Unit,
    onSelectSort: (FriendsRankingSort) -> Unit,
    onRefresh: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val modeOptions = remember {
        listOf("osu" to "osu!", "taiko" to "taiko", "fruits" to "catch", "mania" to "mania")
    }
    val selfEntry = remember(entries) { entries.firstOrNull { it.isSelf } }
    val friendAbove = remember(entries, selfEntry) {
        selfEntry?.let { me ->
            if (me.rankPosition > 1) entries.getOrNull(me.rankPosition - 2) else null
        }
    }
    val visibleEntries = remember(entries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) entries
        else entries.filter {
            it.isSelf ||
                it.username.contains(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        items(modeOptions, key = { it.first }) { (modeKey, modeLabel) ->
                            OsuFilterChip(
                                label = modeLabel,
                                selected = selectedMode == modeKey,
                                onClick = { onSelectMode(modeKey) }
                            )
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh friends ranking",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    items(FriendsRankingSort.entries, key = { it.name }) { sortOpt ->
                        OsuFilterChip(
                            label = "Sort: ${sortOpt.label}",
                            selected = selectedSort == sortOpt,
                            onClick = { onSelectSort(sortOpt) }
                        )
                    }
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
                OsuHairlineDivider()
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(OsuSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sync friends in osu! OAuth mode to build your friends leaderboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(OsuSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                selfEntry?.let { me ->
                    item(key = "self_standing_header", contentType = "self_standing") {
                        val gapSummary = when {
                            friendAbove == null -> "Leading your friends list"
                            selectedSort == FriendsRankingSort.ACCURACY &&
                                me.accuracy != null && friendAbove.accuracy != null ->
                                String.format(
                                    Locale.US,
                                    "%.2f%% behind %s (#%d)",
                                    (friendAbove.accuracy - me.accuracy).coerceAtLeast(0.0),
                                    friendAbove.username,
                                    friendAbove.rankPosition
                                )
                            me.pp != null && friendAbove.pp != null ->
                                String.format(
                                    Locale.US,
                                    "%dpp behind %s (#%d)",
                                    kotlin.math.round((friendAbove.pp - me.pp).coerceAtLeast(0.0)).toInt(),
                                    friendAbove.username,
                                    friendAbove.rankPosition
                                )
                            else -> "Ahead: ${friendAbove.username} (#${friendAbove.rankPosition})"
                        }
                        OsuAccentBarContainer(
                            showAccentBar = true,
                            accentColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            shape = cardShape
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(OsuSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "You are #${me.rankPosition} of ${entries.size} friends",
                                        style = MaterialTheme.typography.titleSmall.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = gapSummary,
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                me.pp?.takeIf { it.isFinite() && it > 0.0 }?.let { myPp ->
                                    Text(
                                        text = "${numFmt.format(kotlin.math.round(myPp).toInt())}pp",
                                        style = MaterialTheme.typography.titleSmall.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = visibleEntries,
                    key = { "${it.isSelf}_${it.username.lowercase()}" },
                    contentType = { "friend_leaderboard_row" }
                ) { entry ->
                    val rankColor = when (entry.rankPosition) {
                        1 -> PodiumGold
                        2 -> PodiumSilver
                        3 -> PodiumBronze
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    OsuAccentBarContainer(
                        showAccentBar = entry.isSelf,
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerColor = if (entry.isSelf) {
                            surfaces.accentSubtle
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = cardShape,
                        onClick = { onOpenProfile(entry.username) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${entry.rankPosition}",
                                style = MaterialTheme.typography.titleSmall.tnum(),
                                fontWeight = FontWeight.SemiBold,
                                color = rankColor,
                                modifier = Modifier.width(36.dp)
                            )
                            PlayerAvatar(
                                name = entry.username,
                                avatarUrl = entry.avatarUrl,
                                size = 40.dp,
                                showOnlineDot = true,
                                isOnline = entry.isOnline
                            )
                            Spacer(modifier = Modifier.width(OsuSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    val flag = countryCodeToFlagEmoji(entry.countryCode)
                                    if (flag.isNotEmpty()) {
                                        Text(
                                            text = flag,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    } else if (entry.countryCode.isNotBlank() && entry.countryCode != "XX") {
                                        Text(
                                            text = entry.countryCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = entry.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val rankText = entry.globalRank?.takeIf { it > 0 }
                                    ?.let { "Global #${numFmt.format(it)}" }
                                    ?: "Unranked / Syncing"
                                Text(
                                    text = rankText,
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = entry.pp?.takeIf { it.isFinite() && it > 0.0 }
                                        ?.let { "${numFmt.format(kotlin.math.round(it).toInt())}pp" }
                                        ?: "— pp",
                                    style = MaterialTheme.typography.labelLarge.tnum(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (entry.isSelf) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = entry.accuracy?.takeIf { it.isFinite() && it > 0.0 }
                                        ?.let { String.format(Locale.US, "%.2f%%", it) }
                                        ?: "—%",
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FriendScoreFeedSection(
    events: List<FriendScoreEventItem>,
    authMode: AuthMode,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onShareScoreCard: (FriendScoreEventItem) -> Unit,
    onOpenBeatmap: (FriendScoreEventItem) -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val controlShape = RoundedCornerShape(OsuRadius.md)
    val timeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${events.size} recent friend scores",
                        style = MaterialTheme.typography.labelMedium.tnum(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing && authMode == AuthMode.OAUTH_V2,
                        shape = controlShape
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(OsuSpacing.xs))
                            Text("Refresh")
                        }
                    }
                }
                OsuHairlineDivider()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing && events.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(OsuSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    OsuGroupedContainer(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OsuEmptyState(
                            icon = Icons.Rounded.EmojiEvents,
                            title = "No friend score activity",
                            subtitle = if (authMode != AuthMode.OAUTH_V2) {
                                "Sign in with osu! OAuth in Settings to sync friend scores"
                            } else {
                                "Track friends for Top 5 PP or refresh to load recent plays"
                            },
                            primaryActionLabel = if (authMode == AuthMode.OAUTH_V2 && !isRefreshing) {
                                "Refresh score feed"
                            } else {
                                null
                            },
                            onPrimaryAction = if (authMode == AuthMode.OAUTH_V2 && !isRefreshing) onRefresh else null
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(OsuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    items(
                        items = events,
                        key = { it.scoreId },
                        contentType = { "friend_score_event" }
                    ) { event ->
                        Surface(
                            shape = cardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = event.beatmapId > 0L) { onOpenBeatmap(event) }
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
                                        .clickable { onOpenProfile(event.username) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                    ) {
                                        PlayerAvatar(
                                            name = event.username,
                                            avatarUrl = event.avatarUrl,
                                            size = 28.dp
                                        )
                                        Text(
                                            text = event.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                                    Text(
                                        text = timeFormatter.format(Date(event.recordedAt)),
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = surfaces.textMuted,
                                        maxLines = 1
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                                ) {
                                    GradeBadge(grade = event.rank)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                                    ) {
                                        Text(
                                            text = event.beatmapTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                        ) {
                                            if (event.beatmapVersion.isNotBlank()) {
                                                Text(
                                                    text = "[${event.beatmapVersion}]",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                            }
                                            if (event.starRating > 0.0) {
                                                Text(
                                                    text = String.format(Locale.US, "★ %.2f", event.starRating),
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    color = surfaces.textMuted,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    FlowRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        event.mods.forEach { mod ->
                                            OsuMicroTag(text = mod)
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%.2f%%", event.accuracy),
                                            style = MaterialTheme.typography.labelMedium.tnum(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (event.pp > 0.0) {
                                                "${kotlin.math.round(event.pp).toInt()}pp"
                                            } else {
                                                "—"
                                            },
                                            style = MaterialTheme.typography.labelLarge.tnum(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = { onShareScoreCard(event) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = "Share score card",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
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
}

private fun FriendScoreEventItem.toOsuScoreItem(): OsuScoreItem = OsuScoreItem(
    id = scoreId,
    userId = userId,
    username = username,
    rank = rank,
    pp = pp.takeIf { it > 0.0 },
    accuracy = accuracy,
    maxCombo = 0,
    mods = mods,
    passed = true,
    createdAt = createdAtIso,
    beatmapId = beatmapId.takeIf { it > 0L },
    beatmapTitle = beatmapTitle,
    beatmapVersion = beatmapVersion,
    starRating = starRating,
    userAvatarUrl = avatarUrl
)

@Composable
private fun GlobalMessageSearchHitCard(
    hit: GlobalMessageSearchHit,
    query: String,
    onClick: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val highlightColor = MaterialTheme.colorScheme.primary
    val timeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val channelBadgeLabel = if (hit.isDirectChannel && !hit.channelName.startsWith("@")) {
        "DM · ${hit.channelName}"
    } else {
        hit.channelName
    }
    val highlightedContent = remember(hit.content, query, highlightColor) {
        buildHighlightedSnippet(hit.content, query, highlightColor)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(OsuRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                OsuMicroTag(
                    text = channelBadgeLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = hit.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timeFormatter.format(Date(hit.timestamp)),
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = surfaces.textMuted,
                    maxLines = 1
                )
            }
            Text(
                text = highlightedContent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildHighlightedSnippet(
    content: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty() || content.isEmpty()) return AnnotatedString(content)
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < content.length) {
            val matchIdx = content.indexOf(cleanQuery, startIndex = cursor, ignoreCase = true)
            if (matchIdx < 0) {
                append(content.substring(cursor))
                break
            }
            if (matchIdx > cursor) {
                append(content.substring(cursor, matchIdx))
            }
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(content.substring(matchIdx, matchIdx + cleanQuery.length))
            }
            cursor = matchIdx + cleanQuery.length
        }
    }
}

@Composable
private fun TwoPaneEmptyChatPlaceholder(
    onStartNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(OsuSpacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        OsuGroupedContainer(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.width(340.dp)
        ) {
            OsuEmptyState(
                icon = Icons.Rounded.Forum,
                title = "Select a conversation",
                subtitle = "Choose a channel or direct message on the left",
                primaryActionLabel = "New chat",
                onPrimaryAction = onStartNewChat
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListRow(
    channel: ChatChannel,
    isDotOnlyBadge: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onAvatarClick: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val rowBg by animateColorAsState(
        targetValue = if (isSelected) surfaces.accentSubtle else Color.Transparent,
        animationSpec = OsuMotion.colorSpec(),
        label = "channelRowBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (isSelected) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(OsuRadius.accentBar)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(
                name = channel.name,
                avatarUrl = channel.avatarUrl,
                size = 48.dp,
                isChannel = channel.type == ChannelType.PUBLIC,
                isActive = isSelected,
                modifier = Modifier.clickable(onClick = onAvatarClick)
            )

            Spacer(modifier = Modifier.width(OsuSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        if (channel.isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = channel.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (channel.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        when (channel.notificationMode) {
                            ChannelNotificationMode.MUTED -> Icon(
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = "Muted",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            ChannelNotificationMode.MENTIONS_ONLY -> Icon(
                                imageVector = Icons.Rounded.AlternateEmail,
                                contentDescription = "Mentions only",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            ChannelNotificationMode.ALL -> Unit
                        }
                    }
                    if (channel.lastMessageTimestamp > 0L) {
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Text(
                            text = timeFormatter.format(Date(channel.lastMessageTimestamp)),
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = if (channel.unreadCount > 0 && !isDotOnlyBadge) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                surfaces.textMuted
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(OsuSpacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val preview = when {
                        !channel.lastMessageText.isNullOrBlank() -> {
                            if (channel.type == ChannelType.PUBLIC && !channel.lastSenderName.isNullOrBlank()) {
                                "${channel.lastSenderName}: ${channel.lastMessageText}"
                            } else {
                                channel.lastMessageText
                            }
                        }
                        channel.description.isNotBlank() -> channel.description
                        else -> "No messages yet"
                    }

                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (channel.unreadCount > 0) {
                        Spacer(Modifier.width(OsuSpacing.sm))
                        if (isDotOnlyBadge) {
                            Box(
                                modifier = Modifier
                                    .size(OsuSpacing.sm)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        } else {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(
                                    text = if (channel.unreadCount > 99) "99+" else channel.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
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
private fun NewChatOrChannelDialog(
    onDismiss: () -> Unit,
    onStartDm: (String) -> Unit,
    onJoinChannel: (String) -> Unit
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var dmInput by rememberSaveable { mutableStateOf("") }
    var channelFilter by rememberSaveable { mutableStateOf("") }

    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val controlShape = RoundedCornerShape(OsuRadius.md)
    val chipShape = RoundedCornerShape(OsuRadius.md)

    val submitDm: () -> Unit = {
        val trimmed = dmInput.trim()
        if (trimmed.isNotEmpty()) {
            onStartDm(trimmed)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = if (mode == 0) "New direct message" else "Official osu! channels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        shape = RoundedCornerShape(topStart = OsuRadius.md, bottomStart = OsuRadius.md)
                    ) { Text("Direct message", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    SegmentedButton(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        shape = RoundedCornerShape(topEnd = OsuRadius.md, bottomEnd = OsuRadius.md)
                    ) { Text("Public channel", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }

                Spacer(modifier = Modifier.height(OsuSpacing.md))

                if (mode == 0) {
                    OutlinedTextField(
                        value = dmInput,
                        onValueChange = { dmInput = it },
                        label = { Text("osu! username or user ID") },
                        singleLine = true,
                        shape = controlShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitDm() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = channelFilter,
                        onValueChange = { channelFilter = it },
                        label = { Text("Filter channels (e.g. #osu, #taiko)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (channelFilter.isNotEmpty()) {
                                IconButton(onClick = { channelFilter = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear filter")
                                }
                            }
                        },
                        singleLine = true,
                        shape = controlShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(OsuSpacing.xs))
                    Text(
                        text = "Official Bancho channels only; join #mp_ rooms from Multiplayer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(OsuSpacing.sm))

                    val query = channelFilter.trim().lowercase()
                    val priorityFiltered = remember(query) {
                        if (query.isEmpty()) OfficialOsuChannels.PRIORITY
                        else OfficialOsuChannels.PRIORITY.filter { it.contains(query) }
                    }
                    val remainingFiltered = remember(query) {
                        if (query.isEmpty()) OfficialOsuChannels.REMAINING_ALPHABETICAL
                        else OfficialOsuChannels.REMAINING_ALPHABETICAL.filter { it.contains(query) }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (priorityFiltered.isNotEmpty()) {
                            Text(
                                text = "Priority channels",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(OsuSpacing.xs))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                priorityFiltered.forEach { ch ->
                                    AssistChip(
                                        onClick = { onJoinChannel(ch) },
                                        shape = chipShape,
                                        label = { Text(ch) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(OsuSpacing.md))
                        }

                        if (remainingFiltered.isNotEmpty()) {
                            Text(
                                text = "All official channels (A–Z)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(OsuSpacing.xs))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                remainingFiltered.forEach { ch ->
                                    AssistChip(
                                        onClick = { onJoinChannel(ch) },
                                        shape = chipShape,
                                        label = { Text(ch) }
                                    )
                                }
                            }
                        }

                        if (priorityFiltered.isEmpty() && remainingFiltered.isEmpty()) {
                            Text(
                                text = "No official channel matches \"$channelFilter\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = OsuSpacing.md)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode == 0) {
                Button(
                    onClick = submitDm,
                    enabled = dmInput.isNotBlank(),
                    shape = controlShape
                ) {
                    Text("Start chat")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = controlShape
            ) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChannelOptionsBottomSheet(
    channel: ChatChannel,
    isDotOnly: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleDotOnly: () -> Unit,
    onSelectNotificationMode: (ChannelNotificationMode) -> Unit,
    onCloseOrDelete: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = OsuRadius.xl, topEnd = OsuRadius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OsuSpacing.xl)
                .padding(bottom = OsuSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
            ) {
                PlayerAvatar(
                    name = channel.name,
                    avatarUrl = channel.avatarUrl,
                    size = 44.dp,
                    isChannel = channel.type == ChannelType.PUBLIC
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (channel.type == ChannelType.DIRECT) "Direct message" else "Public channel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OsuHairlineDivider()

            OutlinedButton(
                onClick = onTogglePin,
                shape = controlShape,
                border = osuHairlineBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(OsuSpacing.sm))
                Text(
                    text = if (channel.isPinned) "Unpin chat" else "Pin chat to top",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val isMuted = channel.notificationMode == ChannelNotificationMode.MUTED
                OutlinedButton(
                    onClick = onToggleMute,
                    shape = controlShape,
                    border = osuHairlineBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.NotificationsOff
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(OsuSpacing.sm))
                    Text(
                        text = if (isMuted) "Unmute notifications" else "Mute notifications",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onToggleDotOnly,
                    shape = controlShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDotOnly) surfaces.accentSubtle else Color.Transparent,
                        contentColor = if (isDotOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    border = osuHairlineBorder(
                        if (isDotOnly) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(OsuSpacing.sm)
                            .clip(CircleShape)
                            .background(
                                if (isDotOnly) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                    )
                    Spacer(Modifier.width(OsuSpacing.sm))
                    Text(
                        text = "Dot-only badge",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isDotOnly) {
                        Spacer(Modifier.width(OsuSpacing.xs))
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    ChannelNotificationMode.entries.forEach { mode ->
                        val selected = channel.notificationMode == mode
                        val icon = when (mode) {
                            ChannelNotificationMode.ALL -> Icons.Rounded.NotificationsActive
                            ChannelNotificationMode.MENTIONS_ONLY -> Icons.Rounded.AlternateEmail
                            ChannelNotificationMode.MUTED -> Icons.Rounded.NotificationsOff
                        }
                        OsuFilterChip(
                            label = mode.label,
                            selected = selected,
                            onClick = { onSelectNotificationMode(mode) },
                            leadingIcon = icon
                        )
                    }
                }
            }

            Button(
                onClick = onCloseOrDelete,
                shape = controlShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (channel.type == ChannelType.DIRECT) {
                        Icons.Rounded.Delete
                    } else {
                        Icons.AutoMirrored.Rounded.ExitToApp
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(OsuSpacing.sm))
                Text(
                    text = if (channel.type == ChannelType.DIRECT) {
                        "Delete conversation"
                    } else {
                        "Leave ${channel.name}"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
