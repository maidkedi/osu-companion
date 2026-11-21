package com.osuchat.app.ui.screens.chat

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.data.network.OAuthConfig
import com.osuchat.app.di.NetworkAndDatabaseModule
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChannelType
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ChatInlineEmbed
import com.osuchat.app.domain.model.ChatLogExportFormat
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.ChatUserColorInfo
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.DeliveryStatus
import com.osuchat.app.domain.model.MpLinkRef
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.ui.components.BeatmapPreviewCard
import com.osuchat.app.ui.components.BookmarksBottomSheet
import com.osuchat.app.ui.components.ChatBackgroundContainer
import com.osuchat.app.ui.components.MpLobbyBoard
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.components.OsuMicroTag
import com.osuchat.app.ui.components.PlayerAvatar
import com.osuchat.app.ui.components.PlayerStatsBottomSheet
import com.osuchat.app.ui.components.ScoreCardShareDialog
import com.osuchat.app.ui.components.TrackedPlayerAlertsDialog
import com.osuchat.app.ui.components.UserProfilePreviewCard
import com.osuchat.app.ui.components.WallpaperCustomizerPanel
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusConnecting
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.StatusRead
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.BeatmapUrlParser
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onNavigateToChannel: (String) -> Unit,
    onOpenMatchAnalyzer: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    overrideChannelId: String? = null,
    overrideFocusMessageId: String? = null,
    isEmbeddedInTwoPane: Boolean = false,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val activeChannelId = overrideChannelId?.takeIf { it.isNotBlank() } ?: viewModel.channelId
    val activeFocusMessageId = if (overrideChannelId != null) overrideFocusMessageId else viewModel.initialFocusMessageId
    val activeIsMpRoom = activeChannelId.startsWith("#mp_", ignoreCase = true)
    val activeIsDirectChannel = activeChannelId.startsWith("dm:", ignoreCase = true)
    val activeDmFallbackName = if (activeIsDirectChannel) {
        activeChannelId.substring(3).replace('_', ' ').trim().ifBlank { activeChannelId }
    } else {
        activeChannelId
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberSaveable(activeChannelId, saver = LazyListState.Saver) {
        LazyListState()
    }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val composerFocusRequester = remember { FocusRequester() }

    val isAtBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || !listState.canScrollForward
        }
    }
    var initialScrollDone by remember(activeChannelId, activeFocusMessageId) { mutableStateOf(false) }
    var prevLastMessageId by remember(activeChannelId) { mutableStateOf<String?>(null) }
    var prevFirstMessageId by remember(activeChannelId) { mutableStateOf<String?>(null) }
    var prevHadPaginationHeader by remember(activeChannelId) { mutableStateOf(false) }
    var unreadScrolledUpCount by remember(activeChannelId) { mutableIntStateOf(0) }
    var stickyBottom by remember(activeChannelId) { mutableStateOf(true) }
    var reauthBannerDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.requiresChatReadReauth) {
        if (!state.requiresChatReadReauth) {
            reauthBannerDismissed = false
        }
    }

    var inputValue by rememberSaveable(activeChannelId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyTarget by remember(activeChannelId) { mutableStateOf<ChatMessage?>(null) }
    var showOverflowMenu by remember(activeChannelId) { mutableStateOf(false) }
    var showNotifMenu by remember(activeChannelId) { mutableStateOf(false) }
    var showWallpaperSheet by remember(activeChannelId) { mutableStateOf(false) }
    var showUsersSheet by remember(activeChannelId) { mutableStateOf(false) }
    var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by remember(activeChannelId) { mutableStateOf(false) }
    var showExportFormatDialog by remember(activeChannelId) { mutableStateOf(false) }
    var previewImageUrl by rememberSaveable(activeChannelId) { mutableStateOf<String?>(null) }
    var pendingShareScoreCard by remember(activeChannelId) { mutableStateOf<OsuScoreItem?>(null) }
    var trackedAlertsDialogProfile by remember(activeChannelId) { mutableStateOf<PlayerProfile?>(null) }
    var showMpController by rememberSaveable(activeChannelId) { mutableStateOf(activeIsMpRoom) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAndSendImage(uri)
        }
    }
    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ChatLogExportFormat.PLAIN_TEXT.mimeType)
    ) { uri ->
        if (uri != null) {
            viewModel.exportChatLog(ChatLogExportFormat.PLAIN_TEXT, uri)
        }
    }
    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ChatLogExportFormat.MARKDOWN.mimeType)
    ) { uri ->
        if (uri != null) {
            viewModel.exportChatLog(ChatLogExportFormat.MARKDOWN, uri)
        }
    }

    val channel = state.channel
    val isDm = channel?.type == ChannelType.DIRECT || activeIsDirectChannel
    val dmTargetName = channel?.name ?: activeDmFallbackName
    val displayTitle = channel?.customAlias?.takeIf { it.isNotBlank() }
        ?: channel?.name
        ?: activeDmFallbackName

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activeChannelId, activeFocusMessageId, lifecycleOwner) {
        if (!overrideChannelId.isNullOrBlank()) {
            viewModel.selectChannel(overrideChannelId, overrideFocusMessageId)
        }
        viewModel.setChannelActive(true)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    viewModel.setChannelActive(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.stopBeatmapAudio()
                    viewModel.setChannelActive(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopBeatmapAudio()
            viewModel.setChannelActive(false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatUiEffect.NavigateBack -> onBack()
                is ChatUiEffect.NavigateToChannel -> onNavigateToChannel(effect.channelId)
                is ChatUiEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        effect.retryInput?.let { viewModel.sendInput(it) }
                        effect.retryMpCommand?.let { viewModel.sendMpCommand(it) }
                    }
                }
            }
        }
    }

    LaunchedEffect(activeChannelId) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .distinctUntilChanged()
            .collect { (scrolling, atBottom) ->
                if (atBottom) {
                    unreadScrolledUpCount = 0
                    stickyBottom = true
                } else if (scrolling) {
                    stickyBottom = false
                }
            }
    }

    var revealedIgnoredMessageIds by remember(activeChannelId) { mutableStateOf(emptySet<String>()) }
    val displayMessages = remember(
        state.messages,
        isDm,
        state.ignoredUsers,
        state.ignoreDisplayMode,
        state.selfUserId,
        state.selfUsername,
        state.selfIrcUsername
    ) {
        if (isDm || state.ignoredUsers.isEmpty() || state.ignoreDisplayMode != "HIDE") {
            state.messages
        } else {
            state.messages.filterNot { msg ->
                val isSelf = msg.isOutgoing || BeatmapUrlParser.isSelfOsuSender(
                    senderName = msg.senderName,
                    senderId = msg.senderId,
                    currentUserId = state.selfUserId,
                    currentUsername = state.selfUsername,
                    currentIrcUsername = state.selfIrcUsername
                )
                val norm = BeatmapUrlParser.normalizeOsuNick(msg.senderName)
                !isSelf && norm.isNotEmpty() && norm != "banchobot" && norm != "system" && norm in state.ignoredUsers
            }
        }.distinctBy { it.id }
    }

    val connectedAt = state.sessionConnectedAt
    val hasCachedHistory = connectedAt > 0L && displayMessages.any { it.timestamp < connectedAt }
    val firstLiveIndex = remember(displayMessages, hasCachedHistory, connectedAt) {
        if (hasCachedHistory) {
            displayMessages.indexOfFirst { it.timestamp >= connectedAt }
        } else {
            -1
        }
    }
    val expectedTotalItems = displayMessages.size +
        (if (state.canLoadOlderMessages) 1 else 0) +
        (if (hasCachedHistory) 1 else 0)

    LaunchedEffect(activeChannelId, expectedTotalItems) {
        var prevViewportHeight = 0
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect { viewportHeight ->
                if (viewportHeight > 0 && prevViewportHeight > 0 &&
                    viewportHeight < prevViewportHeight && stickyBottom && expectedTotalItems > 0
                ) {
                    listState.scrollToItem(expectedTotalItems - 1)
                }
                if (viewportHeight > 0) {
                    prevViewportHeight = viewportHeight
                }
            }
    }

    // Track tail appends vs head pagination accurately without scroll jitter or phantom unread counts.
    var consumedInitialFocus by remember(activeChannelId, activeFocusMessageId) {
        mutableStateOf(false)
    }
    LaunchedEffect(
        activeChannelId,
        activeFocusMessageId,
        displayMessages.size,
        displayMessages.firstOrNull()?.id,
        displayMessages.lastOrNull()?.id,
        expectedTotalItems
    ) {
        val messages = displayMessages
        if (messages.isEmpty()) {
            prevLastMessageId = null
            prevFirstMessageId = null
            prevHadPaginationHeader = state.canLoadOlderMessages
            unreadScrolledUpCount = 0
            return@LaunchedEffect
        }
        if (expectedTotalItems <= 0) return@LaunchedEffect

        val firstId = messages.first().id
        val lastId = messages.last().id
        val lastIndex = expectedTotalItems - 1

        if (!initialScrollDone) {
            val focusId = activeFocusMessageId
            val focusPos = if (!consumedInitialFocus && !focusId.isNullOrBlank()) {
                messages.indexOfFirst { it.id == focusId }
            } else {
                -1
            }
            initialScrollDone = true
            prevFirstMessageId = firstId
            prevLastMessageId = lastId
            prevHadPaginationHeader = state.canLoadOlderMessages
            if (focusPos >= 0) {
                consumedInitialFocus = true
                stickyBottom = false
                val headerOffset = if (state.canLoadOlderMessages) 1 else 0
                val dividerOffset = if (hasCachedHistory && firstLiveIndex in 0..focusPos) 1 else 0
                val targetIdx = (focusPos + headerOffset + dividerOffset).coerceIn(0, lastIndex)
                listState.animateScrollToItem(targetIdx)
            } else {
                stickyBottom = true
                listState.scrollToItem(lastIndex)
            }
            return@LaunchedEffect
        }

        val headPrepended = firstId != prevFirstMessageId
        val tailAppended = lastId != prevLastMessageId
        val oldFirstId = prevFirstMessageId
        val oldLastId = prevLastMessageId
        val oldHadHeader = prevHadPaginationHeader

        prevFirstMessageId = firstId
        prevLastMessageId = lastId
        prevHadPaginationHeader = state.canLoadOlderMessages

        if (headPrepended) {
            if (tailAppended && (stickyBottom || isAtBottom || messages.last().isOutgoing)) {
                stickyBottom = true
                unreadScrolledUpCount = 0
                listState.animateScrollToItem(lastIndex)
            } else if (!tailAppended && stickyBottom && !state.isLoadingOlderMessages) {
                listState.scrollToItem(lastIndex)
            } else {
                if (tailAppended) {
                    val oldLastIndex = oldLastId?.let { prev ->
                        messages.indexOfLast { it.id == prev }
                    } ?: -1
                    val tailDelta = if (oldLastIndex >= 0) {
                        (messages.lastIndex - oldLastIndex).coerceAtLeast(1)
                    } else {
                        1
                    }
                    unreadScrolledUpCount += tailDelta
                }
                val msgPos = oldFirstId?.let { anchor ->
                    messages.indexOfFirst { it.id == anchor }
                } ?: -1
                if (msgPos >= 0) {
                    val oldAnchorIdx = if (oldHadHeader) 1 else 0
                    val newHeaderOffset = if (state.canLoadOlderMessages) 1 else 0
                    val newDividerOffset = if (hasCachedHistory && firstLiveIndex in 0..msgPos) 1 else 0
                    val newAnchorIdx = msgPos + newHeaderOffset + newDividerOffset
                    val shift = newAnchorIdx - oldAnchorIdx
                    val targetIdx = (listState.firstVisibleItemIndex + shift).coerceIn(0, lastIndex)
                    listState.scrollToItem(targetIdx, listState.firstVisibleItemScrollOffset)
                }
            }
        } else if (tailAppended) {
            val oldLastIndex = oldLastId?.let { prev ->
                messages.indexOfLast { it.id == prev }
            } ?: -1
            val tailDelta = if (oldLastIndex >= 0) {
                (messages.lastIndex - oldLastIndex).coerceAtLeast(1)
            } else {
                1
            }
            val latestIsOutgoing = messages.last().isOutgoing
            if (stickyBottom || isAtBottom || latestIsOutgoing) {
                stickyBottom = true
                unreadScrolledUpCount = 0
                listState.animateScrollToItem(lastIndex)
            } else {
                unreadScrolledUpCount += tailDelta
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
            onShareScore = { score -> pendingShareScoreCard = score },
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

    pendingShareScoreCard?.let { shareScore ->
        ScoreCardShareDialog(
            score = shareScore,
            defaultChannelId = activeChannelId,
            onSendTextFallback = {
                pendingShareScoreCard = null
                viewModel.shareScoreInCurrentChat(it)
            },
            onNotifyMessage = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onDismiss = { pendingShareScoreCard = null }
        )
    }

    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            bookmarks = state.bookmarkedMessages,
            onDismiss = { showBookmarksSheet = false },
            onOpenChannel = { targetChannelId ->
                showBookmarksSheet = false
                if (!targetChannelId.equals(activeChannelId, ignoreCase = true)) {
                    onNavigateToChannel(targetChannelId)
                }
            },
            onDeleteBookmark = viewModel::removeBookmark,
            onCopyText = { text ->
                clipboardManager.setText(AnnotatedString(text))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Message copied")
                }
            }
        )
    }

    if (showWallpaperSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWallpaperSheet = false },
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
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs)
            ) {
                Text(
                    text = "Wallpaper — $displayTitle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(OsuSpacing.sm))
                OsuHairlineDivider()
                Spacer(modifier = Modifier.height(OsuSpacing.md))
                WallpaperCustomizerPanel(
                    config = state.effectiveWallpaper,
                    onConfigChange = { viewModel.updateChannelWallpaper(it) },
                    onResetToGlobal = if (state.hasCustomChannelWallpaper) {
                        { viewModel.updateChannelWallpaper(null) }
                    } else {
                        null
                    },
                    seasonalBackgrounds = state.seasonalBackgrounds,
                    isSeasonalLoading = state.isSeasonalLoading,
                    downloadingSeasonalUrl = state.downloadingSeasonalUrl,
                    onSelectSeasonalBackground = { item ->
                        viewModel.selectSeasonalWallpaper(item.url)
                    }
                )
                Spacer(modifier = Modifier.height(OsuSpacing.xl))
            }
        }
    }

    if (showUsersSheet) {
        ChannelUsersBottomSheet(
            channelName = displayTitle,
            users = state.usersInChannel,
            onDismiss = { showUsersSheet = false },
            onOpenProfile = { username ->
                showUsersSheet = false
                viewModel.openPlayerProfile(username)
            },
            onMentionUser = { username ->
                showUsersSheet = false
                inputValue = insertMentionAtCursor(inputValue, username)
                composerFocusRequester.requestFocus()
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = RoundedCornerShape(OsuRadius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Clear local history?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Cached messages for $displayTitle will be removed from this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearChat()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportFormatDialog) {
        AlertDialog(
            onDismissRequest = { showExportFormatDialog = false },
            shape = RoundedCornerShape(OsuRadius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Export chat log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    Text(
                        text = "Choose export format for $displayTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            showExportFormatDialog = false
                            exportTxtLauncher.launch(
                                viewModel.buildExportFilename(ChatLogExportFormat.PLAIN_TEXT)
                            )
                        },
                        shape = RoundedCornerShape(OsuRadius.md),
                        border = osuHairlineBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(ChatLogExportFormat.PLAIN_TEXT.label)
                    }
                    OutlinedButton(
                        onClick = {
                            showExportFormatDialog = false
                            exportMdLauncher.launch(
                                viewModel.buildExportFilename(ChatLogExportFormat.MARKDOWN)
                            )
                        },
                        shape = RoundedCornerShape(OsuRadius.md),
                        border = osuHairlineBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(ChatLogExportFormat.MARKDOWN.label)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportFormatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    previewImageUrl?.let { url ->
        FullscreenImagePreviewDialog(
            imageUrl = url,
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(url))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Image URL copied")
                }
            },
            onDismiss = { previewImageUrl = null }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(
                    if (isEmbeddedInTwoPane) WindowInsets.ime else WindowInsets.navigationBars.union(WindowInsets.ime)
                )
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(OsuRadius.md),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        topBar = {
            ChatTopBar(
                displayTitle = displayTitle,
                channel = channel,
                isDm = isDm,
                dmTargetName = dmTargetName,
                dmPartnerOnline = state.dmPartnerOnline,
                usersCount = state.usersInChannel.size,
                isMpRoom = activeIsMpRoom,
                hasMpLobby = state.mpLobbyState != null,
                showMpController = showMpController,
                canSyncHistory = state.canSyncHistory,
                showNotifMenu = showNotifMenu,
                showOverflowMenu = showOverflowMenu,
                isChannelMuted = state.isChannelMuted,
                isChannelDotOnly = state.isChannelDotOnly,
                isEmbeddedInTwoPane = isEmbeddedInTwoPane,
                onBack = onBack,
                onHeaderClick = {
                    if (isDm) {
                        viewModel.openPlayerProfile(dmTargetName)
                    } else if (state.usersInChannel.isNotEmpty()) {
                        showUsersSheet = true
                    }
                },
                onToggleMpController = { showMpController = !showMpController },
                onSetNotifMenuVisible = { showNotifMenu = it },
                onSelectNotificationMode = { mode ->
                    showNotifMenu = false
                    viewModel.setNotificationMode(mode)
                },
                onToggleMute = {
                    showNotifMenu = false
                    showOverflowMenu = false
                    viewModel.toggleChannelMute()
                },
                onToggleDotOnly = {
                    showOverflowMenu = false
                    viewModel.toggleChannelDotOnly()
                },
                onOpenWallpaperSheet = {
                    viewModel.loadSeasonalBackgrounds()
                    showWallpaperSheet = true
                },
                onSetOverflowVisible = { showOverflowMenu = it },
                onOpenProfile = {
                    showOverflowMenu = false
                    viewModel.openPlayerProfile(dmTargetName)
                },
                onOpenUsers = {
                    showOverflowMenu = false
                    showUsersSheet = true
                },
                onOpenBookmarks = {
                    showOverflowMenu = false
                    showBookmarksSheet = true
                },
                onTogglePin = {
                    showOverflowMenu = false
                    viewModel.togglePinChannel()
                },
                onSyncHistory = {
                    showOverflowMenu = false
                    viewModel.refreshHistory()
                },
                onRequestExportLog = {
                    showOverflowMenu = false
                    showExportFormatDialog = true
                },
                onRequestClearHistory = {
                    showOverflowMenu = false
                    showClearConfirm = true
                },
                onLeaveChannel = {
                    showOverflowMenu = false
                    viewModel.leaveChannel()
                }
            )
        }
    ) { padding ->
        ChatBackgroundContainer(
            config = state.effectiveWallpaper,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OsuHairlineDivider()

                ChatStatusAndLoadingStrip(
                    isHistoryLoading = state.isLoadingHistory && !state.isLoadingOlderMessages,
                    requiresChatReadReauth = OAuthConfig.SUPPORTS_CHAT_READ_HISTORY &&
                        state.requiresChatReadReauth &&
                        !reauthBannerDismissed,
                    historyError = state.historyError,
                    connectionStatus = state.connectionStatus,
                    onOpenSettings = onOpenSettings,
                    onDismissReauthBanner = { reauthBannerDismissed = true },
                    onRetryHistory = viewModel::refreshHistory
                )

                val mpLobby = state.mpLobbyState
                AnimatedVisibility(
                    visible = activeIsMpRoom && mpLobby != null && showMpController,
                    enter = OsuMotion.expandEnter(),
                    exit = OsuMotion.expandExit()
                ) {
                    if (mpLobby != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)
                            ) {
                                MpLobbyBoard(
                                    lobby = mpLobby,
                                    selfUsername = state.selfUsername,
                                    matchHistory = state.mpMatchHistory,
                                    isHistoryLoading = state.isMpHistoryLoading,
                                    onSendCommand = viewModel::sendMpCommand,
                                    onRefreshMatchHistory = {
                                        mpLobby.matchId?.let { viewModel.refreshMpMatchHistory(it) }
                                    },
                                    onOpenBeatmapSearch = onBack,
                                    onOpenPlayerProfile = viewModel::openPlayerProfile,
                                    historyError = state.mpHistoryError,
                                    mappools = state.mappools,
                                    onSaveMappool = viewModel::saveMappool,
                                    onDeleteMappool = viewModel::deleteMappool,
                                    onSelectMappoolSlot = viewModel::applyMappoolSlot,
                                    seriesState = state.matchSeriesState,
                                    refereeMacros = state.refereeMacros,
                                    onSetSeriesBestOf = viewModel::setSeriesBestOf,
                                    onSetSeriesTeamNames = viewModel::setSeriesTeamNames,
                                    onAdjustSeriesScore = viewModel::adjustSeriesScore,
                                    onUndoLastSeriesRound = viewModel::undoLastSeriesRound,
                                    onResetSeries = viewModel::resetSeriesState,
                                    onSetAutoScoreTeamVs = viewModel::setAutoScoreTeamVs,
                                    onSetWarmupActive = viewModel::setSeriesWarmupActive,
                                    onSetSelectedPoolId = viewModel::setSelectedMappoolId,
                                    onSetPickBanMode = viewModel::setPickBanMode,
                                    onUpdatePoolSlotState = viewModel::updatePoolSlotState,
                                    onSendMacro = viewModel::sendRefereeMacro,
                                    onSaveMacros = viewModel::saveRefereeMacros,
                                    onResetMacros = viewModel::resetRefereeMacros,
                                    onNotifyMessage = viewModel::notifyMessage,
                                    onGetCachedBeatmaps = viewModel::getCachedBeatmaps,
                                    onResolveBeatmapDetails = viewModel::resolveBeatmapDetails,
                                    onOpenMatchAnalyzer = onOpenMatchAnalyzer
                                )
                            }
                            OsuHairlineDivider()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (isEmbeddedInTwoPane) {
                                Modifier
                            } else {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                                )
                            }
                        )
                ) {
                    if (displayMessages.isEmpty()) {
                        ChatEmptyState(
                            displayTitle = displayTitle,
                            isDm = isDm,
                            isMpRoom = activeIsMpRoom,
                            isHistoryLoading = state.isLoadingHistory,
                            canSyncHistory = state.canSyncHistory,
                            hasChannelUsers = state.usersInChannel.isNotEmpty(),
                            connectionStatus = state.connectionStatus,
                            onReconnect = viewModel::reconnect,
                            onSyncHistory = viewModel::refreshHistory,
                            onFetchMpSettings = { viewModel.sendMpCommand("!mp settings") },
                            onViewDmProfile = { viewModel.openPlayerProfile(dmTargetName) },
                            onOpenUsers = { showUsersSheet = true },
                            onFocusComposer = { composerFocusRequester.requestFocus() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val resolveBeatmapCb = remember(viewModel) { viewModel::resolveBeatmap }
                        val resolveUserProfileCb = remember(viewModel) { viewModel::resolveUserProfile }
                        val toggleAudioCb = remember(viewModel) { viewModel::toggleBeatmapAudio }
                        val idleAudioState = remember { AudioPlaybackState() }
                        val openUserProfileCb = remember(viewModel) {
                            { profile: PlayerProfile ->
                                val target = if (profile.userId > 0L) profile.userId.toString() else profile.username
                                viewModel.openPlayerProfile(target)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                        ) {
                            if (OAuthConfig.SUPPORTS_CHAT_READ_HISTORY && state.canLoadOlderMessages) {
                                item(key = "load_older_messages", contentType = "pagination") {
                                    OlderMessagesPaginationHeader(
                                        isLoading = state.isLoadingOlderMessages,
                                        onLoadOlder = {
                                            stickyBottom = false
                                            viewModel.loadOlderMessages()
                                        }
                                    )
                                }
                            }

                            displayMessages.forEachIndexed { index, msg ->
                                val dividerBefore = hasCachedHistory && firstLiveIndex == index
                                if (dividerBefore) {
                                    item(key = "live_chat_divider", contentType = "live_divider") {
                                        LiveChatHistoryDivider()
                                    }
                                }
                                val msgOutgoing = msg.isOutgoing || BeatmapUrlParser.isSelfOsuSender(
                                    senderName = msg.senderName,
                                    senderId = msg.senderId,
                                    currentUserId = state.selfUserId,
                                    currentUsername = state.selfUsername,
                                    currentIrcUsername = state.selfIrcUsername
                                )
                                val canIgnoreSender = !msgOutgoing &&
                                    msg.senderName.isNotBlank() &&
                                    !msg.senderName.equals("BanchoBot", ignoreCase = true) &&
                                    !msg.senderName.equals("System", ignoreCase = true)
                                val isSenderIgnored = canIgnoreSender &&
                                    BeatmapUrlParser.normalizeOsuNick(msg.senderName) in state.ignoredUsers
                                val isCollapsedIgnored = !isDm &&
                                    isSenderIgnored &&
                                    state.ignoreDisplayMode == "COLLAPSE" &&
                                    msg.id !in revealedIgnoredMessageIds
                                if (isCollapsedIgnored) {
                                    item(key = msg.id, contentType = "ignored_collapsed_msg") {
                                        Surface(
                                            onClick = { revealedIgnoredMessageIds = revealedIgnoredMessageIds + msg.id },
                                            shape = RoundedCornerShape(OsuRadius.md),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = osuHairlineBorder(),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = OsuSpacing.xxs)
                                        ) {
                                            Text(
                                                text = "Ignored message from ${msg.senderName} — tap to reveal",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)
                                            )
                                        }
                                    }
                                    return@forEachIndexed
                                }
                                val prev = displayMessages.getOrNull(index - 1)
                                val prevOutgoing = prev?.let {
                                    it.isOutgoing || BeatmapUrlParser.isSelfOsuSender(
                                        senderName = it.senderName,
                                        senderId = it.senderId,
                                        currentUserId = state.selfUserId,
                                        currentUsername = state.selfUsername,
                                        currentIrcUsername = state.selfIrcUsername
                                    )
                                }
                                // Group consecutive messages from same sender within 2 minutes
                                val isGroupStart = dividerBefore || prev == null ||
                                    prevOutgoing != msgOutgoing ||
                                    !prev.senderName.equals(msg.senderName, ignoreCase = true) ||
                                    prev.isAction || msg.isAction ||
                                    (msg.timestamp - prev.timestamp) > 120_000L
                                val msgContentType = when {
                                    msg.isAction -> "action_msg"
                                    msg.inlineEmbeds.isNotEmpty() || msg.beatmapUrls.isNotEmpty() || msg.imageUrls.isNotEmpty() -> "embed_msg"
                                    msgOutgoing -> "outgoing_msg"
                                    else -> "incoming_msg"
                                }
                                val senderColorInfo = if (!isDm && !msgOutgoing) {
                                    state.userColors[BeatmapUrlParser.normalizeOsuNick(msg.senderName)]
                                } else {
                                    null
                                }
                                val hasBeatmapEmbed = msg.beatmapUrls.isNotEmpty() ||
                                    msg.inlineEmbeds.any { it is ChatInlineEmbed.Beatmap }
                                item(key = msg.id, contentType = msgContentType) {
                                    MessageBubbleItem(
                                        message = msg,
                                        effectiveIsOutgoing = msgOutgoing,
                                        senderColorInfo = senderColorInfo,
                                        isBookmarked = msg.id in state.bookmarkedMessageIds,
                                        isSenderIgnored = isSenderIgnored,
                                        canIgnoreSender = canIgnoreSender,
                                        isConnected = state.connectionStatus == ConnectionStatus.ONLINE,
                                        showAvatarColumn = !isDm,
                                        showSenderHeader = !msgOutgoing && !isDm,
                                        isGroupStart = isGroupStart,
                                        audioState = if (hasBeatmapEmbed) state.audioState else idleAudioState,
                                        resolveBeatmap = resolveBeatmapCb,
                                        resolveUserProfile = resolveUserProfileCb,
                                        onToggleAudio = toggleAudioCb,
                                        onImageClick = { previewImageUrl = it },
                                        onAvatarClick = {
                                            if (msg.senderName.isNotBlank()) {
                                                viewModel.openPlayerProfile(msg.senderName)
                                            }
                                        },
                                        onOpenUserProfile = openUserProfileCb,
                                        onCopyMessage = {
                                            clipboardManager.setText(AnnotatedString(msg.content))
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Message copied")
                                            }
                                        },
                                        onToggleBookmark = { viewModel.toggleBookmark(msg) },
                                        onReplyMessage = {
                                            replyTarget = msg
                                            composerFocusRequester.requestFocus()
                                        },
                                        onMentionSender = {
                                            if (msg.senderName.isNotBlank()) {
                                                inputValue = insertMentionAtCursor(inputValue, msg.senderName)
                                                composerFocusRequester.requestFocus()
                                            }
                                        },
                                        onToggleIgnoreSender = {
                                            viewModel.toggleIgnoreUser(msg.senderName)
                                        },
                                        onSendQueuedNow = { viewModel.sendQueuedMessageNow(msg) },
                                        onCancelQueued = { viewModel.cancelQueuedMessage(msg) },
                                        onOpenMatchAnalyzer = onOpenMatchAnalyzer,
                                        onJoinMpLobby = viewModel::joinMpLobbyAndNavigate,
                                        onRetryFailed = { viewModel.retryFailedMessage(msg) }
                                    )
                                }
                            }

                            if (hasCachedHistory && firstLiveIndex == -1) {
                                item(key = "live_chat_divider_end", contentType = "live_divider") {
                                    LiveChatHistoryDivider()
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAtBottom && displayMessages.isNotEmpty(),
                        enter = OsuMotion.fadeEnter(),
                        exit = OsuMotion.fadeExit(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = OsuSpacing.lg, bottom = OsuSpacing.md)
                    ) {
                        JumpToBottomButton(
                            unreadCount = unreadScrolledUpCount,
                            onClick = {
                                coroutineScope.launch {
                                    if (expectedTotalItems > 0) {
                                        stickyBottom = true
                                        unreadScrolledUpCount = 0
                                        listState.animateScrollToItem(expectedTotalItems - 1)
                                    }
                                }
                            }
                        )
                    }
                }

                ChatComposerSection(
                    inputValue = inputValue,
                    onInputChange = { inputValue = it },
                    replyTarget = replyTarget,
                    onClearReply = { replyTarget = null },
                    isMpRoom = activeIsMpRoom,
                    isDm = isDm,
                    usersInChannel = state.usersInChannel,
                    isUploadingImage = state.isUploadingImage,
                    isEmbeddedInTwoPane = isEmbeddedInTwoPane,
                    onPickImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    focusRequester = composerFocusRequester,
                    onSend = {
                        val raw = inputValue.text.trim()
                        if (raw.isNotEmpty()) {
                            val reply = replyTarget
                            val payload = if (
                                reply != null &&
                                !raw.startsWith("/") &&
                                !raw.startsWith("!") &&
                                reply.senderName.isNotBlank() &&
                                !raw.contains(reply.senderName, ignoreCase = true)
                            ) {
                                "@${reply.senderName} $raw"
                            } else {
                                raw
                            }
                            inputValue = TextFieldValue("")
                            replyTarget = null
                            stickyBottom = true
                            viewModel.sendInput(payload)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    displayTitle: String,
    channel: ChatChannel?,
    isDm: Boolean,
    dmTargetName: String,
    dmPartnerOnline: Boolean?,
    usersCount: Int,
    isMpRoom: Boolean,
    hasMpLobby: Boolean,
    showMpController: Boolean,
    canSyncHistory: Boolean,
    showNotifMenu: Boolean,
    showOverflowMenu: Boolean,
    isChannelMuted: Boolean,
    isChannelDotOnly: Boolean,
    isEmbeddedInTwoPane: Boolean = false,
    onBack: () -> Unit,
    onHeaderClick: () -> Unit,
    onToggleMpController: () -> Unit,
    onSetNotifMenuVisible: (Boolean) -> Unit,
    onSelectNotificationMode: (ChannelNotificationMode) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDotOnly: () -> Unit,
    onOpenWallpaperSheet: () -> Unit,
    onSetOverflowVisible: (Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onTogglePin: () -> Unit,
    onSyncHistory: () -> Unit,
    onRequestExportLog: () -> Unit,
    onRequestClearHistory: () -> Unit,
    onLeaveChannel: () -> Unit
) {
    val headerClickable = isDm || usersCount > 0
    TopAppBar(
        expandedHeight = 52.dp,
        windowInsets = if (isEmbeddedInTwoPane) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        navigationIcon = {
            if (!isEmbeddedInTwoPane) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = headerClickable, onClick = onHeaderClick)
            ) {
                PlayerAvatar(
                    name = displayTitle,
                    avatarUrl = channel?.avatarUrl,
                    size = 36.dp,
                    isChannel = !isDm,
                    showOnlineDot = isDm && dmPartnerOnline != null,
                    isOnline = dmPartnerOnline == true
                )
                Spacer(modifier = Modifier.width(OsuSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isChannelMuted) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = "Notifications muted",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (isDm) {
                        val presenceSuffix = when (dmPartnerOnline) {
                            true -> "Online"
                            false -> "Offline"
                            null -> null
                        }
                        val subtitle = if (!channel?.customAlias.isNullOrBlank()) {
                            if (presenceSuffix != null) "@$dmTargetName · $presenceSuffix" else "@$dmTargetName"
                        } else {
                            presenceSuffix ?: "Direct message"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (usersCount > 0) {
                        Text(
                            text = "$usersCount online",
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        val desc = channel?.description?.takeIf { it.isNotBlank() } ?: "Bancho channel"
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        actions = {
            if (isMpRoom && hasMpLobby) {
                IconButton(onClick = onToggleMpController) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "MP lobby controls",
                        tint = if (showMpController) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (channel != null) {
                val (notifIcon, notifLabel) = when (channel.notificationMode) {
                    ChannelNotificationMode.ALL ->
                        Icons.Rounded.NotificationsActive to "Notifications: All messages"
                    ChannelNotificationMode.MENTIONS_ONLY ->
                        Icons.Rounded.AlternateEmail to "Notifications: Mentions only"
                    ChannelNotificationMode.MUTED ->
                        Icons.Rounded.NotificationsOff to "Notifications: Muted"
                }
                Box {
                    IconButton(onClick = { onSetNotifMenuVisible(true) }) {
                        Icon(
                            imageVector = notifIcon,
                            contentDescription = notifLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showNotifMenu,
                        onDismissRequest = { onSetNotifMenuVisible(false) },
                        shape = RoundedCornerShape(OsuRadius.md),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = osuHairlineBorder()
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isChannelMuted) "Unmute notifications" else "Mute notifications",
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isChannelMuted) {
                                        Icons.Rounded.NotificationsActive
                                    } else {
                                        Icons.Rounded.NotificationsOff
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = onToggleMute
                        )
                        OsuHairlineDivider()
                        ChannelNotificationMode.entries.forEach { mode ->
                            val selected = channel.notificationMode == mode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.label,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = { onSelectNotificationMode(mode) }
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onOpenWallpaperSheet) {
                Icon(
                    imageVector = Icons.Rounded.Wallpaper,
                    contentDescription = "Chat wallpaper",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { onSetOverflowVisible(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { onSetOverflowVisible(false) },
                    shape = RoundedCornerShape(OsuRadius.md),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = osuHairlineBorder()
                ) {
                    if (isDm) {
                        DropdownMenuItem(
                            text = { Text("Player stats & alias") },
                            onClick = onOpenProfile
                        )
                    } else if (usersCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Online users ($usersCount)", style = MaterialTheme.typography.bodyMedium.tnum()) },
                            onClick = onOpenUsers
                        )
                    }
                    if (channel != null) {
                        DropdownMenuItem(
                            text = { Text(if (isChannelMuted) "Unmute notifications" else "Mute notifications") },
                            onClick = onToggleMute
                        )
                        DropdownMenuItem(
                            text = { Text("Dot-only badge") },
                            trailingIcon = if (isChannelDotOnly) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = onToggleDotOnly
                        )
                        DropdownMenuItem(
                            text = { Text(if (channel.isPinned) "Unpin chat" else "Pin chat") },
                            onClick = onTogglePin
                        )
                    }
                    if (canSyncHistory) {
                        DropdownMenuItem(
                            text = { Text("Sync history") },
                            onClick = onSyncHistory
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Saved bookmarks") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Bookmarks,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = onOpenBookmarks
                    )
                    DropdownMenuItem(
                        text = { Text("Export chat log") },
                        onClick = onRequestExportLog
                    )
                    DropdownMenuItem(
                        text = { Text("Clear local history") },
                        onClick = onRequestClearHistory
                    )
                    DropdownMenuItem(
                        text = { Text(if (isDm) "Close chat" else "Leave channel") },
                        onClick = onLeaveChannel
                    )
                }
            }
        }
    )
}

@Composable
private fun ChatStatusAndLoadingStrip(
    isHistoryLoading: Boolean,
    requiresChatReadReauth: Boolean,
    historyError: String?,
    connectionStatus: ConnectionStatus,
    onOpenSettings: () -> Unit,
    onDismissReauthBanner: () -> Unit,
    onRetryHistory: () -> Unit
) {
    // Layout-stable 2.dp track prevents vertical message jump when history sync starts or stops.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        if (isHistoryLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }

    if (requiresChatReadReauth) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = OsuSpacing.md, end = OsuSpacing.xs, top = OsuSpacing.xs, bottom = OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                    Text(
                        text = "Re-login with osu! to load message history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDismissReauthBanner,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                OsuHairlineDivider()
            }
        }
    }

    if (connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.RECONNECTING) {
        val statusText = if (connectionStatus == ConnectionStatus.RECONNECTING) {
            "Reconnecting to Bancho · Reading cached messages"
        } else {
            "Connecting to Bancho · Reading cached messages"
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = StatusConnecting
                    )
                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OsuHairlineDivider()
            }
        }
    }

    if (!historyError.isNullOrBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                    Text(
                        text = historyError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetryHistory,
                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xxs),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                OsuHairlineDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun ChatEmptyState(
    displayTitle: String,
    isDm: Boolean,
    isMpRoom: Boolean,
    isHistoryLoading: Boolean,
    canSyncHistory: Boolean,
    hasChannelUsers: Boolean,
    connectionStatus: ConnectionStatus,
    onReconnect: () -> Unit,
    onSyncHistory: () -> Unit,
    onFetchMpSettings: () -> Unit,
    onViewDmProfile: () -> Unit,
    onOpenUsers: () -> Unit,
    onFocusComposer: () -> Unit,
    modifier: Modifier = Modifier
) {
    OsuGroupedContainer(
        modifier = modifier
            .padding(horizontal = OsuSpacing.xxl)
            .widthIn(max = 320.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(OsuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            val headline = when {
                isHistoryLoading -> "Loading history…"
                isMpRoom -> "No lobby messages yet"
                isDm -> "No messages with $displayTitle"
                else -> "No messages in $displayTitle"
            }
            val body = when {
                isHistoryLoading -> "Fetching recent messages from osu!"
                connectionStatus == ConnectionStatus.OFFLINE || connectionStatus == ConnectionStatus.ERROR ->
                    "Offline — reconnect to send or receive Bancho messages"
                isMpRoom -> "Fetch current room slots and beatmap with !mp settings"
                isDm -> "Send a message below or inspect player stats"
                else -> "Send a message or try /me, /np, or /stats <user>"
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isHistoryLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    modifier = Modifier.padding(top = OsuSpacing.xs)
                ) {
                    when {
                        connectionStatus == ConnectionStatus.OFFLINE || connectionStatus == ConnectionStatus.ERROR -> {
                            OutlinedButton(
                                onClick = onReconnect,
                                shape = RoundedCornerShape(OsuRadius.md),
                                border = osuHairlineBorder()
                            ) {
                                Text("Reconnect")
                            }
                        }
                        isMpRoom -> {
                            OutlinedButton(
                                onClick = onFetchMpSettings,
                                shape = RoundedCornerShape(OsuRadius.md),
                                border = osuHairlineBorder()
                            ) {
                                Text("!mp settings")
                            }
                        }
                        canSyncHistory -> {
                            OutlinedButton(
                                onClick = onSyncHistory,
                                shape = RoundedCornerShape(OsuRadius.md),
                                border = osuHairlineBorder()
                            ) {
                                Text("Sync history")
                            }
                        }
                        isDm -> {
                            OutlinedButton(
                                onClick = onViewDmProfile,
                                shape = RoundedCornerShape(OsuRadius.md),
                                border = osuHairlineBorder()
                            ) {
                                Text("View profile")
                            }
                            TextButton(
                                onClick = onFocusComposer,
                                shape = RoundedCornerShape(OsuRadius.md)
                            ) {
                                Text("Message")
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = onFocusComposer,
                                shape = RoundedCornerShape(OsuRadius.md),
                                border = osuHairlineBorder()
                            ) {
                                Text("Write message")
                            }
                            if (hasChannelUsers) {
                                TextButton(
                                    onClick = onOpenUsers,
                                    shape = RoundedCornerShape(OsuRadius.md)
                                ) {
                                    Text("Users")
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
private fun OlderMessagesPaginationHeader(
    isLoading: Boolean,
    onLoadOlder: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp
                )
                Text(
                    text = "Loading older messages…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Surface(
                onClick = onLoadOlder,
                shape = RoundedCornerShape(OsuRadius.md),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = osuHairlineBorder(),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = "Load older messages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                )
            }
        }
    }
}

@Composable
private fun JumpToBottomButton(
    unreadCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(OsuRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .padding(horizontal = if (unreadCount > 0) OsuSpacing.md else OsuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            if (unreadCount > 0) {
                Surface(
                    shape = OsuRadius.PillShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Jump to bottom",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChatComposerSection(
    inputValue: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    replyTarget: ChatMessage?,
    onClearReply: () -> Unit,
    isMpRoom: Boolean,
    isDm: Boolean,
    usersInChannel: List<String>,
    isUploadingImage: Boolean,
    isEmbeddedInTwoPane: Boolean = false,
    onPickImage: () -> Unit,
    focusRequester: FocusRequester,
    onSend: () -> Unit
) {
    val text = inputValue.text
    val showCommandChips = text.startsWith("/") || (isMpRoom && text.startsWith("!"))
    val activeMentionPrefix = remember(text, inputValue.selection, isDm, showCommandChips) {
        if (isDm || showCommandChips) null else extractActiveMentionPrefix(text, inputValue.selection.end)
    }
    val matchingUsers = remember(activeMentionPrefix, usersInChannel) {
        if (activeMentionPrefix == null || usersInChannel.isEmpty()) {
            emptyList()
        } else {
            usersInChannel
                .filter { activeMentionPrefix.isEmpty() || it.contains(activeMentionPrefix, ignoreCase = true) }
                .take(8)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = showCommandChips || matchingUsers.isNotEmpty(),
            enter = OsuMotion.expandEnter(),
            exit = OsuMotion.expandExit()
        ) {
            val commands = remember(isMpRoom) {
                if (isMpRoom) {
                    listOf(
                        "!mp settings",
                        "!mp start 10",
                        "!mp abort",
                        "!mp map ",
                        "!mp mods ",
                        "!mp invite ",
                        "!mp host ",
                        "/stats ",
                        "/rs ",
                        "/c ",
                        "/top "
                    )
                } else {
                    listOf("/rs ", "/c ", "/top ", "/me ", "/np", "/stats ", "/dm ", "/join #", "/clear")
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                if (showCommandChips) {
                    items(
                        items = commands,
                        key = { "cmd_$it" },
                        contentType = { "command_chip" }
                    ) { cmd ->
                        Surface(
                            onClick = {
                                onInputChange(TextFieldValue(cmd, TextRange(cmd.length)))
                            },
                            shape = RoundedCornerShape(OsuRadius.md),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = cmd.trim(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                            )
                        }
                    }
                } else {
                    items(
                        items = matchingUsers,
                        key = { "mention_$it" },
                        contentType = { "mention_chip" }
                    ) { username ->
                        Surface(
                            onClick = {
                                onInputChange(replaceActiveMention(inputValue, username))
                            },
                            shape = RoundedCornerShape(OsuRadius.md),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = "@$username",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                            )
                        }
                    }
                }
            }
        }

        if (replyTarget != null) {
            OsuHairlineDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(end = OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(OsuRadius.accentBar)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(OsuSpacing.sm))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(OsuSpacing.sm))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = OsuSpacing.xs)
                ) {
                    Text(
                        text = "Replying to ${replyTarget.senderDisplayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = replyTarget.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClearReply,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel reply",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        OsuHairlineDivider()

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        if (isEmbeddedInTwoPane) WindowInsets.ime else WindowInsets.navigationBars.union(WindowInsets.ime)
                    )
                    .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onPickImage,
                    enabled = !isUploadingImage,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isUploadingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(OsuSpacing.xs))
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    placeholder = { Text("Message, /me, /np, /stats…") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(OsuRadius.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.width(OsuSpacing.sm))
                FilledIconButton(
                    onClick = onSend,
                    enabled = inputValue.text.isNotBlank(),
                    shape = RoundedCornerShape(OsuRadius.md),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    effectiveIsOutgoing: Boolean = message.isOutgoing,
    senderColorInfo: ChatUserColorInfo? = null,
    isBookmarked: Boolean,
    isSenderIgnored: Boolean = false,
    canIgnoreSender: Boolean = false,
    isConnected: Boolean = true,
    showAvatarColumn: Boolean,
    showSenderHeader: Boolean,
    isGroupStart: Boolean,
    audioState: AudioPlaybackState,
    resolveBeatmap: suspend (BeatmapLinkRef) -> Result<BeatmapMetadata>,
    resolveUserProfile: suspend (Long) -> Result<PlayerProfile>,
    onToggleAudio: (beatmapsetId: Long, previewUrl: String) -> Unit,
    onImageClick: (String) -> Unit,
    onAvatarClick: () -> Unit,
    onOpenUserProfile: (PlayerProfile) -> Unit,
    onCopyMessage: () -> Unit,
    onToggleBookmark: () -> Unit,
    onReplyMessage: () -> Unit,
    onMentionSender: () -> Unit,
    onToggleIgnoreSender: () -> Unit = {},
    onSendQueuedNow: () -> Unit = {},
    onCancelQueued: () -> Unit = {},
    onOpenMatchAnalyzer: (Long) -> Unit = {},
    onJoinMpLobby: (Long) -> Unit = {},
    onRetryFailed: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showActionsMenu by remember(message.id) { mutableStateOf(false) }

    val isDarkSurface = surfaces.isDark
    val isFailed = effectiveIsOutgoing && message.deliveryStatus == DeliveryStatus.FAILED
    val isQueued = effectiveIsOutgoing && message.deliveryStatus == DeliveryStatus.QUEUED

    val bubbleShape = if (effectiveIsOutgoing) {
        RoundedCornerShape(
            topStart = OsuRadius.lg,
            topEnd = if (isGroupStart) OsuRadius.xs else OsuRadius.lg,
            bottomEnd = OsuRadius.lg,
            bottomStart = OsuRadius.lg
        )
    } else {
        RoundedCornerShape(
            topStart = if (isGroupStart) OsuRadius.xs else OsuRadius.lg,
            topEnd = OsuRadius.lg,
            bottomEnd = OsuRadius.lg,
            bottomStart = OsuRadius.lg
        )
    }

    val bubbleColor = when {
        message.isHighlighted -> MaterialTheme.colorScheme.surfaceContainerHighest
        effectiveIsOutgoing -> surfaces.outgoingBubble
        else -> surfaces.incomingBubble
    }

    val bubbleBorder: BorderStroke = when {
        isFailed -> BorderStroke(1.dp, StatusError)
        message.isHighlighted -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else -> osuHairlineBorder()
    }

    val contentColor = MaterialTheme.colorScheme.onSurface
    val defaultSenderColor = MaterialTheme.colorScheme.onSurface
    val botSenderColor = MaterialTheme.colorScheme.primary
    val senderNameColor = remember(message.senderName, senderColorInfo, defaultSenderColor, botSenderColor, isDarkSurface) {
        resolveChatSenderColor(message.senderName, senderColorInfo, defaultSenderColor, botSenderColor, isDarkSurface)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isGroupStart) OsuSpacing.md else OsuSpacing.xxs),
        horizontalArrangement = if (effectiveIsOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!effectiveIsOutgoing && showAvatarColumn) {
            if (isGroupStart) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                        .clickable(onClick = onAvatarClick)
                        .padding(end = OsuSpacing.sm),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PlayerAvatar(
                        name = message.senderName,
                        avatarUrl = message.senderAvatarUrl,
                        size = 32.dp
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(44.dp))
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (effectiveIsOutgoing) Alignment.End else Alignment.Start
        ) {
            Box {
                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    border = bubbleBorder,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .clip(bubbleShape)
                        .combinedClickable(
                            onClick = {
                                if (isFailed) onRetryFailed() else showActionsMenu = true
                            },
                            onLongClick = { showActionsMenu = true }
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)) {
                        if (isGroupStart && showSenderHeader) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(bottom = OsuSpacing.xxs)
                                    .clickable(onClick = onAvatarClick)
                            ) {
                                if (!message.senderAlias.isNullOrBlank()) {
                                    Text(
                                        text = message.senderAlias,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = senderNameColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "@${message.senderName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = surfaces.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = message.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = senderNameColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (message.isHighlighted) {
                            OsuMicroTag(
                                text = "Highlight",
                                containerColor = surfaces.accentSubtle,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                        }

                        Text(
                            text = if (message.isAction) "* ${message.senderDisplayName} ${message.content}" else message.content,
                            style = if (message.isAction) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                            color = if (message.isAction) MaterialTheme.colorScheme.onSurfaceVariant else contentColor
                        )

                        val imageUrls = remember(message.id, message.content, message.imageUrls) {
                            message.imageUrls.ifEmpty { BeatmapUrlParser.extractImageUrls(message.content) }
                        }
                        if (imageUrls.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(OsuSpacing.sm))
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                                imageUrls.forEach { url ->
                                    ChatInlineImagePreview(
                                        imageUrl = url,
                                        onClick = { onImageClick(url) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.xs))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFailed) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "Failed to send",
                                    tint = StatusError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                Text(
                                    text = "Not sent · Tap to retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusError
                                )
                            } else if (isQueued) {
                                Text(
                                    text = "Queued · offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = surfaces.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(OsuSpacing.sm))
                                Text(
                                    text = timeFormatter.format(Date(message.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted
                                )
                                Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = "Queued",
                                    tint = surfaces.textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                if (isBookmarked) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = "Bookmarked",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                }
                                Text(
                                    text = timeFormatter.format(Date(message.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted
                                )
                                if (effectiveIsOutgoing) {
                                    Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                    val (icon, tint) = when (message.deliveryStatus) {
                                        DeliveryStatus.QUEUED -> Icons.Rounded.Schedule to surfaces.textMuted
                                        DeliveryStatus.SENDING -> Icons.Rounded.Schedule to surfaces.textMuted
                                        DeliveryStatus.SENT -> Icons.Rounded.Done to surfaces.textMuted
                                        DeliveryStatus.READ -> Icons.Rounded.DoneAll to StatusRead
                                        DeliveryStatus.FAILED -> Icons.Rounded.ErrorOutline to StatusError
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = message.deliveryStatus.name,
                                        tint = tint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = showActionsMenu,
                    onDismissRequest = { showActionsMenu = false },
                    shape = RoundedCornerShape(OsuRadius.md),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = osuHairlineBorder()
                ) {
                    if (isFailed) {
                        DropdownMenuItem(
                            text = { Text("Retry send") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showActionsMenu = false
                                onRetryFailed()
                            }
                        )
                    }
                    if (isQueued) {
                        if (isConnected) {
                            DropdownMenuItem(
                                text = { Text("Send now") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showActionsMenu = false
                                    onSendQueuedNow()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Cancel queued message") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showActionsMenu = false
                                onCancelQueued()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy text") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showActionsMenu = false
                            onCopyMessage()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBookmarked) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showActionsMenu = false
                            onToggleBookmark()
                        }
                    )
                    if (!effectiveIsOutgoing && message.senderName.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Reply") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showActionsMenu = false
                                onReplyMessage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mention @${message.senderName}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AlternateEmail,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showActionsMenu = false
                                onMentionSender()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View profile") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showActionsMenu = false
                                onAvatarClick()
                            }
                        )
                        if (canIgnoreSender) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (isSenderIgnored) "Unignore user" else "Ignore user")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = null,
                                        tint = if (isSenderIgnored) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showActionsMenu = false
                                    onToggleIgnoreSender()
                                }
                            )
                        }
                    }
                }
            }

            // Render link embeds as depth-1 siblings below the text bubble to prevent cards-in-cards.
            if (message.inlineEmbeds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(OsuSpacing.xs))
                message.inlineEmbeds.forEachIndexed { idx, embed ->
                    if (idx > 0) Spacer(modifier = Modifier.height(OsuSpacing.xs))
                    when (embed) {
                        is ChatInlineEmbed.Beatmap -> BeatmapPreviewCard(
                            linkRef = embed.ref,
                            resolveMetadata = resolveBeatmap,
                            audioState = audioState,
                            onToggleAudio = onToggleAudio
                        )
                        is ChatInlineEmbed.User -> UserProfilePreviewCard(
                            linkRef = embed.ref,
                            resolveProfile = resolveUserProfile,
                            onOpenProfile = onOpenUserProfile
                        )
                        is ChatInlineEmbed.MpMatch -> MpMatchPreviewCard(
                            linkRef = embed.ref,
                            onAnalyzeMatch = { onOpenMatchAnalyzer(embed.ref.matchId) },
                            onJoinLobby = { onJoinMpLobby(embed.ref.matchId) }
                        )
                    }
                }
            } else if (message.beatmapUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(OsuSpacing.xs))
                message.beatmapUrls.forEachIndexed { idx, linkRef ->
                    if (idx > 0) Spacer(modifier = Modifier.height(OsuSpacing.xs))
                    BeatmapPreviewCard(
                        linkRef = linkRef,
                        resolveMetadata = resolveBeatmap,
                        audioState = audioState,
                        onToggleAudio = onToggleAudio
                    )
                }
            }
        }
    }
}

@Composable
private fun MpMatchPreviewCard(
    linkRef: MpLinkRef,
    onAnalyzeMatch: () -> Unit,
    onJoinLobby: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    Surface(
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
                .padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                OsuMicroTag(
                    text = "#mp_${linkRef.matchId}",
                    containerColor = surfaces.accentSubtle,
                    contentColor = MaterialTheme.colorScheme.primary,
                    isNumeric = true
                )
                Text(
                    text = "Multiplayer match",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                Button(
                    onClick = onAnalyzeMatch,
                    shape = RoundedCornerShape(OsuRadius.md),
                    contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(OsuSpacing.xs))
                    Text(
                        text = "Analyze match",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = onJoinLobby,
                    shape = RoundedCornerShape(OsuRadius.md),
                    border = osuHairlineBorder(),
                    contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Join #mp_${linkRef.matchId}",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun resolveChatSenderColor(
    senderName: String,
    colorInfo: ChatUserColorInfo?,
    defaultColor: Color,
    botColor: Color,
    isDarkSurface: Boolean = true
): Color {
    if (senderName.equals("BanchoBot", ignoreCase = true)) {
        return botColor
    }
    val hex = colorInfo?.profileColourHex?.trim()?.takeIf { it.isNotEmpty() }
    if (hex != null) {
        val normalizedHex = if (hex.startsWith("#")) hex else "#$hex"
        val parsed = runCatching { android.graphics.Color.parseColor(normalizedHex) }.getOrNull()
        if (parsed != null) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(parsed, hsl)
            hsl[2] = if (isDarkSurface) {
                hsl[2].coerceIn(0.62f, 0.90f)
            } else {
                hsl[2].coerceIn(0.20f, 0.42f)
            }
            return Color(ColorUtils.HSLToColor(hsl))
        }
    }
    val hue = colorInfo?.profileHue
    if (colorInfo?.isSupporter == true && hue != null) {
        val normalizedHue = ((hue % 360) + 360) % 360
        return Color.hsl(
            hue = normalizedHue.toFloat(),
            saturation = 0.78f,
            lightness = if (isDarkSurface) 0.72f else 0.40f
        )
    }
    return defaultColor
}

@Composable
private fun ChatInlineImagePreview(
    imageUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val externalImageLoader = remember(context) {
        NetworkAndDatabaseModule.getOrCreateExternalChatImageLoader(context)
    }
    var reloadKey by remember(imageUrl) { mutableIntStateOf(0) }
    var imageState by remember(imageUrl, reloadKey) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val shape = RoundedCornerShape(OsuRadius.sm)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                if (imageState is AsyncImagePainter.State.Error) {
                    reloadKey++
                } else {
                    onClick()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp, max = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            val request = remember(context, imageUrl, reloadKey) {
                ImageRequest.Builder(context)
                    .data(imageUrl.trim())
                    .addHeader("User-Agent", "OsuCompanion/${com.osuchat.app.BuildConfig.VERSION_NAME}")
                    .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .setParameter("retry", reloadKey, memoryCacheKey = null)
                    .apply {
                        if (reloadKey > 0) {
                            memoryCachePolicy(CachePolicy.WRITE_ONLY)
                            diskCachePolicy(CachePolicy.WRITE_ONLY)
                        }
                    }
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = externalImageLoader,
                contentDescription = "Shared image",
                contentScale = ContentScale.Crop,
                onState = { state ->
                    imageState = state
                    if (state is AsyncImagePainter.State.Error) {
                        val err = state.result.throwable
                        runCatching {
                            Log.w(
                                "ChatInlineImagePreview",
                                "Coil failed loading url=$imageUrl (${err::class.java.simpleName}: ${err.message})",
                                err
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp, max = 220.dp)
            )
            when (imageState) {
                is AsyncImagePainter.State.Empty,
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                        modifier = Modifier.padding(OsuSpacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Image failed · Tap to retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is AsyncImagePainter.State.Success -> Unit
            }
        }
    }
}

@Composable
private fun FullscreenImagePreviewDialog(
    imageUrl: String,
    onCopyUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val context = LocalContext.current
    val externalImageLoader = remember(context) {
        NetworkAndDatabaseModule.getOrCreateExternalChatImageLoader(context)
    }
    var imageState by remember(imageUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = surfaces.fullscreenBackdrop,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close preview",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Image preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onCopyUrl) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy image URL",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl)))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open in browser",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        OsuHairlineDivider()
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(OsuSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl.trim())
                            .addHeader("User-Agent", "OsuCompanion/${com.osuchat.app.BuildConfig.VERSION_NAME}")
                            .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                            .crossfade(true)
                            .build(),
                        imageLoader = externalImageLoader,
                        contentDescription = "Fullscreen image preview",
                        contentScale = ContentScale.Fit,
                        onState = { state ->
                            imageState = state
                            if (state is AsyncImagePainter.State.Error) {
                                val err = state.result.throwable
                                runCatching {
                                    Log.w(
                                        "FullscreenImagePreview",
                                        "Coil failed loading url=$imageUrl (${err::class.java.simpleName}: ${err.message})",
                                        err
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    when (imageState) {
                        is AsyncImagePainter.State.Empty,
                        is AsyncImagePainter.State.Loading -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                        is AsyncImagePainter.State.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Could not load image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is AsyncImagePainter.State.Success -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChatHistoryDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OsuSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OsuHairlineDivider(modifier = Modifier.weight(1f))
        Text(
            text = "Live session",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = OsuSpacing.md)
        )
        OsuHairlineDivider(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelUsersBottomSheet(
    channelName: String,
    users: List<String>,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onMentionUser: (String) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    val filteredUsers = remember(users, filter) {
        val clean = filter.trim()
        val matching = if (clean.isEmpty()) users else users.filter { it.contains(clean, ignoreCase = true) }
        matching.distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$channelName users",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${users.size} online",
                    style = MaterialTheme.typography.labelMedium.tnum(),
                    color = StatusOnline
                )
            }
            Spacer(modifier = Modifier.height(OsuSpacing.sm))
            if (users.size > 12) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Filter users…") },
                    singleLine = true,
                    shape = RoundedCornerShape(OsuRadius.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(OsuSpacing.sm))
            }
            OsuHairlineDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                if (filteredUsers.isEmpty()) {
                    item(key = "users_empty") {
                        Text(
                            text = "No matching users in channel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = OsuSpacing.lg)
                        )
                    }
                } else {
                    items(
                        items = filteredUsers,
                        key = { it }
                    ) { username ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onOpenProfile(username) }
                                .padding(vertical = OsuSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerAvatar(
                                name = username,
                                avatarUrl = null,
                                size = 32.dp
                            )
                            Spacer(modifier = Modifier.width(OsuSpacing.md))
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onMentionUser(username) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AlternateEmail,
                                    contentDescription = "Mention $username",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        OsuHairlineDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(OsuSpacing.md))
        }
    }
}

private fun extractActiveMentionPrefix(text: String, cursorPos: Int): String? {
    val safeCursor = cursorPos.coerceIn(0, text.length)
    var idx = safeCursor - 1
    while (idx >= 0 && !text[idx].isWhitespace()) {
        idx--
    }
    val tokenStart = idx + 1
    if (tokenStart >= safeCursor) return null
    if (text[tokenStart] != '@') return null
    if (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) return null
    return text.substring(tokenStart + 1, safeCursor)
}

private fun replaceActiveMention(value: TextFieldValue, username: String): TextFieldValue {
    val text = value.text
    val safeCursor = value.selection.end.coerceIn(0, text.length)
    var idx = safeCursor - 1
    while (idx >= 0 && !text[idx].isWhitespace()) {
        idx--
    }
    val tokenStart = idx + 1
    val insertion = "@$username "
    val updated = buildString {
        append(text, 0, tokenStart)
        append(insertion)
        append(text, safeCursor, text.length)
    }
    val newCursor = tokenStart + insertion.length
    return TextFieldValue(updated, TextRange(newCursor))
}

private fun insertMentionAtCursor(value: TextFieldValue, username: String): TextFieldValue {
    val text = value.text
    val safeCursor = value.selection.end.coerceIn(0, text.length)
    val needsLeadingSpace = safeCursor > 0 && !text[safeCursor - 1].isWhitespace()
    val insertion = (if (needsLeadingSpace) " " else "") + "@$username "
    val updated = buildString {
        append(text, 0, safeCursor)
        append(insertion)
        append(text, safeCursor, text.length)
    }
    val newCursor = safeCursor + insertion.length
    return TextFieldValue(updated, TextRange(newCursor))
}
