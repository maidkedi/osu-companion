package com.osuchat.app.ui.screens.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.data.repository.BeatmapDownloadRepository
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.AudioQueueTrack
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapRecommendationPreset
import com.osuchat.app.domain.model.BeatmapSearchFilter
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.BookmarkedMessageItem
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChannelType
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.DailySessionSummary
import com.osuchat.app.domain.model.FriendContact
import com.osuchat.app.domain.model.FriendLeaderboardEntry
import com.osuchat.app.domain.model.FriendRelationStatus
import com.osuchat.app.domain.model.FriendScoreEventItem
import com.osuchat.app.domain.model.FriendsRankingSort
import com.osuchat.app.domain.model.GlobalMessageSearchHit
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MatchSetupRunConfig
import com.osuchat.app.domain.model.MatchSetupTemplate
import com.osuchat.app.domain.model.MpLobbyState
import com.osuchat.app.domain.model.MpMatchHistory
import com.osuchat.app.domain.model.MpPickBanMode
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.NewsAndSpotlightsFeed
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.OszDownloadState
import com.osuchat.app.domain.model.PlayLaterSortOption
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.PlayerRecommendationProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.model.RecommendedBeatmapItem
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.domain.model.SavedBeatmapItem
import com.osuchat.app.domain.model.SeriesRoundRecord
import com.osuchat.app.domain.model.matchesNormalizedChannelSet
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.service.BeatmapAudioPlayer
import com.osuchat.app.util.AppShortcutManager
import com.osuchat.app.util.BeatmapRecommendationEngine
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.MappoolPickBanResolver
import com.osuchat.app.util.MpLobbyParser
import com.osuchat.app.util.RankPpMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.SSLException

enum class CompanionTab(val label: String) {
    CHATS("Chats"),
    FRIENDS("Friends"),
    MULTIPLAYER("Multiplayer"),
    EXPLORE("Explore")
}

enum class ChatFilterTab(val label: String) {
    ALL("All"),
    DIRECT("Direct"),
    PUBLIC("Channels"),
    UNREAD("Unread")
}

enum class ChatSearchScope(val label: String) {
    CHANNELS("Channels"),
    ALL_MESSAGES("All Messages")
}

enum class FriendsFilterTab(val label: String) {
    ALL("All"),
    ONLINE("Online"),
    MUTUAL("Mutual"),
    TRACKED_PP("Tracked PP"),
    RANKING("Ranking"),
    SCORE_FEED("Score Feed")
}

private data class NavLocalState(
    val activeTab: CompanionTab = CompanionTab.CHATS,
    val chatSearchQuery: String = "",
    val chatSearchScope: ChatSearchScope = ChatSearchScope.CHANNELS,
    val globalMessageSearchHits: List<GlobalMessageSearchHit> = emptyList(),
    val isGlobalMessageSearchLoading: Boolean = false,
    val friendSearchQuery: String = "",
    val filterTab: ChatFilterTab = ChatFilterTab.ALL,
    val friendsFilterTab: FriendsFilterTab = FriendsFilterTab.ALL,
    val friendsRankingMode: String = "osu",
    val friendsRankingSort: FriendsRankingSort = FriendsRankingSort.PP,
    val isFriendsRankingLoading: Boolean = false,
    val friendsModeProfiles: Map<String, PlayerProfile> = emptyMap(),
    val selfRankingProfile: PlayerProfile? = null,
    val selectedProfileTarget: String? = null,
    val selectedProfile: PlayerProfile? = null,
    val selectedProfileMode: String = "osu",
    val selectedProfilePinnedScores: List<OsuScoreItem> = emptyList(),
    val selectedProfileBestScores: List<OsuScoreItem> = emptyList(),
    val selectedProfileRecentScores: List<OsuScoreItem> = emptyList(),
    val isProfileLoading: Boolean = false,
    val isProfileScoresLoading: Boolean = false,
    val isLoadingMoreSelectedBestScores: Boolean = false,
    val canLoadMoreSelectedBestScores: Boolean = false,
    val selectedProfileError: String? = null,
    val selectedProfileLocalHistory: List<ProfileHistoryPoint> = emptyList(),
    val selectedProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    val isSelectedProfileOsuTrackLoading: Boolean = false,
    val isPpCheckRunning: Boolean = false,
    val isScoreFeedRefreshing: Boolean = false,
    val isFriendsLoading: Boolean = false,
    val friendsError: String? = null,
    val isRefreshingConnection: Boolean = false,
    val pendingShareText: String? = null,
    val pendingShareScore: OsuScoreItem? = null,
    val bookmarkedMessages: List<BookmarkedMessageItem> = emptyList(),
    val actionError: String? = null,
    val actionMessage: String? = null,
    val pendingOpenMatchWizard: Boolean = false
)

private data class MpLocalState(
    val selectedMpChannelId: String? = null,
    val matchHistory: MpMatchHistory? = null,
    val isHistoryLoading: Boolean = false,
    val matchHistoryError: String? = null,
    val isBusy: Boolean = false,
    val isWizardRunning: Boolean = false,
    val wizardStepMessage: String? = null,
    val wizardError: String? = null
)

private data class ExploreLocalState(
    val subTab: ExploreSubTab = ExploreSubTab.BEATMAPS,
    val beatmapFilter: BeatmapSearchFilter = BeatmapSearchFilter(),
    val beatmapResults: List<BeatmapSetSearchResult> = emptyList(),
    val isBeatmapsLoading: Boolean = false,
    val hasSearchedBeatmaps: Boolean = false,
    val beatmapsError: String? = null,
    val isForYouMode: Boolean = false,
    val recommendationPreset: BeatmapRecommendationPreset = BeatmapRecommendationPreset.FOR_YOU,
    val recommendationProfile: PlayerRecommendationProfile = PlayerRecommendationProfile(),
    val recommendedBeatmaps: List<RecommendedBeatmapItem> = emptyList(),
    val isRecommendationsLoading: Boolean = false,
    val recommendationsError: String? = null,
    val rankingMode: String = "osu",
    val rankingCountry: String = "",
    val playerSearchQuery: String = "",
    val isPlayerSearchActive: Boolean = false,
    val players: List<PlayerProfile> = emptyList(),
    val isPlayersLoading: Boolean = false,
    val playersError: String? = null,
    val selfProfile: PlayerProfile? = null,
    val selfProfileMode: String = "osu",
    val selfPinnedScores: List<OsuScoreItem> = emptyList(),
    val selfBestScores: List<OsuScoreItem> = emptyList(),
    val selfRecentScores: List<OsuScoreItem> = emptyList(),
    val isLoadingSelfProfile: Boolean = false,
    val isLoadingSelfScores: Boolean = false,
    val isLoadingMoreSelfBestScores: Boolean = false,
    val canLoadMoreSelfBestScores: Boolean = false,
    val selfProfileError: String? = null,
    val selfProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    val isSelfProfileOsuTrackLoading: Boolean = false,
    val selfDailySession: DailySessionSummary? = null,
    val isLoadingDailySession: Boolean = false,
    val rankPpMode: String = "osu",
    val isConvertingRankPp: Boolean = false,
    val rankPpResult: RankPpConversionResult? = null,
    val rankPpError: String? = null,
    val isSendingQueueToInGame: Boolean = false,
    val playLaterSelectedTag: String? = null,
    val playLaterSortOption: PlayLaterSortOption = PlayLaterSortOption.SAVED_DATE,
    val playLaterSortDescending: Boolean = true,
    val newsFeed: NewsAndSpotlightsFeed = NewsAndSpotlightsFeed(),
    val isNewsLoading: Boolean = false,
    val newsError: String? = null
)

@Immutable
data class HomeUiState(
    val activeTab: CompanionTab = CompanionTab.CHATS,
    val allChannels: List<ChatChannel> = emptyList(),
    val channels: List<ChatChannel> = emptyList(),
    val mpChannels: List<ChatChannel> = emptyList(),
    val activeMpLobby: MpLobbyState? = null,
    val mappools: List<Mappool> = emptyList(),
    val activeMpSeriesState: MatchSeriesState = MatchSeriesState(),
    val refereeMacros: List<RefereeMacro> = RefereeMacro.DEFAULT_MACROS,
    val matchSetupTemplates: List<MatchSetupTemplate> = MatchSetupTemplate.DEFAULT_TEMPLATES,
    val isWizardRunning: Boolean = false,
    val wizardStepMessage: String? = null,
    val wizardError: String? = null,
    val activeMpMessages: List<ChatMessage> = emptyList(),
    val activeMpMatchHistory: MpMatchHistory? = null,
    val isMpHistoryLoading: Boolean = false,
    val activeMpMatchHistoryError: String? = null,
    val isMpActionBusy: Boolean = false,
    val allFriendsCount: Int = 0,
    val mutualFriendsCount: Int = 0,
    val trackedFriendsCount: Int = 0,
    val friends: List<FriendContact> = emptyList(),
    val onlineFriends: List<FriendContact> = emptyList(),
    val friendsRankingMode: String = "osu",
    val friendsRankingSort: FriendsRankingSort = FriendsRankingSort.PP,
    val isFriendsRankingLoading: Boolean = false,
    val friendsLeaderboard: List<FriendLeaderboardEntry> = emptyList(),
    val isFriendsLoading: Boolean = false,
    val friendsError: String? = null,
    val filterTab: ChatFilterTab = ChatFilterTab.ALL,
    val friendsFilterTab: FriendsFilterTab = FriendsFilterTab.ALL,
    val friendScoreEvents: List<FriendScoreEventItem> = emptyList(),
    val isScoreFeedRefreshing: Boolean = false,
    val chatSearchQuery: String = "",
    val chatSearchScope: ChatSearchScope = ChatSearchScope.CHANNELS,
    val globalMessageSearchHits: List<GlobalMessageSearchHit> = emptyList(),
    val isGlobalMessageSearchLoading: Boolean = false,
    val friendSearchQuery: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.OFFLINE,
    val connectionError: String? = null,
    val isRefreshingConnection: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val unreadTotal: Int = 0,
    val hasDotOnlyUnread: Boolean = false,
    val mutedChannels: Set<String> = emptySet(),
    val dotOnlyChannels: Set<String> = emptySet(),
    val selectedProfile: PlayerProfile? = null,
    val selectedProfileMode: String = "osu",
    val selectedProfilePinnedScores: List<OsuScoreItem> = emptyList(),
    val selectedProfileBestScores: List<OsuScoreItem> = emptyList(),
    val selectedProfileRecentScores: List<OsuScoreItem> = emptyList(),
    val isProfileLoading: Boolean = false,
    val isProfileScoresLoading: Boolean = false,
    val isLoadingMoreSelectedBestScores: Boolean = false,
    val canLoadMoreSelectedBestScores: Boolean = false,
    val selectedProfileError: String? = null,
    val selectedProfileLocalHistory: List<ProfileHistoryPoint> = emptyList(),
    val selectedProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    val isSelectedProfileOsuTrackLoading: Boolean = false,
    val isPpCheckRunning: Boolean = false,
    val exploreSubTab: ExploreSubTab = ExploreSubTab.BEATMAPS,
    val beatmapFilter: BeatmapSearchFilter = BeatmapSearchFilter(),
    val beatmapResults: List<BeatmapSetSearchResult> = emptyList(),
    val isBeatmapsLoading: Boolean = false,
    val hasSearchedBeatmaps: Boolean = false,
    val beatmapsError: String? = null,
    val isForYouMode: Boolean = false,
    val recommendationPreset: BeatmapRecommendationPreset = BeatmapRecommendationPreset.FOR_YOU,
    val recommendationProfile: PlayerRecommendationProfile = PlayerRecommendationProfile(),
    val recommendedBeatmaps: List<RecommendedBeatmapItem> = emptyList(),
    val isRecommendationsLoading: Boolean = false,
    val recommendationsError: String? = null,
    val audioState: AudioPlaybackState = AudioPlaybackState(),
    val rankingMode: String = "osu",
    val rankingCountry: String = "",
    val playerSearchQuery: String = "",
    val isPlayerSearchActive: Boolean = false,
    val players: List<PlayerProfile> = emptyList(),
    val isPlayersLoading: Boolean = false,
    val playersError: String? = null,
    val selfProfile: PlayerProfile? = null,
    val selfProfileMode: String = "osu",
    val selfPinnedScores: List<OsuScoreItem> = emptyList(),
    val selfBestScores: List<OsuScoreItem> = emptyList(),
    val selfRecentScores: List<OsuScoreItem> = emptyList(),
    val isLoadingSelfProfile: Boolean = false,
    val isLoadingSelfScores: Boolean = false,
    val isLoadingMoreSelfBestScores: Boolean = false,
    val canLoadMoreSelfBestScores: Boolean = false,
    val selfProfileError: String? = null,
    val selfProfileHistory: List<ProfileHistoryPoint> = emptyList(),
    val selfProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    val isSelfProfileOsuTrackLoading: Boolean = false,
    val selfDailySession: DailySessionSummary? = null,
    val isLoadingDailySession: Boolean = false,
    val savedBeatmaps: List<SavedBeatmapItem> = emptyList(),
    val filteredSavedBeatmaps: List<SavedBeatmapItem> = emptyList(),
    val availablePlayLaterTags: List<String> = emptyList(),
    val playLaterSelectedTag: String? = null,
    val playLaterSortOption: PlayLaterSortOption = PlayLaterSortOption.SAVED_DATE,
    val playLaterSortDescending: Boolean = true,
    val savedBeatmapIds: Set<Long> = emptySet(),
    val isSendingQueueToInGame: Boolean = false,
    val oszDownloadStates: Map<Long, OszDownloadState> = emptyMap(),
    val rankPpMode: String = "osu",
    val isConvertingRankPp: Boolean = false,
    val rankPpResult: RankPpConversionResult? = null,
    val rankPpError: String? = null,
    val newsFeed: NewsAndSpotlightsFeed = NewsAndSpotlightsFeed(),
    val isNewsLoading: Boolean = false,
    val newsError: String? = null,
    val pendingShareText: String? = null,
    val pendingShareScore: OsuScoreItem? = null,
    val bookmarkedMessages: List<BookmarkedMessageItem> = emptyList(),
    val trackedAlertConfigs: Map<String, TrackedPlayerAlertConfig> = emptyMap(),
    val actionError: String? = null,
    val actionMessage: String? = null,
    val pendingOpenMatchWizard: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val beatmapAudioPlayer: BeatmapAudioPlayer,
    private val beatmapDownloadRepository: BeatmapDownloadRepository,
    private val appShortcutManager: AppShortcutManager
) : ViewModel() {

    private val navLocal = MutableStateFlow(NavLocalState())
    private val mpLocal = MutableStateFlow(MpLocalState())
    private val exploreLocal = MutableStateFlow(ExploreLocalState())

    private var profileLoadJob: Job? = null
    private var profileRequestSeq = 0L
    private var selectedOsuTrackJob: Job? = null
    private var selectedOsuTrackSeq = 0L
    private var selectedMoreScoresJob: Job? = null
    private var selfProfileJob: Job? = null
    private var selfProfileRequestSeq = 0L
    private var selfOsuTrackJob: Job? = null
    private var selfOsuTrackSeq = 0L
    private var selfMoreScoresJob: Job? = null
    private var playersJob: Job? = null
    private var playersRequestSeq = 0L
    private var rankPpJob: Job? = null
    private var rankPpRequestSeq = 0L
    private var beatmapsJob: Job? = null
    private var beatmapsRequestSeq = 0L
    private var newsJob: Job? = null
    private var newsRequestSeq = 0L
    private var recommendationJob: Job? = null
    private var recommendationsRequestSeq = 0L
    private var dailySessionJob: Job? = null
    private var dailySessionRequestSeq = 0L
    private var friendsRankingJob: Job? = null
    private var friendsRankingRequestSeq = 0L
    private var globalSearchJob: Job? = null
    private var matchSetupWizardJob: Job? = null
    private var lastSelfProfileNavNs = 0L
    private var cachedSelfDefaultMode: String? = null

    private data class MpObservedTuple(
        val lobby: MpLobbyState?,
        val messages: List<ChatMessage>,
        val seriesState: MatchSeriesState
    )

    private val mpLobbyAndMessagesFlow: Flow<MpObservedTuple> = combine(
        mpLocal.map { it.selectedMpChannelId }.distinctUntilChanged(),
        chatRepository.channels
    ) { selectedId, allChannels ->
        val mpChannels = allChannels.filter { it.id.startsWith("#mp_", ignoreCase = true) }
        mpChannels.firstOrNull { it.id.equals(selectedId, ignoreCase = true) }
            ?: mpChannels.firstOrNull()
    }.distinctUntilChanged().flatMapLatest { channel ->
        if (channel == null) {
            flowOf(MpObservedTuple(null, emptyList(), MatchSeriesState()))
        } else {
            combine(
                chatRepository.observeMessages(channel.id),
                chatRepository.observeMatchSeriesState(channel.id)
            ) { msgs, series ->
                val lobby = MpLobbyParser.parseLobbyState(channel.id, channel.name, msgs)
                val displaySeries = if (!series.isCustomTeamNames) {
                    val extracted = MpLobbyParser.extractTournamentTeams(lobby.roomName)
                    if (extracted != null) {
                        series.copy(redTeamName = extracted.first, blueTeamName = extracted.second)
                    } else {
                        series
                    }
                } else {
                    series
                }
                MpObservedTuple(lobby, msgs, displaySeries)
            }
        }
    }

    private val selfProfileHistoryFlow: Flow<List<ProfileHistoryPoint>> = exploreLocal
        .map { it.selfProfileMode }
        .distinctUntilChanged()
        .flatMapLatest { mode ->
            chatRepository.observeProfileHistory(mode, limitDays = 90)
        }

    val uiState: StateFlow<HomeUiState> = combine(
        chatRepository.channels,
        chatRepository.friends,
        chatRepository.connectionStatus,
        chatRepository.connectionError,
        chatRepository.settings,
        navLocal,
        mpLocal,
        mpLobbyAndMessagesFlow,
        exploreLocal,
        beatmapAudioPlayer.state,
        chatRepository.savedBeatmaps,
        chatRepository.mappools,
        chatRepository.refereeMacros,
        beatmapDownloadRepository.downloadStates,
        chatRepository.bookmarkedMessages,
        chatRepository.friendScoreEvents,
        selfProfileHistoryFlow,
        chatRepository.mutedChannels,
        chatRepository.dotOnlyChannels,
        chatRepository.matchSetupTemplates,
        chatRepository.trackedAlertConfigs
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allChannels = values[0] as List<ChatChannel>
        @Suppress("UNCHECKED_CAST")
        val allFriends = values[1] as List<FriendContact>
        val status = values[2] as ConnectionStatus
        val connError = values[3] as String?
        val settings = values[4] as AppSettings
        val nav = values[5] as NavLocalState
        val mp = values[6] as MpLocalState
        val mpTuple = values[7] as MpObservedTuple
        val exp = values[8] as ExploreLocalState
        val audio = values[9] as AudioPlaybackState
        @Suppress("UNCHECKED_CAST")
        val savedBeatmaps = values[10] as List<SavedBeatmapItem>
        @Suppress("UNCHECKED_CAST")
        val mappools = values[11] as List<Mappool>
        @Suppress("UNCHECKED_CAST")
        val refereeMacros = values[12] as List<RefereeMacro>
        @Suppress("UNCHECKED_CAST")
        val oszDownloadStates = values[13] as Map<Long, OszDownloadState>
        @Suppress("UNCHECKED_CAST")
        val bookmarkedMessages = values[14] as List<BookmarkedMessageItem>
        @Suppress("UNCHECKED_CAST")
        val allScoreEvents = values[15] as List<FriendScoreEventItem>
        @Suppress("UNCHECKED_CAST")
        val selfProfileHistory = values[16] as List<ProfileHistoryPoint>
        @Suppress("UNCHECKED_CAST")
        val mutedSet = values[17] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val dotOnlySet = values[18] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val matchSetupTemplates = values[19] as List<MatchSetupTemplate>
        @Suppress("UNCHECKED_CAST")
        val trackedAlertConfigs = values[20] as Map<String, TrackedPlayerAlertConfig>

        val availablePlayLaterTags = savedBeatmaps
            .flatMap { it.tags }
            .distinctBy { it.lowercase(Locale.US) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val activePlayLaterTag = exp.playLaterSelectedTag?.takeIf { selected ->
            availablePlayLaterTags.any { it.equals(selected, ignoreCase = true) }
        }
        val filteredSaved = savedBeatmaps.filter { item ->
            activePlayLaterTag == null || item.tags.any { it.equals(activePlayLaterTag, ignoreCase = true) }
        }
        val sortedSavedBeatmaps = when (exp.playLaterSortOption) {
            PlayLaterSortOption.SAVED_DATE ->
                if (exp.playLaterSortDescending) filteredSaved.sortedByDescending { it.savedAt }
                else filteredSaved.sortedBy { it.savedAt }
            PlayLaterSortOption.STAR_RATING ->
                if (exp.playLaterSortDescending) filteredSaved.sortedByDescending { it.starRating }
                else filteredSaved.sortedBy { it.starRating }
            PlayLaterSortOption.BPM ->
                if (exp.playLaterSortDescending) filteredSaved.sortedByDescending { it.bpm }
                else filteredSaved.sortedBy { it.bpm }
            PlayLaterSortOption.DURATION ->
                if (exp.playLaterSortDescending) filteredSaved.sortedByDescending { it.totalLengthSeconds }
                else filteredSaved.sortedBy { it.totalLengthSeconds }
        }

        val chatQuery = nav.chatSearchQuery.trim()
        val filteredChannels = allChannels.filter { ch ->
            val tabMatches = when (nav.filterTab) {
                ChatFilterTab.ALL -> true
                ChatFilterTab.DIRECT -> ch.type == ChannelType.DIRECT
                ChatFilterTab.PUBLIC -> ch.type == ChannelType.PUBLIC
                ChatFilterTab.UNREAD -> ch.unreadCount > 0
            }
            val queryMatches = chatQuery.isBlank() ||
                ch.name.contains(chatQuery, ignoreCase = true) ||
                ch.customAlias?.contains(chatQuery, ignoreCase = true) == true ||
                ch.lastMessageText?.contains(chatQuery, ignoreCase = true) == true
            tabMatches && queryMatches
        }

        val friendQuery = nav.friendSearchQuery.trim()
        val uniqueFriends = allFriends.distinctBy { it.username.lowercase() }
        val filteredFriends = uniqueFriends.filter { f ->
            val tabMatches = when (nav.friendsFilterTab) {
                FriendsFilterTab.ONLINE -> f.isOnline
                FriendsFilterTab.MUTUAL -> f.isMutual
                FriendsFilterTab.TRACKED_PP -> f.isTrackedForPp
                FriendsFilterTab.ALL, FriendsFilterTab.RANKING, FriendsFilterTab.SCORE_FEED -> true
            }
            val queryMatches = friendQuery.isBlank() ||
                f.username.contains(friendQuery, ignoreCase = true) ||
                f.customAlias?.contains(friendQuery, ignoreCase = true) == true ||
                f.statusText.contains(friendQuery, ignoreCase = true)
            tabMatches && queryMatches
        }
        val friendsLeaderboard = computeFriendsLeaderboard(
            uniqueFriends = uniqueFriends,
            selfProfile = nav.selfRankingProfile?.takeIf { it.mode.equals(nav.friendsRankingMode, true) }
                ?: exp.selfProfile?.takeIf { it.mode.equals(nav.friendsRankingMode, true) },
            settings = settings,
            mode = nav.friendsRankingMode,
            sort = nav.friendsRankingSort,
            modeProfiles = nav.friendsModeProfiles
        )
        val filteredScoreEvents = if (friendQuery.isBlank()) {
            allScoreEvents
        } else {
            allScoreEvents.filter { ev ->
                ev.username.contains(friendQuery, ignoreCase = true) ||
                    ev.customAlias?.contains(friendQuery, ignoreCase = true) == true ||
                    ev.beatmapTitle.contains(friendQuery, ignoreCase = true) ||
                    ev.beatmapVersion.contains(friendQuery, ignoreCase = true)
            }
        }

        val mpChannels = allChannels.filter { it.id.startsWith("#mp_", ignoreCase = true) }

        fun isChannelSuppressed(ch: ChatChannel): Boolean =
            ch.notificationMode == ChannelNotificationMode.MUTED ||
                ch.matchesNormalizedChannelSet(mutedSet) ||
                ch.matchesNormalizedChannelSet(dotOnlySet)

        val numericUnreadTotal = allChannels
            .filterNot(::isChannelSuppressed)
            .sumOf { it.unreadCount }
        val hasDotOnlyUnread = allChannels.any { isChannelSuppressed(it) && it.unreadCount > 0 }

        HomeUiState(
            activeTab = nav.activeTab,
            allChannels = allChannels,
            channels = filteredChannels,
            mpChannels = mpChannels,
            activeMpLobby = mpTuple.lobby,
            mappools = mappools,
            activeMpSeriesState = mpTuple.seriesState,
            refereeMacros = refereeMacros,
            matchSetupTemplates = matchSetupTemplates,
            isWizardRunning = mp.isWizardRunning,
            wizardStepMessage = mp.wizardStepMessage,
            wizardError = mp.wizardError,
            activeMpMessages = mpTuple.messages,
            activeMpMatchHistory = mp.matchHistory,
            isMpHistoryLoading = mp.isHistoryLoading,
            activeMpMatchHistoryError = mp.matchHistoryError,
            isMpActionBusy = mp.isBusy,
            allFriendsCount = uniqueFriends.size,
            mutualFriendsCount = uniqueFriends.count { it.isMutual },
            trackedFriendsCount = uniqueFriends.count { it.isTrackedForPp },
            friends = filteredFriends,
            onlineFriends = uniqueFriends.filter { it.isOnline },
            friendsRankingMode = nav.friendsRankingMode,
            friendsRankingSort = nav.friendsRankingSort,
            isFriendsRankingLoading = nav.isFriendsRankingLoading,
            friendsLeaderboard = friendsLeaderboard,
            isFriendsLoading = nav.isFriendsLoading,
            friendsError = nav.friendsError,
            filterTab = nav.filterTab,
            friendsFilterTab = nav.friendsFilterTab,
            friendScoreEvents = filteredScoreEvents,
            isScoreFeedRefreshing = nav.isScoreFeedRefreshing,
            chatSearchQuery = nav.chatSearchQuery,
            chatSearchScope = nav.chatSearchScope,
            globalMessageSearchHits = nav.globalMessageSearchHits,
            isGlobalMessageSearchLoading = nav.isGlobalMessageSearchLoading,
            friendSearchQuery = nav.friendSearchQuery,
            connectionStatus = status,
            connectionError = connError?.let { sanitizeRawText(it, "Connection error.") },
            isRefreshingConnection = nav.isRefreshingConnection,
            settings = settings,
            unreadTotal = numericUnreadTotal,
            hasDotOnlyUnread = hasDotOnlyUnread,
            mutedChannels = mutedSet,
            dotOnlyChannels = dotOnlySet,
            selectedProfile = nav.selectedProfile,
            selectedProfileMode = nav.selectedProfileMode,
            selectedProfilePinnedScores = nav.selectedProfilePinnedScores,
            selectedProfileBestScores = nav.selectedProfileBestScores,
            selectedProfileRecentScores = nav.selectedProfileRecentScores,
            isProfileLoading = nav.isProfileLoading,
            isProfileScoresLoading = nav.isProfileScoresLoading,
            isLoadingMoreSelectedBestScores = nav.isLoadingMoreSelectedBestScores,
            canLoadMoreSelectedBestScores = nav.canLoadMoreSelectedBestScores,
            selectedProfileError = nav.selectedProfileError,
            selectedProfileLocalHistory = nav.selectedProfileLocalHistory,
            selectedProfileOsuTrack = nav.selectedProfileOsuTrack,
            isSelectedProfileOsuTrackLoading = nav.isSelectedProfileOsuTrackLoading,
            isPpCheckRunning = nav.isPpCheckRunning,
            exploreSubTab = exp.subTab,
            beatmapFilter = exp.beatmapFilter,
            beatmapResults = exp.beatmapResults,
            isBeatmapsLoading = exp.isBeatmapsLoading,
            hasSearchedBeatmaps = exp.hasSearchedBeatmaps,
            beatmapsError = exp.beatmapsError,
            isForYouMode = exp.isForYouMode,
            recommendationPreset = exp.recommendationPreset,
            recommendationProfile = exp.recommendationProfile,
            recommendedBeatmaps = exp.recommendedBeatmaps,
            isRecommendationsLoading = exp.isRecommendationsLoading,
            recommendationsError = exp.recommendationsError,
            audioState = audio,
            rankingMode = exp.rankingMode,
            rankingCountry = exp.rankingCountry,
            playerSearchQuery = exp.playerSearchQuery,
            isPlayerSearchActive = exp.isPlayerSearchActive,
            players = exp.players,
            isPlayersLoading = exp.isPlayersLoading,
            playersError = exp.playersError,
            selfProfile = exp.selfProfile,
            selfProfileMode = exp.selfProfileMode,
            selfPinnedScores = exp.selfPinnedScores,
            selfBestScores = exp.selfBestScores,
            selfRecentScores = exp.selfRecentScores,
            isLoadingSelfProfile = exp.isLoadingSelfProfile,
            isLoadingSelfScores = exp.isLoadingSelfScores,
            isLoadingMoreSelfBestScores = exp.isLoadingMoreSelfBestScores,
            canLoadMoreSelfBestScores = exp.canLoadMoreSelfBestScores,
            selfProfileError = exp.selfProfileError,
            selfProfileHistory = selfProfileHistory,
            selfProfileOsuTrack = exp.selfProfileOsuTrack,
            isSelfProfileOsuTrackLoading = exp.isSelfProfileOsuTrackLoading,
            selfDailySession = exp.selfDailySession,
            isLoadingDailySession = exp.isLoadingDailySession,
            savedBeatmaps = savedBeatmaps,
            filteredSavedBeatmaps = sortedSavedBeatmaps,
            availablePlayLaterTags = availablePlayLaterTags,
            playLaterSelectedTag = activePlayLaterTag,
            playLaterSortOption = exp.playLaterSortOption,
            playLaterSortDescending = exp.playLaterSortDescending,
            savedBeatmapIds = savedBeatmaps.map { it.beatmapId }.toSet(),
            isSendingQueueToInGame = exp.isSendingQueueToInGame,
            oszDownloadStates = oszDownloadStates,
            rankPpMode = exp.rankPpMode,
            isConvertingRankPp = exp.isConvertingRankPp,
            rankPpResult = exp.rankPpResult,
            rankPpError = exp.rankPpError,
            newsFeed = exp.newsFeed,
            isNewsLoading = exp.isNewsLoading,
            newsError = exp.newsError,
            pendingShareText = nav.pendingShareText,
            pendingShareScore = nav.pendingShareScore,
            bookmarkedMessages = bookmarkedMessages,
            trackedAlertConfigs = trackedAlertConfigs,
            actionError = nav.actionError,
            actionMessage = nav.actionMessage,
            pendingOpenMatchWizard = nav.pendingOpenMatchWizard
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshFriends()
        viewModelScope.launch {
            navLocal
                .map { it.chatSearchQuery.trim() to it.chatSearchScope }
                .distinctUntilChanged()
                .collect { (query, scope) ->
                    globalSearchJob?.cancel()
                    if (scope == ChatSearchScope.ALL_MESSAGES && query.length >= 2) {
                        navLocal.update { it.copy(isGlobalMessageSearchLoading = true) }
                        globalSearchJob = viewModelScope.launch {
                            delay(250L)
                            val hits = chatRepository.searchAllMessages(query, 100)
                            navLocal.update {
                                it.copy(
                                    globalMessageSearchHits = hits,
                                    isGlobalMessageSearchLoading = false
                                )
                            }
                        }
                    } else {
                        navLocal.update {
                            it.copy(
                                globalMessageSearchHits = emptyList(),
                                isGlobalMessageSearchLoading = false
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            mpLobbyAndMessagesFlow
                .map { it.lobby }
                .distinctUntilChanged()
                .collect { lobby ->
                    if (lobby != null) {
                        syncActiveMpAutoScoring(lobby)
                    }
                }
        }
        viewModelScope.launch {
            chatRepository.createdMpChannelEvents.collect { newMpChannelId ->
                mpLocal.update { it.copy(selectedMpChannelId = newMpChannelId) }
                navLocal.update {
                    it.copy(
                        activeTab = CompanionTab.MULTIPLAYER,
                        actionMessage = "Joined multiplayer lobby $newMpChannelId"
                    )
                }
            }
        }
        viewModelScope.launch {
            chatRepository.settings
                .map { it.userId to it.username }
                .distinctUntilChanged()
                .collect { (uid, uname) ->
                    if (uid > 0L || uname.isNotBlank()) {
                        if (exploreLocal.value.selfProfile == null && !exploreLocal.value.isLoadingSelfProfile) {
                            loadSelfProfile(mode = null, forceRefresh = false)
                        }
                    } else {
                        selfProfileJob?.cancel()
                        selfOsuTrackJob?.cancel()
                        selfMoreScoresJob?.cancel()
                        selfProfileRequestSeq++
                        selfOsuTrackSeq++
                        cachedSelfDefaultMode = null
                        exploreLocal.update {
                            it.copy(
                                selfProfile = null,
                                selfProfileMode = "osu",
                                isLoadingSelfProfile = false,
                                isLoadingSelfScores = false,
                                isLoadingMoreSelfBestScores = false,
                                canLoadMoreSelfBestScores = false,
                                selfProfileError = null,
                                selfProfileOsuTrack = emptyList(),
                                isSelfProfileOsuTrackLoading = false,
                                selfPinnedScores = emptyList(),
                                selfBestScores = emptyList(),
                                selfRecentScores = emptyList()
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(
                chatRepository.settings
                    .map { s ->
                        s.authMode == AuthMode.IRC ||
                            (s.authMode == AuthMode.OAUTH_V2 && s.hasIrcPassword)
                    }
                    .distinctUntilChanged(),
                chatRepository.channels
            ) { isAuth, channels -> isAuth to channels }
                .collectLatest { (isAuth, channels) ->
                    delay(1_500L)
                    appShortcutManager.syncDynamicShortcuts(isAuth, channels)
                }
        }
    }

    fun onCompanionTabSelected(tab: CompanionTab) {
        navLocal.update { it.copy(activeTab = tab) }
        if (tab == CompanionTab.EXPLORE) {
            ensureExploreSubTabLoaded(exploreLocal.value.subTab)
        }
    }

    fun onChatSearchQueryChange(query: String) {
        navLocal.update { it.copy(chatSearchQuery = query) }
    }

    fun onChatSearchScopeSelected(scope: ChatSearchScope) {
        navLocal.update { it.copy(chatSearchScope = scope) }
    }

    fun onFriendSearchQueryChange(query: String) {
        navLocal.update { it.copy(friendSearchQuery = query) }
    }

    fun resetChatSearchAndFilter() {
        navLocal.update {
            it.copy(
                chatSearchQuery = "",
                chatSearchScope = ChatSearchScope.CHANNELS,
                globalMessageSearchHits = emptyList(),
                isGlobalMessageSearchLoading = false,
                filterTab = ChatFilterTab.ALL
            )
        }
    }

    fun clearFriendSearch() {
        navLocal.update { it.copy(friendSearchQuery = "") }
    }

    fun onFilterTabSelected(tab: ChatFilterTab) {
        navLocal.update { it.copy(filterTab = tab) }
    }

    fun onFriendsFilterTabSelected(tab: FriendsFilterTab) {
        navLocal.update { it.copy(friendsFilterTab = tab) }
        if (tab == FriendsFilterTab.SCORE_FEED &&
            uiState.value.friendScoreEvents.isEmpty() &&
            !navLocal.value.isScoreFeedRefreshing
        ) {
            refreshFriendScoreFeed()
        } else if (tab == FriendsFilterTab.RANKING) {
            refreshFriendsRanking()
        }
    }

    fun selectFriendsRankingMode(mode: String) {
        val clean = mode.trim().lowercase().ifBlank { "osu" }
        navLocal.update { it.copy(friendsRankingMode = clean) }
        refreshFriendsRanking()
    }

    fun selectFriendsRankingSort(sort: FriendsRankingSort) {
        navLocal.update { it.copy(friendsRankingSort = sort) }
    }

    fun refreshFriendsRanking() {
        friendsRankingJob?.cancel()
        val requestSeq = ++friendsRankingRequestSeq
        val targetMode = navLocal.value.friendsRankingMode
        friendsRankingJob = viewModelScope.launch {
            navLocal.update { it.copy(isFriendsRankingLoading = true) }
            try {
                val settings = chatRepository.settings.first()
                if (settings.authMode != AuthMode.OAUTH_V2) return@launch

                val selfTarget = when {
                    settings.userId > 0L -> settings.userId.toString()
                    settings.username.isNotBlank() -> settings.username.trim()
                    else -> null
                }
                if (selfTarget != null) {
                    val existingSelf = navLocal.value.selfRankingProfile
                        ?.takeIf { it.mode.equals(targetMode, true) && it.pp != null }
                    if (existingSelf == null) {
                        val selfProf = if (targetMode == "osu") {
                            chatRepository.getCachedSelfProfile(false).getOrNull()
                                ?.takeIf { it.mode.equals(targetMode, true) }
                                ?: chatRepository.getPlayerProfile(selfTarget, targetMode).getOrNull()
                        } else {
                            chatRepository.getPlayerProfile(selfTarget, targetMode).getOrNull()
                        }
                        if (requestSeq != friendsRankingRequestSeq) return@launch
                        if (selfProf != null) {
                            navLocal.update { it.copy(selfRankingProfile = selfProf) }
                        }
                    }
                }

                val currentFriends = chatRepository.friends.first()
                val existingModeProfiles = navLocal.value.friendsModeProfiles
                val toFetch = currentFriends.filter { f ->
                    val cacheKey = "${BeatmapUrlParser.normalizeUsername(f.username)}:$targetMode"
                    val cached = existingModeProfiles[cacheKey]
                    cached == null && (!f.mode.equals(targetMode, true) || f.accuracy == null || f.pp == null)
                }.take(15)

                for ((index, friend) in toFetch.withIndex()) {
                    if (requestSeq != friendsRankingRequestSeq) return@launch
                    val fetched = chatRepository.getPlayerProfile(friend.username, targetMode).getOrNull()
                    if (fetched != null) {
                        val cacheKey = "${BeatmapUrlParser.normalizeUsername(friend.username)}:$targetMode"
                        navLocal.update { state ->
                            state.copy(friendsModeProfiles = state.friendsModeProfiles + (cacheKey to fetched))
                        }
                    }
                    if (index < toFetch.lastIndex) {
                        delay(320L)
                    }
                }
            } finally {
                if (requestSeq == friendsRankingRequestSeq) {
                    navLocal.update { it.copy(isFriendsRankingLoading = false) }
                }
            }
        }
    }

    private fun computeFriendsLeaderboard(
        uniqueFriends: List<FriendContact>,
        selfProfile: PlayerProfile?,
        settings: AppSettings,
        mode: String,
        sort: FriendsRankingSort,
        modeProfiles: Map<String, PlayerProfile>
    ): List<FriendLeaderboardEntry> {
        val rows = ArrayList<FriendLeaderboardEntry>(uniqueFriends.size + 1)
        val selfNorm = BeatmapUrlParser.normalizeUsername(selfProfile?.username ?: settings.username)

        if (selfProfile != null) {
            rows += FriendLeaderboardEntry(
                rankPosition = 0,
                userId = selfProfile.userId,
                username = selfProfile.username,
                displayName = "${selfProfile.username} (You)",
                avatarUrl = selfProfile.avatarUrl,
                countryCode = selfProfile.countryCode,
                globalRank = selfProfile.globalRank,
                pp = selfProfile.pp,
                accuracy = selfProfile.accuracy,
                isOnline = true,
                isMutual = false,
                isSelf = true
            )
        } else if (settings.username.isNotBlank()) {
            rows += FriendLeaderboardEntry(
                rankPosition = 0,
                userId = settings.userId,
                username = settings.username,
                displayName = "${settings.username} (You)",
                avatarUrl = settings.avatarUrl ?: if (settings.userId > 0L) "https://a.ppy.sh/${settings.userId}" else "",
                countryCode = "XX",
                globalRank = null,
                pp = null,
                accuracy = null,
                isOnline = true,
                isMutual = false,
                isSelf = true
            )
        }

        for (friend in uniqueFriends) {
            val norm = BeatmapUrlParser.normalizeUsername(friend.username)
            if (norm.isBlank() || norm == selfNorm) continue
            val modeProf = modeProfiles["$norm:$mode"]
            val useBase = modeProf == null && friend.mode.equals(mode, ignoreCase = true)
            rows += FriendLeaderboardEntry(
                rankPosition = 0,
                userId = friend.userId,
                username = friend.username,
                displayName = friend.displayName,
                avatarUrl = friend.avatarUrl,
                countryCode = modeProf?.countryCode ?: friend.countryCode,
                globalRank = modeProf?.globalRank ?: if (useBase) friend.globalRank else null,
                pp = modeProf?.pp ?: if (useBase) friend.pp else null,
                accuracy = modeProf?.accuracy ?: if (useBase) friend.accuracy else null,
                isOnline = friend.isOnline,
                isMutual = friend.isMutual,
                isSelf = false
            )
        }

        val comparator = when (sort) {
            FriendsRankingSort.PP -> compareByDescending<FriendLeaderboardEntry> {
                it.pp?.takeIf { p -> p.isFinite() && p > 0.0 } ?: -1.0
            }.thenBy {
                it.globalRank?.takeIf { r -> r > 0 } ?: Int.MAX_VALUE
            }.thenBy { it.username.lowercase() }

            FriendsRankingSort.GLOBAL_RANK -> compareBy<FriendLeaderboardEntry> {
                it.globalRank?.takeIf { r -> r > 0 } ?: Int.MAX_VALUE
            }.thenByDescending {
                it.pp?.takeIf { p -> p.isFinite() && p > 0.0 } ?: -1.0
            }.thenBy { it.username.lowercase() }

            FriendsRankingSort.ACCURACY -> compareByDescending<FriendLeaderboardEntry> {
                it.accuracy?.takeIf { a -> a.isFinite() && a > 0.0 } ?: -1.0
            }.thenByDescending {
                it.pp?.takeIf { p -> p.isFinite() && p > 0.0 } ?: -1.0
            }.thenBy { it.username.lowercase() }
        }

        return rows.sortedWith(comparator).mapIndexed { idx, entry ->
            entry.copy(rankPosition = idx + 1)
        }
    }

    fun openSelfProfileDestination() {
        val nowNs = System.nanoTime()
        if (nowNs - lastSelfProfileNavNs < 300_000_000L) return
        lastSelfProfileNavNs = nowNs
        dismissPlayerProfile()
        exploreLocal.update { it.copy(subTab = ExploreSubTab.MY_PROFILE) }
        navLocal.update { it.copy(activeTab = CompanionTab.EXPLORE) }
        ensureExploreSubTabLoaded(ExploreSubTab.MY_PROFILE)
    }

    fun openRankingsDestination() {
        exploreLocal.update { it.copy(subTab = ExploreSubTab.RANKINGS) }
        navLocal.update { it.copy(activeTab = CompanionTab.EXPLORE) }
        ensureExploreSubTabLoaded(ExploreSubTab.RANKINGS)
    }

    fun openBeatmapSearchDestination() {
        exploreLocal.update { it.copy(subTab = ExploreSubTab.BEATMAPS, isForYouMode = false) }
        navLocal.update { it.copy(activeTab = CompanionTab.EXPLORE) }
        ensureExploreSubTabLoaded(ExploreSubTab.BEATMAPS)
    }

    fun openMpSetupWizardDestination() {
        navLocal.update {
            it.copy(
                activeTab = CompanionTab.MULTIPLAYER,
                pendingOpenMatchWizard = true
            )
        }
    }

    fun consumePendingOpenMatchWizard() {
        navLocal.update { it.copy(pendingOpenMatchWizard = false) }
    }

    fun refreshFriends() {
        viewModelScope.launch {
            navLocal.update { it.copy(isFriendsLoading = true, friendsError = null) }
            chatRepository.refreshFriends()
                .onFailure { err ->
                    navLocal.update {
                        it.copy(friendsError = sanitizeErrorMessage(err, "Could not sync friends list."))
                    }
                }
            navLocal.update { it.copy(isFriendsLoading = false) }
        }
    }

    fun openFriendDmFast(friend: FriendContact, onChannelReady: (String) -> Unit) {
        val clean = friend.username.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            navLocal.update { it.copy(actionError = null) }
            chatRepository.openFriendDmFast(
                username = clean,
                userId = friend.userId.takeIf { it > 0L },
                avatarUrl = friend.avatarUrl.takeIf { it.isNotBlank() }
            )
                .onSuccess { onChannelReady(it.id) }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not open DM.")) }
                }
        }
    }

    fun togglePinChannel(channel: ChatChannel) {
        val nextPinned = !channel.isPinned
        viewModelScope.launch {
            chatRepository.setChannelPinned(channel.id, nextPinned)
                .onSuccess {
                    navLocal.update {
                        it.copy(actionMessage = if (nextPinned) "Pinned ${channel.displayTitle}" else "Unpinned ${channel.displayTitle}")
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update pin.")) }
                }
        }
    }

    fun setChannelNotificationMode(channel: ChatChannel, mode: ChannelNotificationMode) {
        viewModelScope.launch {
            chatRepository.setChannelNotificationMode(channel.id, mode)
                .onSuccess {
                    navLocal.update {
                        it.copy(actionMessage = "${channel.displayTitle} notifications: ${mode.label}")
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update notifications.")) }
                }
        }
    }

    fun toggleChannelMute(channel: ChatChannel) {
        viewModelScope.launch {
            chatRepository.toggleChannelMute(channel.name.ifBlank { channel.id })
                .onSuccess { nowMuted ->
                    navLocal.update {
                        it.copy(
                            actionMessage = if (nowMuted) {
                                "${channel.displayTitle}: Notifications muted"
                            } else {
                                "${channel.displayTitle}: Notifications enabled"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update notifications.")) }
                }
        }
    }

    fun toggleChannelDotOnly(channel: ChatChannel) {
        viewModelScope.launch {
            chatRepository.toggleChannelDotOnly(channel.id)
                .onSuccess { nowDotOnly ->
                    navLocal.update {
                        it.copy(
                            actionMessage = if (nowDotOnly) {
                                "${channel.displayTitle}: Dot-only badge enabled"
                            } else {
                                "${channel.displayTitle}: Unread badge enabled"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update badge preference.")) }
                }
        }
    }

    fun leaveChannel(channel: ChatChannel) {
        viewModelScope.launch {
            chatRepository.leaveChannel(channel.id)
                .onSuccess {
                    mpLocal.update { state ->
                        if (state.selectedMpChannelId.equals(channel.id, ignoreCase = true)) {
                            state.copy(selectedMpChannelId = null, matchHistory = null, matchHistoryError = null)
                        } else {
                            state
                        }
                    }
                    navLocal.update { it.copy(actionMessage = "Left ${channel.displayTitle}") }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not leave channel.")) }
                }
        }
    }

    fun startDirectMessage(usernameOrId: String, onChannelReady: (String) -> Unit) {
        viewModelScope.launch {
            navLocal.update { it.copy(actionError = null) }
            chatRepository.startDirectMessage(usernameOrId)
                .onSuccess { onChannelReady(it.id) }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not start DM.")) }
                }
        }
    }

    fun joinPublicChannel(channelName: String, onChannelReady: (String) -> Unit) {
        viewModelScope.launch {
            navLocal.update { it.copy(actionError = null) }
            chatRepository.joinPublicChannel(channelName)
                .onSuccess {
                    if (it.id.startsWith("#mp_", ignoreCase = true)) {
                        mpLocal.update { m -> m.copy(selectedMpChannelId = it.id, matchHistory = null, matchHistoryError = null) }
                        navLocal.update { n -> n.copy(activeTab = CompanionTab.MULTIPLAYER) }
                    } else {
                        onChannelReady(it.id)
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not join channel.")) }
                }
        }
    }

    // Multiplayer Actions
    fun selectMpChannel(channelId: String) {
        mpLocal.update { it.copy(selectedMpChannelId = channelId, matchHistory = null, matchHistoryError = null) }
    }

    fun dismissClosedMpLobby(channelId: String) {
        viewModelScope.launch {
            chatRepository.leaveChannel(channelId)
                .onSuccess {
                    mpLocal.update { state ->
                        if (state.selectedMpChannelId.equals(channelId, ignoreCase = true)) {
                            state.copy(selectedMpChannelId = null, matchHistory = null, matchHistoryError = null)
                        } else {
                            state
                        }
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not dismiss lobby.")) }
                }
        }
    }

    fun createMultiplayerLobby(roomName: String) {
        viewModelScope.launch {
            mpLocal.update { it.copy(isBusy = true) }
            chatRepository.createMultiplayerLobby(roomName)
                .onSuccess { chId ->
                    if (chId.startsWith("#mp_", ignoreCase = true)) {
                        mpLocal.update { it.copy(selectedMpChannelId = chId, matchHistory = null, matchHistoryError = null) }
                    } else {
                        navLocal.update {
                            it.copy(actionMessage = "Sent !mp make to BanchoBot — lobby opens on reply.")
                        }
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not create lobby.")) }
                }
            mpLocal.update { it.copy(isBusy = false) }
        }
    }

    fun joinMultiplayerLobby(matchIdOrUrl: String) {
        viewModelScope.launch {
            mpLocal.update { it.copy(isBusy = true) }
            chatRepository.joinMultiplayerLobby(matchIdOrUrl)
                .onSuccess { ch ->
                    mpLocal.update { it.copy(selectedMpChannelId = ch.id, matchHistory = null, matchHistoryError = null) }
                    navLocal.update { it.copy(activeTab = CompanionTab.MULTIPLAYER) }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Invalid match ID or URL.")) }
                }
            mpLocal.update { it.copy(isBusy = false) }
        }
    }

    fun sendMpChannelInput(channelId: String, text: String) {
        viewModelScope.launch {
            val res = if (text.trim().startsWith("!mp", ignoreCase = true)) {
                chatRepository.sendMpCommand(channelId, text)
            } else {
                chatRepository.sendMessage(channelId, text)
            }
            res.onFailure { err ->
                navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not send command.")) }
            }
        }
    }

    fun refreshMatchHistory(matchId: Long) {
        viewModelScope.launch {
            mpLocal.update { it.copy(isHistoryLoading = true, matchHistoryError = null) }
            chatRepository.fetchMatchHistory(matchId)
                .onSuccess { hist ->
                    mpLocal.update { it.copy(matchHistory = hist, matchHistoryError = null) }
                }
                .onFailure { err ->
                    mpLocal.update {
                        it.copy(matchHistoryError = sanitizeErrorMessage(err, "Could not load match history."))
                    }
                }
            mpLocal.update { it.copy(isHistoryLoading = false) }
        }
    }

    fun openBeatmapSearchForMp() {
        exploreLocal.update { it.copy(subTab = ExploreSubTab.BEATMAPS) }
        navLocal.update { it.copy(activeTab = CompanionTab.EXPLORE) }
        ensureExploreSubTabLoaded(ExploreSubTab.BEATMAPS)
    }

    fun selectBeatmapForMpLobby(beatmapId: Long, modeInt: Int) {
        val activeLobby = uiState.value.activeMpLobby
        val activeMpId = if (activeLobby != null) {
            if (activeLobby.isClosed) null else activeLobby.channelId
        } else {
            uiState.value.mpChannels.firstOrNull()?.id
        }
        if (activeMpId == null) {
            navLocal.update { it.copy(actionError = "Open an active multiplayer lobby first.") }
            return
        }
        viewModelScope.launch {
            chatRepository.sendMpCommand(activeMpId, "!mp map $beatmapId $modeInt")
                .onSuccess {
                    navLocal.update {
                        it.copy(
                            activeTab = CompanionTab.MULTIPLAYER,
                            actionMessage = "Sent !mp map $beatmapId $modeInt to $activeMpId"
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not set beatmap.")) }
                }
        }
    }

    fun saveMappool(poolId: Long?, name: String, slots: List<MappoolSlot>) {
        viewModelScope.launch {
            chatRepository.saveMappool(poolId, name, slots)
                .onSuccess {
                    navLocal.update { it.copy(actionMessage = "Saved mappool \"$name\"") }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not save mappool.")) }
                }
        }
    }

    fun deleteMappool(poolId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMappool(poolId)
                .onSuccess {
                    navLocal.update { it.copy(actionMessage = "Deleted mappool") }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not delete mappool.")) }
                }
        }
    }

    fun applyMappoolSlotToActiveLobby(slot: MappoolSlot) {
        val activeLobby = uiState.value.activeMpLobby
        if (activeLobby == null || activeLobby.isClosed) {
            navLocal.update { it.copy(actionError = "Open an active multiplayer lobby first.") }
            return
        }
        viewModelScope.launch {
            chatRepository.applyMappoolSlot(activeLobby.channelId, slot)
                .onSuccess {
                    navLocal.update {
                        it.copy(actionMessage = "Applied ${slot.slotTag}: !mp map ${slot.beatmapId} / !mp mods ${slot.mods}")
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not apply mappool slot.")) }
                }
        }
    }

    private suspend fun syncActiveMpAutoScoring(lobby: MpLobbyState) {
        val channelId = lobby.channelId
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
                mpLocal.update { it.copy(matchHistory = history, matchHistoryError = null) }
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

    fun setActiveMpBestOf(bestOf: Int) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) { it.copy(bestOf = bestOf.coerceIn(1, 25)) }
    }

    fun setActiveMpTeamNames(redName: String, blueName: String) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) {
            it.copy(
                redTeamName = redName.trim().ifEmpty { "Red Team" },
                blueTeamName = blueName.trim().ifEmpty { "Blue Team" },
                isCustomTeamNames = true
            )
        }
    }

    fun adjustActiveMpScore(team: MpTeam, delta: Int) {
        val lobby = uiState.value.activeMpLobby ?: return
        if (team == MpTeam.NONE || delta == 0) return
        chatRepository.updateMatchSeriesState(lobby.channelId) { current ->
            if (delta > 0) {
                val nextRounds = current.rounds + SeriesRoundRecord(
                    slotTag = current.activePickedSlotTag.orEmpty(),
                    beatmapLabel = lobby.beatmapTitle.orEmpty(),
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

    fun undoLastActiveMpRound() {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
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

    fun resetActiveMpSeries() {
        val lobby = uiState.value.activeMpLobby ?: return
        val extracted = MpLobbyParser.extractTournamentTeams(lobby.roomName)
        chatRepository.updateMatchSeriesState(lobby.channelId) { current ->
            val allProcessed = current.processedRoundKeys + lobby.finishedRounds.map { it.finishKey }
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

    fun setActiveMpAutoScore(enabled: Boolean) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) { it.copy(autoScoreTeamVs = enabled) }
    }

    fun setActiveMpWarmup(isWarmup: Boolean) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) { it.copy(isWarmupActive = isWarmup) }
    }

    fun setActiveMpSelectedPoolId(poolId: Long?) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) { it.copy(selectedPoolId = poolId) }
    }

    fun setActiveMpPickBanMode(mode: MpPickBanMode) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) { it.copy(pickBanMode = mode) }
    }

    fun updateActiveMpPoolSlotState(
        slot: MappoolSlot,
        targetState: MpPoolSlotState,
        triggerMpCommands: Boolean
    ) {
        val channelId = uiState.value.activeMpLobby?.channelId ?: return
        chatRepository.updateMatchSeriesState(channelId) { current ->
            MappoolPickBanResolver.applySlotTransition(current, slot, targetState)
        }
        if (triggerMpCommands && targetState != MpPoolSlotState.AVAILABLE) {
            applyMappoolSlotToActiveLobby(slot)
        }
    }

    fun sendActiveMpMacro(rawText: String) {
        val lobby = uiState.value.activeMpLobby ?: return
        val series = uiState.value.activeMpSeriesState
        val resolved = MpLobbyParser.resolveRefereeMacro(rawText, series, lobby).trim()
        if (resolved.isEmpty()) return
        sendMpChannelInput(lobby.channelId, resolved)
    }

    fun saveRefereeMacros(macros: List<RefereeMacro>) {
        viewModelScope.launch {
            chatRepository.saveRefereeMacros(macros)
            navLocal.update { it.copy(actionMessage = "Referee macros saved") }
        }
    }

    fun resetRefereeMacros() {
        viewModelScope.launch {
            chatRepository.resetRefereeMacros()
            navLocal.update { it.copy(actionMessage = "Referee macros reset to defaults") }
        }
    }

    fun saveMatchSetupTemplates(templates: List<MatchSetupTemplate>) {
        viewModelScope.launch {
            chatRepository.saveMatchSetupTemplates(templates)
            navLocal.update { it.copy(actionMessage = "Match setup presets saved") }
        }
    }

    fun runMatchSetupWizard(
        config: MatchSetupRunConfig,
        onCompleted: (mpChannelId: String) -> Unit
    ) {
        if (mpLocal.value.isWizardRunning) return
        matchSetupWizardJob?.cancel()
        matchSetupWizardJob = viewModelScope.launch {
            mpLocal.update {
                it.copy(
                    isWizardRunning = true,
                    wizardStepMessage = "Starting tournament setup...",
                    wizardError = null
                )
            }
            val result = try {
                chatRepository.createAndSetupTournamentLobby(config) { stepMsg ->
                    mpLocal.update { it.copy(wizardStepMessage = stepMsg) }
                }
            } catch (e: CancellationException) {
                mpLocal.update {
                    it.copy(
                        isWizardRunning = false,
                        wizardStepMessage = null
                    )
                }
                throw e
            }
            mpLocal.update {
                it.copy(
                    isWizardRunning = false,
                    wizardStepMessage = null
                )
            }
            result
                .onSuccess { mpChannelId ->
                    mpLocal.update {
                        it.copy(
                            selectedMpChannelId = mpChannelId,
                            matchHistory = null,
                            matchHistoryError = null,
                            wizardError = null
                        )
                    }
                    onCompleted(mpChannelId)
                }
                .onFailure { err ->
                    mpLocal.update {
                        it.copy(
                            wizardError = sanitizeErrorMessage(err, "Tournament setup failed.")
                        )
                    }
                }
        }
    }

    fun cancelMatchSetupWizard() {
        matchSetupWizardJob?.cancel()
        matchSetupWizardJob = null
        mpLocal.update {
            it.copy(
                isWizardRunning = false,
                wizardStepMessage = null
            )
        }
    }

    fun clearMatchSetupWizardError() {
        mpLocal.update { it.copy(wizardError = null) }
    }

    fun notifyMessage(message: String) {
        navLocal.update { it.copy(actionMessage = message) }
    }

    fun deleteDirectConversation(channel: ChatChannel) {
        viewModelScope.launch {
            chatRepository.deleteDirectConversation(channel.id)
                .onSuccess {
                    navLocal.update {
                        it.copy(actionMessage = "Deleted conversation with ${channel.displayTitle}")
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not delete conversation.")) }
                }
        }
    }

    // Profile & Top 5 PP Tracking
    fun openPlayerProfile(usernameOrId: String) {
        loadTargetProfile(usernameOrId = usernameOrId, mode = null)
    }

    fun retrySelectedProfile() {
        val nav = navLocal.value
        val target = nav.selectedProfileTarget
            ?: nav.selectedProfile?.let { if (it.userId > 0L) it.userId.toString() else it.username }
            ?: return
        val mode = if (nav.selectedProfile == null) null else nav.selectedProfileMode
        loadTargetProfile(usernameOrId = target, mode = mode)
    }

    fun selectProfileMode(mode: String) {
        val nav = navLocal.value
        val target = nav.selectedProfileTarget
            ?: nav.selectedProfile?.let { if (it.userId > 0L) it.userId.toString() else it.username }
            ?: return
        loadTargetProfile(usernameOrId = target, mode = mode)
    }

    private fun loadTargetProfile(usernameOrId: String, mode: String?) {
        profileLoadJob?.cancel()
        selectedOsuTrackJob?.cancel()
        selectedMoreScoresJob?.cancel()
        val requestSeq = ++profileRequestSeq
        selectedOsuTrackSeq++
        profileLoadJob = viewModelScope.launch {
            navLocal.update {
                val isSameTarget = it.selectedProfileTarget.equals(usernameOrId, ignoreCase = true)
                val isSameMode = mode == null || mode.equals(it.selectedProfileMode, ignoreCase = true)
                it.copy(
                    selectedProfileTarget = usernameOrId,
                    selectedProfile = if (isSameTarget) it.selectedProfile else null,
                    selectedProfileMode = mode ?: if (isSameTarget) it.selectedProfileMode else "osu",
                    isProfileLoading = true,
                    isProfileScoresLoading = false,
                    isLoadingMoreSelectedBestScores = false,
                    canLoadMoreSelectedBestScores = false,
                    selectedProfileError = null,
                    selectedProfileLocalHistory = if (isSameTarget && isSameMode) it.selectedProfileLocalHistory else emptyList(),
                    selectedProfileOsuTrack = if (isSameTarget && isSameMode) it.selectedProfileOsuTrack else emptyList(),
                    isSelectedProfileOsuTrackLoading = false,
                    selectedProfilePinnedScores = emptyList(),
                    selectedProfileBestScores = emptyList(),
                    selectedProfileRecentScores = emptyList()
                )
            }
            val profileResult = chatRepository.getPlayerProfile(usernameOrId, mode)
            if (requestSeq != profileRequestSeq) return@launch

            val profile = profileResult.getOrNull()
            if (profile == null) {
                val errMsg = sanitizeErrorMessage(profileResult.exceptionOrNull(), "Could not load player profile.")
                navLocal.update {
                    if (requestSeq != profileRequestSeq) it
                    else if (it.selectedProfile != null) {
                        it.copy(
                            selectedProfileMode = it.selectedProfile.mode,
                            isProfileLoading = false,
                            isProfileScoresLoading = false,
                            isLoadingMoreSelectedBestScores = false,
                            canLoadMoreSelectedBestScores = false,
                            actionError = errMsg
                        )
                    } else {
                        it.copy(
                            isProfileLoading = false,
                            isProfileScoresLoading = false,
                            isLoadingMoreSelectedBestScores = false,
                            canLoadMoreSelectedBestScores = false,
                            selectedProfileError = errMsg
                        )
                    }
                }
                return@launch
            }

            val resolvedMode = mode ?: profile.mode
            navLocal.update {
                if (requestSeq != profileRequestSeq) it
                else it.copy(
                    selectedProfile = profile,
                    selectedProfileMode = resolvedMode,
                    isProfileLoading = false,
                    selectedProfileError = null
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
                                navLocal.update { state ->
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
                navLocal.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(isProfileScoresLoading = true)
                }
                val (pinned, best, recent) = fetchScoresTriple(profile.userId.toString(), resolvedMode)
                if (requestSeq != profileRequestSeq) return@launch
                navLocal.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(
                        selectedProfilePinnedScores = pinned,
                        selectedProfileBestScores = best,
                        selectedProfileRecentScores = recent,
                        isProfileScoresLoading = false,
                        isLoadingMoreSelectedBestScores = false,
                        canLoadMoreSelectedBestScores = best.size >= 100
                    )
                }
            } else {
                navLocal.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(
                        isProfileScoresLoading = false,
                        isLoadingMoreSelectedBestScores = false,
                        canLoadMoreSelectedBestScores = false
                    )
                }
            }
        }
    }

    fun loadMoreSelectedBestScores() {
        val nav = navLocal.value
        val uid = nav.selectedProfile?.userId?.takeIf { it > 0L } ?: return
        if (nav.isLoadingMoreSelectedBestScores || !nav.canLoadMoreSelectedBestScores) return
        val requestSeq = profileRequestSeq
        val mode = nav.selectedProfileMode
        selectedMoreScoresJob?.cancel()
        selectedMoreScoresJob = viewModelScope.launch {
            navLocal.update {
                if (requestSeq != profileRequestSeq) it
                else it.copy(isLoadingMoreSelectedBestScores = true)
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
                navLocal.update { state ->
                    if (requestSeq != profileRequestSeq) state
                    else {
                        val merged = mergeBestScores(state.selectedProfileBestScores, pageTwo)
                        state.copy(
                            selectedProfileBestScores = merged,
                            isLoadingMoreSelectedBestScores = false,
                            canLoadMoreSelectedBestScores = false
                        )
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                if (requestSeq != profileRequestSeq) return@onFailure
                navLocal.update { state ->
                    if (requestSeq != profileRequestSeq) state
                    else state.copy(
                        isLoadingMoreSelectedBestScores = false,
                        canLoadMoreSelectedBestScores = true,
                        actionError = sanitizeErrorMessage(err, "Could not load more top plays.")
                    )
                }
            }
        }
    }

    private suspend fun fetchScoresTriple(
        userIdOrName: String,
        mode: String
    ): Triple<List<OsuScoreItem>, List<OsuScoreItem>, List<OsuScoreItem>> = coroutineScope {
        val pinnedDef = async {
            chatRepository.getUserScores(userIdOrName, "pinned", includeFails = false, mode = mode)
                .getOrDefault(emptyList())
        }
        val bestDef = async {
            chatRepository.getUserScores(userIdOrName, "best", includeFails = false, mode = mode, limit = 100)
                .getOrDefault(emptyList())
        }
        val recentDef = async {
            chatRepository.getUserScores(userIdOrName, "recent", includeFails = true, mode = mode)
                .getOrDefault(emptyList())
        }
        Triple(pinnedDef.await(), bestDef.await(), recentDef.await())
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
            navLocal.update {
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
            navLocal.update {
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
        selectedMoreScoresJob?.cancel()
        profileRequestSeq++
        selectedOsuTrackSeq++
        navLocal.update {
            it.copy(
                selectedProfileTarget = null,
                selectedProfile = null,
                selectedProfileMode = "osu",
                isProfileLoading = false,
                isProfileScoresLoading = false,
                isLoadingMoreSelectedBestScores = false,
                canLoadMoreSelectedBestScores = false,
                selectedProfileError = null,
                selectedProfileLocalHistory = emptyList(),
                selectedProfileOsuTrack = emptyList(),
                isSelectedProfileOsuTrackLoading = false,
                selectedProfilePinnedScores = emptyList(),
                selectedProfileBestScores = emptyList(),
                selectedProfileRecentScores = emptyList()
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
                navLocal.update {
                    val cur = it.selectedProfile
                    val updated = if (cur != null && cur.username.equals(profile.username, ignoreCase = true)) {
                        cur.copy(friendStatus = newStatus)
                    } else cur
                    it.copy(
                        selectedProfile = updated,
                        actionMessage = if (newStatus == FriendRelationStatus.NONE) {
                            "Removed ${profile.username} from friends"
                        } else {
                            "Added ${profile.username} as friend"
                        }
                    )
                }
            }.onFailure { err ->
                navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update friend status.")) }
            }
        }
    }

    fun toggleIgnoreUser(username: String) {
        val norm = BeatmapUrlParser.normalizeOsuNick(username)
        if (norm.isEmpty() || norm == "banchobot" || norm == "system") return
        viewModelScope.launch {
            val currentlyIgnored = norm in uiState.value.settings.ignoredUsers
            if (currentlyIgnored) {
                chatRepository.unignoreUser(username)
                navLocal.update { it.copy(actionMessage = "Unignored $username") }
            } else {
                chatRepository.ignoreUser(username)
                navLocal.update { it.copy(actionMessage = "Ignored $username") }
            }
        }
    }

    fun saveUserAlias(username: String, alias: String?, userId: Long?, note: String? = null) {
        viewModelScope.launch {
            chatRepository.setUserAlias(username, alias, userId, note)
            val refreshed = chatRepository.getPlayerProfile(username, navLocal.value.selectedProfileMode).getOrNull()
            if (refreshed != null) {
                navLocal.update {
                    if (it.selectedProfile?.username?.equals(username, ignoreCase = true) == true ||
                        it.selectedProfileTarget?.equals(username, ignoreCase = true) == true
                    ) {
                        it.copy(selectedProfile = refreshed)
                    } else it
                }
            }
            val cleanAlias = alias?.trim().orEmpty()
            val cleanNote = note?.trim().orEmpty()
            navLocal.update {
                it.copy(
                    actionMessage = if (cleanAlias.isEmpty() && cleanNote.isEmpty()) {
                        "Player alias & note cleared"
                    } else {
                        "Player alias & note saved"
                    }
                )
            }
        }
    }

    fun removeBookmark(messageId: String) {
        viewModelScope.launch {
            chatRepository.removeBookmarkMessage(messageId)
        }
    }

    fun toggleTrackFriendPp(username: String, userId: Long = 0L, avatarUrl: String = "") {
        viewModelScope.launch {
            chatRepository.toggleTrackFriendPp(username, userId, avatarUrl)
                .onSuccess { tracked ->
                    val currentProfile = navLocal.value.selectedProfile
                    val updatedProfile = if (currentProfile != null &&
                        currentProfile.username.equals(username, ignoreCase = true)
                    ) {
                        currentProfile.copy(isTrackedForPp = tracked)
                    } else currentProfile
                    exploreLocal.update { exp ->
                        exp.copy(
                            players = exp.players.map { p ->
                                if (p.username.equals(username, ignoreCase = true)) {
                                    p.copy(isTrackedForPp = tracked)
                                } else p
                            }
                        )
                    }
                        navLocal.update {
                        it.copy(
                            selectedProfile = updatedProfile,
                            actionMessage = if (tracked) {
                                "Tracking alerts enabled for $username"
                            } else {
                                "Stopped tracking $username"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update PP tracking.")) }
                }
        }
    }

    fun saveTrackedPlayerAlertConfig(
        userId: Long,
        mode: String,
        config: TrackedPlayerAlertConfig,
        username: String = "",
        avatarUrl: String = ""
    ) {
        viewModelScope.launch {
            chatRepository.saveTrackedPlayerAlertConfig(
                userId = userId,
                mode = mode,
                config = config,
                username = username,
                avatarUrl = avatarUrl
            )
            val currentProfile = navLocal.value.selectedProfile
            val updatedProfile = if (currentProfile != null &&
                ((userId > 0L && currentProfile.userId == userId) ||
                    (username.isNotBlank() && currentProfile.username.equals(username, ignoreCase = true)))
            ) {
                currentProfile.copy(isTrackedForPp = true)
            } else {
                currentProfile
            }
            exploreLocal.update { exp ->
                exp.copy(
                    players = exp.players.map { p ->
                        if ((userId > 0L && p.userId == userId) ||
                            (username.isNotBlank() && p.username.equals(username, ignoreCase = true))
                        ) {
                            p.copy(isTrackedForPp = true)
                        } else {
                            p
                        }
                    }
                )
            }
            navLocal.update {
                it.copy(
                    selectedProfile = updatedProfile,
                    actionMessage = if (username.isNotBlank()) {
                        "Tracking rules saved for $username"
                    } else {
                        "Tracked player alert rules saved"
                    }
                )
            }
        }
    }

    fun checkTrackedFriendsTopPlaysNow() {
        viewModelScope.launch {
            navLocal.update { it.copy(isPpCheckRunning = true) }
            chatRepository.checkTrackedFriendsTopPlays(forceNotifyLatest = false)
                .onSuccess { count ->
                    navLocal.update {
                        it.copy(
                            actionMessage = if (count > 0) {
                                "Sent $count tracked player alert(s)"
                            } else {
                                "No new tracked player alerts"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "PP check failed.")) }
                }
            navLocal.update { it.copy(isPpCheckRunning = false) }
        }
    }

    fun toggleFriendOnlineAlert(friend: FriendContact) {
        viewModelScope.launch {
            chatRepository.toggleFriendOnlineAlert(friend.username, friend.userId, friend.avatarUrl)
                .onSuccess { enabled ->
                    navLocal.update {
                        it.copy(
                            actionMessage = if (enabled) {
                                "Online alert enabled for ${friend.shortName}"
                            } else {
                                "Online alert disabled for ${friend.shortName}"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update {
                        it.copy(actionError = sanitizeErrorMessage(err, "Could not update online alert."))
                    }
                }
        }
    }

    fun refreshFriendScoreFeed() {
        if (navLocal.value.isScoreFeedRefreshing) return
        viewModelScope.launch {
            navLocal.update { it.copy(isScoreFeedRefreshing = true) }
            chatRepository.refreshFriendScoreFeed()
                .onFailure { err ->
                    navLocal.update {
                        it.copy(actionError = sanitizeErrorMessage(err, "Could not refresh friend score feed."))
                    }
                }
            navLocal.update { it.copy(isScoreFeedRefreshing = false) }
        }
    }

    // Explore Tab Actions
    fun onExploreSubTabSelected(tab: ExploreSubTab) {
        exploreLocal.update { it.copy(subTab = tab) }
        ensureExploreSubTabLoaded(tab)
    }

    private fun ensureExploreSubTabLoaded(tab: ExploreSubTab) {
        val state = exploreLocal.value
        when (tab) {
            ExploreSubTab.BEATMAPS ->
                if (!state.hasSearchedBeatmaps && state.beatmapResults.isEmpty() && !state.isBeatmapsLoading) {
                    searchBeatmaps()
                }
            ExploreSubTab.RANK_PP, ExploreSubTab.PLAY_LATER -> Unit
            ExploreSubTab.NEWS ->
                if (state.newsFeed.news.isEmpty() && state.newsFeed.spotlights.isEmpty() && !state.isNewsLoading) {
                    loadNewsAndSpotlights()
                }
            ExploreSubTab.RANKINGS ->
                if (state.players.isEmpty() && !state.isPlayersLoading) loadRankings()
            ExploreSubTab.MY_PROFILE -> {
                if (state.selfProfile == null && !state.isLoadingSelfProfile) {
                    loadSelfProfile(mode = null, forceRefresh = false)
                } else if (
                    state.selfProfile != null &&
                    state.selfProfile.userId > 0L &&
                    state.selfProfileOsuTrack.isEmpty() &&
                    !state.isSelfProfileOsuTrackLoading
                ) {
                    fetchSelfOsuTrackHistory(state.selfProfile.userId, state.selfProfileMode)
                }
            }
        }
    }

    fun selectSelfProfileMode(mode: String) {
        loadSelfProfile(mode = mode, forceRefresh = false)
    }

    fun refreshSelfProfile() {
        val currentMode = exploreLocal.value.selfProfileMode
        val modeArg = if (cachedSelfDefaultMode == null || currentMode.equals(cachedSelfDefaultMode, ignoreCase = true)) {
            null
        } else {
            currentMode
        }
        loadSelfProfile(mode = modeArg, forceRefresh = true)
    }

    fun loadSelfProfile(mode: String? = null, forceRefresh: Boolean = false) {
        selfProfileJob?.cancel()
        selfOsuTrackJob?.cancel()
        selfMoreScoresJob?.cancel()
        val requestSeq = ++selfProfileRequestSeq
        selfOsuTrackSeq++
        selfProfileJob = viewModelScope.launch {
            val s = chatRepository.settings.first()
            if (requestSeq != selfProfileRequestSeq) return@launch
            val target = when {
                s.userId > 0L -> s.userId.toString()
                s.username.isNotBlank() -> s.username.trim()
                else -> return@launch
            }
            val keepCachedProfile = forceRefresh || (mode != null && exploreLocal.value.selfProfile != null)
            exploreLocal.update {
                if (requestSeq != selfProfileRequestSeq) it
                else {
                    val isModeChange = mode != null && !mode.equals(it.selfProfileMode, ignoreCase = true)
                    it.copy(
                        selfProfileMode = mode ?: it.selfProfileMode,
                        isLoadingSelfProfile = true,
                        isLoadingSelfScores = false,
                        isLoadingMoreSelfBestScores = false,
                        canLoadMoreSelfBestScores = if (keepCachedProfile && mode == null) it.canLoadMoreSelfBestScores else false,
                        selfProfileError = null,
                        selfProfileOsuTrack = if (isModeChange) emptyList() else it.selfProfileOsuTrack,
                        isSelfProfileOsuTrackLoading = false,
                        selfPinnedScores = if (keepCachedProfile) it.selfPinnedScores else emptyList(),
                        selfBestScores = if (keepCachedProfile) it.selfBestScores else emptyList(),
                        selfRecentScores = if (keepCachedProfile) it.selfRecentScores else emptyList()
                    )
                }
            }
            val profileResult = if (mode == null) {
                chatRepository.getCachedSelfProfile(forceRefresh = forceRefresh)
            } else {
                chatRepository.getPlayerProfile(target, mode)
            }
            if (requestSeq != selfProfileRequestSeq) return@launch

            val profile = profileResult.getOrNull()
            if (profile == null) {
                val errMsg = sanitizeErrorMessage(profileResult.exceptionOrNull(), "Could not load your osu! profile.")
                exploreLocal.update {
                    if (requestSeq != selfProfileRequestSeq) it
                    else it.copy(
                        selfProfileMode = it.selfProfile?.mode ?: it.selfProfileMode,
                        isLoadingSelfProfile = false,
                        isLoadingSelfScores = false,
                        isLoadingMoreSelfBestScores = false,
                        selfProfileError = if (it.selfProfile == null) errMsg else null
                    )
                }
                if (exploreLocal.value.selfProfile != null) {
                    navLocal.update { it.copy(actionError = errMsg) }
                }
                return@launch
            }

            if (mode == null && profile.mode.isNotBlank()) {
                cachedSelfDefaultMode = profile.mode
            }
            val resolvedMode = mode ?: profile.mode
            exploreLocal.update {
                if (requestSeq != selfProfileRequestSeq) it
                else it.copy(
                    selfProfile = profile,
                    selfProfileMode = resolvedMode,
                    isLoadingSelfProfile = false,
                    selfProfileError = null
                )
            }
            if (profile.userId > 0L) {
                fetchSelfOsuTrackHistory(profile.userId, resolvedMode)
                refreshDailySession(
                    userId = profile.userId,
                    mode = resolvedMode,
                    profile = profile
                )
                if (
                    mode == null &&
                    !forceRefresh &&
                    (profile.userAchievements.isEmpty() || profile.rankHistory.isEmpty()) &&
                    s.authMode == AuthMode.OAUTH_V2
                ) {
                    viewModelScope.launch {
                        chatRepository.getCachedSelfProfile(forceRefresh = true).onSuccess { fresh ->
                            if (
                                requestSeq == selfProfileRequestSeq &&
                                (fresh.userAchievements.isNotEmpty() || fresh.rankHistory.isNotEmpty())
                            ) {
                                exploreLocal.update { state ->
                                    if (state.selfProfile?.userId == fresh.userId) {
                                        state.copy(selfProfile = fresh)
                                    } else {
                                        state
                                    }
                                }
                            }
                        }
                    }
                }
                exploreLocal.update {
                    if (requestSeq != selfProfileRequestSeq) it
                    else it.copy(isLoadingSelfScores = true)
                }
                val (pinned, best, recent) = fetchScoresTriple(profile.userId.toString(), resolvedMode)
                if (requestSeq != selfProfileRequestSeq) return@launch
                exploreLocal.update {
                    if (requestSeq != selfProfileRequestSeq) it
                    else it.copy(
                        selfPinnedScores = pinned,
                        selfBestScores = best,
                        selfRecentScores = recent,
                        isLoadingSelfScores = false,
                        isLoadingMoreSelfBestScores = false,
                        canLoadMoreSelfBestScores = best.size >= 100
                    )
                }
            } else {
                exploreLocal.update {
                    if (requestSeq != selfProfileRequestSeq) it
                    else it.copy(
                        isLoadingSelfScores = false,
                        isLoadingMoreSelfBestScores = false,
                        canLoadMoreSelfBestScores = false
                    )
                }
            }
        }
    }

    private fun fetchSelfOsuTrackHistory(userId: Long, mode: String) {
        if (userId <= 0L) return
        selfOsuTrackJob?.cancel()
        val trackSeq = ++selfOsuTrackSeq
        selfOsuTrackJob = viewModelScope.launch {
            exploreLocal.update {
                if (trackSeq != selfOsuTrackSeq) it
                else it.copy(isSelfProfileOsuTrackLoading = true)
            }
            val points = chatRepository.fetchOsuTrackHistory(userId, mode, days = 90)
                .getOrDefault(emptyList())
            if (trackSeq != selfOsuTrackSeq) return@launch
            exploreLocal.update {
                if (trackSeq != selfOsuTrackSeq) it
                else it.copy(
                    selfProfileOsuTrack = points,
                    isSelfProfileOsuTrackLoading = false
                )
            }
        }
    }

    fun refreshDailySession(
        userId: Long = exploreLocal.value.selfProfile?.userId ?: 0L,
        mode: String = exploreLocal.value.selfProfileMode,
        profile: PlayerProfile? = exploreLocal.value.selfProfile
    ) {
        if (userId <= 0L) return
        dailySessionJob?.cancel()
        val requestSeq = ++dailySessionRequestSeq
        dailySessionJob = viewModelScope.launch {
            exploreLocal.update { it.copy(isLoadingDailySession = true) }
            chatRepository.fetchDailySessionSummary(
                userId = userId,
                mode = mode,
                currentProfile = profile
            ).onSuccess { summary ->
                if (requestSeq == dailySessionRequestSeq) {
                    exploreLocal.update {
                        it.copy(
                            selfDailySession = summary,
                            isLoadingDailySession = false
                        )
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                if (requestSeq == dailySessionRequestSeq) {
                    exploreLocal.update { it.copy(isLoadingDailySession = false) }
                }
            }
        }
    }

    fun loadMoreSelfBestScores() {
        val exp = exploreLocal.value
        val uid = exp.selfProfile?.userId?.takeIf { it > 0L } ?: return
        if (exp.isLoadingMoreSelfBestScores || !exp.canLoadMoreSelfBestScores) return
        val requestSeq = selfProfileRequestSeq
        val mode = exp.selfProfileMode
        selfMoreScoresJob?.cancel()
        selfMoreScoresJob = viewModelScope.launch {
            exploreLocal.update {
                if (requestSeq != selfProfileRequestSeq) it
                else it.copy(isLoadingMoreSelfBestScores = true)
            }
            chatRepository.getUserScores(
                usernameOrId = uid.toString(),
                type = "best",
                includeFails = false,
                mode = mode,
                limit = 100,
                offset = 100
            ).onSuccess { pageTwo ->
                if (requestSeq != selfProfileRequestSeq) return@onSuccess
                exploreLocal.update { state ->
                    if (requestSeq != selfProfileRequestSeq) state
                    else {
                        val merged = mergeBestScores(state.selfBestScores, pageTwo)
                        state.copy(
                            selfBestScores = merged,
                            isLoadingMoreSelfBestScores = false,
                            canLoadMoreSelfBestScores = false
                        )
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                if (requestSeq != selfProfileRequestSeq) return@onFailure
                exploreLocal.update { state ->
                    if (requestSeq != selfProfileRequestSeq) state
                    else state.copy(
                        isLoadingMoreSelfBestScores = false,
                        canLoadMoreSelfBestScores = true
                    )
                }
                navLocal.update {
                    it.copy(actionError = sanitizeErrorMessage(err, "Could not load more top plays."))
                }
            }
        }
    }

    private fun mergeBestScores(
        existing: List<OsuScoreItem>,
        nextPage: List<OsuScoreItem>
    ): List<OsuScoreItem> {
        if (nextPage.isEmpty()) return existing.take(200)
        val seenIds = existing.mapNotNull { it.id.takeIf { id -> id > 0L } }.toHashSet()
        val merged = existing.toMutableList()
        for (item in nextPage) {
            if (item.id <= 0L || seenIds.add(item.id)) {
                merged.add(item)
            }
            if (merged.size >= 200) break
        }
        return merged
    }

    suspend fun getCachedBeatmaps(beatmapIds: Collection<Long>): Map<Long, BeatmapMetadata> {
        return chatRepository.getCachedBeatmaps(beatmapIds)
    }

    suspend fun resolveBeatmapDetails(beatmapId: Long): Result<BeatmapMetadata> {
        return chatRepository.getBeatmapDetails(beatmapId).recoverCatching { err ->
            if (err is kotlinx.coroutines.CancellationException) throw err
            throw IllegalStateException(sanitizeErrorMessage(err, "Could not load beatmap details."))
        }
    }

    suspend fun resolveExternalBeatmap(beatmapId: Long?, beatmapsetId: Long?): Result<BeatmapMetadata> {
        val url = when {
            beatmapId != null && beatmapId > 0L -> "https://osu.ppy.sh/b/$beatmapId"
            beatmapsetId != null && beatmapsetId > 0L -> "https://osu.ppy.sh/s/$beatmapsetId"
            else -> return Result.failure(IllegalArgumentException("Invalid beatmap link"))
        }
        return chatRepository.getBeatmapMetadata(
            BeatmapLinkRef(
                url = url,
                beatmapId = beatmapId,
                beatmapsetId = beatmapsetId
            )
        ).recoverCatching { err ->
            if (err is kotlinx.coroutines.CancellationException) throw err
            throw IllegalStateException(sanitizeErrorMessage(err, "Could not load beatmap details."))
        }
    }

    suspend fun loadBeatmapLeaderboard(beatmapId: Long, mode: String?): Result<List<OsuScoreItem>> {
        return chatRepository.getBeatmapScores(beatmapId, mode).recoverCatching { err ->
            if (err is kotlinx.coroutines.CancellationException) throw err
            throw IllegalStateException(sanitizeErrorMessage(err, "Could not load beatmap leaderboard."))
        }
    }

    suspend fun estimatePpForRank(
        mode: String,
        targetRank: Int
    ): Result<RankPpConversionResult> {
        if (targetRank < 1) {
            return Result.failure(IllegalArgumentException("Rank must be at least #1"))
        }
        return chatRepository.estimatePpForRank(mode, RankPpMath.clampRank(targetRank))
    }

    suspend fun estimateRankForPp(
        mode: String,
        targetPp: Double,
        currentRankHint: Int?,
        currentPpHint: Double?
    ): Result<RankPpConversionResult> {
        if (!targetPp.isFinite() || targetPp <= 0.0) {
            return Result.failure(IllegalArgumentException("Target PP must be positive and finite"))
        }
        return chatRepository.estimateRankForPp(
            mode = mode,
            targetPp = RankPpMath.clampTotalPp(targetPp),
            currentRankHint = RankPpMath.sanitizeRank(currentRankHint),
            currentPpHint = RankPpMath.sanitizeFinitePositive(currentPpHint)
        )
    }

    fun onRankPpModeChange(mode: String) {
        rankPpJob?.cancel()
        rankPpRequestSeq++
        exploreLocal.update {
            it.copy(rankPpMode = mode, rankPpResult = null, isConvertingRankPp = false, rankPpError = null)
        }
    }

    fun lookupPpForRank(targetRank: Int) {
        if (targetRank < 1) {
            navLocal.update { it.copy(actionError = "Rank must be at least #1") }
            return
        }
        val safeRank = RankPpMath.clampRank(targetRank)
        val mode = exploreLocal.value.rankPpMode
        rankPpJob?.cancel()
        val requestSeq = ++rankPpRequestSeq
        rankPpJob = viewModelScope.launch {
            exploreLocal.update { it.copy(isConvertingRankPp = true, rankPpError = null) }
            chatRepository.estimatePpForRank(mode, safeRank)
                .onSuccess { res ->
                    if (requestSeq == rankPpRequestSeq) {
                        exploreLocal.update { it.copy(rankPpResult = res, rankPpError = null) }
                    }
                }
                .onFailure { err ->
                    if (requestSeq == rankPpRequestSeq) {
                        exploreLocal.update {
                            it.copy(rankPpError = sanitizeErrorMessage(err, "Could not estimate PP for #$safeRank."))
                        }
                    }
                }
            if (requestSeq == rankPpRequestSeq) {
                exploreLocal.update { it.copy(isConvertingRankPp = false) }
            }
        }
    }

    fun lookupRankForPp(targetPp: Double) {
        if (!targetPp.isFinite() || targetPp <= 0.0) {
            navLocal.update { it.copy(actionError = "Enter a valid PP amount (> 0)") }
            return
        }
        val safePp = RankPpMath.clampTotalPp(targetPp)
        val exp = exploreLocal.value
        val mode = exp.rankPpMode
        val self = exp.selfProfile?.takeIf { exp.selfProfileMode == mode }
        rankPpJob?.cancel()
        val requestSeq = ++rankPpRequestSeq
        rankPpJob = viewModelScope.launch {
            exploreLocal.update { it.copy(isConvertingRankPp = true, rankPpError = null) }
            chatRepository.estimateRankForPp(
                mode = mode,
                targetPp = safePp,
                currentRankHint = RankPpMath.sanitizeRank(self?.globalRank),
                currentPpHint = RankPpMath.sanitizeFinitePositive(self?.pp)
            )
                .onSuccess { res ->
                    if (requestSeq == rankPpRequestSeq) {
                        exploreLocal.update { it.copy(rankPpResult = res, rankPpError = null) }
                    }
                }
                .onFailure { err ->
                    if (requestSeq == rankPpRequestSeq) {
                        exploreLocal.update {
                            it.copy(rankPpError = sanitizeErrorMessage(err, "Could not estimate rank for ${safePp.toLong()}pp."))
                        }
                    }
                }
            if (requestSeq == rankPpRequestSeq) {
                exploreLocal.update { it.copy(isConvertingRankPp = false) }
            }
        }
    }

    fun toggleSaveBeatmap(item: SavedBeatmapItem) {
        viewModelScope.launch {
            chatRepository.toggleSaveBeatmap(item)
                .onSuccess { saved ->
                    navLocal.update {
                        it.copy(
                            actionMessage = if (saved) {
                                "Saved ${item.artist} - ${item.title} [${item.version}] to Play Later"
                            } else {
                                "Removed ${item.title} [${item.version}] from Play Later"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update Play Later queue.")) }
                }
        }
    }

    fun removeSavedBeatmap(beatmapId: Long) {
        viewModelScope.launch {
            chatRepository.removeSavedBeatmap(beatmapId)
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not remove beatmap.")) }
                }
        }
    }

    fun selectPlayLaterTag(tag: String?) {
        exploreLocal.update { state ->
            val next = if (tag != null && tag.equals(state.playLaterSelectedTag, ignoreCase = true)) null else tag
            state.copy(playLaterSelectedTag = next)
        }
    }

    fun selectPlayLaterSortOption(option: PlayLaterSortOption) {
        exploreLocal.update { state ->
            if (state.playLaterSortOption == option) {
                state.copy(playLaterSortDescending = !state.playLaterSortDescending)
            } else {
                state.copy(playLaterSortOption = option, playLaterSortDescending = true)
            }
        }
    }

    fun togglePlayLaterSortDirection() {
        exploreLocal.update { state ->
            state.copy(playLaterSortDescending = !state.playLaterSortDescending)
        }
    }

    fun updateSavedBeatmapTags(beatmapId: Long, tags: List<String>) {
        viewModelScope.launch {
            val csv = SavedBeatmapItem.formatTagsCsv(tags)
            chatRepository.updateSavedBeatmapTags(beatmapId, csv)
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not update tags.")) }
                }
        }
    }

    fun downloadBeatmapSetOsz(beatmapsetId: Long, artist: String, title: String) {
        if (beatmapsetId <= 0L) {
            navLocal.update { it.copy(actionError = "Invalid beatmapset ID for download.") }
            return
        }
        val current = beatmapDownloadRepository.downloadStates.value[beatmapsetId]
        if (current is OszDownloadState.Downloading) return
        viewModelScope.launch {
            beatmapDownloadRepository.downloadOszToDevice(beatmapsetId, artist, title)
                .onSuccess { downloaded ->
                    navLocal.update { it.copy(actionMessage = "Saved ${downloaded.fileName}") }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not download .osz from mirrors.")) }
                }
        }
    }

    fun shareBeatmapSetOsz(context: Context, beatmapsetId: Long, artist: String, title: String) {
        if (beatmapsetId <= 0L) {
            navLocal.update { it.copy(actionError = "Invalid beatmapset ID for sharing.") }
            return
        }
        val current = beatmapDownloadRepository.downloadStates.value[beatmapsetId]
        if (current is OszDownloadState.Downloading) return
        viewModelScope.launch {
            beatmapDownloadRepository.prepareShareableOszUri(beatmapsetId, artist, title)
                .onSuccess { (uri, fileName) ->
                    val chooser = beatmapDownloadRepository.createShareOszIntent(uri, fileName)
                    if (context !is Activity) {
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching {
                        context.startActivity(chooser)
                    }.onFailure {
                        navLocal.update { it.copy(actionError = "No app available to share .osz file.") }
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not prepare .osz for sharing.")) }
                }
        }
    }

    fun sendSavedBeatmapToInGame(item: SavedBeatmapItem) {
        viewModelScope.launch {
            chatRepository.sendBeatmapToSelfInGame(item)
                .onSuccess {
                    navLocal.update {
                        it.copy(actionMessage = "Sent [${item.title}] link to in-game Bancho chat")
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not send beatmap to Bancho.")) }
                }
        }
    }

    fun sendAllSavedBeatmapsToInGame() {
        viewModelScope.launch {
            exploreLocal.update { it.copy(isSendingQueueToInGame = true) }
            chatRepository.sendAllSavedBeatmapsToSelfInGame()
                .onSuccess { count ->
                    navLocal.update {
                        it.copy(actionMessage = "Sent $count beatmap link(s) to in-game Bancho chat")
                    }
                }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not sync Play Later queue.")) }
                }
            exploreLocal.update { it.copy(isSendingQueueToInGame = false) }
        }
    }

    fun loadNewsAndSpotlights(forceRefresh: Boolean = false) {
        newsJob?.cancel()
        val requestSeq = ++newsRequestSeq
        newsJob = viewModelScope.launch {
            exploreLocal.update { it.copy(isNewsLoading = true, newsError = null) }
            chatRepository.getNewsAndSpotlights(forceRefresh = forceRefresh)
                .onSuccess { feed ->
                    if (requestSeq == newsRequestSeq) {
                        exploreLocal.update { it.copy(newsFeed = feed, newsError = null) }
                    }
                }
                .onFailure { err ->
                    if (requestSeq == newsRequestSeq) {
                        exploreLocal.update {
                            it.copy(newsError = sanitizeErrorMessage(err, "Could not load osu! news."))
                        }
                    }
                }
            if (requestSeq == newsRequestSeq) {
                exploreLocal.update { it.copy(isNewsLoading = false) }
            }
        }
    }

    fun showMessage(message: String) {
        navLocal.update { it.copy(actionMessage = message) }
    }

    fun onBeatmapQueryChange(q: String) {
        exploreLocal.update { it.copy(beatmapFilter = it.beatmapFilter.copy(query = q)) }
    }

    fun onBeatmapFilterChange(filter: BeatmapSearchFilter, triggerSearch: Boolean = true) {
        exploreLocal.update { it.copy(beatmapFilter = filter) }
        if (triggerSearch) {
            searchBeatmaps()
        }
    }

    fun resetBeatmapFilters() {
        exploreLocal.update {
            it.copy(beatmapFilter = BeatmapSearchFilter(query = it.beatmapFilter.query))
        }
        searchBeatmaps()
    }

    fun clearBeatmapQueryAndFilters() {
        exploreLocal.update {
            it.copy(beatmapFilter = BeatmapSearchFilter())
        }
        searchBeatmaps()
    }

    fun searchBeatmaps() {
        beatmapsJob?.cancel()
        val requestSeq = ++beatmapsRequestSeq
        beatmapsJob = viewModelScope.launch {
            val filter = exploreLocal.value.beatmapFilter
            exploreLocal.update { it.copy(isBeatmapsLoading = true, beatmapsError = null) }
            chatRepository.searchBeatmapsets(filter)
                .onSuccess { list ->
                    if (requestSeq == beatmapsRequestSeq) {
                        exploreLocal.update {
                            it.copy(
                                beatmapResults = list.distinctBy { set -> set.beatmapsetId },
                                hasSearchedBeatmaps = true,
                                beatmapsError = null
                            )
                        }
                    }
                }
                .onFailure { err ->
                    if (requestSeq == beatmapsRequestSeq) {
                        exploreLocal.update {
                            it.copy(
                                hasSearchedBeatmaps = true,
                                beatmapsError = sanitizeErrorMessage(err, "Beatmap search failed.")
                            )
                        }
                    }
                }
            if (requestSeq == beatmapsRequestSeq) {
                exploreLocal.update { it.copy(isBeatmapsLoading = false) }
            }
        }
    }

    fun setBeatmapsForYouMode(enabled: Boolean) {
        exploreLocal.update { it.copy(isForYouMode = enabled) }
        if (enabled &&
            exploreLocal.value.recommendedBeatmaps.isEmpty() &&
            !exploreLocal.value.isRecommendationsLoading
        ) {
            refreshRecommendations()
        }
    }

    fun selectRecommendationPreset(preset: BeatmapRecommendationPreset) {
        exploreLocal.update { it.copy(recommendationPreset = preset) }
        refreshRecommendations()
    }

    fun refreshRecommendations() {
        recommendationJob?.cancel()
        val requestSeq = ++recommendationsRequestSeq
        recommendationJob = viewModelScope.launch {
            exploreLocal.update {
                it.copy(isRecommendationsLoading = true, recommendationsError = null)
            }
            try {
                val settings = chatRepository.settings.first()
                var exp = exploreLocal.value
                var bestScores = exp.selfBestScores
                var modeHint = exp.selfProfile?.mode ?: exp.selfProfileMode

                if (bestScores.isEmpty() && settings.authMode == AuthMode.OAUTH_V2) {
                    val selfProf = exp.selfProfile ?: chatRepository.getCachedSelfProfile(false).getOrNull()
                    if (selfProf != null && selfProf.userId > 0L) {
                        modeHint = selfProf.mode
                        val loadedTop = chatRepository.getUserScores(
                            usernameOrId = selfProf.userId.toString(),
                            type = "best",
                            includeFails = false,
                            mode = selfProf.mode,
                            limit = 100,
                            offset = 0
                        ).getOrNull().orEmpty()
                        if (loadedTop.isNotEmpty()) {
                            bestScores = loadedTop
                            exploreLocal.update { state ->
                                if (state.selfProfile == null || state.selfBestScores.isEmpty()) {
                                    state.copy(
                                        selfProfile = state.selfProfile ?: selfProf,
                                        selfProfileMode = selfProf.mode,
                                        selfBestScores = loadedTop
                                    )
                                } else {
                                    state
                                }
                            }
                        }
                    }
                }

                if (requestSeq != recommendationsRequestSeq) return@launch
                exp = exploreLocal.value
                val preset = exp.recommendationPreset
                val profile = BeatmapRecommendationEngine.buildPlayerProfile(bestScores, fallbackMode = modeHint)
                val savedMaps = chatRepository.savedBeatmaps.first()

                val strictFilter = BeatmapRecommendationEngine.buildSearchFilter(
                    profile = profile,
                    preset = preset,
                    relaxed = false
                )
                val primaryResult = chatRepository.searchBeatmapsets(strictFilter)
                var selected = BeatmapRecommendationEngine.selectRecommendations(
                    sets = primaryResult.getOrDefault(emptyList()),
                    profile = profile,
                    preset = preset,
                    topScores = bestScores,
                    savedBeatmaps = savedMaps
                )

                var finalError: String? = primaryResult.exceptionOrNull()?.let {
                    sanitizeErrorMessage(it, "Could not load recommendations.")
                }

                if (selected.isEmpty()) {
                    val relaxedFilter = BeatmapRecommendationEngine.buildSearchFilter(
                        profile = profile,
                        preset = preset,
                        relaxed = true
                    )
                    val fallbackResult = chatRepository.searchBeatmapsets(relaxedFilter)
                    val relaxedSelected = BeatmapRecommendationEngine.selectRecommendations(
                        sets = fallbackResult.getOrDefault(emptyList()),
                        profile = profile,
                        preset = preset,
                        topScores = bestScores,
                        savedBeatmaps = savedMaps
                    )
                    if (relaxedSelected.isNotEmpty()) {
                        selected = relaxedSelected
                        finalError = null
                    } else if (finalError == null) {
                        finalError = fallbackResult.exceptionOrNull()?.let {
                            sanitizeErrorMessage(it, "Could not load recommendations.")
                        }
                    }
                }

                if (requestSeq == recommendationsRequestSeq) {
                    exploreLocal.update {
                        it.copy(
                            recommendationProfile = profile,
                            recommendedBeatmaps = selected,
                            recommendationsError = finalError
                        )
                    }
                }
            } finally {
                if (requestSeq == recommendationsRequestSeq) {
                    exploreLocal.update { it.copy(isRecommendationsLoading = false) }
                }
            }
        }
    }

    fun playRecommendedQueue(items: List<RecommendedBeatmapItem>, startIndex: Int = 0) {
        val queue = items.map { item ->
            item.beatmapSet.toAudioQueueTrack(item.recommendedDiff)
        }
        beatmapAudioPlayer.playQueue(queue, startIndex = startIndex, autoAdvance = false)
    }

    fun toggleBeatmapAudio(beatmapsetId: Long, previewUrl: String) {
        beatmapAudioPlayer.togglePlayPause(beatmapsetId, previewUrl)
    }

    fun playSavedBeatmapsQueue(tracks: List<SavedBeatmapItem>, startIndex: Int = 0) {
        val queue = tracks.map { it.toAudioQueueTrack() }
        beatmapAudioPlayer.playQueue(queue, startIndex = startIndex, autoAdvance = false)
    }

    fun playSearchResultsQueue(
        results: List<BeatmapSetSearchResult>,
        startIndex: Int = 0,
        chosenDiff: BeatmapMetadata? = null
    ) {
        val queue = results.mapIndexed { idx, set ->
            val diff = if (idx == startIndex) chosenDiff else set.difficulties.lastOrNull()
            set.toAudioQueueTrack(diff)
        }
        beatmapAudioPlayer.playQueue(queue, startIndex = startIndex, autoAdvance = false)
    }

    fun toggleCurrentAudioTrack() {
        beatmapAudioPlayer.toggleCurrentPlayPause()
    }

    fun playNextAudioTrack() {
        beatmapAudioPlayer.playNext()
    }

    fun playPreviousAudioTrack() {
        beatmapAudioPlayer.playPrevious()
    }

    fun stopAndClearAudio() {
        beatmapAudioPlayer.stopAndClear()
    }

    fun toggleSaveFromQueueTrack(track: AudioQueueTrack) {
        if (track.beatmapId <= 0L) return
        toggleSaveBeatmap(
            SavedBeatmapItem(
                beatmapId = track.beatmapId,
                beatmapsetId = track.beatmapsetId,
                title = track.title.ifBlank { "Beatmap #${track.beatmapsetId}" },
                artist = track.artist,
                creator = track.creator,
                version = track.version.ifBlank { "Default" },
                starRating = track.starRating,
                bpm = track.bpm,
                mode = track.mode.ifBlank { "osu" },
                coverUrl = track.coverUrl,
                previewAudioUrl = track.previewAudioUrl,
                totalLengthSeconds = track.totalLengthSeconds
            )
        )
    }

    private fun SavedBeatmapItem.toAudioQueueTrack(): AudioQueueTrack = AudioQueueTrack(
        beatmapId = beatmapId,
        beatmapsetId = beatmapsetId,
        title = title,
        artist = artist,
        version = version,
        creator = creator,
        starRating = starRating,
        bpm = bpm,
        totalLengthSeconds = totalLengthSeconds,
        coverUrl = coverUrl,
        previewAudioUrl = previewAudioUrl,
        mode = mode
    )

    private fun BeatmapSetSearchResult.toAudioQueueTrack(chosenDiff: BeatmapMetadata?): AudioQueueTrack {
        val diff = chosenDiff ?: difficulties.lastOrNull()
        return AudioQueueTrack(
            beatmapId = diff?.beatmapId?.takeIf { it > 0L } ?: primaryBeatmapId,
            beatmapsetId = beatmapsetId,
            title = diff?.title?.ifBlank { title } ?: title,
            artist = diff?.artist?.ifBlank { artist } ?: artist,
            version = diff?.version ?: "",
            creator = diff?.creator?.ifBlank { creator } ?: creator,
            starRating = diff?.starRating ?: 0.0,
            bpm = diff?.bpm ?: bpm,
            totalLengthSeconds = diff?.totalLengthSeconds ?: 0,
            coverUrl = diff?.coverUrl?.ifBlank { coverUrl } ?: coverUrl,
            previewAudioUrl = diff?.previewAudioUrl?.ifBlank { previewAudioUrl } ?: previewAudioUrl,
            mode = diff?.mode ?: "osu"
        )
    }

    fun onRankingModeChange(mode: String) {
        exploreLocal.update {
            it.copy(rankingMode = mode, playerSearchQuery = "", isPlayerSearchActive = false)
        }
        loadRankings()
    }

    fun onRankingCountryChange(country: String) {
        exploreLocal.update {
            it.copy(rankingCountry = country, playerSearchQuery = "", isPlayerSearchActive = false)
        }
        loadRankings()
    }

    fun onPlayerSearchQueryChange(query: String) {
        exploreLocal.update { it.copy(playerSearchQuery = query) }
    }

    fun clearPlayerSearch() {
        exploreLocal.update { it.copy(playerSearchQuery = "", isPlayerSearchActive = false, playersError = null) }
        loadRankings()
    }

    fun loadRankings() {
        playersJob?.cancel()
        val requestSeq = ++playersRequestSeq
        playersJob = viewModelScope.launch {
            val s = exploreLocal.value
            exploreLocal.update {
                it.copy(isPlayersLoading = true, isPlayerSearchActive = false, playersError = null)
            }
            chatRepository.getRankings(s.rankingMode, s.rankingCountry)
                .onSuccess { list ->
                    if (requestSeq == playersRequestSeq) {
                        exploreLocal.update {
                            it.copy(
                                players = list.distinctBy { p -> p.userId.takeIf { id -> id > 0L } ?: p.username.lowercase() },
                                playersError = null
                            )
                        }
                    }
                }
                .onFailure { err ->
                    if (requestSeq == playersRequestSeq) {
                        exploreLocal.update {
                            it.copy(playersError = sanitizeErrorMessage(err, "Could not load rankings."))
                        }
                    }
                }
            if (requestSeq == playersRequestSeq) {
                exploreLocal.update { it.copy(isPlayersLoading = false) }
            }
        }
    }

    fun searchPlayers() {
        val q = exploreLocal.value.playerSearchQuery.trim()
        if (q.isEmpty()) {
            loadRankings()
            return
        }
        playersJob?.cancel()
        val requestSeq = ++playersRequestSeq
        playersJob = viewModelScope.launch {
            exploreLocal.update {
                it.copy(isPlayersLoading = true, isPlayerSearchActive = true, playersError = null)
            }
            chatRepository.searchPlayers(q)
                .onSuccess { list ->
                    if (requestSeq == playersRequestSeq) {
                        exploreLocal.update {
                            it.copy(
                                players = list.distinctBy { p -> p.userId.takeIf { id -> id > 0L } ?: p.username.lowercase() },
                                playersError = null
                            )
                        }
                    }
                }
                .onFailure { err ->
                    if (requestSeq == playersRequestSeq) {
                        exploreLocal.update {
                            it.copy(playersError = sanitizeErrorMessage(err, "Player search failed."))
                        }
                    }
                }
            if (requestSeq == playersRequestSeq) {
                exploreLocal.update { it.copy(isPlayersLoading = false) }
            }
        }
    }

    // Share Score Card / Chat Picker
    fun promptShareScore(score: OsuScoreItem) {
        navLocal.update { it.copy(pendingShareScore = score) }
    }

    fun dismissScoreShareDialog() {
        navLocal.update { it.copy(pendingShareScore = null) }
    }

    fun promptShareScoreTextOnly(score: OsuScoreItem) {
        val modsPart = if (score.mods.isNotEmpty()) " +${score.mods.joinToString("")}" else ""
        val ppPart = score.pp?.takeIf { it.isFinite() }?.let { " ${kotlin.math.round(it).toInt()}pp" } ?: ""
        val acc = String.format(Locale.US, "%.2f%%", score.accuracy)
        val url = score.beatmapId?.let { "https://osu.ppy.sh/b/$it" }
            ?: score.beatmapsetId?.let { "https://osu.ppy.sh/s/$it" }
            ?: ""
        val msg = "${score.username} | ${score.beatmapArtist} - ${score.beatmapTitle} [${score.beatmapVersion}]$modsPart ${score.gradeLabel} $acc ${score.maxCombo}x$ppPart $url".trim()
        navLocal.update { it.copy(pendingShareScore = null, pendingShareText = msg) }
    }

    fun showActionMessage(message: String) {
        navLocal.update { it.copy(actionMessage = message) }
    }

    fun sendShareToChannel(channelId: String, onOpenChannel: (String) -> Unit) {
        val text = navLocal.value.pendingShareText ?: return
        navLocal.update { it.copy(pendingShareText = null) }
        viewModelScope.launch {
            chatRepository.sendMessage(channelId, text)
                .onSuccess { onOpenChannel(channelId) }
                .onFailure { err ->
                    navLocal.update { it.copy(actionError = sanitizeErrorMessage(err, "Could not share to chat.")) }
                }
        }
    }

    fun dismissSharePicker() {
        navLocal.update { it.copy(pendingShareText = null) }
    }

    fun refreshConnection() {
        viewModelScope.launch {
            navLocal.update { it.copy(isRefreshingConnection = true) }
            chatRepository.clearConnectionError()
            chatRepository.reconnectIfAuthenticated()
            chatRepository.refreshPublicChannels()
            chatRepository.refreshFriends()
            navLocal.update { it.copy(isRefreshingConnection = false) }
        }
    }

    fun clearError() {
        navLocal.update { it.copy(actionError = null) }
    }

    fun clearMessage() {
        navLocal.update { it.copy(actionMessage = null) }
    }

    private fun sanitizeErrorMessage(err: Throwable?, fallback: String): String {
        if (err == null) return fallback
        return when (err) {
            is UnknownHostException,
            is ConnectException -> "Unable to reach osu!. Check your internet connection."
            is SocketTimeoutException -> "Request to osu! timed out. Retry."
            is SSLException -> "Secure connection failed. Check network and retry."
            is IOException -> sanitizeRawText(err.message, "Network error. Check your connection and retry.")
            else -> sanitizeRawText(err.message, fallback)
        }
    }

    private fun sanitizeRawText(raw: String?, fallback: String): String {
        if (raw.isNullOrBlank()) return fallback
        val trimmed = raw.trim()
        val looksTechnical = trimmed.contains("Exception", ignoreCase = true) ||
            trimmed.contains("java.") ||
            trimmed.contains("javax.") ||
            trimmed.contains("kotlin.") ||
            trimmed.contains("okhttp", ignoreCase = true) ||
            trimmed.contains("retrofit", ignoreCase = true) ||
            trimmed.contains("gson", ignoreCase = true) ||
            trimmed.contains("http://", ignoreCase = true) ||
            trimmed.contains("https://", ignoreCase = true) ||
            trimmed.contains("Failed to connect to /", ignoreCase = true) ||
            trimmed.contains("Unable to resolve host", ignoreCase = true) ||
            trimmed.length > 120
        if (!looksTechnical) return trimmed
        return when {
            trimmed.contains("401") || trimmed.contains("expired", ignoreCase = true) ->
                "Session expired. Sign in again."
            trimmed.contains("403") || trimmed.contains("cloudflare", ignoreCase = true) ->
                "Blocked by Cloudflare or missing permission. Retry."
            trimmed.contains("404") ->
                "Requested osu! resource was not found."
            trimmed.contains("429") || trimmed.contains("rate limit", ignoreCase = true) ->
                "osu! API rate limit reached. Wait a moment and retry."
            trimmed.contains("timeout", ignoreCase = true) || trimmed.contains("timed out", ignoreCase = true) ->
                "Request timed out. Check your connection."
            trimmed.contains("host", ignoreCase = true) ||
                trimmed.contains("connect", ignoreCase = true) ||
                trimmed.contains("network", ignoreCase = true) ->
                "Unable to reach osu!. Check your internet connection."
            else -> fallback
        }
    }

    override fun onCleared() {
        beatmapAudioPlayer.stop()
        super.onCleared()
    }
}
