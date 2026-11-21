package com.osuchat.app.ui.screens.chat

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osuchat.app.data.network.OsuApiException
import com.osuchat.app.data.repository.ImageUploadRepository
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BookmarkedMessageItem
import com.osuchat.app.domain.model.ChannelHistoryState
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChannelType
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ChatLogExportFormat
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.ChatUserColorInfo
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.DeliveryStatus
import com.osuchat.app.domain.model.FriendContact
import com.osuchat.app.domain.model.FriendRelationStatus
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MpLobbyState
import com.osuchat.app.domain.model.MpMatchHistory
import com.osuchat.app.domain.model.MpPickBanMode
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.domain.model.SeasonalBackgroundItem
import com.osuchat.app.domain.model.SeriesRoundRecord
import com.osuchat.app.domain.model.WallpaperConfig
import com.osuchat.app.domain.model.matchesNormalizedChannelSet
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.domain.usecase.CommandOutcome
import com.osuchat.app.domain.usecase.SendChatCommandUseCase
import com.osuchat.app.service.BeatmapAudioPlayer
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.ChatLogFormatter
import com.osuchat.app.util.MpLobbyParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

private data class ChatLocalState(
    val selectedProfileTarget: String? = null,
    val selectedProfile: PlayerProfile? = null,
    val selectedProfileMode: String = "osu",
    val pinnedScores: List<OsuScoreItem> = emptyList(),
    val bestScores: List<OsuScoreItem> = emptyList(),
    val recentScores: List<OsuScoreItem> = emptyList(),
    val isProfileLoading: Boolean = false,
    val isProfileScoresLoading: Boolean = false,
    val isLoadingMoreBestScores: Boolean = false,
    val canLoadMoreBestScores: Boolean = false,
    val profileError: String? = null,
    val selectedProfileLocalHistory: List<ProfileHistoryPoint> = emptyList(),
    val selectedProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    val isSelectedProfileOsuTrackLoading: Boolean = false,
    val mpMatchHistory: MpMatchHistory? = null,
    val isMpHistoryLoading: Boolean = false,
    val mpHistoryError: String? = null,
    val historyError: String? = null,
    val isLoadingOlderMessages: Boolean = false,
    val isUploadingImage: Boolean = false,
    val retriedMessageIds: Set<String> = emptySet(),
    val seasonalBackgrounds: List<SeasonalBackgroundItem> = emptyList(),
    val isSeasonalLoading: Boolean = false,
    val downloadingSeasonalUrl: String? = null,
    val bookmarkedMessages: List<BookmarkedMessageItem> = emptyList(),
    val bookmarkedMessageIds: Set<String> = emptySet(),
    val ignoredUsers: Set<String> = emptySet(),
    val ignoreDisplayMode: String = "HIDE"
)

private data class ChatChannelSnapshot(
    val channel: ChatChannel?,
    val messages: List<ChatMessage>,
    val sessionConnectedAt: Long,
    val usersInChannel: List<String>,
    val friends: List<FriendContact>
)

private data class ChatSessionSnapshot(
    val channelWallpaper: WallpaperConfig?,
    val settings: AppSettings,
    val connectionStatus: ConnectionStatus,
    val connectionError: String?,
    val audioState: AudioPlaybackState
)

@Immutable
data class ChatUiState(
    val channel: ChatChannel? = null,
    val messages: List<ChatMessage> = emptyList(),
    val sessionConnectedAt: Long = 0L,
    val usersInChannel: List<String> = emptyList(),
    val dmPartnerOnline: Boolean? = null,
    val effectiveWallpaper: WallpaperConfig = WallpaperConfig(),
    val hasCustomChannelWallpaper: Boolean = false,
    val isChannelMuted: Boolean = false,
    val isChannelDotOnly: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.OFFLINE,
    val connectionError: String? = null,
    val isExplicitlyDisconnected: Boolean = false,
    val audioState: AudioPlaybackState = AudioPlaybackState(),
    val selfUserId: Long = 0L,
    val selfUsername: String = "",
    val selfIrcUsername: String = "",
    val userColors: Map<String, ChatUserColorInfo> = emptyMap(),
    val isOAuthV2: Boolean = false,
    val canSyncHistory: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val hasMoreHistory: Boolean = false,
    val requiresChatReadReauth: Boolean = false,
    val historyError: String? = null,
    val messageWindowLimit: Int = ChatRepository.DEFAULT_MESSAGE_WINDOW,
    val canLoadOlderMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isUploadingImage: Boolean = false,
    val mpLobbyState: MpLobbyState? = null,
    val mappools: List<Mappool> = emptyList(),
    val matchSeriesState: MatchSeriesState = MatchSeriesState(),
    val refereeMacros: List<RefereeMacro> = RefereeMacro.DEFAULT_MACROS,
    val mpMatchHistory: MpMatchHistory? = null,
    val isMpHistoryLoading: Boolean = false,
    val mpHistoryError: String? = null,
    val selectedProfile: PlayerProfile? = null,
    val selectedProfileMode: String = "osu",
    val selectedProfilePinnedScores: List<OsuScoreItem> = emptyList(),
    val selectedProfileBestScores: List<OsuScoreItem> = emptyList(),
    val selectedProfileRecentScores: List<OsuScoreItem> = emptyList(),
    val isProfileLoading: Boolean = false,
    val isProfileScoresLoading: Boolean = false,
    val isLoadingMoreBestScores: Boolean = false,
    val canLoadMoreBestScores: Boolean = false,
    val profileError: String? = null,
    val selectedProfileLocalHistory: List<ProfileHistoryPoint> = emptyList(),
    val selectedProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    val isSelectedProfileOsuTrackLoading: Boolean = false,
    val seasonalBackgrounds: List<SeasonalBackgroundItem> = emptyList(),
    val isSeasonalLoading: Boolean = false,
    val downloadingSeasonalUrl: String? = null,
    val bookmarkedMessages: List<BookmarkedMessageItem> = emptyList(),
    val bookmarkedMessageIds: Set<String> = emptySet(),
    val ignoredUsers: Set<String> = emptySet(),
    val ignoreDisplayMode: String = "HIDE"
)

sealed class ChatUiEffect {
    data class NavigateToChannel(val channelId: String) : ChatUiEffect()
    data object NavigateBack : ChatUiEffect()
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val retryInput: String? = null,
        val retryMpCommand: String? = null
    ) : ChatUiEffect()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val sendChatCommand: SendChatCommandUseCase,
    private val beatmapAudioPlayer: BeatmapAudioPlayer,
    private val imageUploadRepository: ImageUploadRepository
) : ViewModel() {

    private val _selectedChannelId = MutableStateFlow(
        savedStateHandle.get<String>("channelId")
            ?.let { android.net.Uri.decode(it) }
            ?.trim()
            .orEmpty()
    )
    private val _focusMessageId = MutableStateFlow(
        savedStateHandle.get<String>("focusMessageId")
            ?.let { android.net.Uri.decode(it) }
            ?.takeIf { it.isNotBlank() }
    )

    val channelId: String
        get() = _selectedChannelId.value
    val initialFocusMessageId: String?
        get() = _focusMessageId.value
    val isMpRoom: Boolean
        get() = channelId.startsWith("#mp_", ignoreCase = true)
    val isDirectChannel: Boolean
        get() = channelId.startsWith("dm:", ignoreCase = true)
    val dmFallbackName: String
        get() = if (isDirectChannel) {
            channelId.substring(3).replace('_', ' ').trim().ifBlank { channelId }
        } else {
            channelId
        }

    private val messageWindowLimit = MutableStateFlow(ChatRepository.DEFAULT_MESSAGE_WINDOW)
    private val localState = MutableStateFlow(ChatLocalState())
    private var profileLoadJob: Job? = null
    private var profileRequestSeq = 0L
    private var selectedOsuTrackJob: Job? = null
    private var selectedOsuTrackSeq = 0L
    private var loadMoreBestScoresJob: Job? = null

    private val _effects = MutableSharedFlow<ChatUiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<ChatUiEffect> = _effects.asSharedFlow()

    private val channelSnapshotFlow = _selectedChannelId.flatMapLatest { chId ->
        if (chId.isBlank()) {
            flowOf(ChatChannelSnapshot(null, emptyList(), 0L, emptyList(), emptyList()))
        } else {
            combine(
                chatRepository.observeChannel(chId),
                messageWindowLimit.flatMapLatest { limit ->
                    chatRepository.observeMessages(chId, limit)
                },
                chatRepository.sessionConnectedAt,
                chatRepository.observeChannelUsers(chId),
                chatRepository.friends
            ) { channel, messages, connectedAt, users, friends ->
                ChatChannelSnapshot(
                    channel = channel,
                    messages = messages,
                    sessionConnectedAt = connectedAt,
                    usersInChannel = users,
                    friends = friends
                )
            }
        }
    }

    private val sessionSnapshotFlow = combine(
        _selectedChannelId.flatMapLatest { chId ->
            if (chId.isBlank()) flowOf(null) else chatRepository.observeChannelWallpaper(chId)
        },
        chatRepository.settings,
        chatRepository.connectionStatus,
        chatRepository.connectionError,
        beatmapAudioPlayer.state
    ) { channelWallpaper, settings, status, connError, audio ->
        ChatSessionSnapshot(
            channelWallpaper = channelWallpaper,
            settings = settings,
            connectionStatus = status,
            connectionError = connError,
            audioState = audio
        )
    }

    private val historySnapshotFlow = combine(
        _selectedChannelId.flatMapLatest { chId ->
            if (chId.isBlank()) flowOf(ChannelHistoryState()) else chatRepository.observeChannelHistoryState(chId)
        },
        chatRepository.requiresChatReadReauth
    ) { historyState, requiresReauth ->
        historyState to requiresReauth
    }

    val uiState: StateFlow<ChatUiState> = combine(
        channelSnapshotFlow,
        sessionSnapshotFlow,
        messageWindowLimit,
        localState,
        chatRepository.mappools,
        _selectedChannelId.flatMapLatest { chId ->
            if (chId.isBlank()) flowOf(MatchSeriesState()) else chatRepository.observeMatchSeriesState(chId)
        },
        chatRepository.refereeMacros,
        chatRepository.bookmarkedMessages,
        chatRepository.bookmarkedMessageIds,
        chatRepository.dotOnlyChannels,
        chatRepository.chatUserColors,
        historySnapshotFlow
    ) { values ->
        val chSnap = values[0] as ChatChannelSnapshot
        val sessSnap = values[1] as ChatSessionSnapshot
        val windowLimit = values[2] as Int
        val local = values[3] as ChatLocalState
        @Suppress("UNCHECKED_CAST")
        val mappools = values[4] as List<Mappool>
        val rawSeriesState = values[5] as MatchSeriesState
        @Suppress("UNCHECKED_CAST")
        val refereeMacros = values[6] as List<RefereeMacro>
        @Suppress("UNCHECKED_CAST")
        val bookmarkedMessages = values[7] as List<BookmarkedMessageItem>
        @Suppress("UNCHECKED_CAST")
        val bookmarkedMessageIds = values[8] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val dotOnlyChannels = values[9] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val chatUserColors = values[10] as Map<String, ChatUserColorInfo>
        @Suppress("UNCHECKED_CAST")
        val historyPair = values[11] as Pair<ChannelHistoryState, Boolean>
        val (historyState, repoRequiresReauth) = historyPair
        val rawMessages = chSnap.messages
        val filteredMessages = if (local.retriedMessageIds.isEmpty()) {
            rawMessages
        } else {
            rawMessages.filterNot { it.id in local.retriedMessageIds }
        }
        val visibleMessages = filteredMessages.map { msg ->
            val effectiveOutgoing = msg.isOutgoing || BeatmapUrlParser.isSelfOsuSender(
                senderName = msg.senderName,
                senderId = msg.senderId,
                currentUserId = sessSnap.settings.userId,
                currentUsername = sessSnap.settings.username,
                currentIrcUsername = sessSnap.settings.ircUsername
            )
            if (effectiveOutgoing == msg.isOutgoing) msg else msg.copy(isOutgoing = true)
        }

        val mpState = if (isMpRoom) {
            MpLobbyParser.parseLobbyState(
                channelId = channelId,
                channelName = chSnap.channel?.name ?: channelId,
                messages = visibleMessages
            )
        } else {
            null
        }

        val displaySeriesState = if (mpState != null && !rawSeriesState.isCustomTeamNames) {
            val extracted = MpLobbyParser.extractTournamentTeams(mpState.roomName)
            if (extracted != null) {
                rawSeriesState.copy(redTeamName = extracted.first, blueTeamName = extracted.second)
            } else {
                rawSeriesState
            }
        } else {
            rawSeriesState
        }

        val isDm = chSnap.channel?.type == ChannelType.DIRECT || isDirectChannel
        val dmPartnerOnline = if (isDm) {
            val targetName = chSnap.channel?.name ?: dmFallbackName
            val normTarget = BeatmapUrlParser.normalizeUsername(targetName)
            val matchedFriend = chSnap.friends.firstOrNull {
                (chSnap.channel?.targetUserId != null && it.userId == chSnap.channel.targetUserId) ||
                    BeatmapUrlParser.normalizeUsername(it.username) == normTarget
            }
            matchedFriend?.isOnline ?: local.selectedProfile?.takeIf {
                BeatmapUrlParser.normalizeUsername(it.username) == normTarget
            }?.isOnline
        } else {
            null
        }

        val isOAuthV2 = sessSnap.settings.authMode == AuthMode.OAUTH_V2
        val requiresReauth = isOAuthV2 && repoRequiresReauth
        val hasMoreApiHistory = isOAuthV2 && historyState.hasMore && !requiresReauth
        val canSyncHistory = isOAuthV2 &&
            !requiresReauth &&
            channelId.isNotBlank() &&
            (!isMpRoom || historyState.oldestApiMessageId != null)
        val hasMoreLocalWindow = rawMessages.size >= windowLimit &&
            windowLimit < ChatRepository.MAX_MESSAGE_WINDOW
        val canLoadOlderMessages = (hasMoreApiHistory && rawMessages.size < ChatRepository.MAX_MESSAGE_WINDOW) ||
            hasMoreLocalWindow
        val isDotOnly = chSnap.channel?.matchesNormalizedChannelSet(dotOnlyChannels)
            ?: (channelId.trim().lowercase(Locale.ROOT) in dotOnlyChannels)

        ChatUiState(
            channel = chSnap.channel,
            messages = visibleMessages,
            sessionConnectedAt = chSnap.sessionConnectedAt,
            usersInChannel = chSnap.usersInChannel,
            dmPartnerOnline = dmPartnerOnline,
            effectiveWallpaper = sessSnap.channelWallpaper ?: sessSnap.settings.globalWallpaper,
            hasCustomChannelWallpaper = sessSnap.channelWallpaper != null,
            isChannelMuted = chSnap.channel?.notificationMode == ChannelNotificationMode.MUTED,
            isChannelDotOnly = isDotOnly,
            connectionStatus = sessSnap.connectionStatus,
            connectionError = sessSnap.connectionError?.let {
                sanitizeError(it, "Connection unavailable.")
            },
            isExplicitlyDisconnected = sessSnap.settings.isExplicitlyDisconnected,
            audioState = sessSnap.audioState,
            selfUserId = sessSnap.settings.userId,
            selfUsername = sessSnap.settings.username,
            selfIrcUsername = sessSnap.settings.ircUsername,
            userColors = chatUserColors,
            isOAuthV2 = isOAuthV2,
            canSyncHistory = canSyncHistory,
            isLoadingHistory = historyState.isLoading,
            hasMoreHistory = hasMoreApiHistory,
            requiresChatReadReauth = requiresReauth,
            historyError = local.historyError,
            messageWindowLimit = windowLimit,
            canLoadOlderMessages = canLoadOlderMessages,
            isLoadingOlderMessages = local.isLoadingOlderMessages,
            isUploadingImage = local.isUploadingImage,
            mpLobbyState = mpState,
            mappools = mappools,
            matchSeriesState = displaySeriesState,
            refereeMacros = refereeMacros,
            mpMatchHistory = local.mpMatchHistory,
            isMpHistoryLoading = local.isMpHistoryLoading,
            mpHistoryError = local.mpHistoryError,
            selectedProfile = local.selectedProfile,
            selectedProfileMode = local.selectedProfileMode,
            selectedProfilePinnedScores = local.pinnedScores,
            selectedProfileBestScores = local.bestScores,
            selectedProfileRecentScores = local.recentScores,
            isProfileLoading = local.isProfileLoading,
            isProfileScoresLoading = local.isProfileScoresLoading,
            isLoadingMoreBestScores = local.isLoadingMoreBestScores,
            canLoadMoreBestScores = local.canLoadMoreBestScores,
            profileError = local.profileError,
            selectedProfileLocalHistory = local.selectedProfileLocalHistory,
            selectedProfileOsuTrack = local.selectedProfileOsuTrack,
            isSelectedProfileOsuTrackLoading = local.isSelectedProfileOsuTrackLoading,
            seasonalBackgrounds = local.seasonalBackgrounds,
            isSeasonalLoading = local.isSeasonalLoading,
            downloadingSeasonalUrl = local.downloadingSeasonalUrl,
            bookmarkedMessages = bookmarkedMessages,
            bookmarkedMessageIds = bookmarkedMessageIds,
            ignoredUsers = sessSnap.settings.ignoredUsers,
            ignoreDisplayMode = sessSnap.settings.ignoreDisplayMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    init {
        viewModelScope.launch {
            _selectedChannelId
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collectLatest { chId ->
                    chatRepository.markChannelAsRead(chId)
                    syncChannelHistoryInternal(userInitiated = false, targetChannelId = chId)
                }
        }
        viewModelScope.launch {
            uiState
                .map { it.mpLobbyState }
                .distinctUntilChanged()
                .collect { lobby ->
                    if (lobby != null && isMpRoom) {
                        syncLobbyAutoScoring(lobby)
                    }
                }
        }
        viewModelScope.launch {
            beatmapAudioPlayer.state
                .map { it.error }
                .distinctUntilChanged()
                .collect { err ->
                    if (!err.isNullOrBlank()) {
                        _effects.emit(
                            ChatUiEffect.ShowSnackbar(
                                sanitizeError(err, "Audio preview unavailable.")
                            )
                        )
                    }
                }
        }
        viewModelScope.launch {
            uiState
                .map { state ->
                    state.channel?.type to state.messages.takeLast(50).map { it.senderName }
                }
                .distinctUntilChanged()
                .collect { (channelType, senders) ->
                    if (!isDirectChannel && channelType != ChannelType.DIRECT) {
                        val distinctSenders = senders.asSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { BeatmapUrlParser.normalizeOsuNick(it) }
                            .toList()
                        if (distinctSenders.isNotEmpty()) {
                            chatRepository.prefetchChatUserColors(distinctSenders)
                        }
                    }
                }
        }
    }

    fun selectChannel(newChannelId: String, focusMessageId: String? = null) {
        val clean = newChannelId.trim()
        if (clean.isEmpty()) return
        val changed = !clean.equals(_selectedChannelId.value, ignoreCase = true)
        _focusMessageId.value = focusMessageId?.takeIf { it.isNotBlank() }
        if (!changed) return
        profileLoadJob?.cancel()
        loadMoreBestScoresJob?.cancel()
        profileRequestSeq++
        messageWindowLimit.value = ChatRepository.DEFAULT_MESSAGE_WINDOW
        localState.update {
            it.copy(
                selectedProfileTarget = null,
                selectedProfile = null,
                selectedProfileMode = "osu",
                pinnedScores = emptyList(),
                bestScores = emptyList(),
                recentScores = emptyList(),
                isProfileLoading = false,
                isProfileScoresLoading = false,
                isLoadingMoreBestScores = false,
                canLoadMoreBestScores = false,
                profileError = null,
                mpMatchHistory = null,
                isMpHistoryLoading = false,
                mpHistoryError = null,
                historyError = null,
                isLoadingOlderMessages = false,
                retriedMessageIds = emptySet()
            )
        }
        _selectedChannelId.value = clean
        chatRepository.setActiveChannel(clean)
    }

    fun setChannelActive(active: Boolean) {
        val current = channelId
        chatRepository.setActiveChannel(if (active && current.isNotBlank()) current else null)
        if (active && current.isNotBlank()) {
            viewModelScope.launch { chatRepository.markChannelAsRead(current) }
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            chatRepository.clearConnectionError()
            if (uiState.value.isExplicitlyDisconnected) {
                chatRepository.setExplicitlyDisconnected(false)
            }
            chatRepository.reconnectIfAuthenticated()
        }
    }

    fun sendInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            sendChatCommand(channelId, trimmed).onSuccess { outcome ->
                when (outcome) {
                    is CommandOutcome.NavigateToChannel -> {
                        _effects.emit(ChatUiEffect.NavigateToChannel(outcome.channelId))
                    }
                    is CommandOutcome.LeftChannel -> {
                        _effects.emit(ChatUiEffect.NavigateBack)
                    }
                    is CommandOutcome.OpenPlayerStats -> {
                        val target = outcome.usernameOrNull
                            ?: uiState.value.channel?.name
                            ?: dmFallbackName.takeIf { isDirectChannel }
                        if (!target.isNullOrBlank() && !target.startsWith("#")) {
                            openPlayerProfile(target)
                        } else {
                            _effects.emit(ChatUiEffect.ShowSnackbar("Usage: /stats <username>"))
                        }
                    }
                    is CommandOutcome.Cleared -> {
                        localState.update { it.copy(retriedMessageIds = emptySet()) }
                        _effects.emit(ChatUiEffect.ShowSnackbar("Local history cleared"))
                    }
                    else -> Unit
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val canRetry = err !is IllegalArgumentException
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        message = friendlyErrorMessage(err, "Message could not be sent."),
                        actionLabel = if (canRetry) "Retry" else null,
                        retryInput = if (canRetry) trimmed else null
                    )
                )
            }
        }
    }

    fun retryFailedMessage(message: ChatMessage) {
        if (message.deliveryStatus != DeliveryStatus.FAILED) return
        val raw = if (message.isAction) "/me ${message.content}" else message.content
        localState.update { it.copy(retriedMessageIds = it.retriedMessageIds + message.id) }
        sendInput(raw)
    }

    fun sendQueuedMessageNow(message: ChatMessage) {
        if (message.deliveryStatus != DeliveryStatus.QUEUED) return
        viewModelScope.launch {
            chatRepository.sendQueuedMessageNow(message.id)
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Not connected to Bancho IRC yet.")
                        )
                    )
                }
        }
    }

    fun cancelQueuedMessage(message: ChatMessage) {
        if (message.deliveryStatus != DeliveryStatus.QUEUED) return
        viewModelScope.launch {
            chatRepository.cancelQueuedMessage(message.id)
            _effects.emit(ChatUiEffect.ShowSnackbar("Queued message cancelled"))
        }
    }

    fun uploadAndSendImage(uri: Uri) {
        if (localState.value.isUploadingImage) return
        viewModelScope.launch {
            localState.update { it.copy(isUploadingImage = true) }
            try {
                imageUploadRepository.compressAndUpload(uri)
                    .onSuccess { imageUrl ->
                        sendInput(imageUrl)
                    }
                    .onFailure { err ->
                        if (err is CancellationException) throw err
                        _effects.emit(
                            ChatUiEffect.ShowSnackbar(
                                friendlyErrorMessage(err, "Image upload failed. Please try again.")
                            )
                        )
                    }
            } finally {
                localState.update { it.copy(isUploadingImage = false) }
            }
        }
    }

    fun sendMpCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            chatRepository.sendMpCommand(channelId, trimmed)
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            message = friendlyErrorMessage(err, "MP command failed."),
                            actionLabel = "Retry",
                            retryMpCommand = trimmed
                        )
                    )
                }
        }
    }

    fun applyMappoolSlot(slot: MappoolSlot) {
        viewModelScope.launch {
            chatRepository.applyMappoolSlot(channelId, slot)
                .onSuccess {
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar("Applied ${slot.slotTag}: !mp map ${slot.beatmapId} / !mp mods ${slot.mods}")
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not apply mappool slot.")
                        )
                    )
                }
        }
    }

    fun saveMappool(poolId: Long?, name: String, slots: List<MappoolSlot>) {
        viewModelScope.launch {
            chatRepository.saveMappool(poolId, name, slots)
                .onSuccess {
                    _effects.emit(ChatUiEffect.ShowSnackbar("Saved mappool \"$name\""))
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not save mappool.")
                        )
                    )
                }
        }
    }

    fun deleteMappool(poolId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMappool(poolId)
                .onSuccess {
                    _effects.emit(ChatUiEffect.ShowSnackbar("Deleted mappool"))
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not delete mappool.")
                        )
                    )
                }
        }
    }

    private suspend fun syncLobbyAutoScoring(lobby: MpLobbyState) {
        var needsHistoryFallback = false
        chatRepository.updateMatchSeriesState(channelId) { current ->
            val extracted = if (!current.isCustomTeamNames) {
                MpLobbyParser.extractTournamentTeams(lobby.roomName)
            } else {
                null
            }
            var updated = if (extracted != null) {
                current.copy(redTeamName = extracted.first, blueTeamName = extracted.second)
            } else {
                current
            }

            val unseenRounds = lobby.finishedRounds.filter { it.finishKey !in updated.processedRoundKeys }
            if (unseenRounds.isEmpty()) return@updateMatchSeriesState updated

            val newProcessed = updated.processedRoundKeys.toMutableSet()
            val newRounds = updated.rounds.toMutableList()
            var nextRedScore = updated.redScore
            var nextBlueScore = updated.blueScore
            var warmupActive = updated.isWarmupActive

            for (event in unseenRounds) {
                newProcessed.add(event.finishKey)
                if (!updated.autoScoreTeamVs) continue
                val isTeamMode = event.teamMode.equals("TeamVs", ignoreCase = true) ||
                    event.teamMode.equals("TagTeamVs", ignoreCase = true)
                if (!isTeamMode) continue

                if (warmupActive && updated.ignoreWarmups) {
                    newRounds.add(
                        SeriesRoundRecord(
                            slotTag = updated.activePickedSlotTag.orEmpty(),
                            beatmapLabel = event.beatmapTitle ?: lobby.beatmapTitle.orEmpty(),
                            pickedBy = updated.activePickedBy,
                            winner = MpTeam.NONE,
                            redTotalScore = event.redTotalScore,
                            blueTotalScore = event.blueTotalScore,
                            isWarmup = true,
                            roundKey = event.finishKey
                        )
                    )
                    warmupActive = false
                    continue
                }

                if (event.redTotalScore == 0L && event.blueTotalScore == 0L) {
                    needsHistoryFallback = true
                    continue
                }

                if (event.redTotalScore != event.blueTotalScore) {
                    val winner = if (event.redTotalScore > event.blueTotalScore) MpTeam.RED else MpTeam.BLUE
                    if (winner == MpTeam.RED) nextRedScore++ else nextBlueScore++
                    newRounds.add(
                        SeriesRoundRecord(
                            slotTag = updated.activePickedSlotTag.orEmpty(),
                            beatmapLabel = event.beatmapTitle ?: lobby.beatmapTitle.orEmpty(),
                            pickedBy = updated.activePickedBy,
                            winner = winner,
                            redTotalScore = event.redTotalScore,
                            blueTotalScore = event.blueTotalScore,
                            isWarmup = false,
                            roundKey = event.finishKey
                        )
                    )
                    updated = updated.copy(
                        activePickedSlotId = null,
                        activePickedSlotTag = null,
                        activePickedBeatmapId = null,
                        activePickedBy = MpTeam.NONE
                    )
                }
            }

            updated.copy(
                redScore = nextRedScore,
                blueScore = nextBlueScore,
                rounds = newRounds,
                isWarmupActive = warmupActive,
                processedRoundKeys = newProcessed
            )
        }

        val matchId = lobby.matchId
        if (needsHistoryFallback && matchId != null) {
            chatRepository.fetchMatchHistory(matchId).onSuccess { history ->
                localState.update { it.copy(mpMatchHistory = history, mpHistoryError = null) }
                val latestGame = history.games.lastOrNull() ?: return@onSuccess
                val roundKey = "api_game_${latestGame.gameId}"
                chatRepository.updateMatchSeriesState(channelId) { current ->
                    if (!current.autoScoreTeamVs || roundKey in current.processedRoundKeys) {
                        return@updateMatchSeriesState current
                    }
                    if (latestGame.redTeamTotal == latestGame.blueTeamTotal ||
                        (latestGame.redTeamTotal == 0L && latestGame.blueTeamTotal == 0L)
                    ) {
                        return@updateMatchSeriesState current
                    }
                    val winner = if (latestGame.redTeamTotal > latestGame.blueTeamTotal) MpTeam.RED else MpTeam.BLUE
                    current.copy(
                        redScore = if (winner == MpTeam.RED) current.redScore + 1 else current.redScore,
                        blueScore = if (winner == MpTeam.BLUE) current.blueScore + 1 else current.blueScore,
                        rounds = current.rounds + SeriesRoundRecord(
                            slotTag = current.activePickedSlotTag.orEmpty(),
                            beatmapLabel = latestGame.beatmapTitle,
                            pickedBy = current.activePickedBy,
                            winner = winner,
                            redTotalScore = latestGame.redTeamTotal,
                            blueTotalScore = latestGame.blueTeamTotal,
                            isWarmup = false,
                            roundKey = roundKey
                        ),
                        activePickedSlotId = null,
                        activePickedSlotTag = null,
                        activePickedBeatmapId = null,
                        activePickedBy = MpTeam.NONE,
                        processedRoundKeys = current.processedRoundKeys + roundKey
                    )
                }
            }
        }
    }

    fun setSeriesBestOf(bestOf: Int) {
        val safeBestOf = bestOf.coerceIn(1, 25)
        chatRepository.updateMatchSeriesState(channelId) { it.copy(bestOf = safeBestOf) }
    }

    fun setSeriesTeamNames(redName: String, blueName: String) {
        val cleanRed = redName.trim().ifEmpty { "Red Team" }
        val cleanBlue = blueName.trim().ifEmpty { "Blue Team" }
        chatRepository.updateMatchSeriesState(channelId) {
            it.copy(redTeamName = cleanRed, blueTeamName = cleanBlue, isCustomTeamNames = true)
        }
    }

    fun adjustSeriesScore(team: MpTeam, delta: Int) {
        if (team == MpTeam.NONE || delta == 0) return
        chatRepository.updateMatchSeriesState(channelId) { current ->
            if (delta > 0) {
                val nextRounds = current.rounds + SeriesRoundRecord(
                    slotTag = current.activePickedSlotTag.orEmpty(),
                    beatmapLabel = uiState.value.mpLobbyState?.beatmapTitle.orEmpty(),
                    pickedBy = current.activePickedBy,
                    winner = team,
                    isWarmup = false,
                    roundKey = "manual_${System.nanoTime()}"
                )
                current.copy(
                    redScore = if (team == MpTeam.RED) current.redScore + 1 else current.redScore,
                    blueScore = if (team == MpTeam.BLUE) current.blueScore + 1 else current.blueScore,
                    rounds = nextRounds
                )
            } else {
                val canDecrement = if (team == MpTeam.RED) current.redScore > 0 else current.blueScore > 0
                if (!canDecrement) return@updateMatchSeriesState current
                val removeIdx = current.rounds.indexOfLast { !it.isWarmup && it.winner == team }
                val nextRounds = if (removeIdx >= 0) {
                    current.rounds.toMutableList().apply { removeAt(removeIdx) }
                } else {
                    current.rounds
                }
                current.copy(
                    redScore = if (team == MpTeam.RED) (current.redScore - 1).coerceAtLeast(0) else current.redScore,
                    blueScore = if (team == MpTeam.BLUE) (current.blueScore - 1).coerceAtLeast(0) else current.blueScore,
                    rounds = nextRounds
                )
            }
        }
    }

    fun undoLastSeriesRound() {
        chatRepository.updateMatchSeriesState(channelId) { current ->
            val lastIdx = current.rounds.lastIndex
            if (lastIdx < 0) return@updateMatchSeriesState current
            val removed = current.rounds[lastIdx]
            val nextRounds = current.rounds.dropLast(1)
            val nextRed = if (!removed.isWarmup && removed.winner == MpTeam.RED) {
                (current.redScore - 1).coerceAtLeast(0)
            } else {
                current.redScore
            }
            val nextBlue = if (!removed.isWarmup && removed.winner == MpTeam.BLUE) {
                (current.blueScore - 1).coerceAtLeast(0)
            } else {
                current.blueScore
            }
            current.copy(redScore = nextRed, blueScore = nextBlue, rounds = nextRounds)
        }
    }

    fun resetSeriesState() {
        val lobby = uiState.value.mpLobbyState
        val extracted = lobby?.let { MpLobbyParser.extractTournamentTeams(it.roomName) }
        chatRepository.updateMatchSeriesState(channelId) { current ->
            val allProcessed = current.processedRoundKeys + lobby?.finishedRounds.orEmpty().map { it.finishKey }
            current.copy(
                redTeamName = extracted?.first ?: if (current.isCustomTeamNames) current.redTeamName else "Red Team",
                blueTeamName = extracted?.second ?: if (current.isCustomTeamNames) current.blueTeamName else "Blue Team",
                redScore = 0,
                blueScore = 0,
                rounds = emptyList(),
                isWarmupActive = false,
                slotStates = emptyMap(),
                activePickedSlotId = null,
                activePickedSlotTag = null,
                activePickedBeatmapId = null,
                activePickedBy = MpTeam.NONE,
                processedRoundKeys = allProcessed
            )
        }
    }

    fun setAutoScoreTeamVs(enabled: Boolean) {
        chatRepository.updateMatchSeriesState(channelId) { it.copy(autoScoreTeamVs = enabled) }
    }

    fun setSeriesWarmupActive(isWarmup: Boolean) {
        chatRepository.updateMatchSeriesState(channelId) { it.copy(isWarmupActive = isWarmup) }
    }

    fun setSelectedMappoolId(poolId: Long?) {
        chatRepository.updateMatchSeriesState(channelId) { it.copy(selectedPoolId = poolId) }
    }

    fun setPickBanMode(mode: MpPickBanMode) {
        chatRepository.updateMatchSeriesState(channelId) { it.copy(pickBanMode = mode) }
    }

    fun updatePoolSlotState(
        slot: MappoolSlot,
        targetState: MpPoolSlotState,
        triggerMpCommands: Boolean
    ) {
        chatRepository.updateMatchSeriesState(channelId) { current ->
            val nextStates = current.slotStates.toMutableMap()
            if (targetState == MpPoolSlotState.AVAILABLE) {
                nextStates.remove(slot.id)
            } else {
                nextStates[slot.id] = targetState
            }
            val isPickState = targetState == MpPoolSlotState.PICKED_RED ||
                targetState == MpPoolSlotState.PICKED_BLUE ||
                targetState == MpPoolSlotState.PLAYED_TB ||
                targetState == MpPoolSlotState.PLAYED
            val pickedBy = when (targetState) {
                MpPoolSlotState.PICKED_RED -> MpTeam.RED
                MpPoolSlotState.PICKED_BLUE -> MpTeam.BLUE
                else -> MpTeam.NONE
            }
            current.copy(
                slotStates = nextStates,
                activePickedSlotId = if (isPickState) slot.id else current.activePickedSlotId,
                activePickedSlotTag = if (isPickState) slot.slotTag else current.activePickedSlotTag,
                activePickedBeatmapId = if (isPickState) slot.beatmapId else current.activePickedBeatmapId,
                activePickedBy = if (isPickState) pickedBy else current.activePickedBy
            )
        }
        if (triggerMpCommands) {
            applyMappoolSlot(slot)
        }
    }

    fun sendRefereeMacro(rawText: String) {
        val state = uiState.value
        val resolved = MpLobbyParser.resolveRefereeMacro(
            template = rawText,
            series = state.matchSeriesState,
            lobby = state.mpLobbyState
        ).trim()
        if (resolved.isEmpty()) return
        viewModelScope.launch {
            val result = if (resolved.startsWith("!mp ", ignoreCase = true) || resolved.equals("!mp", ignoreCase = true)) {
                chatRepository.sendMpCommand(channelId, resolved)
            } else {
                chatRepository.sendMessage(channelId, resolved)
            }
            result.onFailure { err ->
                if (err is CancellationException) throw err
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        friendlyErrorMessage(err, "Could not send macro message.")
                    )
                )
            }
        }
    }

    fun saveRefereeMacros(macros: List<RefereeMacro>) {
        viewModelScope.launch {
            chatRepository.saveRefereeMacros(macros)
            _effects.emit(ChatUiEffect.ShowSnackbar("Referee macros saved"))
        }
    }

    fun resetRefereeMacros() {
        viewModelScope.launch {
            chatRepository.resetRefereeMacros()
            _effects.emit(ChatUiEffect.ShowSnackbar("Referee macros reset to defaults"))
        }
    }

    fun notifyMessage(message: String) {
        viewModelScope.launch {
            _effects.emit(ChatUiEffect.ShowSnackbar(message))
        }
    }

    suspend fun getCachedBeatmaps(beatmapIds: Collection<Long>): Map<Long, BeatmapMetadata> {
        return chatRepository.getCachedBeatmaps(beatmapIds)
    }

    suspend fun resolveBeatmapDetails(beatmapId: Long): Result<BeatmapMetadata> {
        return chatRepository.getBeatmapDetails(beatmapId).recoverCatching { err ->
            if (err is CancellationException) throw err
            throw IllegalStateException(friendlyErrorMessage(err, "Could not load beatmap info."))
        }
    }

    fun refreshMpMatchHistory(matchId: Long) {
        viewModelScope.launch {
            localState.update { it.copy(isMpHistoryLoading = true, mpHistoryError = null) }
            chatRepository.fetchMatchHistory(matchId)
                .onSuccess { history ->
                    localState.update {
                        it.copy(
                            mpMatchHistory = history,
                            mpHistoryError = null,
                            isMpHistoryLoading = false
                        )
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    localState.update {
                        it.copy(
                            isMpHistoryLoading = false,
                            mpHistoryError = friendlyErrorMessage(err, "Could not load match history.")
                        )
                    }
                }
        }
    }

    suspend fun resolveBeatmap(linkRef: BeatmapLinkRef): Result<BeatmapMetadata> {
        return chatRepository.getBeatmapMetadata(linkRef).recoverCatching { err ->
            if (err is CancellationException) throw err
            throw IllegalStateException(friendlyErrorMessage(err, "Could not load beatmap info."))
        }
    }

    suspend fun estimateRankForPp(
        mode: String,
        targetPp: Double,
        currentRankHint: Int?,
        currentPpHint: Double?
    ): Result<RankPpConversionResult> {
        return chatRepository.estimateRankForPp(mode, targetPp, currentRankHint, currentPpHint)
            .recoverCatching { err ->
                if (err is CancellationException) throw err
                throw IllegalStateException(friendlyErrorMessage(err, "Could not estimate rank."))
            }
    }

    fun toggleBeatmapAudio(beatmapsetId: Long, previewUrl: String) {
        beatmapAudioPlayer.togglePlayPause(beatmapsetId, previewUrl)
    }

    fun stopBeatmapAudio() {
        beatmapAudioPlayer.stop()
    }

    suspend fun resolveUserProfile(userId: Long): Result<PlayerProfile> {
        return chatRepository.getPlayerProfile(userId.toString()).recoverCatching { err ->
            if (err is CancellationException) throw err
            throw IllegalStateException(friendlyErrorMessage(err, "Could not load player #$userId."))
        }
    }

    fun openPlayerProfile(usernameOrId: String) {
        loadTargetProfile(usernameOrId = usernameOrId, mode = null)
    }

    fun retryLoadPlayerProfile() {
        val local = localState.value
        val target = local.selectedProfileTarget
            ?: local.selectedProfile?.let { if (it.userId > 0L) it.userId.toString() else it.username }
            ?: return
        loadTargetProfile(usernameOrId = target, mode = local.selectedProfileMode)
    }

    fun selectProfileMode(mode: String) {
        val local = localState.value
        val target = local.selectedProfileTarget
            ?: local.selectedProfile?.let { if (it.userId > 0L) it.userId.toString() else it.username }
            ?: return
        loadTargetProfile(usernameOrId = target, mode = mode)
    }

    private fun loadTargetProfile(usernameOrId: String, mode: String?) {
        profileLoadJob?.cancel()
        selectedOsuTrackJob?.cancel()
        loadMoreBestScoresJob?.cancel()
        val requestSeq = ++profileRequestSeq
        selectedOsuTrackSeq++
        profileLoadJob = viewModelScope.launch {
            localState.update {
                val isSameTarget = it.selectedProfileTarget.equals(usernameOrId, ignoreCase = true)
                val isSameMode = mode == null || mode.equals(it.selectedProfileMode, ignoreCase = true)
                it.copy(
                    selectedProfileTarget = usernameOrId,
                    selectedProfile = if (isSameTarget) it.selectedProfile else null,
                    selectedProfileMode = mode ?: if (isSameTarget) it.selectedProfileMode else "osu",
                    isProfileLoading = true,
                    isProfileScoresLoading = false,
                    isLoadingMoreBestScores = false,
                    canLoadMoreBestScores = false,
                    profileError = null,
                    selectedProfileLocalHistory = if (isSameTarget && isSameMode) it.selectedProfileLocalHistory else emptyList(),
                    selectedProfileOsuTrack = if (isSameTarget && isSameMode) it.selectedProfileOsuTrack else emptyList(),
                    isSelectedProfileOsuTrackLoading = false,
                    pinnedScores = emptyList(),
                    bestScores = emptyList(),
                    recentScores = emptyList()
                )
            }
            val profileResult = chatRepository.getPlayerProfile(usernameOrId, mode)
            if (requestSeq != profileRequestSeq) return@launch

            val profile = profileResult.getOrNull()
            if (profile == null) {
                val err = profileResult.exceptionOrNull()
                if (err is CancellationException) throw err
                val friendlyErr = friendlyErrorMessage(err, "Could not load profile for $usernameOrId.")
                localState.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(
                        selectedProfileMode = it.selectedProfile?.mode ?: it.selectedProfileMode,
                        isProfileLoading = false,
                        isProfileScoresLoading = false,
                        isLoadingMoreBestScores = false,
                        canLoadMoreBestScores = false,
                        profileError = friendlyErr
                    )
                }
                return@launch
            }

            val resolvedMode = mode ?: profile.mode
            localState.update {
                if (requestSeq != profileRequestSeq) it
                else it.copy(
                    selectedProfile = profile,
                    selectedProfileMode = resolvedMode,
                    isProfileLoading = false,
                    profileError = null
                )
            }
            if (profile.userId > 0L) {
                val settings = chatRepository.settings.first()
                if (requestSeq != profileRequestSeq) return@launch
                val isSelfTarget = (settings.userId > 0L && profile.userId == settings.userId) ||
                    (settings.username.isNotBlank() && profile.username.equals(settings.username, ignoreCase = true))
                fetchSelectedOsuTrackHistory(
                    userId = profile.userId,
                    mode = resolvedMode,
                    isSelfTarget = isSelfTarget,
                    expectedProfileSeq = requestSeq
                )
                if (mode == null && profile.rankHistory.isEmpty() && settings.authMode == AuthMode.OAUTH_V2) {
                    viewModelScope.launch {
                        chatRepository.getPlayerProfile(profile.userId.toString(), resolvedMode).onSuccess { fresh ->
                            if (requestSeq == profileRequestSeq && fresh.rankHistory.isNotEmpty()) {
                                localState.update { state ->
                                    if (requestSeq == profileRequestSeq && state.selectedProfile?.userId == fresh.userId) {
                                        state.copy(selectedProfile = fresh)
                                    } else {
                                        state
                                    }
                                }
                            }
                        }
                    }
                }
                localState.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(isProfileScoresLoading = true)
                }
                val (pinned, best, recent) = coroutineScope {
                    val pinnedDef = async {
                        chatRepository.getUserScores(
                            profile.userId.toString(),
                            "pinned",
                            includeFails = false,
                            mode = resolvedMode
                        ).getOrDefault(emptyList())
                    }
                    val bestDef = async {
                        chatRepository.getUserScores(
                            profile.userId.toString(),
                            "best",
                            includeFails = false,
                            mode = resolvedMode,
                            limit = 100
                        ).getOrDefault(emptyList())
                    }
                    val recentDef = async {
                        chatRepository.getUserScores(
                            profile.userId.toString(),
                            "recent",
                            includeFails = true,
                            mode = resolvedMode
                        ).getOrDefault(emptyList())
                    }
                    Triple(pinnedDef.await(), bestDef.await(), recentDef.await())
                }
                if (requestSeq != profileRequestSeq) return@launch
                localState.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(
                        pinnedScores = pinned,
                        bestScores = best,
                        recentScores = recent,
                        isProfileScoresLoading = false,
                        isLoadingMoreBestScores = false,
                        canLoadMoreBestScores = best.size >= 100
                    )
                }
            } else {
                localState.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(
                        isProfileScoresLoading = false,
                        isLoadingMoreBestScores = false,
                        canLoadMoreBestScores = false
                    )
                }
            }
        }
    }

    fun loadMoreBestScores() {
        val local = localState.value
        val uid = local.selectedProfile?.userId?.takeIf { it > 0L } ?: return
        if (local.isLoadingMoreBestScores || !local.canLoadMoreBestScores) return
        val requestSeq = profileRequestSeq
        val mode = local.selectedProfileMode
        loadMoreBestScoresJob?.cancel()
        loadMoreBestScoresJob = viewModelScope.launch {
            localState.update {
                if (requestSeq != profileRequestSeq) it
                else it.copy(isLoadingMoreBestScores = true)
            }
            chatRepository.getUserScores(
                usernameOrId = uid.toString(),
                type = "best",
                includeFails = false,
                mode = mode,
                limit = 100,
                offset = 100
            ).onSuccess { pageTwo ->
                if (requestSeq != profileRequestSeq) return@onSuccess
                localState.update { state ->
                    if (requestSeq != profileRequestSeq) state
                    else {
                        val seenIds = state.bestScores.mapNotNull { it.id.takeIf { id -> id > 0L } }.toHashSet()
                        val merged = state.bestScores.toMutableList()
                        for (item in pageTwo) {
                            if (item.id <= 0L || seenIds.add(item.id)) {
                                merged.add(item)
                            }
                            if (merged.size >= 200) break
                        }
                        state.copy(
                            bestScores = merged,
                            isLoadingMoreBestScores = false,
                            canLoadMoreBestScores = false
                        )
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                if (requestSeq != profileRequestSeq) return@onFailure
                localState.update { state ->
                    if (requestSeq != profileRequestSeq) state
                    else state.copy(
                        isLoadingMoreBestScores = false,
                        canLoadMoreBestScores = true
                    )
                }
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        friendlyErrorMessage(err, "Could not load more top plays.")
                    )
                )
            }
        }
    }

    private fun fetchSelectedOsuTrackHistory(
        userId: Long,
        mode: String,
        isSelfTarget: Boolean,
        expectedProfileSeq: Long
    ) {
        if (userId <= 0L) return
        selectedOsuTrackJob?.cancel()
        val trackSeq = ++selectedOsuTrackSeq
        selectedOsuTrackJob = viewModelScope.launch {
            localState.update {
                if (expectedProfileSeq != profileRequestSeq || trackSeq != selectedOsuTrackSeq) it
                else it.copy(isSelectedProfileOsuTrackLoading = true)
            }
            val localHistory = if (isSelfTarget) {
                runCatching { chatRepository.observeProfileHistory(mode, limitDays = 90).first() }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val points = chatRepository.fetchOsuTrackHistory(userId, mode, days = 90)
                .getOrDefault(emptyList())
            if (expectedProfileSeq != profileRequestSeq || trackSeq != selectedOsuTrackSeq) return@launch
            localState.update {
                if (expectedProfileSeq != profileRequestSeq || trackSeq != selectedOsuTrackSeq) it
                else it.copy(
                    selectedProfileLocalHistory = localHistory,
                    selectedProfileOsuTrack = points,
                    isSelectedProfileOsuTrackLoading = false
                )
            }
        }
    }

    fun dismissPlayerProfile() {
        profileLoadJob?.cancel()
        selectedOsuTrackJob?.cancel()
        loadMoreBestScoresJob?.cancel()
        profileRequestSeq++
        selectedOsuTrackSeq++
        localState.update {
            it.copy(
                selectedProfileTarget = null,
                selectedProfile = null,
                selectedProfileMode = "osu",
                isProfileLoading = false,
                isProfileScoresLoading = false,
                isLoadingMoreBestScores = false,
                canLoadMoreBestScores = false,
                profileError = null,
                selectedProfileLocalHistory = emptyList(),
                selectedProfileOsuTrack = emptyList(),
                isSelectedProfileOsuTrackLoading = false,
                pinnedScores = emptyList(),
                bestScores = emptyList(),
                recentScores = emptyList()
            )
        }
    }

    fun toggleFriendStatus(profile: PlayerProfile) {
        viewModelScope.launch {
            val result = if (profile.friendStatus == FriendRelationStatus.NONE) {
                chatRepository.addFriend(profile)
            } else {
                chatRepository.removeFriend(profile.userId, profile.username)
            }
            result.onSuccess { newStatus ->
                localState.update {
                    val cur = it.selectedProfile
                    if (cur != null && cur.username.equals(profile.username, ignoreCase = true)) {
                        it.copy(selectedProfile = cur.copy(friendStatus = newStatus))
                    } else {
                        it
                    }
                }
                val msg = if (newStatus == FriendRelationStatus.NONE) {
                    "Removed ${profile.username} from friends"
                } else {
                    "Added ${profile.username} as friend"
                }
                _effects.emit(ChatUiEffect.ShowSnackbar(msg))
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        friendlyErrorMessage(err, "Could not update friend status.")
                    )
                )
            }
        }
    }

    fun toggleIgnoreUser(username: String) {
        val norm = BeatmapUrlParser.normalizeOsuNick(username)
        if (norm.isEmpty() || norm == "banchobot" || norm == "system") return
        viewModelScope.launch {
            val currentlyIgnored = norm in uiState.value.ignoredUsers
            if (currentlyIgnored) {
                chatRepository.unignoreUser(username)
                _effects.emit(ChatUiEffect.ShowSnackbar("Unignored $username"))
            } else {
                chatRepository.ignoreUser(username)
                _effects.emit(ChatUiEffect.ShowSnackbar("Ignored $username"))
            }
        }
    }

    fun saveUserAlias(username: String, alias: String?, userId: Long?, note: String? = null) {
        viewModelScope.launch {
            runCatching {
                chatRepository.setUserAlias(username, alias, userId, note)
            }.onSuccess {
                val cleanAlias = alias?.trim().orEmpty()
                val cleanNote = note?.trim().orEmpty()
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        if (cleanAlias.isEmpty() && cleanNote.isEmpty()) {
                            "Alias & note cleared"
                        } else {
                            "Player alias & note saved"
                        }
                    )
                )
                val refreshed = chatRepository.getPlayerProfile(
                    username,
                    localState.value.selectedProfileMode
                ).getOrNull()
                if (refreshed != null) {
                    localState.update {
                        if (it.selectedProfile?.username?.equals(username, ignoreCase = true) == true ||
                            it.selectedProfileTarget?.equals(username, ignoreCase = true) == true
                        ) {
                            it.copy(selectedProfile = refreshed)
                        } else {
                            it
                        }
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        friendlyErrorMessage(err, "Could not save nickname.")
                    )
                )
            }
        }
    }

    fun toggleBookmark(message: ChatMessage) {
        viewModelScope.launch {
            val channelLabel = uiState.value.channel?.displayTitle
                ?: uiState.value.channel?.name
                ?: if (isDirectChannel) dmFallbackName else channelId
            chatRepository.toggleBookmarkMessage(message, channelLabel)
                .onSuccess { bookmarked ->
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            if (bookmarked) "Message bookmarked" else "Bookmark removed"
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not update bookmark.")
                        )
                    )
                }
        }
    }

    fun removeBookmark(messageId: String) {
        viewModelScope.launch {
            chatRepository.removeBookmarkMessage(messageId)
        }
    }

    fun toggleTrackFriendPp(profile: PlayerProfile) {
        viewModelScope.launch {
            chatRepository.toggleTrackFriendPp(profile.username, profile.userId, profile.avatarUrl)
                .onSuccess { tracked ->
                    localState.update { state ->
                        val cur = state.selectedProfile
                        if (cur != null && cur.username.equals(profile.username, ignoreCase = true)) {
                            state.copy(selectedProfile = cur.copy(isTrackedForPp = tracked))
                        } else {
                            state
                        }
                    }
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            if (tracked) {
                                "Tracking ${profile.username} top PP plays"
                            } else {
                                "Stopped tracking ${profile.username}"
                            }
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not update PP tracking.")
                        )
                    )
                }
        }
    }

    fun shareScoreInCurrentChat(score: OsuScoreItem) {
        val modsPart = if (score.mods.isNotEmpty()) " +${score.mods.joinToString("")}" else ""
        val ppPart = score.pp?.takeIf { it.isFinite() }?.let { " ${it.roundToInt()}pp" } ?: ""
        val acc = String.format(Locale.US, "%.2f%%", score.accuracy)
        val url = score.beatmapId?.let { "https://osu.ppy.sh/b/$it" }
            ?: score.beatmapsetId?.let { "https://osu.ppy.sh/s/$it" }
            ?: ""
        val msg = "${score.username} | ${score.beatmapArtist} - ${score.beatmapTitle} [${score.beatmapVersion}]$modsPart ${score.gradeLabel} $acc ${score.maxCombo}x$ppPart $url".trim()
        dismissPlayerProfile()
        sendInput(msg)
    }

    fun startDirectMessage(username: String) {
        viewModelScope.launch {
            chatRepository.startDirectMessage(username)
                .onSuccess { targetChannel ->
                    if (!targetChannel.id.equals(channelId, ignoreCase = true)) {
                        _effects.emit(ChatUiEffect.NavigateToChannel(targetChannel.id))
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not open direct message.")
                        )
                    )
                }
        }
    }

    fun joinMpLobbyAndNavigate(matchId: Long) {
        if (matchId <= 0L) return
        viewModelScope.launch {
            chatRepository.joinMultiplayerLobby(matchId.toString())
                .onSuccess { targetChannel ->
                    if (!targetChannel.id.equals(channelId, ignoreCase = true)) {
                        _effects.emit(ChatUiEffect.NavigateToChannel(targetChannel.id))
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not join #mp_$matchId.")
                        )
                    )
                }
        }
    }

    fun updateChannelWallpaper(config: WallpaperConfig?) {
        viewModelScope.launch {
            chatRepository.setChannelWallpaper(channelId, config)
        }
    }

    fun loadSeasonalBackgrounds() {
        if (localState.value.isSeasonalLoading || localState.value.seasonalBackgrounds.isNotEmpty()) return
        viewModelScope.launch {
            localState.update { it.copy(isSeasonalLoading = true) }
            val items = chatRepository.fetchSeasonalBackgrounds().getOrDefault(emptyList())
            localState.update { it.copy(seasonalBackgrounds = items, isSeasonalLoading = false) }
        }
    }

    fun selectSeasonalWallpaper(imageUrl: String) {
        if (localState.value.downloadingSeasonalUrl != null) return
        viewModelScope.launch {
            localState.update { it.copy(downloadingSeasonalUrl = imageUrl) }
            try {
                chatRepository.downloadSeasonalWallpaper(imageUrl)
                    .onSuccess { localUri ->
                        val current = uiState.value.effectiveWallpaper
                        chatRepository.setChannelWallpaper(
                            channelId,
                            current.copy(
                                customImageUri = localUri,
                                scale = 1.0f,
                                biasX = 0.0f,
                                biasY = 0.0f
                            )
                        )
                    }
                    .onFailure { err ->
                        if (err is CancellationException) throw err
                        _effects.emit(
                            ChatUiEffect.ShowSnackbar(
                                friendlyErrorMessage(err, "Could not download seasonal background.")
                            )
                        )
                    }
            } finally {
                localState.update { it.copy(downloadingSeasonalUrl = null) }
            }
        }
    }

    fun buildExportFilename(format: ChatLogExportFormat): String {
        val name = uiState.value.channel?.name ?: dmFallbackName
        return ChatLogFormatter.buildDefaultFilename(name, channelId, format)
    }

    fun exportChatLog(format: ChatLogExportFormat, destinationUri: Uri) {
        viewModelScope.launch {
            chatRepository.exportChatLog(channelId, format, destinationUri)
                .onSuccess { count ->
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            "Exported $count messages (${format.extension.uppercase(Locale.US)})"
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not export chat log.")
                        )
                    )
                }
        }
    }

    fun togglePinChannel() {
        val currentPinned = uiState.value.channel?.isPinned == true
        viewModelScope.launch {
            chatRepository.setChannelPinned(channelId, !currentPinned)
                .onSuccess {
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            if (!currentPinned) "Pinned to top of chats" else "Unpinned chat"
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not update pin state.")
                        )
                    )
                }
        }
    }

    fun setNotificationMode(mode: ChannelNotificationMode) {
        viewModelScope.launch {
            chatRepository.setChannelNotificationMode(channelId, mode)
                .onSuccess {
                    _effects.emit(ChatUiEffect.ShowSnackbar("Notifications: ${mode.label}"))
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not update notification mode.")
                        )
                    )
                }
        }
    }

    fun toggleChannelMute() {
        val target = uiState.value.channel?.name ?: channelId
        viewModelScope.launch {
            chatRepository.toggleChannelMute(target)
                .onSuccess { nowMuted ->
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            if (nowMuted) "Bildirimler kapatıldı" else "Bildirimler açıldı"
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not update notification mute state.")
                        )
                    )
                }
        }
    }

    fun toggleChannelDotOnly() {
        val targetId = uiState.value.channel?.id ?: channelId
        viewModelScope.launch {
            chatRepository.toggleChannelDotOnly(targetId)
                .onSuccess { nowDotOnly ->
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            if (nowDotOnly) "Sayı yerine sadece nokta" else "Okunmamış sayısı gösterilecek"
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not update badge preference.")
                        )
                    )
                }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            syncChannelHistoryInternal(userInitiated = true)
        }
    }

    private suspend fun syncChannelHistoryInternal(
        userInitiated: Boolean,
        targetChannelId: String = channelId
    ) {
        val currentSettings = chatRepository.settings.first()
        if (currentSettings.authMode != AuthMode.OAUTH_V2) {
            if (userInitiated) {
                _effects.emit(
                    ChatUiEffect.ShowSnackbar("History sync is available when signed in with osu!.")
                )
            }
            return
        }
        if (chatRepository.requiresChatReadReauth.value) {
            return
        }
        if (channelId.equals(targetChannelId, ignoreCase = true)) {
            localState.update { it.copy(historyError = null) }
        }
        chatRepository.syncChannelHistory(
            channelId = targetChannelId,
            loadOlder = false,
            force = userInitiated
        ).onFailure { err ->
            if (err is CancellationException) throw err
            if (!chatRepository.requiresChatReadReauth.value &&
                channelId.equals(targetChannelId, ignoreCase = true)
            ) {
                localState.update {
                    it.copy(
                        historyError = friendlyErrorMessage(err, "Could not sync recent messages.")
                    )
                }
            }
        }
    }

    fun loadOlderMessages() {
        val targetChannelId = channelId.trim()
        if (targetChannelId.isBlank()) return
        val currentUi = uiState.value
        if (localState.value.isLoadingOlderMessages || currentUi.isLoadingHistory) {
            return
        }

        viewModelScope.launch {
            localState.update { it.copy(isLoadingOlderMessages = true, historyError = null) }
            try {
                val currentLimit = messageWindowLimit.value
                val beforeCount = currentUi.messages.size
                var revealedLocal = false
                if (beforeCount >= currentLimit && currentLimit < ChatRepository.MAX_MESSAGE_WINDOW) {
                    val nextLimit = (currentLimit + 200).coerceAtMost(ChatRepository.MAX_MESSAGE_WINDOW)
                    messageWindowLimit.value = nextLimit
                    val afterLocal = chatRepository.observeMessages(targetChannelId, nextLimit).first()
                    revealedLocal = afterLocal.size > beforeCount
                }

                if (!revealedLocal && currentUi.hasMoreHistory) {
                    val neededLimit = (beforeCount + 60).coerceAtMost(ChatRepository.MAX_MESSAGE_WINDOW)
                    if (messageWindowLimit.value < neededLimit) {
                        messageWindowLimit.value = neededLimit
                    }
                    chatRepository.syncChannelHistory(
                        channelId = targetChannelId,
                        loadOlder = true,
                        force = false
                    ).onFailure { err ->
                        if (err is CancellationException) throw err
                        if (!chatRepository.requiresChatReadReauth.value &&
                            channelId.equals(targetChannelId, ignoreCase = true)
                        ) {
                            localState.update {
                                it.copy(
                                    historyError = friendlyErrorMessage(err, "Could not load older messages.")
                                )
                            }
                        }
                    }
                }
            } finally {
                localState.update { it.copy(isLoadingOlderMessages = false) }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            runCatching {
                chatRepository.clearChannelMessages(channelId)
            }.onSuccess {
                localState.update { it.copy(retriedMessageIds = emptySet()) }
                _effects.emit(ChatUiEffect.ShowSnackbar("Local history cleared"))
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _effects.emit(
                    ChatUiEffect.ShowSnackbar(
                        friendlyErrorMessage(err, "Could not clear local history.")
                    )
                )
            }
        }
    }

    fun leaveChannel() {
        viewModelScope.launch {
            chatRepository.leaveChannel(channelId)
                .onSuccess { _effects.emit(ChatUiEffect.NavigateBack) }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(
                        ChatUiEffect.ShowSnackbar(
                            friendlyErrorMessage(err, "Could not leave channel.")
                        )
                    )
                }
        }
    }

    override fun onCleared() {
        beatmapAudioPlayer.stop()
        chatRepository.setActiveChannel(null)
        super.onCleared()
    }

    private fun friendlyErrorMessage(err: Throwable?, fallback: String): String {
        if (err == null) return fallback
        if (err is OsuApiException || err is IllegalArgumentException) {
            return sanitizeError(err.message, fallback)
        }
        if (err is HttpException) {
            return when (err.code()) {
                401 -> "Session expired. Sign in again."
                403 -> "Access denied by osu!."
                404 -> "Not found on osu!."
                429 -> "Rate limited by osu!. Wait and retry."
                in 500..599 -> "osu! servers temporarily unavailable. Retry shortly."
                else -> fallback
            }
        }
        if (err is SocketTimeoutException) {
            return "Request timed out. Check connection and retry."
        }
        if (err is UnknownHostException || err is ConnectException) {
            return "Cannot reach osu! servers. Check internet connection."
        }
        if (err is SSLException) {
            return "Secure connection failed. Retry."
        }
        val raw = err.message?.trim().orEmpty()
        return when {
            raw.equals("IRC socket not connected", ignoreCase = true) ->
                "Not connected to Bancho. Reconnect and retry."
            raw.equals("Not logged in", ignoreCase = true) ->
                "Signed out. Sign in again to send messages."
            raw.equals("Channel not found", ignoreCase = true) ->
                "Channel unavailable."
            else -> sanitizeError(raw, fallback)
        }
    }

    private fun sanitizeError(raw: String?, fallback: String): String {
        if (raw.isNullOrBlank()) return fallback
        val trimmed = raw.trim()
        val looksTechnical = trimmed.contains("Exception", ignoreCase = true) ||
            trimmed.contains("java.") ||
            trimmed.contains("javax.") ||
            trimmed.contains("kotlin.") ||
            trimmed.contains("okhttp", ignoreCase = true) ||
            trimmed.contains("cronet", ignoreCase = true) ||
            trimmed.contains("retrofit", ignoreCase = true) ||
            trimmed.contains("http://", ignoreCase = true) ||
            trimmed.contains("https://", ignoreCase = true) ||
            trimmed.contains("\n") ||
            trimmed.length > 96
        if (!looksTechnical) return trimmed
        return when {
            trimmed.contains("timeout", ignoreCase = true) ||
                trimmed.contains("timed out", ignoreCase = true) ->
                "Request timed out. Check connection and retry."
            trimmed.contains("ssl", ignoreCase = true) ||
                trimmed.contains("tls", ignoreCase = true) ||
                trimmed.contains("certificate", ignoreCase = true) ->
                "Secure connection failed. Retry."
            trimmed.contains("resolve host", ignoreCase = true) ||
                trimmed.contains("connect", ignoreCase = true) ||
                trimmed.contains("network", ignoreCase = true) ->
                "Cannot reach osu! servers. Check internet connection."
            else -> fallback
        }
    }
}
