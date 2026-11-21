package com.osuchat.app.domain.repository

import android.content.Context
import android.net.Uri
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapSearchFilter
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.BookmarkedMessageItem
import com.osuchat.app.domain.model.ChannelHistoryState
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ChatLogExportFormat
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.ChatUserColorInfo
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.DailySessionSummary
import com.osuchat.app.domain.model.DeliveryStatus
import com.osuchat.app.domain.model.FriendContact
import com.osuchat.app.domain.model.FriendRelationStatus
import com.osuchat.app.domain.model.FriendScoreEventItem
import com.osuchat.app.domain.model.GlobalMessageSearchHit
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchAnalysisReport
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MatchSetupRunConfig
import com.osuchat.app.domain.model.MatchSetupTemplate
import com.osuchat.app.domain.model.MpMatchHistory
import com.osuchat.app.domain.model.NewsAndSpotlightsFeed
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuTrackPeak
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.domain.model.SavedBeatmapItem
import com.osuchat.app.domain.model.SeasonalBackgroundItem
import com.osuchat.app.domain.model.SelfProfile
import com.osuchat.app.domain.model.ThemeMode
import com.osuchat.app.domain.model.UserAlias
import com.osuchat.app.domain.model.WallpaperConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val connectionStatus: StateFlow<ConnectionStatus>
    val connectionError: StateFlow<String?>
    val isIrcAuthError: StateFlow<Boolean>
    val isSessionExpired: StateFlow<Boolean>
    val requiresChatReadReauth: StateFlow<Boolean>
    val pendingOAuthCode: StateFlow<String?>
    val activeChannelId: StateFlow<String?>
    val sessionConnectedAt: StateFlow<Long>
    val createdMpChannelEvents: SharedFlow<String>
    val settings: Flow<AppSettings>
    val mutedChannels: Flow<Set<String>>
    val dotOnlyChannels: Flow<Set<String>>
    val chatUserColors: StateFlow<Map<String, ChatUserColorInfo>>
    val channels: Flow<List<ChatChannel>>
    val friends: Flow<List<FriendContact>>
    val trackedFriendNorms: Flow<Set<String>>
    val friendScoreEvents: Flow<List<FriendScoreEventItem>>
    val allAliases: Flow<List<UserAlias>>
    val savedBeatmaps: Flow<List<SavedBeatmapItem>>
    val mappools: Flow<List<Mappool>>
    val refereeMacros: Flow<List<RefereeMacro>>
    val matchSetupTemplates: Flow<List<MatchSetupTemplate>>
    val bookmarkedMessages: Flow<List<BookmarkedMessageItem>>
    val bookmarkedMessageIds: Flow<Set<String>>
    val cloudflareChallengeUrl: StateFlow<String?>

    fun observeMessages(
        channelId: String,
        limit: Int = DEFAULT_MESSAGE_WINDOW
    ): Flow<List<ChatMessage>>
    fun observeChannel(channelId: String): Flow<ChatChannel?>
    fun observeChannelWallpaper(channelId: String): Flow<WallpaperConfig?>
    fun observeChannelUsers(channelId: String): Flow<List<String>>
    fun observeChannelHistoryState(channelId: String): Flow<ChannelHistoryState>

    fun setActiveChannel(channelId: String?)
    fun setAppInForeground(inForeground: Boolean)
    fun resolveCloudflareChallenge(verified: Boolean)
    fun clearConnectionError()

    suspend fun launchOAuthLogin(context: Context, customClientId: String? = null)
    fun prepareInAppOAuthAuthorizeUrl(): String
    fun isOAuthCallbackUri(uri: Uri): Boolean
    fun cancelOAuthLoginAttempt()
    suspend fun handleOAuthRedirect(uri: Uri): Result<Unit>
    suspend fun completeOAuthFromWebView(
        tokenJson: String,
        meJson: String,
        friendsJson: String? = null
    ): Result<Unit>
    suspend fun exchangeOAuthCodeViaOkHttp(code: String): Result<Unit>
    fun tryClaimOAuthCodeForExchange(code: String): Boolean
    fun isOAuthCodeConsumed(code: String): Boolean
    fun isOAuthExchangeInFlight(): Boolean
    fun clearPendingOAuthCode()
    suspend fun completeIrcSetupForOAuth(ircPassword: String): Result<Unit>
    suspend fun loginWithIrc(username: String, ircPassword: String): Result<Unit>
    suspend fun reconnectIfAuthenticated()
    suspend fun disconnect()
    suspend fun disconnectExplicitly()
    suspend fun setExplicitlyDisconnected(disconnected: Boolean)
    suspend fun logout()

    suspend fun sendMessage(channelId: String, rawInput: String): Result<Unit>
    suspend fun sendQueuedMessageNow(messageId: String): Result<Unit>
    suspend fun cancelQueuedMessage(messageId: String)
    suspend fun searchAllMessages(query: String, limit: Int = 100): List<GlobalMessageSearchHit>
    suspend fun sendRecentScoreCommand(channelId: String, targetUsernameOrNull: String?): Result<Unit>
    suspend fun sendCompareScoreCommand(channelId: String, targetUsernameOrNull: String?): Result<Unit>
    suspend fun sendTopScoreCommand(channelId: String, rawArgsOrNull: String?): Result<Unit>
    suspend fun ignoreUser(username: String)
    suspend fun unignoreUser(username: String)
    suspend fun setIgnoreDisplayMode(mode: String)
    suspend fun joinPublicChannel(channelName: String): Result<ChatChannel>
    suspend fun leaveChannel(channelId: String): Result<Unit>
    suspend fun deleteDirectConversation(channelId: String): Result<Unit>
    suspend fun startDirectMessage(targetUsernameOrId: String): Result<ChatChannel>
    suspend fun openFriendDmFast(
        username: String,
        userId: Long? = null,
        avatarUrl: String? = null
    ): Result<ChatChannel>
    suspend fun setChannelPinned(channelId: String, pinned: Boolean): Result<Unit>
    suspend fun setChannelNotificationMode(channelId: String, mode: ChannelNotificationMode): Result<Unit>
    suspend fun toggleChannelMute(channelIdOrName: String): Result<Boolean>
    suspend fun toggleChannelDotOnly(channelId: String): Result<Boolean>
    suspend fun markChannelAsRead(channelId: String)
    suspend fun clearChannelMessages(channelId: String)
    suspend fun refreshPublicChannels(): Result<List<ChatChannel>>
    suspend fun refreshFriends(): Result<List<FriendContact>>
    suspend fun syncChannelHistory(
        channelId: String,
        loadOlder: Boolean = false,
        force: Boolean = false
    ): Result<Unit>
    suspend fun loadChannelHistory(channelId: String): Result<Unit>

    suspend fun setUserAlias(
        username: String,
        customNickname: String?,
        userId: Long? = null,
        note: String? = null
    )
    suspend fun prefetchChatUserColors(usernames: Collection<String>)
    suspend fun toggleBookmarkMessage(message: ChatMessage, channelName: String): Result<Boolean>
    suspend fun removeBookmarkMessage(messageId: String)
    suspend fun getCachedSelfProfile(forceRefresh: Boolean = false): Result<PlayerProfile>
    suspend fun getPlayerProfile(usernameOrId: String, mode: String? = null): Result<PlayerProfile>
    suspend fun addFriend(profile: PlayerProfile): Result<FriendRelationStatus>
    suspend fun removeFriend(userId: Long, username: String): Result<FriendRelationStatus>
    suspend fun getBeatmapMetadata(linkRef: BeatmapLinkRef): Result<BeatmapMetadata>
    suspend fun getBeatmapDetails(beatmapId: Long): Result<BeatmapMetadata>
    suspend fun getCachedBeatmaps(beatmapIds: Collection<Long>): Map<Long, BeatmapMetadata>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setWidgetTheme(theme: com.osuchat.app.domain.model.WidgetTheme)
    suspend fun setWidgetTranslucent(translucent: Boolean)
    suspend fun setGlobalWallpaper(config: WallpaperConfig)
    suspend fun setChannelWallpaper(channelId: String, config: WallpaperConfig?)
    suspend fun setHighlightKeywords(keywords: List<String>)
    suspend fun setKeepAliveService(enabled: Boolean)
    suspend fun setAutoConnectOnBoot(enabled: Boolean)
    suspend fun setNotificationPreferences(notifyDm: Boolean, notifyKeywords: Boolean)
    suspend fun setAutoJoinConfig(enabled: Boolean, channels: List<String>)

    // Multiplayer Lobby Hub (Chat4osu)
    suspend fun createMultiplayerLobby(roomName: String): Result<String>
    suspend fun createAndSetupTournamentLobby(
        config: MatchSetupRunConfig,
        onStepUpdate: (String) -> Unit
    ): Result<String>
    suspend fun joinMultiplayerLobby(matchIdOrUrl: String): Result<ChatChannel>
    suspend fun sendMpCommand(channelId: String, mpCommand: String): Result<Unit>
    suspend fun fetchMatchHistory(matchId: Long): Result<MpMatchHistory>
    suspend fun fetchFullMatchAnalysis(matchId: Long): Result<MatchAnalysisReport>
    suspend fun saveMappool(poolId: Long?, name: String, slots: List<MappoolSlot>): Result<Long>
    suspend fun deleteMappool(poolId: Long): Result<Unit>
    suspend fun applyMappoolSlot(channelId: String, slot: MappoolSlot): Result<Unit>
    fun observeMatchSeriesState(channelId: String): Flow<MatchSeriesState>
    fun updateMatchSeriesState(channelId: String, transform: (MatchSeriesState) -> MatchSeriesState)
    suspend fun saveRefereeMacros(macros: List<RefereeMacro>)
    suspend fun resetRefereeMacros()
    suspend fun saveMatchSetupTemplates(templates: List<MatchSetupTemplate>)

    // Top 5 PP Score Tracker & Friend Alerts/Feed
    suspend fun toggleTrackFriendPp(username: String, userId: Long = 0L, avatarUrl: String = ""): Result<Boolean>
    suspend fun toggleFriendOnlineAlert(username: String, userId: Long = 0L, avatarUrl: String = ""): Result<Boolean>
    suspend fun checkTrackedFriendsTopPlays(forceNotifyLatest: Boolean = false): Result<Int>
    suspend fun refreshFriendScoreFeed(): Result<Int>

    // Daily Profile History Snapshots & Session Tracker
    suspend fun recordDailyProfileSnapshot(profile: SelfProfile)
    fun observeProfileHistory(mode: String, limitDays: Int = 90): Flow<List<ProfileHistoryPoint>>
    suspend fun fetchOsuTrackHistory(userId: Long, mode: String, days: Int = 90): Result<List<OsuTrackPoint>>
    suspend fun fetchOsuTrackPeak(userId: Long, mode: String): Result<OsuTrackPeak?>
    suspend fun fetchDailySessionSummary(
        userId: Long,
        mode: String,
        currentProfile: PlayerProfile? = null
    ): Result<DailySessionSummary>

    // Explore & Rich Scores / Beatmap Search / Rankings
    suspend fun getUserScores(
        usernameOrId: String,
        type: String = "best",
        includeFails: Boolean = true,
        mode: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<OsuScoreItem>>
    suspend fun getBeatmapScores(
        beatmapId: Long,
        mode: String? = null
    ): Result<List<OsuScoreItem>>
    suspend fun searchBeatmapsets(
        query: String,
        status: String? = null,
        modeInt: Int? = null
    ): Result<List<BeatmapSetSearchResult>>
    suspend fun searchBeatmapsets(filter: BeatmapSearchFilter): Result<List<BeatmapSetSearchResult>>
    suspend fun getRankings(mode: String = "osu", country: String? = null): Result<List<PlayerProfile>>
    suspend fun getRankingsPage(mode: String = "osu", page: Int = 1): Result<List<PlayerProfile>>
    suspend fun searchPlayers(query: String): Result<List<PlayerProfile>>

    // Play Later Queue & One-Tap Bancho Sync
    suspend fun toggleSaveBeatmap(item: SavedBeatmapItem): Result<Boolean>
    suspend fun removeSavedBeatmap(beatmapId: Long): Result<Unit>
    suspend fun updateSavedBeatmapTags(beatmapId: Long, tagsCsv: String): Result<Unit>
    suspend fun sendBeatmapToSelfInGame(item: SavedBeatmapItem): Result<Unit>
    suspend fun sendAllSavedBeatmapsToSelfInGame(): Result<Int>

    // Rank <-> PP Estimation & What-If Rank Projection
    suspend fun estimatePpForRank(mode: String, targetRank: Int): Result<RankPpConversionResult>
    suspend fun estimateRankForPp(
        mode: String,
        targetPp: Double,
        currentRankHint: Int? = null,
        currentPpHint: Double? = null
    ): Result<RankPpConversionResult>

    // News & Seasonal Spotlights
    suspend fun getNewsAndSpotlights(forceRefresh: Boolean = false): Result<NewsAndSpotlightsFeed>

    // Seasonal Wallpapers & Chat Log Export
    suspend fun fetchSeasonalBackgrounds(forceRefresh: Boolean = false): Result<List<SeasonalBackgroundItem>>
    suspend fun downloadSeasonalWallpaper(imageUrl: String): Result<String>
    suspend fun exportChatLog(
        channelId: String,
        format: ChatLogExportFormat,
        destinationUri: Uri
    ): Result<Int>

    companion object {
        const val DEFAULT_MESSAGE_WINDOW = 200
        const val MAX_MESSAGE_WINDOW = 1000
    }
}
