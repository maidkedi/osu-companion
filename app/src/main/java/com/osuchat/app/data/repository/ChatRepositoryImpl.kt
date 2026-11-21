package com.osuchat.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.osuchat.app.data.irc.BanchoIrcClient
import com.osuchat.app.data.irc.IrcAuthException
import com.osuchat.app.data.irc.IrcEvent
import com.osuchat.app.data.local.BeatmapCacheDao
import com.osuchat.app.data.local.BeatmapCacheEntity
import com.osuchat.app.data.local.BookmarkedMessageDao
import com.osuchat.app.data.local.BookmarkedMessageEntity
import com.osuchat.app.data.local.ChannelDao
import com.osuchat.app.data.local.ChannelEntity
import com.osuchat.app.data.local.FriendScoreEventDao
import com.osuchat.app.data.local.FriendScoreEventEntity
import com.osuchat.app.data.local.MappoolDao
import com.osuchat.app.data.local.MappoolSlotEntity
import com.osuchat.app.data.local.MessageDao
import com.osuchat.app.data.local.MessageEntity
import com.osuchat.app.data.local.PlayerProfileCacheDao
import com.osuchat.app.data.local.PlayerProfileCacheEntity
import com.osuchat.app.data.local.ProfileHistoryDao
import com.osuchat.app.data.local.ProfileHistoryEntity
import com.osuchat.app.data.local.SavedBeatmapDao
import com.osuchat.app.data.local.SavedBeatmapEntity
import com.osuchat.app.data.local.SelfProfileCacheDao
import com.osuchat.app.data.local.SelfProfileCacheEntity
import com.osuchat.app.data.local.TrackedFriendDao
import com.osuchat.app.data.local.TrackedFriendEntity
import com.osuchat.app.data.local.UserAliasDao
import com.osuchat.app.data.local.UserAliasEntity
import com.osuchat.app.data.network.ChatChannelDto
import com.osuchat.app.data.network.ChatMessageDto
import com.osuchat.app.data.network.NetworkConnectivityMonitor
import com.osuchat.app.data.network.OAuthCallbackResult
import com.osuchat.app.data.network.OAuthConfig
import com.osuchat.app.data.network.OAuthPkceManager
import com.osuchat.app.data.network.OAuthTokenExchangeRequest
import com.osuchat.app.data.network.OAuthTokenResponse
import com.osuchat.app.data.network.OsuApiException
import com.osuchat.app.data.network.OsuApiPoller
import com.osuchat.app.data.network.OsuApiPollerEvent
import com.osuchat.app.data.network.OsuApiService
import com.osuchat.app.data.network.OsuAuthService
import com.osuchat.app.data.network.OsuBeatmapDto
import com.osuchat.app.data.network.OsuBeatmapsetDto
import com.osuchat.app.data.network.OsuCreateChannelRequestDto
import com.osuchat.app.data.network.OsuMatchEventDto
import com.osuchat.app.data.network.OsuMatchHeaderDto
import com.osuchat.app.data.network.OsuOAuthErrorDto
import com.osuchat.app.data.network.OsuScoreDto
import com.osuchat.app.data.network.OsuUserDto
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapSearchFilter
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.BookmarkedMessageItem
import com.osuchat.app.domain.model.ChannelHistoryState
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChannelType
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
import com.osuchat.app.domain.model.MatchAnalysisGame
import com.osuchat.app.domain.model.MatchAnalysisPlayerScore
import com.osuchat.app.domain.model.MatchAnalysisReport
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MatchSetupRunConfig
import com.osuchat.app.domain.model.MatchSetupTemplate
import com.osuchat.app.domain.model.MpCompletedGame
import com.osuchat.app.domain.model.MpGameScoreSummary
import com.osuchat.app.domain.model.MpMatchHistory
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.NewsAndSpotlightsFeed
import com.osuchat.app.domain.model.OfficialOsuChannels
import com.osuchat.app.domain.model.OsuNewsItem
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuSpotlightItem
import com.osuchat.app.domain.model.OsuTrackPeak
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.OsuUserMedalUnlock
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.domain.model.SavedBeatmapItem
import com.osuchat.app.domain.model.SeasonalBackgroundItem
import com.osuchat.app.domain.model.SelfProfile
import com.osuchat.app.domain.model.ThemeMode
import com.osuchat.app.domain.model.UserAlias
import com.osuchat.app.domain.model.WallpaperConfig
import com.osuchat.app.domain.model.WidgetTheme
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.service.ChatNotificationManager
import com.osuchat.app.service.MpTimerManager
import com.osuchat.app.util.AppShortcutManager
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.ChatHistorySyncHelper
import com.osuchat.app.util.ChatLogFormatter
import com.osuchat.app.util.MilestoneEvaluationResult
import com.osuchat.app.util.MpLobbyParser
import com.osuchat.app.util.NewTopPlayMatch
import com.osuchat.app.util.OsuBeatmapCalculator
import com.osuchat.app.util.RankPpMath
import com.osuchat.app.util.RosuPpEngine
import com.osuchat.app.util.SessionTrackerCalculator
import com.osuchat.app.util.TopPlayCandidate
import com.osuchat.app.util.TrackedPlayerAlertEngine
import com.osuchat.app.util.TrackedPlayerAlertEngine.toTopPlayCandidate
import com.osuchat.app.widget.BanchoStatsWidget
import com.osuchat.app.worker.TrackedPlayerAlertWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val userAliasDao: UserAliasDao,
    private val beatmapCacheDao: BeatmapCacheDao,
    private val playerProfileCacheDao: PlayerProfileCacheDao,
    private val trackedFriendDao: TrackedFriendDao,
    private val savedBeatmapDao: SavedBeatmapDao,
    private val selfProfileCacheDao: SelfProfileCacheDao,
    private val mappoolDao: MappoolDao,
    private val bookmarkedMessageDao: BookmarkedMessageDao,
    private val friendScoreEventDao: FriendScoreEventDao,
    private val profileHistoryDao: ProfileHistoryDao,
    private val prefs: UserPreferencesDataStore,
    private val authService: OsuAuthService,
    private val apiService: OsuApiService,
    private val gson: Gson,
    private val pkceManager: OAuthPkceManager,
    private val apiPoller: OsuApiPoller,
    private val ircClient: BanchoIrcClient,
    private val notificationManager: ChatNotificationManager,
    private val mpTimerManager: MpTimerManager,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val okHttpClient: OkHttpClient,
    private val rosuPpEngine: RosuPpEngine,
    private val appShortcutManager: AppShortcutManager,
    private val osuTrackRepository: OsuTrackRepository
) : ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val oauthExchangeMutex = Mutex()
    private val outboxFlushMutex = Mutex()
    private val trackedAlertSyncMutex = Mutex()
    private val okHttpExchangeActive = AtomicBoolean(false)
    private val modeProfileCache = ConcurrentHashMap<String, Pair<Long, PlayerProfile>>()
    private val beatmapMemoryCache = LruCache<String, BeatmapMetadata>(128)
    private val inFlightBeatmapLookups = ConcurrentHashMap<String, Deferred<Result<BeatmapMetadata>>>()
    private val inFlightProfileLookups = ConcurrentHashMap<String, Deferred<Result<PlayerProfile>>>()
    private val beatmapScoresCache = ConcurrentHashMap<String, Pair<Long, List<OsuScoreItem>>>()
    private val rankingsPageCache = ConcurrentHashMap<String, Pair<Long, List<PlayerProfile>>>()
    private val userAchievementsCache = ConcurrentHashMap<Long, List<OsuUserMedalUnlock>>()
    private val userRankHistoryCache = ConcurrentHashMap<String, List<Int>>()
    private val matchSeriesStates = MutableStateFlow<Map<String, MatchSeriesState>>(emptyMap())
    @Volatile
    private var newsFeedCache: Pair<Long, NewsAndSpotlightsFeed>? = null
    @Volatile
    private var seasonalBackgroundsCache: Pair<Long, List<SeasonalBackgroundItem>>? = null
    @Volatile
    private var selfProfileMemoryCache: PlayerProfile? = null

    override val cloudflareChallengeUrl: StateFlow<String?> =
        apiPoller.cloudflareChallengeManager.challengeUrl

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.OFFLINE)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    override val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _isIrcAuthError = MutableStateFlow(false)
    override val isIrcAuthError: StateFlow<Boolean> = _isIrcAuthError.asStateFlow()

    private val _isSessionExpired = MutableStateFlow(false)
    override val isSessionExpired: StateFlow<Boolean> = _isSessionExpired.asStateFlow()

    private val _requiresChatReadReauth = MutableStateFlow(false)
    override val requiresChatReadReauth: StateFlow<Boolean> = _requiresChatReadReauth.asStateFlow()

    private val sessionExpiredHandled = AtomicBoolean(false)

    private val _pendingOAuthCode = MutableStateFlow<String?>(null)
    override val pendingOAuthCode: StateFlow<String?> = _pendingOAuthCode.asStateFlow()

    private val _activeChannelId = MutableStateFlow<String?>(null)
    override val activeChannelId: StateFlow<String?> = _activeChannelId.asStateFlow()

    private val _sessionConnectedAt = MutableStateFlow(System.currentTimeMillis())
    override val sessionConnectedAt: StateFlow<Long> = _sessionConnectedAt.asStateFlow()

    private val _createdMpChannelEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val createdMpChannelEvents: SharedFlow<String> = _createdMpChannelEvents.asSharedFlow()

    override val trackedFriendNorms: Flow<Set<String>> = trackedFriendDao.observeTrackedFriends()
        .map { entities ->
            entities.filter { it.lastCheckedAt > 0L || it.topScoreIdsCsv.isNotEmpty() }
                .map { it.normalizedUsername }
                .toSet()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val trackedAlertConfigs: Flow<Map<String, TrackedPlayerAlertConfig>> =
        prefs.trackedAlertConfigsFlow
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    private val onlineAlertFriendNorms: Flow<Set<String>> = trackedFriendDao.observeTrackedFriends()
        .map { entities ->
            entities.filter { it.notifyOnline }
                .map { it.normalizedUsername }
                .toSet()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val refereeMacros: Flow<List<RefereeMacro>> = prefs.refereeMacrosFlow
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val matchSetupTemplates: Flow<List<MatchSetupTemplate>> = prefs.matchSetupTemplatesFlow
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val savedBeatmaps: Flow<List<SavedBeatmapItem>> = savedBeatmapDao.observeSavedBeatmaps()
        .map { list -> list.map { it.toDomain() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val mappools: Flow<List<Mappool>> = combine(
        mappoolDao.observeMappools().distinctUntilChanged(),
        mappoolDao.observeAllSlots().distinctUntilChanged()
    ) { pools, slots ->
        val slotsByPoolId = slots.groupBy { it.poolId }
        pools.map { pool ->
            Mappool(
                id = pool.id,
                name = pool.name,
                createdAt = pool.createdAt,
                slots = slotsByPoolId[pool.id].orEmpty()
                    .sortedWith(compareBy({ it.orderIndex }, { it.id }))
                    .map { slot ->
                        MappoolSlot(
                            id = slot.id,
                            poolId = slot.poolId,
                            slotTag = slot.slotTag,
                            beatmapId = slot.beatmapId,
                            mods = slot.mods,
                            orderIndex = slot.orderIndex
                        )
                    }
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private val _apiFriends = MutableStateFlow<List<OsuUserDto>>(emptyList())
    private val _onlineIrcNicks = MutableStateFlow<Set<String>>(emptySet())
    private val _seenOnlineSenders = ConcurrentHashMap.newKeySet<String>()
    private val lastKnownOnlineByUserId = ConcurrentHashMap<Long, Boolean>()
    private val lastKnownOnlineByNorm = ConcurrentHashMap<String, Boolean>()
    private val hasSeededFriendOnlineState = AtomicBoolean(false)
    private val lastFriendOnlineAlertAt = ConcurrentHashMap<String, Long>()

    private val channelUsersState = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()
    private val ircNamesAccum = ConcurrentHashMap<String, MutableSet<String>>()

    private data class RecentLocalOutgoing(
        val localMessageId: String,
        val channelId: String,
        val trimmedContent: String,
        val sentAt: Long
    )
    private val recentLocalOutgoing = CopyOnWriteArrayList<RecentLocalOutgoing>()

    private val _chatUserColors = MutableStateFlow<Map<String, ChatUserColorInfo>>(emptyMap())
    override val chatUserColors: StateFlow<Map<String, ChatUserColorInfo>> = _chatUserColors.asStateFlow()
    private val inFlightColorLookups = ConcurrentHashMap.newKeySet<String>()
    private val failedColorLookupAt = ConcurrentHashMap<String, Long>()
    private val colorLookupSemaphore = Semaphore(2)

    private val apiChannelIdCache = ConcurrentHashMap<String, Long>()
    private val apiDmChannelIdByUserId = ConcurrentHashMap<Long, Long>()
    private val publicChannelsFetchMutex = Mutex()
    @Volatile
    private var publicChannelsCatalogLoaded = false
    private val _channelHistoryStates = MutableStateFlow<Map<String, ChannelHistoryState>>(emptyMap())
    private val inFlightHistoryChannels = ConcurrentHashMap.newKeySet<String>()

    private sealed interface ChatHistoryCallResult<out T> {
        data class Success<T>(val body: T?) : ChatHistoryCallResult<T>
        data object ForbiddenReauth : ChatHistoryCallResult<Nothing>
        data object NotFound : ChatHistoryCallResult<Nothing>
    }

    private data class ResolvedApiChannel(
        val apiChannelId: Long,
        val recentMessages: List<ChatMessageDto>? = null
    )

    override val settings: Flow<AppSettings> = prefs.settingsFlow
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val mutedChannels: Flow<Set<String>> = prefs.mutedChannels
    override val dotOnlyChannels: Flow<Set<String>> = prefs.dotOnlyChannels
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val allAliases: Flow<List<UserAlias>> = userAliasDao.observeAllAliases()
        .map { entities ->
            entities.map {
                UserAlias(
                    normalizedUsername = it.normalizedUsername,
                    originalUsername = it.originalUsername,
                    userId = it.userId,
                    customNickname = it.customNickname,
                    note = it.note,
                    updatedAt = it.updatedAt
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private val aliasNicknameMapFlow: Flow<Map<String, String>> = userAliasDao.observeAllAliases()
        .map { entities ->
            entities.mapNotNull { entity ->
                entity.customNickname.takeIf { it.isNotBlank() }?.let { entity.normalizedUsername to it }
            }.toMap()
        }
        .distinctUntilChanged()

    override val bookmarkedMessages: Flow<List<BookmarkedMessageItem>> = bookmarkedMessageDao.observeBookmarks()
        .map { list ->
            list.map {
                BookmarkedMessageItem(
                    messageId = it.messageId,
                    channelId = it.channelId,
                    channelName = it.channelName,
                    senderName = it.senderName,
                    content = it.content,
                    messageTimestamp = it.messageTimestamp,
                    bookmarkedAt = it.bookmarkedAt
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val bookmarkedMessageIds: Flow<Set<String>> = bookmarkedMessageDao.observeBookmarkedMessageIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val channels: Flow<List<ChatChannel>> = combine(
        channelDao.observeJoinedChannels().distinctUntilChanged(),
        aliasNicknameMapFlow,
        mutedChannels
    ) { channelEntities, aliasByNorm, mutedSet ->
        channelEntities.map { entity ->
            val alias = if (entity.type == ChannelType.DIRECT.name) {
                aliasByNorm[BeatmapUrlParser.normalizeUsername(entity.name)]
            } else {
                null
            }
            entity.toDomain(customAlias = alias, mutedChannels = mutedSet)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val friends: Flow<List<FriendContact>> = combine(
        _apiFriends,
        aliasNicknameMapFlow,
        prefs.settingsFlow.map { it.username }.distinctUntilChanged(),
        trackedFriendNorms,
        onlineAlertFriendNorms
    ) { apiFriends, aliasByNorm, username, trackedNorms, onlineAlertNorms ->
        val selfNorm = BeatmapUrlParser.normalizeUsername(username)

        apiFriends
            .mapNotNull { apiDto ->
                val rawUsername = apiDto.username?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val norm = BeatmapUrlParser.normalizeUsername(rawUsername)
                if (norm.isBlank() || norm == selfNorm) return@mapNotNull null

                val aliasNickname = aliasByNorm[norm]
                val isOnline = apiDto.isOnline == true
                val lastSeenFormatted = formatLastVisit(apiDto.lastVisit)
                val statusText = when {
                    isOnline -> "Online on osu!"
                    !lastSeenFormatted.isNullOrBlank() -> "Last seen $lastSeenFormatted"
                    else -> "Offline"
                }

                FriendContact(
                    userId = apiDto.id,
                    username = rawUsername,
                    customAlias = aliasNickname,
                    avatarUrl = apiDto.avatarUrl ?: "https://a.ppy.sh/${apiDto.id}",
                    isOnline = isOnline,
                    isMutual = apiDto.isMutual == true,
                    statusText = statusText,
                    lastVisit = lastSeenFormatted,
                    pp = apiDto.statistics?.pp,
                    globalRank = apiDto.statistics?.globalRank,
                    isTrackedForPp = norm in trackedNorms,
                    notifyOnline = norm in onlineAlertNorms,
                    countryCode = apiDto.country?.code ?: apiDto.countryCode ?: "XX",
                    accuracy = apiDto.statistics?.hitAccuracy,
                    playCount = apiDto.statistics?.playCount,
                    mode = apiDto.playmode?.takeIf { it in VALID_OSU_MODES } ?: "osu"
                )
            }
            .distinctBy { BeatmapUrlParser.normalizeUsername(it.username) }
            .sortedWith(
                compareByDescending<FriendContact> { it.isOnline }
                    .thenBy { it.shortName.lowercase() }
            )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val friendScoreEvents: Flow<List<FriendScoreEventItem>> = combine(
        friendScoreEventDao.observeRecentEvents(100),
        aliasNicknameMapFlow
    ) { entities, aliasByNorm ->
        entities.map { entity ->
            val norm = BeatmapUrlParser.normalizeUsername(entity.username)
            FriendScoreEventItem(
                scoreId = entity.scoreId,
                userId = entity.userId,
                username = entity.username,
                customAlias = aliasByNorm[norm],
                avatarUrl = entity.avatarUrl,
                beatmapId = entity.beatmapId,
                beatmapTitle = entity.beatmapTitle,
                beatmapVersion = entity.beatmapVersion,
                starRating = entity.starRating,
                rank = entity.rank,
                pp = entity.pp,
                accuracy = entity.accuracy,
                mods = entity.modsCsv.split(',').mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() } },
                createdAtIso = entity.createdAtIso,
                recordedAt = entity.recordedAt
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    init {
        observeIrcClientStatusAndEvents()
        observeApiPollerEvents()
        observeNetworkTransitions()
        scope.launch {
            migrateLegacyDmIds()
            loadPersistedFriendsIntoState()
            val explicitlyDisconnected = prefs.isExplicitlyDisconnected()
            ircClient.setExplicitlyDisconnected(explicitlyDisconnected)
            ensureDefaultIrcChannels()
            if (!explicitlyDisconnected) {
                reconnectIfAuthenticated()
            } else {
                _connectionStatus.value = ConnectionStatus.OFFLINE
            }
            if (selfProfileMemoryCache == null) {
                selfProfileCacheDao.getByKey()?.let { cached ->
                    val cachedNorm = BeatmapUrlParser.normalizeUsername(cached.username)
                    val cachedAliasEntity = userAliasDao.getAlias(cachedNorm)
                    val cachedTracked = trackedFriendDao.getTrackedFriend(cachedNorm)
                    selfProfileMemoryCache = cached.toDomain(
                        customAlias = cachedAliasEntity?.customNickname?.takeIf { it.isNotBlank() },
                        customNote = cachedAliasEntity?.note?.takeIf { it.isNotBlank() },
                        isTrackedForPp = cachedTracked != null &&
                            (cachedTracked.lastCheckedAt > 0L || cachedTracked.topScoreIdsCsv.isNotEmpty()),
                        notifyOnline = cachedTracked?.notifyOnline == true,
                        friendStatus = FriendRelationStatus.NONE
                    )
                } ?: run {
                    val s = prefs.settingsFlow.first()
                    if (s.authMode == AuthMode.OAUTH_V2 && !explicitlyDisconnected) {
                        getCachedSelfProfile(forceRefresh = true)
                    }
                }
            }
            if (!explicitlyDisconnected) {
                refreshFriends()
            }
        }
        startPpTrackerLoop()
        observeTrackedAlertWorkerSchedule()
    }

    private fun observeTrackedAlertWorkerSchedule() {
        scope.launch {
            combine(
                prefs.settingsFlow
                    .map { it.trackedAlertsEnabled to it.trackedAlertsIntervalMinutes }
                    .distinctUntilChanged(),
                trackedFriendNorms
                    .map { it.isNotEmpty() }
                    .distinctUntilChanged()
            ) { (enabled, interval), hasTracked ->
                Triple(enabled, interval, hasTracked)
            }
                .distinctUntilChanged()
                .collect { (enabled, interval, hasTracked) ->
                    TrackedPlayerAlertWorker.syncSchedule(
                        context = context,
                        enabled = enabled,
                        intervalMinutes = interval,
                        hasTrackedPlayers = hasTracked
                    )
                }
        }
    }

    private suspend fun migrateLegacyDmIds() {
        runCatching {
            channelDao.migrateAndNormalizeChannels()
        }
    }

    private fun startPpTrackerLoop() {
        scope.launch {
            delay(15_000L)
            while (true) {
                if (apiPoller.isForeground.value && networkMonitor.isOnline.value) {
                    val current = prefs.settingsFlow.first()
                    if (current.authMode == AuthMode.OAUTH_V2 && !current.isExplicitlyDisconnected) {
                        runCatching { refreshFriends() }
                        if (current.trackedAlertsEnabled) {
                            runCatching { checkTrackedFriendsTopPlays() }
                        }
                    }
                }
                delay(180_000L)
            }
        }
    }

    private fun observeNetworkTransitions() {
        scope.launch {
            networkMonitor.isOnline.drop(1).collect { online ->
                val currentSettings = prefs.settingsFlow.first()
                if (currentSettings.authMode == AuthMode.NONE) return@collect
                if (currentSettings.isExplicitlyDisconnected ||
                    prefs.isExplicitlyDisconnected() ||
                    ircClient.isExplicitlyDisconnected()
                ) {
                    return@collect
                }

                if (!online) {
                    _connectionStatus.value = ConnectionStatus.OFFLINE
                    _connectionError.value = "No internet connection — waiting for network…"
                } else {
                    if (_connectionError.value?.startsWith("No internet") == true) {
                        _connectionError.value = null
                    }
                    when (currentSettings.authMode) {
                        AuthMode.OAUTH_V2 -> {
                            if (currentSettings.hasIrcPassword) {
                                if (ircClient.status.value == ConnectionStatus.ONLINE) {
                                    scope.launch { flushQueuedMessages() }
                                } else {
                                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                                    ircClient.onNetworkChanged()
                                    reconnectIfAuthenticated()
                                }
                            }
                        }
                        AuthMode.IRC -> {
                            if (ircClient.status.value == ConnectionStatus.ONLINE) {
                                scope.launch { flushQueuedMessages() }
                            } else {
                                _connectionStatus.value = ConnectionStatus.RECONNECTING
                                ircClient.onNetworkChanged()
                                reconnectIfAuthenticated()
                            }
                        }
                        AuthMode.NONE -> Unit
                    }
                }
            }
        }

        scope.launch {
            networkMonitor.networkSwitchEvents.collect {
                val currentSettings = prefs.settingsFlow.first()
                if (currentSettings.isExplicitlyDisconnected ||
                    prefs.isExplicitlyDisconnected() ||
                    ircClient.isExplicitlyDisconnected()
                ) {
                    return@collect
                }
                when (currentSettings.authMode) {
                    AuthMode.OAUTH_V2 -> {
                        if (currentSettings.hasIrcPassword) {
                            _connectionStatus.value = ConnectionStatus.RECONNECTING
                            ircClient.onNetworkChanged()
                            reconnectIfAuthenticated()
                        }
                    }
                    AuthMode.IRC -> {
                        _connectionStatus.value = ConnectionStatus.RECONNECTING
                        ircClient.onNetworkChanged()
                        reconnectIfAuthenticated()
                    }
                    AuthMode.NONE -> Unit
                }
            }
        }
    }

    private fun observeIrcClientStatusAndEvents() {
        scope.launch {
            ircClient.status.collect { ircStatus ->
                val currentSettings = prefs.settingsFlow.first()
                val ircActive = currentSettings.authMode == AuthMode.IRC ||
                    (currentSettings.authMode == AuthMode.OAUTH_V2 && currentSettings.hasIrcPassword)
                if (ircActive) {
                    _connectionStatus.value = ircStatus
                    if (ircStatus == ConnectionStatus.ONLINE) {
                        _connectionError.value = null
                        _isIrcAuthError.value = false
                        scope.launch { flushQueuedMessages() }
                    }
                }
            }
        }
        scope.launch {
            ircClient.isAuthError.collect { authErr ->
                _isIrcAuthError.value = authErr
            }
        }
        scope.launch {
            ircClient.events.collect { event ->
                handleIrcEvent(event)
            }
        }
    }

    private fun observeApiPollerEvents() {
        scope.launch {
            apiPoller.events.collect { event ->
                when (event) {
                    is OsuApiPollerEvent.UpdatesReceived -> {
                        _connectionStatus.value = ConnectionStatus.ONLINE
                        _connectionError.value = null
                        _isSessionExpired.value = false
                        val currentSettings = prefs.settingsFlow.first()
                        event.updates.presence.orEmpty().forEach { dto ->
                            upsertApiChannel(dto, currentSettings.userId)
                        }
                        event.updates.messages.orEmpty().forEach { dto ->
                            persistApiMessage(dto, currentSettings, allowNotification = true)
                        }
                    }
                    is OsuApiPollerEvent.Reconnecting -> {
                        val currentSettings = prefs.settingsFlow.first()
                        val ircReady = currentSettings.authMode == AuthMode.IRC ||
                            (currentSettings.authMode == AuthMode.OAUTH_V2 && currentSettings.hasIrcPassword)
                        if (ircReady && ircClient.status.value != ConnectionStatus.ONLINE) {
                            _connectionStatus.value = ConnectionStatus.RECONNECTING
                        }
                    }
                    is OsuApiPollerEvent.AuthFailed -> {
                        if (sessionExpiredHandled.compareAndSet(false, true)) {
                            apiPoller.stopPolling()
                            _pendingOAuthCode.value = null
                            pkceManager.stopLoopbackServer()
                            ircClient.disconnectExplicitly()
                            _onlineIrcNicks.value = emptySet()
                            _seenOnlineSenders.clear()
                            selfProfileMemoryCache = null
                            runCatching { selfProfileCacheDao.clearAll() }
                            prefs.clearOAuthTokens()
                            ircClient.resetExplicitDisconnect()
                            _isIrcAuthError.value = false
                            _isSessionExpired.value = true
                            _connectionStatus.value = ConnectionStatus.OFFLINE
                            _connectionError.value = event.message.ifBlank {
                                "Session expired, please sign in again"
                            }
                        }
                    }
                }
            }
        }
    }

    override fun observeMessages(channelId: String, limit: Int): Flow<List<ChatMessage>> {
        val normalizedId = normalizeChannelId(channelId)
        val safeLimit = limit.coerceIn(1, ChatRepository.MAX_MESSAGE_WINDOW)
        return combine(
            messageDao.observeRecentMessagesForChannel(normalizedId, safeLimit)
                .distinctUntilChanged(),
            aliasNicknameMapFlow,
            prefs.settingsFlow
        ) { messages, aliasByNorm, currentSettings ->
            messages.map { entity ->
                val alias = aliasByNorm[BeatmapUrlParser.normalizeUsername(entity.senderName)]
                val effectiveEntity = if (!entity.isOutgoing && BeatmapUrlParser.isSelfOsuSender(
                        senderName = entity.senderName,
                        senderId = entity.senderId,
                        currentUserId = currentSettings.userId,
                        currentUsername = currentSettings.username,
                        currentIrcUsername = currentSettings.ircUsername
                    )
                ) {
                    entity.copy(isOutgoing = true)
                } else {
                    entity
                }
                effectiveEntity.toDomain(senderAlias = alias)
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override fun observeChannel(channelId: String): Flow<ChatChannel?> {
        val normalizedId = normalizeChannelId(channelId)
        return combine(
            channelDao.observeChannel(normalizedId).distinctUntilChanged(),
            aliasNicknameMapFlow,
            mutedChannels
        ) { entity, aliasByNorm, mutedSet ->
            entity?.let {
                val alias = if (it.type == ChannelType.DIRECT.name) {
                    aliasByNorm[BeatmapUrlParser.normalizeUsername(it.name)]
                } else {
                    null
                }
                it.toDomain(customAlias = alias, mutedChannels = mutedSet)
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override fun observeChannelWallpaper(channelId: String): Flow<WallpaperConfig?> {
        return prefs.observeChannelWallpaper(normalizeChannelId(channelId))
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override fun observeChannelUsers(channelId: String): Flow<List<String>> {
        val normId = normalizeChannelId(channelId)
        return channelUsersState.getOrPut(normId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    override fun observeChannelHistoryState(channelId: String): Flow<ChannelHistoryState> {
        val normId = normalizeChannelId(channelId)
        return _channelHistoryStates
            .map { it[normId] ?: ChannelHistoryState() }
            .distinctUntilChanged()
    }

    override fun setActiveChannel(channelId: String?) {
        val normId = channelId?.let(::normalizeChannelId)
        _activeChannelId.value = normId
        mpTimerManager.updateActiveChannel(normId)
        if (normId != null) {
            notificationManager.clearChannelNotifications(normId)
        }
    }

    override fun setAppInForeground(inForeground: Boolean) {
        apiPoller.setForeground(inForeground)
        mpTimerManager.updateAppInForeground(inForeground)
    }

    override fun resolveCloudflareChallenge(verified: Boolean) {
        apiPoller.cloudflareChallengeManager.resolveChallenge(verified)
    }

    override fun clearConnectionError() {
        _connectionError.value = null
        _isSessionExpired.value = false
        ircClient.clearAuthError()
        _isIrcAuthError.value = false
        if (_connectionStatus.value == ConnectionStatus.ERROR) {
            _connectionStatus.value = ConnectionStatus.OFFLINE
        }
    }

    override fun prepareInAppOAuthAuthorizeUrl(): String {
        _connectionError.value = null
        _isSessionExpired.value = false
        _isIrcAuthError.value = false
        _pendingOAuthCode.value = null
        sessionExpiredHandled.set(false)
        apiPoller.cloudflareChallengeManager.clearCloudflareCookies()
        return pkceManager.prepareAuthorizeUriForInAppWebView().toString()
    }

    override fun isOAuthCallbackUri(uri: Uri): Boolean = pkceManager.isOAuthCallbackUri(uri)

    override fun cancelOAuthLoginAttempt() {
        pkceManager.cancelActiveLoginAttempt()
        _pendingOAuthCode.value = null
        if (_connectionStatus.value == ConnectionStatus.CONNECTING) {
            _connectionStatus.value = ConnectionStatus.OFFLINE
        }
    }

    override suspend fun launchOAuthLogin(context: Context, customClientId: String?) {
        _connectionError.value = null
        _isSessionExpired.value = false
        _isIrcAuthError.value = false
        _pendingOAuthCode.value = null
        sessionExpiredHandled.set(false)
        apiPoller.cloudflareChallengeManager.clearCloudflareCookies()
        val clientId = OAuthConfig.CLIENT_ID.trim()
        val state = withContext(Dispatchers.IO) {
            val generatedState = pkceManager.prepareNewLoginAttempt()
            runCatching {
                pkceManager.startLoopbackServer(expectedState = generatedState) { codeResult ->
                    codeResult.fold(
                        onSuccess = { code ->
                            val cleanCode = code.trim()
                            if (cleanCode.isEmpty()) {
                                _connectionStatus.value = ConnectionStatus.ERROR
                                _connectionError.value = "Empty authorization code received from osu!"
                            } else if (!pkceManager.isCodeConsumed(cleanCode) && !pkceManager.isExchangeInFlight()) {
                                _connectionError.value = null
                                _connectionStatus.value = ConnectionStatus.CONNECTING
                                scope.launch {
                                    completeOAuthWithCode(cleanCode)
                                }
                            }
                        },
                        onFailure = { err ->
                            _connectionStatus.value = ConnectionStatus.ERROR
                            _connectionError.value = err.message ?: "OAuth callback failed"
                        }
                    )
                }
            }.onFailure { err ->
                Log.e("OsuOAuth", "Failed to start loopback OAuth server on ${OAuthConfig.LOOPBACK_HOST}:${OAuthConfig.LOOPBACK_PORT}", err)
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = "Failed to start local OAuth server: ${err.message}"
                return@withContext null
            }
            generatedState
        } ?: return

        val authorizeUri = pkceManager.buildAuthorizeUri(clientId, state)
        pkceManager.launchCustomTab(context, authorizeUri)
    }

    override suspend fun handleOAuthRedirect(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        when (val parsed = pkceManager.validateAndExtractCode(uri)) {
            is OAuthCallbackResult.LoopbackAck -> Result.success(Unit)
            is OAuthCallbackResult.Error -> {
                pkceManager.stopLoopbackServer()
                _connectionError.value = parsed.message
                _connectionStatus.value = ConnectionStatus.ERROR
                Result.failure(IllegalStateException(parsed.message))
            }
            is OAuthCallbackResult.Invalid -> {
                pkceManager.stopLoopbackServer()
                _connectionError.value = parsed.reason
                _connectionStatus.value = ConnectionStatus.ERROR
                Result.failure(IllegalStateException(parsed.reason))
            }
            is OAuthCallbackResult.Code -> {
                if (!pkceManager.tryClaimCallbackCode(parsed.code)) {
                    return@withContext Result.success(Unit)
                }
                completeOAuthWithCode(parsed.code)
            }
        }
    }

    override fun tryClaimOAuthCodeForExchange(code: String): Boolean {
        return pkceManager.tryClaimCodeForExchange(code)
    }

    override fun isOAuthCodeConsumed(code: String): Boolean {
        return pkceManager.isCodeConsumed(code)
    }

    override fun isOAuthExchangeInFlight(): Boolean {
        return pkceManager.isExchangeInFlight()
    }

    override fun clearPendingOAuthCode() {
        _pendingOAuthCode.value = null
        if (!okHttpExchangeActive.get()) {
            pkceManager.markExchangeFinished()
        }
    }

    override suspend fun exchangeOAuthCodeViaOkHttp(code: String): Result<Unit> {
        _pendingOAuthCode.value = null
        return completeOAuthWithCode(code)
    }

    private suspend fun completeOAuthWithCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        oauthExchangeMutex.withLock {
            val cleanCode = code.trim()
            if (cleanCode.isEmpty()) {
                val err = IllegalStateException("Empty authorization code received from osu!")
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = err.message
                return@withLock Result.failure(err)
            }

            if (!pkceManager.tryClaimCodeForExchange(cleanCode)) {
                val currentMode = prefs.settingsFlow.first().authMode
                if (currentMode == AuthMode.OAUTH_V2) {
                    return@withLock Result.success(Unit)
                }
                val err = IllegalStateException("Authorization code was already used. Please tap Sign in with osu! to try again.")
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = err.message
                return@withLock Result.failure(err)
            }

            sessionExpiredHandled.set(false)
            _isSessionExpired.value = false
            okHttpExchangeActive.set(true)
            val clientId = OAuthConfig.CLIENT_ID.trim()
            val codeVerifier = runCatching {
                prefs.getPendingPkce()?.codeVerifier?.trim()?.takeIf { it.isNotBlank() }
            }.getOrNull()

            _connectionStatus.value = ConnectionStatus.CONNECTING
            _connectionError.value = null

            try {
                val tokenRes = authService.exchangeAuthorizationCode(
                    OAuthTokenExchangeRequest(
                        grantType = "authorization_code",
                        code = cleanCode,
                        redirectUri = OAuthConfig.REDIRECT_URI,
                        codeVerifier = codeVerifier
                    )
                )
                val accessToken = tokenRes.accessToken?.trim().orEmpty()
                if (accessToken.isBlank()) {
                    throw IllegalStateException("Empty access_token from osu!")
                }
                apiPoller.resetAuthState()

                val bearer = "Bearer $accessToken"
                val me = apiService.getMe(bearer)
                val fetchedFriends = runCatching { apiService.getFriends(bearer) }.getOrNull()
                if (fetchedFriends != null) {
                    applyApiFriendsList(fetchedFriends)
                }
                persistOAuthProfileAndConnectIrc(tokenRes, me, clientId)
                Result.success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: HttpException) {
                val parsedError = parseOAuthHttpError(e)
                Log.e("OsuOAuth", "OAuth HTTP ${e.code()}: $parsedError", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = parsedError
                Result.failure(IllegalStateException(parsedError, e))
            } catch (e: Exception) {
                Log.e("OsuOAuth", "OAuth login failed", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = e.message ?: "OAuth login failed"
                Result.failure(e)
            } finally {
                okHttpExchangeActive.set(false)
                pkceManager.markExchangeFinished()
            }
        }
    }

    override suspend fun completeOAuthFromWebView(
        tokenJson: String,
        meJson: String,
        friendsJson: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        oauthExchangeMutex.withLock {
            _pendingOAuthCode.value = null
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _connectionError.value = null

            try {
                val tokenRes = gson.fromJson(tokenJson, OAuthTokenResponse::class.java)
                    ?: throw IllegalStateException("Invalid OAuth token JSON")
                val accessToken = tokenRes.accessToken?.trim().orEmpty()
                if (accessToken.isBlank()) {
                    throw IllegalStateException("Empty access_token from osu!")
                }
                apiPoller.resetAuthState()
                val bearer = "Bearer $accessToken"
                val me = if (meJson.isNotBlank()) {
                    gson.fromJson(meJson, OsuUserDto::class.java)
                        ?: throw IllegalStateException("Invalid osu! profile JSON")
                } else {
                    apiService.getMe(bearer)
                }

                if (!friendsJson.isNullOrBlank() && (meJson.isNotBlank() || friendsJson.trim() != "[]")) {
                    val listType = object : TypeToken<List<OsuUserDto>>() {}.type
                    val parsedFriends = runCatching {
                        gson.fromJson<List<OsuUserDto>>(friendsJson, listType)
                    }.getOrNull()
                    if (parsedFriends != null) {
                        applyApiFriendsList(parsedFriends)
                    }
                } else if (meJson.isBlank()) {
                    val fetchedFriends = runCatching { apiService.getFriends(bearer) }.getOrNull()
                    if (fetchedFriends != null) {
                        applyApiFriendsList(fetchedFriends)
                    }
                }

                persistOAuthProfileAndConnectIrc(tokenRes, me, OAuthConfig.CLIENT_ID.trim())
                Result.success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: HttpException) {
                val parsedError = parseOAuthHttpError(e)
                Log.e("OsuOAuth", "WebView OAuth HTTP ${e.code()}: $parsedError", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = parsedError
                Result.failure(IllegalStateException(parsedError, e))
            } catch (e: Exception) {
                Log.e("OsuOAuth", "WebView OAuth completion failed", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = e.message ?: "OAuth login failed"
                Result.failure(e)
            } finally {
                pkceManager.markExchangeFinished()
            }
        }
    }

    override suspend fun refreshFriends(): Result<List<FriendContact>> = withContext(Dispatchers.IO) {
        val current = prefs.settingsFlow.first()
        if (current.authMode == AuthMode.OAUTH_V2) {
            val apiResult = runCatching {
                apiPoller.coalesceBearerGet(
                    key = "friends",
                    missingTokenMessage = "Missing OAuth token",
                    minIntervalMs = 250L
                ) { bearer ->
                    apiService.getFriends(bearer)
                }
            }.onFailure { if (it is CancellationException) throw it }
            if (apiResult.isSuccess) {
                applyApiFriendsList(apiResult.getOrDefault(emptyList()))
            }
        }
        Result.success(friends.first())
    }

    private fun parseUserDtoList(json: String?): List<OsuUserDto> {
        if (json.isNullOrBlank()) return emptyList()
        val listType = object : TypeToken<List<OsuUserDto>>() {}.type
        return runCatching { gson.fromJson<List<OsuUserDto>>(json, listType) }.getOrNull().orEmpty()
    }

    private suspend fun loadPersistedFriendsIntoState() {
        val cachedServer = parseUserDtoList(prefs.getCachedFriendsJson())
        val merged = mergeFriendsWithLocalOverrides(cachedServer)
        if (merged.isNotEmpty()) {
            _apiFriends.value = merged
        }
    }

    private suspend fun mergeFriendsWithLocalOverrides(serverDtos: List<OsuUserDto>): List<OsuUserDto> {
        val removedIds = prefs.getLocalRemovedFriendIds()
        val localAdded = parseUserDtoList(prefs.getLocalAddedFriendsJson())

        val combined = LinkedHashMap<String, OsuUserDto>()
        for (dto in serverDtos) {
            if (dto.id > 0L && dto.id in removedIds) continue
            val rawName = dto.username?.takeIf { it.isNotBlank() } ?: continue
            val key = BeatmapUrlParser.normalizeUsername(rawName)
            combined[key] = dto
        }
        for (dto in localAdded) {
            if (dto.id > 0L && dto.id in removedIds) continue
            val rawName = dto.username?.takeIf { it.isNotBlank() } ?: continue
            val key = BeatmapUrlParser.normalizeUsername(rawName)
            val existing = combined[key]
            combined[key] = if (existing != null) {
                existing.copy(
                    isOnline = dto.isOnline ?: existing.isOnline,
                    isMutual = dto.isMutual ?: existing.isMutual,
                    lastVisit = dto.lastVisit ?: existing.lastVisit,
                    avatarUrl = dto.avatarUrl ?: existing.avatarUrl,
                    statistics = dto.statistics ?: existing.statistics
                )
            } else {
                dto
            }
        }
        return combined.values.toList()
    }

    private suspend fun applyApiFriendsList(dtos: List<OsuUserDto>) {
        prefs.saveCachedFriendsJson(gson.toJson(dtos))
        val merged = mergeFriendsWithLocalOverrides(dtos)
        _apiFriends.value = merged
        merged.forEach { cacheUserProfile(it, syncFriendsFlow = false) }
        val onlineCount = merged.count { it.isOnline == true }
        prefs.saveWidgetSnapshot(onlineFriendsCount = onlineCount)
        BanchoStatsWidget.updateFromSnapshot(context, prefs.getWidgetSnapshot())

        val isFirstSeed = hasSeededFriendOnlineState.compareAndSet(false, true)
        val onlineAlertNorms = trackedFriendDao.getAllTrackedFriends()
            .filter { it.notifyOnline }
            .map { it.normalizedUsername }
            .toSet()
        val now = System.currentTimeMillis()
        for (dto in merged) {
            val rawName = dto.username?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val norm = BeatmapUrlParser.normalizeUsername(rawName)
            val isOnlineNow = dto.isOnline == true
            val prevById = if (dto.id > 0L) lastKnownOnlineByUserId.put(dto.id, isOnlineNow) else null
            val prevByNorm = lastKnownOnlineByNorm.put(norm, isOnlineNow)
            val prevOnline = prevById ?: prevByNorm
            if (!isFirstSeed && prevOnline == false && isOnlineNow && norm in onlineAlertNorms) {
                val lastAlertAt = lastFriendOnlineAlertAt[norm] ?: 0L
                if ((now - lastAlertAt) >= FRIEND_ONLINE_ALERT_COOLDOWN_MS) {
                    lastFriendOnlineAlertAt[norm] = now
                    val avatar = dto.avatarUrl?.takeIf { it.isNotBlank() }
                        ?: dto.id.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" }
                    notificationManager.showFriendOnlineNotification(
                        username = rawName,
                        userId = dto.id,
                        avatarUrl = avatar
                    )
                }
            }
        }
    }

    private suspend fun syncSelfProfileToWidget(profile: PlayerProfile) {
        val current = prefs.settingsFlow.first()
        val isSelf = (current.userId > 0L && profile.userId == current.userId) ||
            (current.username.isNotBlank() && profile.username.equals(current.username, ignoreCase = true))
        if (!isSelf) return
        val onlineFriends = _apiFriends.value.count { it.isOnline == true }
        prefs.saveWidgetSnapshot(
            username = profile.username,
            avatarUrl = profile.avatarUrl,
            globalRank = profile.globalRank,
            countryRank = profile.countryRank,
            countryCode = profile.countryCode,
            pp = profile.pp,
            accuracy = profile.accuracy,
            onlineFriendsCount = onlineFriends
        )
        BanchoStatsWidget.updateFromSnapshot(context, prefs.getWidgetSnapshot())
        if (!BanchoStatsWidget.avatarCacheFile(context).exists()) {
            BanchoStatsWidget.enqueueImmediateRefresh(context)
        }
    }

    private suspend fun persistOAuthProfileAndConnectIrc(
        tokenRes: OAuthTokenResponse,
        me: OsuUserDto,
        clientId: String
    ) {
        val accessToken = tokenRes.accessToken?.trim().orEmpty()
        val safeUsername = me.username?.trim()?.takeIf { it.isNotBlank() } ?: "User ${me.id}"
        val expiresInSec = tokenRes.expiresIn.coerceAtLeast(60L)
        val expiresAt = System.currentTimeMillis() + (expiresInSec * 1000L)

        prefs.saveOAuthSession(
            accessToken = accessToken,
            refreshToken = tokenRes.refreshToken.orEmpty(),
            expiresAtMillis = expiresAt,
            username = safeUsername,
            userId = me.id,
            avatarUrl = me.avatarUrl,
            clientId = clientId
        )
        resetChatHistorySessionState()
        prefs.setExplicitlyDisconnected(false)
        ircClient.resetExplicitDisconnect()
        val cachedProfileEntity = cacheUserProfile(me)
        val selfMode = me.playmode?.takeIf { it in VALID_OSU_MODES } ?: "osu"
        val selfAliasEntity = userAliasDao.getAlias(BeatmapUrlParser.normalizeUsername(safeUsername))
        val selfDomain = cachedProfileEntity.toDomain(
            customAlias = selfAliasEntity?.customNickname?.takeIf { it.isNotBlank() },
            customNote = selfAliasEntity?.note?.takeIf { it.isNotBlank() },
            isTrackedForPp = false,
            friendStatus = FriendRelationStatus.NONE,
            mode = selfMode
        )
        selfProfileMemoryCache = selfDomain
        selfProfileCacheDao.upsert(selfDomain.toSelfCacheEntity())
        recordDailyProfileSnapshot(selfDomain)
        val onlineFriends = _apiFriends.value.count { it.isOnline == true }
        prefs.saveWidgetSnapshot(
            username = safeUsername,
            avatarUrl = me.avatarUrl ?: "https://a.ppy.sh/${me.id}",
            globalRank = me.statistics?.globalRank,
            countryRank = me.statistics?.countryRank ?: me.statistics?.rank?.country,
            countryCode = me.country?.code ?: me.countryCode,
            pp = me.statistics?.pp,
            accuracy = me.statistics?.hitAccuracy,
            onlineFriendsCount = onlineFriends
        )
        BanchoStatsWidget.updateFromSnapshot(context, prefs.getWidgetSnapshot())
        BanchoStatsWidget.enqueueImmediateRefresh(context)

        ircClient.disconnect()
        ircClient.resetExplicitDisconnect()
        val existingIrc = prefs.getIrcCredentials()
        val existingPass = existingIrc?.password.orEmpty()
        if (existingPass.isNotBlank()) {
            val currentSettings = prefs.settingsFlow.first()
            val initialJoin = resolveAutoJoinPublicChannels(currentSettings)
            val ircResult = ircClient.connectAndVerify(safeUsername, existingPass, initialJoin)
            if (ircResult.isSuccess) {
                prefs.saveIrcCredentialsForOAuth(safeUsername, existingPass)
                ensureDefaultIrcChannels(currentSettings)
                _isIrcAuthError.value = false
                _connectionStatus.value = ConnectionStatus.ONLINE
            } else {
                _isIrcAuthError.value = (ircResult.exceptionOrNull() is IrcAuthException) || ircClient.isAuthError.value
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionError.value = ircResult.exceptionOrNull()?.message ?: "IRC connection failed"
            }
        } else {
            // OAuth profile succeeded; wait for Step 2 IRC password setup in LoginScreen
            _isIrcAuthError.value = false
            _connectionStatus.value = ConnectionStatus.OFFLINE
        }
    }

    private fun parseOAuthHttpError(e: HttpException): String {
        val code = e.code()
        val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()?.trim().orEmpty()
        if (rawBody.contains("<!DOCTYPE html", ignoreCase = true) ||
            rawBody.contains("<html", ignoreCase = true)
        ) {
            val stripped = rawBody
                .replace(HTML_STRIP_BLOCK_REGEX, " ")
                .replace(HTML_TAG_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
                .take(240)
            if (code == 403 || rawBody.contains("Cloudflare", ignoreCase = true)) {
                return if (stripped.isNotBlank()) {
                    "HTTP $code: Blocked by Cloudflare — $stripped"
                } else {
                    "HTTP $code: Blocked by Cloudflare"
                }
            }
            return if (stripped.isNotBlank()) "HTTP $code: $stripped" else "HTTP $code: Unexpected HTML error response"
        }
        val parsed = runCatching {
            if (rawBody.isNotBlank()) gson.fromJson(rawBody, OsuOAuthErrorDto::class.java) else null
        }.getOrNull()

        val details = buildList {
            parsed?.error?.takeIf { it.isNotBlank() }?.let { add("error: $it") }
            parsed?.errorDescription?.takeIf { it.isNotBlank() }?.let { add("description: $it") }
            parsed?.message?.takeIf { it.isNotBlank() }?.let { add("message: $it") }
            parsed?.hint?.takeIf { it.isNotBlank() }?.let { add("hint: $it") }
        }
        val snippet = rawBody.replace(WHITESPACE_REGEX, " ").trim().take(240)

        return when {
            details.isNotEmpty() -> "HTTP $code — ${details.joinToString(" | ")}"
            snippet.isNotBlank() -> "HTTP $code — $snippet"
            else -> "HTTP $code — ${e.message()}"
        }
    }

    override suspend fun completeIrcSetupForOAuth(ircPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentSettings = prefs.settingsFlow.first()
        val cleanUser = currentSettings.username.trim()
        val cleanPass = ircPassword.trim()

        if (cleanUser.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Sign in with osu! first"))
        }
        if (cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("IRC password is required"))
        }

        apiPoller.stopPolling()
        resetChatHistorySessionState()
        sessionExpiredHandled.set(false)
        _isSessionExpired.value = false
        _isIrcAuthError.value = false
        prefs.setExplicitlyDisconnected(false)
        ircClient.resetExplicitDisconnect()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectionError.value = null

        val initialJoin = resolveAutoJoinPublicChannels(currentSettings)
        val result = ircClient.connectAndVerify(cleanUser, cleanPass, initialJoin)
        if (result.isSuccess) {
            prefs.saveIrcCredentialsForOAuth(cleanUser, cleanPass)
            ensureDefaultIrcChannels(currentSettings)
            _isIrcAuthError.value = false
            _connectionStatus.value = ConnectionStatus.ONLINE
        } else {
            _isIrcAuthError.value = (result.exceptionOrNull() is IrcAuthException) || ircClient.isAuthError.value
            _connectionStatus.value = ConnectionStatus.ERROR
            _connectionError.value = result.exceptionOrNull()?.message ?: "Invalid Bancho IRC password"
        }
        return@withContext result
    }

    override suspend fun loginWithIrc(username: String, ircPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanPass = ircPassword.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Username and IRC password are required"))
        }

        apiPoller.stopPolling()
        resetChatHistorySessionState()
        sessionExpiredHandled.set(false)
        _isSessionExpired.value = false
        _isIrcAuthError.value = false
        prefs.setExplicitlyDisconnected(false)
        ircClient.resetExplicitDisconnect()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectionError.value = null

        val currentSettings = prefs.settingsFlow.first()
        val initialJoin = resolveAutoJoinPublicChannels(currentSettings)
        val result = ircClient.connectAndVerify(cleanUser, cleanPass, initialJoin)
        if (result.isSuccess) {
            prefs.saveIrcSession(cleanUser, cleanPass)
            ensureDefaultIrcChannels(currentSettings)
            _isIrcAuthError.value = false
            _connectionStatus.value = ConnectionStatus.ONLINE
            // Resolve self osu! profile via official API v2 when an OAuth token is present
            scope.launch {
                val token = apiPoller.getValidBearerToken() ?: return@launch
                runCatching {
                    apiPoller.withOptionalBearerRetry(token) { activeToken ->
                        apiService.getUser(activeToken, cleanUser, "username")
                    }
                }.getOrNull()?.let { dto ->
                    val cached = cacheUserProfile(dto)
                    val me = cached.toDomain(null)
                    prefs.updateSelfProfile(me.userId, me.username, me.avatarUrl)
                    selfProfileMemoryCache = me
                    selfProfileCacheDao.upsert(me.toSelfCacheEntity())
                }
            }
        } else {
            _isIrcAuthError.value = (result.exceptionOrNull() is IrcAuthException) || ircClient.isAuthError.value
            _connectionStatus.value = ConnectionStatus.ERROR
            _connectionError.value = result.exceptionOrNull()?.message ?: "IRC authentication failed"
        }
        return@withContext result
    }

    override suspend fun reconnectIfAuthenticated() = withContext(Dispatchers.IO) {
        if (_apiFriends.value.isEmpty()) {
            loadPersistedFriendsIntoState()
        }
        val current = prefs.settingsFlow.first()
        if (current.isExplicitlyDisconnected ||
            prefs.isExplicitlyDisconnected() ||
            ircClient.isExplicitlyDisconnected()
        ) {
            _connectionStatus.value = ConnectionStatus.OFFLINE
            return@withContext
        }
        when (current.authMode) {
            AuthMode.OAUTH_V2 -> {
                if (!current.hasIrcPassword) {
                    _connectionStatus.value = ConnectionStatus.OFFLINE
                    return@withContext
                }
                val creds = prefs.getIrcCredentials() ?: return@withContext
                ensureDefaultIrcChannels(current)
                val joined = resolveConnectChannelList(current)
                val nick = current.username.ifBlank { creds.username }
                ircClient.startBackgroundReconnect(nick, creds.password, joined)
            }
            AuthMode.IRC -> {
                val creds = prefs.getIrcCredentials() ?: return@withContext
                ensureDefaultIrcChannels(current)
                val joined = resolveConnectChannelList(current)
                ircClient.startBackgroundReconnect(creds.username, creds.password, joined)
            }
            AuthMode.NONE -> {
                _connectionStatus.value = ConnectionStatus.OFFLINE
            }
        }
    }

    override suspend fun disconnect() {
        disconnectExplicitly()
    }

    override suspend fun disconnectExplicitly() {
        prefs.setExplicitlyDisconnected(true)
        ircClient.disconnectExplicitly()
        _pendingOAuthCode.value = null
        pkceManager.stopLoopbackServer()
        apiPoller.stopPolling()
        _onlineIrcNicks.value = emptySet()
        _seenOnlineSenders.clear()
        _connectionStatus.value = ConnectionStatus.OFFLINE
    }

    override suspend fun setExplicitlyDisconnected(disconnected: Boolean) {
        prefs.setExplicitlyDisconnected(disconnected)
        ircClient.setExplicitlyDisconnected(disconnected)
    }

    override suspend fun logout() {
        _pendingOAuthCode.value = null
        _activeChannelId.value = null
        _apiFriends.value = emptyList()
        _isIrcAuthError.value = false
        _isSessionExpired.value = false
        resetChatHistorySessionState()
        sessionExpiredHandled.set(false)
        selfProfileMemoryCache = null
        runCatching { selfProfileCacheDao.clearAll() }
        disconnectExplicitly()
        prefs.clearSession()
        appShortcutManager.clearDynamicShortcuts()
        ircClient.resetExplicitDisconnect()
        _connectionError.value = null
    }

    override suspend fun sendMessage(channelId: String, rawInput: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normChannelId = normalizeChannelId(channelId)
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(Unit)

        val currentSettings = prefs.settingsFlow.first()
        var channel = channelDao.getChannelById(normChannelId)
        if (channel == null) {
            if (normChannelId.startsWith("dm:")) {
                val targetName = normChannelId.removePrefix("dm:")
                if (targetName.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Channel not found"))
                }
                val newDm = ChannelEntity(
                    id = normChannelId,
                    name = targetName,
                    description = "Direct Message",
                    type = ChannelType.DIRECT.name,
                    lastMessageTimestamp = System.currentTimeMillis(),
                    isJoined = true
                )
                channelDao.upsertChannel(newDm)
                channel = newDm
            } else {
                return@withContext Result.failure(IllegalStateException("Channel not found"))
            }
        }

        val isAction: Boolean
        val content: String
        when {
            trimmed.startsWith("/me ", ignoreCase = true) -> {
                isAction = true
                content = trimmed.substring(4).trim()
            }
            trimmed.equals("/np", ignoreCase = true) -> {
                isAction = true
                content = "is listening to osu! Companion Radio"
            }
            else -> {
                isAction = false
                content = trimmed
            }
        }
        if (content.isEmpty()) return@withContext Result.success(Unit)

        val tempId = "local_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        if (currentSettings.authMode == AuthMode.NONE) {
            messageDao.upsertMessage(
                MessageEntity(
                    id = tempId,
                    channelId = normChannelId,
                    senderId = currentSettings.userId,
                    senderName = currentSettings.username.ifBlank { "Me" },
                    senderAvatarUrl = currentSettings.avatarUrl,
                    content = content,
                    timestamp = now,
                    isOutgoing = true,
                    isAction = isAction,
                    isHighlighted = false,
                    deliveryStatus = DeliveryStatus.FAILED.name
                )
            )
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        val isIrcOnline = _connectionStatus.value == ConnectionStatus.ONLINE &&
            ircClient.status.value == ConnectionStatus.ONLINE &&
            networkMonitor.isOnline.value

        val optimistic = MessageEntity(
            id = tempId,
            channelId = normChannelId,
            senderId = currentSettings.userId,
            senderName = currentSettings.username.ifBlank { "Me" },
            senderAvatarUrl = currentSettings.avatarUrl,
            content = content,
            timestamp = now,
            isOutgoing = true,
            isAction = isAction,
            isHighlighted = false,
            deliveryStatus = if (isIrcOnline) DeliveryStatus.SENDING.name else DeliveryStatus.QUEUED.name
        )
        if (isIrcOnline) {
            recentLocalOutgoing.removeIf { (now - it.sentAt) > 10_000L }
            recentLocalOutgoing.add(
                RecentLocalOutgoing(
                    localMessageId = tempId,
                    channelId = normChannelId,
                    trimmedContent = content.trim(),
                    sentAt = now
                )
            )
        }
        messageDao.upsertMessage(optimistic)
        channelDao.updateChannelLastMessage(
            channelId = normChannelId,
            text = if (isAction) "* $content" else content,
            timestamp = now,
            senderName = currentSettings.username,
            unreadDelta = 0
        )

        if (!isIrcOnline) {
            if (networkMonitor.isOnline.value &&
                !currentSettings.isExplicitlyDisconnected &&
                !prefs.isExplicitlyDisconnected() &&
                !ircClient.isExplicitlyDisconnected()
            ) {
                scope.launch { reconnectIfAuthenticated() }
            }
            return@withContext Result.success(Unit)
        }

        val sendResult = sendViaIrc(channel, content, isAction, tempId)
        try {
            if (sendResult.isSuccess &&
                normChannelId.startsWith("#mp_", ignoreCase = true) &&
                !isAction &&
                messageDao.getMessageById(tempId)?.deliveryStatus == DeliveryStatus.SENT.name
            ) {
                when (val directive = MpLobbyParser.parseOutgoingMpTimerCommand(content)) {
                    is MpLobbyParser.MpTimerDirective.Start -> {
                        val roomName = channel.description.takeIf {
                            it.isNotBlank() && !it.equals("Bancho IRC channel", ignoreCase = true)
                        } ?: channel.name
                        mpTimerManager.startOrSyncTimer(
                            channelId = normChannelId,
                            roomName = roomName,
                            label = directive.label,
                            totalSeconds = directive.totalSeconds,
                            isMatchStart = directive.isMatchStart
                        )
                    }
                    MpLobbyParser.MpTimerDirective.Clear -> {
                        mpTimerManager.clearTimer(normChannelId)
                    }
                    null -> Unit
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            runCatching { Log.w("ChatRepo", "Post-send MP timer sync failed", t) }
        }
        return@withContext sendResult
    }

    override suspend fun sendRecentScoreCommand(
        channelId: String,
        targetUsernameOrNull: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normChannelId = normalizeChannelId(channelId)
        val currentSettings = prefs.settingsFlow.first()
        val token = apiPoller.getValidBearerToken()
        if (currentSettings.authMode != AuthMode.OAUTH_V2 || token == null) {
            val msg = "Sign in with osu! OAuth to use /rs."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val cleanTarget = targetUsernameOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedProfile = when {
            cleanTarget != null -> getPlayerProfile(cleanTarget).getOrNull()
            else -> getCachedSelfProfile(forceRefresh = false).getOrNull()
                ?: currentSettings.username.takeIf { it.isNotBlank() }?.let { getPlayerProfile(it).getOrNull() }
        }
        val targetId = resolvedProfile?.userId?.takeIf { it > 0L }
            ?: cleanTarget?.toLongOrNull()
            ?: currentSettings.userId.takeIf { it > 0L }

        if (targetId == null || targetId <= 0L) {
            val label = cleanTarget ?: currentSettings.username.ifBlank { "player" }
            val msg = "Could not find osu! user \"$label\"."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val displayUser = resolvedProfile?.username?.takeIf { it.isNotBlank() }
            ?: cleanTarget
            ?: currentSettings.username.ifBlank { "User #$targetId" }

        val recentResult = runCatching {
            apiPoller.withBearerRetry("Sign in with osu! OAuth to fetch recent score") { bearer ->
                apiService.getUserScores(
                    bearerToken = bearer,
                    userId = targetId,
                    type = "recent",
                    includeFails = 1,
                    limit = 1
                )
            }
        }.onFailure { if (it is CancellationException) throw it }

        val recentDto = recentResult.getOrNull()?.firstOrNull()
        if (recentResult.isFailure || recentDto == null) {
            val errMsg = recentResult.exceptionOrNull()?.let { apiPoller.toFriendlyApiException(it).message }
                ?: "No recent plays found for $displayUser."
            insertLocalSystemNotice(normChannelId, errMsg)
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        val recent = recentDto.toDomain(displayUser)
        val beatmapId = recent.beatmapId ?: 0L
        val modsList = recent.mods
        val modsStr = if (modsList.isEmpty()) "NM" else modsList.joinToString("")
        val modsBitmask = RosuPpEngine.modsToBitmask(modsList)
        val acc = recent.accuracy.coerceIn(0.0, 100.0)
        val missCount = recent.countMiss.coerceAtLeast(0)
        val mode = recent.mode.ifBlank { "osu" }

        val ifFcRaw = if (beatmapId > 0L) {
            rosuPpEngine.calculateRosuPp(
                beatmapId = beatmapId,
                mode = mode,
                modsBitmask = modsBitmask,
                accuracyPercent = acc,
                nMisses = 0,
                combo = null,
                isLazer = recent.isLazer
            )
        } else {
            null
        }

        val isFail = !recent.passed || recent.rank.equals("F", ignoreCase = true)
        val actualRaw = if (beatmapId > 0L && (isFail || recent.pp == null)) {
            rosuPpEngine.calculateRosuPp(
                beatmapId = beatmapId,
                mode = mode,
                modsBitmask = modsBitmask,
                accuracyPercent = acc,
                nMisses = missCount,
                combo = recent.maxCombo.takeIf { it > 0 },
                isLazer = recent.isLazer
            )
        } else {
            null
        }

        val maxCombo = ifFcRaw?.maxCombo
            ?: actualRaw?.maxCombo
            ?: recent.beatmapMaxCombo
            ?: recentDto.beatmap?.maxCombo
            ?: if (beatmapId > 0L) getBeatmapDetails(beatmapId).getOrNull()?.maxCombo else null

        val fallbackMeta = if (ifFcRaw == null || (actualRaw == null && (isFail || recent.pp == null))) {
            if (beatmapId > 0L) getBeatmapDetails(beatmapId).getOrNull() else null
        } else {
            null
        }
        val modsSet = modsList.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

        val actualPp = when {
            !isFail && recent.pp != null && recent.pp.isFinite() -> recent.pp
            actualRaw != null -> actualRaw.pp
            fallbackMeta != null -> OsuBeatmapCalculator.simulate(
                meta = fallbackMeta,
                mods = modsSet,
                accuracyPercent = acc,
                nMisses = missCount,
                combo = recent.maxCombo.takeIf { it > 0 }
            ).estimatedFcPp
            else -> 0.0
        }

        val ifFcPp = when {
            ifFcRaw != null -> ifFcRaw.pp
            fallbackMeta != null -> OsuBeatmapCalculator.simulate(
                meta = fallbackMeta,
                mods = modsSet,
                accuracyPercent = acc,
                nMisses = 0,
                combo = null
            ).estimatedFcPp
            else -> actualPp
        }

        val artistTitle = if (recent.beatmapArtist.isNotBlank()) {
            "${recent.beatmapArtist} - ${recent.beatmapTitle}"
        } else {
            recent.beatmapTitle.ifBlank { "Beatmap #$beatmapId" }
        }
        val versionPart = recent.beatmapVersion.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        val mapUrl = if (beatmapId > 0L) "https://osu.ppy.sh/b/$beatmapId" else "https://osu.ppy.sh/s/${recent.beatmapsetId ?: 0L}"
        val rankText = if (isFail) "FAIL" else recent.gradeLabel.ifBlank { "—" }
        val maxComboText = maxCombo?.toString() ?: "?"
        val formattedMsg = String.format(
            Locale.US,
            "[%s %s%s] +%s | %s %.2f%% (%d/%sx, %dm) | %.0fpp (%.0fpp if FC)",
            mapUrl,
            artistTitle,
            versionPart,
            modsStr,
            rankText,
            acc,
            recent.maxCombo.coerceAtLeast(0),
            maxComboText,
            missCount,
            actualPp,
            ifFcPp
        )
        sendMessage(normChannelId, formattedMsg)
    }

    override suspend fun sendCompareScoreCommand(
        channelId: String,
        targetUsernameOrNull: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normChannelId = normalizeChannelId(channelId)
        val currentSettings = prefs.settingsFlow.first()
        val token = apiPoller.getValidBearerToken()
        if (currentSettings.authMode != AuthMode.OAUTH_V2 || token == null) {
            val msg = "Sign in with osu! OAuth to use /c."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val recentDesc = messageDao.getRecentMessagesDesc(normChannelId, 50)
        var beatmapId: Long? = null
        for (msg in recentDesc) {
            val linkRef = BeatmapUrlParser.extractFirst(msg.content) ?: continue
            val resolvedId = linkRef.beatmapId?.takeIf { it > 0L }
                ?: getBeatmapMetadata(linkRef).getOrNull()?.beatmapId?.takeIf { it > 0L }
            if (resolvedId != null && resolvedId > 0L) {
                beatmapId = resolvedId
                break
            }
        }
        if (beatmapId == null && normChannelId.startsWith("#mp_")) {
            val lobbyState = MpLobbyParser.parseLobbyState(
                channelId = normChannelId,
                channelName = normChannelId,
                messages = recentDesc.asReversed().map { it.toDomain(null) }
            )
            beatmapId = lobbyState.beatmapId?.takeIf { it > 0L }
        }
        if (beatmapId == null || beatmapId <= 0L) {
            val msg = "No recent beatmap link found in this channel to compare (/c)."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val cleanTarget = targetUsernameOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedProfile = when {
            cleanTarget != null -> getPlayerProfile(cleanTarget).getOrNull()
            else -> getCachedSelfProfile(forceRefresh = false).getOrNull()
                ?: currentSettings.username.takeIf { it.isNotBlank() }?.let { getPlayerProfile(it).getOrNull() }
        }
        val targetId = if (cleanTarget != null) {
            resolvedProfile?.userId?.takeIf { it > 0L } ?: cleanTarget.toLongOrNull()
        } else {
            resolvedProfile?.userId?.takeIf { it > 0L } ?: currentSettings.userId.takeIf { it > 0L }
        }
        if (targetId == null || targetId <= 0L) {
            val label = cleanTarget ?: currentSettings.username.ifBlank { "player" }
            val msg = "Could not find osu! user \"$label\"."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val displayUser = resolvedProfile?.username?.takeIf { it.isNotBlank() }
            ?: cleanTarget
            ?: currentSettings.username.ifBlank { "User #$targetId" }

        val beatmapMeta = getBeatmapDetails(beatmapId).getOrNull()
        val scoresResult = runCatching {
            apiPoller.withBearerRetry("Sign in with osu! OAuth to compare scores") { bearer ->
                apiService.getUserBeatmapScores(
                    bearerToken = bearer,
                    beatmapId = beatmapId,
                    userId = targetId,
                    mode = beatmapMeta?.mode?.takeIf { it.isNotBlank() }
                )
            }
        }.onFailure { if (it is CancellationException) throw it }

        if (scoresResult.isFailure) {
            val errMsg = scoresResult.exceptionOrNull()?.let { apiPoller.toFriendlyApiException(it).message }
                ?: "Could not fetch scores for $displayUser."
            insertLocalSystemNotice(normChannelId, errMsg)
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        val bestDto = scoresResult.getOrNull()?.scores.orEmpty()
            .maxWithOrNull(
                compareBy<OsuScoreDto> { it.pp ?: -1.0 }
                    .thenBy { if (it.score > 0L) it.score else (it.totalScore ?: 0L) }
            )
        if (bestDto == null) {
            val mapLabel = beatmapMeta?.let { meta ->
                val prefix = meta.artist.takeIf { it.isNotBlank() }?.let { "$it - " }.orEmpty()
                "$prefix${meta.title}${meta.version.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()}"
            } ?: "Beatmap #$beatmapId"
            val msg = "No scores found for $displayUser on $mapLabel."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val score = bestDto.toDomain(displayUser)
        val modsList = score.mods
        val modsStr = if (modsList.isEmpty()) "NM" else modsList.joinToString("")
        val modsBitmask = RosuPpEngine.modsToBitmask(modsList)
        val modsSet = modsList.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        val acc = score.accuracy.coerceIn(0.0, 100.0)
        val missCount = score.countMiss.coerceAtLeast(0)
        val mode = beatmapMeta?.mode?.takeIf { it.isNotBlank() } ?: score.mode.ifBlank { "osu" }

        val knownMaxCombo = score.beatmapMaxCombo ?: bestDto.beatmap?.maxCombo ?: beatmapMeta?.maxCombo
        val needsActualPp = !score.passed || score.pp == null || !score.pp.isFinite()
        val actualRaw = if (needsActualPp) {
            rosuPpEngine.calculateRosuPp(
                beatmapId = beatmapId,
                mode = mode,
                modsBitmask = modsBitmask,
                accuracyPercent = acc,
                nMisses = missCount,
                combo = score.maxCombo.takeIf { it > 0 },
                isLazer = score.isLazer
            )
        } else {
            null
        }
        val actualPp = when {
            score.passed && score.pp != null && score.pp.isFinite() -> score.pp
            actualRaw != null -> actualRaw.pp
            beatmapMeta != null -> OsuBeatmapCalculator.simulate(
                meta = beatmapMeta,
                mods = modsSet,
                accuracyPercent = acc,
                nMisses = missCount,
                combo = score.maxCombo.takeIf { it > 0 }
            ).estimatedFcPp
            else -> 0.0
        }

        val initialMaxCombo = actualRaw?.maxCombo ?: knownMaxCombo
        val isNotFc = !score.passed ||
            missCount > 0 ||
            (initialMaxCombo != null && initialMaxCombo > 0 && score.maxCombo < (initialMaxCombo - 15))
        val ifFcRaw = if (isNotFc) {
            rosuPpEngine.calculateRosuPp(
                beatmapId = beatmapId,
                mode = mode,
                modsBitmask = modsBitmask,
                accuracyPercent = acc,
                nMisses = 0,
                combo = null,
                isLazer = score.isLazer
            )
        } else {
            null
        }
        val maxCombo = ifFcRaw?.maxCombo ?: initialMaxCombo
        val ifFcPp = if (isNotFc) {
            when {
                ifFcRaw != null -> ifFcRaw.pp
                beatmapMeta != null -> OsuBeatmapCalculator.simulate(
                    meta = beatmapMeta,
                    mods = modsSet,
                    accuracyPercent = acc,
                    nMisses = 0,
                    combo = null
                ).estimatedFcPp
                else -> null
            }
        } else {
            null
        }

        val artist = score.beatmapArtist.takeIf { it.isNotBlank() } ?: beatmapMeta?.artist.orEmpty()
        val title = score.beatmapTitle.takeIf { it.isNotBlank() && it != "Unknown Beatmap" }
            ?: beatmapMeta?.title?.takeIf { it.isNotBlank() }
            ?: "Beatmap #$beatmapId"
        val version = score.beatmapVersion.takeIf { it.isNotBlank() } ?: beatmapMeta?.version.orEmpty()
        val artistTitle = if (artist.isNotBlank()) "$artist - $title" else title
        val versionPart = version.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        val rankText = if (!score.passed || score.rank.equals("F", ignoreCase = true)) {
            "FAIL"
        } else {
            score.gradeLabel.ifBlank { "—" }
        }
        val maxComboText = maxCombo?.toString() ?: "?"
        val ifFcSuffix = if (ifFcPp != null && isNotFc) {
            String.format(Locale.US, " (%.0fpp if FC)", ifFcPp)
        } else {
            ""
        }
        val formattedMsg = String.format(
            Locale.US,
            "%s on [https://osu.ppy.sh/b/%d %s%s] +%s | %s %.2f%% (%d/%sx, %dm) | %.0fpp%s",
            displayUser,
            beatmapId,
            artistTitle,
            versionPart,
            modsStr,
            rankText,
            acc,
            score.maxCombo.coerceAtLeast(0),
            maxComboText,
            missCount,
            actualPp,
            ifFcSuffix
        )
        sendMessage(normChannelId, formattedMsg)
    }

    override suspend fun sendTopScoreCommand(
        channelId: String,
        rawArgsOrNull: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normChannelId = normalizeChannelId(channelId)
        val currentSettings = prefs.settingsFlow.first()
        val token = apiPoller.getValidBearerToken()
        if (currentSettings.authMode != AuthMode.OAUTH_V2 || token == null) {
            val msg = "Sign in with osu! OAuth to use /top."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val (cleanTarget, modFilterPair) = parseTopCommandArgs(rawArgsOrNull)
        val resolvedProfile = when {
            cleanTarget != null -> getPlayerProfile(cleanTarget).getOrNull()
            else -> getCachedSelfProfile(forceRefresh = false).getOrNull()
                ?: currentSettings.username.takeIf { it.isNotBlank() }?.let { getPlayerProfile(it).getOrNull() }
        }
        val targetId = if (cleanTarget != null) {
            resolvedProfile?.userId?.takeIf { it > 0L } ?: cleanTarget.toLongOrNull()
        } else {
            resolvedProfile?.userId?.takeIf { it > 0L } ?: currentSettings.userId.takeIf { it > 0L }
        }
        if (targetId == null || targetId <= 0L) {
            val label = cleanTarget ?: currentSettings.username.ifBlank { "player" }
            val msg = "Could not find osu! user \"$label\"."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val displayUser = resolvedProfile?.username?.takeIf { it.isNotBlank() }
            ?: cleanTarget
            ?: currentSettings.username.ifBlank { "User #$targetId" }

        val topResult = runCatching {
            apiPoller.withBearerRetry("Sign in with osu! OAuth to fetch top plays") { bearer ->
                apiService.getUserScores(
                    bearerToken = bearer,
                    userId = targetId,
                    type = "best",
                    includeFails = 0,
                    limit = 100
                )
            }
        }.onFailure { if (it is CancellationException) throw it }

        if (topResult.isFailure) {
            val errMsg = topResult.exceptionOrNull()?.let { apiPoller.toFriendlyApiException(it).message }
                ?: "Could not fetch top plays for $displayUser."
            insertLocalSystemNotice(normChannelId, errMsg)
            return@withContext Result.failure(IllegalStateException(errMsg))
        }

        val allTopScores = topResult.getOrNull().orEmpty()
        if (allTopScores.isEmpty()) {
            val msg = "No top plays found for $displayUser."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val filterSet = modFilterPair?.first
        val filterDisplay = modFilterPair?.second
        val matchedIndexed = if (filterSet == null) {
            IndexedValue(0, allTopScores.first())
        } else {
            allTopScores.withIndex().firstOrNull { (_, dto) ->
                canonicalizeModSetForTopFilter(dto.mods.orEmpty()) == filterSet
            }
        }
        if (matchedIndexed == null) {
            val label = filterDisplay?.let { "+$it" } ?: "requested mods"
            val msg = "No $label play found in $displayUser's top 100."
            insertLocalSystemNotice(normChannelId, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val rankPos = matchedIndexed.index + 1
        val topDto = matchedIndexed.value
        val top = topDto.toDomain(displayUser)
        val beatmapId = top.beatmapId ?: 0L
        val modsList = top.mods.filterNot { it.equals("CL", ignoreCase = true) }
        val modsStr = if (modsList.isEmpty()) "NM" else modsList.joinToString("")
        val acc = top.accuracy.coerceIn(0.0, 100.0)
        val missCount = top.countMiss.coerceAtLeast(0)
        val maxCombo = top.beatmapMaxCombo
            ?: topDto.beatmap?.maxCombo
            ?: if (beatmapId > 0L) getBeatmapDetails(beatmapId).getOrNull()?.maxCombo else null
        val ppVal = top.pp?.takeIf { it.isFinite() } ?: 0.0

        val artistTitle = if (top.beatmapArtist.isNotBlank()) {
            "${top.beatmapArtist} - ${top.beatmapTitle}"
        } else {
            top.beatmapTitle.ifBlank { "Beatmap #$beatmapId" }
        }
        val versionPart = top.beatmapVersion.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        val mapUrl = if (beatmapId > 0L) {
            "https://osu.ppy.sh/b/$beatmapId"
        } else {
            "https://osu.ppy.sh/s/${top.beatmapsetId ?: 0L}"
        }
        val rankText = top.gradeLabel.ifBlank { "—" }
        val maxComboText = maxCombo?.toString() ?: "?"
        val rankHeader = if (filterDisplay != null) "#$rankPos (+$filterDisplay)" else "#$rankPos"
        val formattedMsg = String.format(
            Locale.US,
            "%s %s | [%s %s%s] +%s | %s %.2f%% (%d/%sx, %dm) | %.0fpp",
            displayUser,
            rankHeader,
            mapUrl,
            artistTitle,
            versionPart,
            modsStr,
            rankText,
            acc,
            top.maxCombo.coerceAtLeast(0),
            maxComboText,
            missCount,
            ppVal
        )
        sendMessage(normChannelId, formattedMsg)
    }

    private fun parseTopCommandArgs(rawArgsOrNull: String?): Pair<String?, Pair<Set<String>, String>?> {
        val raw = rawArgsOrNull?.trim().orEmpty()
        if (raw.isEmpty()) return null to null
        val tokens = raw.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null to null
        if (tokens.size == 1) {
            val singleMods = parseModFilterToken(tokens[0], strictKnownCombo = true)
            if (singleMods != null) {
                return null to singleMods
            }
            return tokens[0] to null
        }
        val lastMods = parseModFilterToken(tokens.last(), strictKnownCombo = false)
        return if (lastMods != null) {
            val username = tokens.dropLast(1).joinToString(" ").trim().takeIf { it.isNotEmpty() }
            username to lastMods
        } else {
            raw to null
        }
    }

    private fun parseModFilterToken(
        token: String,
        strictKnownCombo: Boolean
    ): Pair<Set<String>, String>? {
        val hasPlus = token.startsWith("+")
        val rawBody = token.removePrefix("+").trim().uppercase(Locale.US)
        if (rawBody.isEmpty()) return null
        if (rawBody == "NM" || rawBody == "NONE" || rawBody == "NOMOD") {
            return emptySet<String>() to "NM"
        }
        if (!hasPlus && strictKnownCombo && rawBody !in KNOWN_TOP_SINGLE_MOD_COMBOS) {
            return null
        }
        if (rawBody.length % 2 != 0) return null
        val chunks = rawBody.chunked(2)
        if (chunks.any { it !in VALID_TOP_MOD_CODES }) return null
        val normalizedSet = canonicalizeModSetForTopFilter(chunks)
        val displayLabel = if (normalizedSet.isEmpty()) "NM" else chunks.distinct().joinToString("")
        return normalizedSet to displayLabel
    }

    private fun canonicalizeModSetForTopFilter(mods: Iterable<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (raw in mods) {
            val upper = raw.trim().uppercase(Locale.US)
            when (upper) {
                "", "NM", "NONE", "CL" -> Unit
                "NC" -> out.add("DT")
                else -> out.add(upper)
            }
        }
        return out
    }

    private suspend fun insertLocalSystemNotice(channelId: String, text: String) {
        val now = System.currentTimeMillis()
        val entity = MessageEntity(
            id = "local_sys_${UUID.randomUUID()}",
            channelId = channelId,
            senderId = 0L,
            senderName = "System",
            senderAvatarUrl = null,
            content = text,
            timestamp = now,
            isOutgoing = false,
            isAction = true,
            isHighlighted = false,
            deliveryStatus = DeliveryStatus.READ.name
        )
        runCatching { messageDao.upsertMessage(entity) }
    }

    private suspend fun sendViaIrc(
        channel: ChannelEntity,
        content: String,
        isAction: Boolean,
        tempId: String
    ): Result<Unit> {
        val target = if (channel.type == ChannelType.PUBLIC.name) {
            channel.name
        } else {
            channel.name.replace(' ', '_')
        }
        val sent = ircClient.sendPrivMsg(target, content, isAction)
        return if (sent) {
            try {
                messageDao.updateDeliveryStatus(tempId, DeliveryStatus.SENT.name)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                runCatching { Log.w("ChatRepo", "Failed to mark message $tempId SENT", t) }
            }
            Result.success(Unit)
        } else if (_isIrcAuthError.value || ircClient.isAuthError.value) {
            messageDao.updateDeliveryStatusIfNotSent(tempId, DeliveryStatus.FAILED.name)
            Result.failure(IllegalStateException("Bancho IRC authentication failed"))
        } else {
            messageDao.updateDeliveryStatusIfNotSent(tempId, DeliveryStatus.QUEUED.name)
            Result.success(Unit)
        }
    }

    override suspend fun sendQueuedMessageNow(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = messageDao.getMessageById(messageId)
            ?: return@withContext Result.failure(IllegalStateException("Queued message not found"))
        if (existing.deliveryStatus == DeliveryStatus.SENT.name ||
            existing.deliveryStatus == DeliveryStatus.READ.name
        ) {
            return@withContext Result.success(Unit)
        }
        if (ircClient.status.value != ConnectionStatus.ONLINE || !networkMonitor.isOnline.value) {
            return@withContext Result.failure(IllegalStateException("Not connected to Bancho IRC"))
        }
        if (existing.deliveryStatus != DeliveryStatus.QUEUED.name) {
            messageDao.updateDeliveryStatusIfNotSent(messageId, DeliveryStatus.QUEUED.name)
        }
        flushQueuedMessages()
        val updated = messageDao.getMessageById(messageId)
        if (updated == null ||
            updated.deliveryStatus == DeliveryStatus.SENT.name ||
            updated.deliveryStatus == DeliveryStatus.READ.name
        ) {
            Result.success(Unit)
        } else if (updated.deliveryStatus == DeliveryStatus.FAILED.name) {
            Result.failure(IllegalStateException("Failed to send message"))
        } else {
            Result.failure(IllegalStateException("Not connected to Bancho IRC"))
        }
    }

    override suspend fun cancelQueuedMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
    }

    override suspend fun searchAllMessages(query: String, limit: Int): List<GlobalMessageSearchHit> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.length < 2) return@withContext emptyList()
            val currentSettings = prefs.settingsFlow.first()
            val entities = messageDao.searchAllMessages(cleanQuery, limit.coerceIn(1, 200))
            val channelCache = HashMap<String, ChannelEntity?>()
            entities.mapNotNull { entity ->
                val ch = channelCache.getOrPut(entity.channelId) {
                    channelDao.getChannelById(entity.channelId)
                }
                val isDirect = ch?.type == ChannelType.DIRECT.name ||
                    entity.channelId.startsWith("dm:", ignoreCase = true)
                if (!isDirect && !entity.isOutgoing && isSenderIgnored(entity.senderName, currentSettings)) {
                    return@mapNotNull null
                }
                val channelName = ch?.name ?: if (entity.channelId.startsWith("dm:", ignoreCase = true)) {
                    entity.channelId.substring(3).replace('_', ' ').trim().ifBlank { entity.channelId }
                } else {
                    entity.channelId
                }
                GlobalMessageSearchHit(
                    messageId = entity.id,
                    channelId = entity.channelId,
                    channelName = channelName,
                    isDirectChannel = isDirect,
                    senderName = entity.senderName,
                    content = if (entity.isAction) "* ${entity.content}" else entity.content,
                    timestamp = entity.timestamp
                )
            }
        }

    private suspend fun flushQueuedMessages() {
        outboxFlushMutex.withLock {
            if (_connectionStatus.value != ConnectionStatus.ONLINE ||
                ircClient.status.value != ConnectionStatus.ONLINE ||
                !networkMonitor.isOnline.value
            ) {
                return
            }
            val queued = messageDao.getQueuedMessages()
            if (queued.isEmpty()) return
            val currentSettings = prefs.settingsFlow.first()
            for (msg in queued) {
                if (_connectionStatus.value != ConnectionStatus.ONLINE ||
                    ircClient.status.value != ConnectionStatus.ONLINE ||
                    !networkMonitor.isOnline.value
                ) {
                    break
                }
                val fresh = messageDao.getMessageById(msg.id)
                if (fresh == null || fresh.deliveryStatus != DeliveryStatus.QUEUED.name) {
                    continue
                }
                val ch = channelDao.getChannelById(msg.channelId)
                val target = when {
                    msg.channelId.startsWith("#") -> ch?.name ?: msg.channelId
                    msg.channelId.startsWith("dm:", ignoreCase = true) ->
                        (ch?.name ?: msg.channelId.substring(3)).replace(' ', '_')
                    ch != null -> if (ch.type == ChannelType.PUBLIC.name) ch.name else ch.name.replace(' ', '_')
                    else -> {
                        messageDao.updateDeliveryStatusIfNotSent(msg.id, DeliveryStatus.FAILED.name)
                        continue
                    }
                }
                if (target.isBlank()) {
                    messageDao.updateDeliveryStatusIfNotSent(msg.id, DeliveryStatus.FAILED.name)
                    continue
                }
                messageDao.updateDeliveryStatusIfNotSent(msg.id, DeliveryStatus.SENDING.name)
                val now = System.currentTimeMillis()
                recentLocalOutgoing.removeIf { (now - it.sentAt) > 10_000L }
                recentLocalOutgoing.add(
                    RecentLocalOutgoing(
                        localMessageId = msg.id,
                        channelId = msg.channelId,
                        trimmedContent = msg.content.trim(),
                        sentAt = now
                    )
                )
                val sent = ircClient.sendPrivMsg(target, msg.content, msg.isAction)
                if (sent) {
                    try {
                        messageDao.updateDeliveryStatus(msg.id, DeliveryStatus.SENT.name)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        runCatching { Log.w("ChatRepo", "Failed to mark queued message ${msg.id} SENT", t) }
                    }
                    try {
                        channelDao.updateChannelLastMessage(
                            channelId = msg.channelId,
                            text = if (msg.isAction) "* ${msg.content}" else msg.content,
                            timestamp = maxOf(msg.timestamp, now),
                            senderName = currentSettings.username,
                            unreadDelta = 0
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        runCatching { Log.w("ChatRepo", "Post-send channel update failed for ${msg.channelId}", t) }
                    }
                } else {
                    if (_isIrcAuthError.value || ircClient.isAuthError.value) {
                        messageDao.updateDeliveryStatusIfNotSent(msg.id, DeliveryStatus.FAILED.name)
                        break
                    }
                    messageDao.updateDeliveryStatusIfNotSent(msg.id, DeliveryStatus.QUEUED.name)
                    break
                }
            }
        }
    }

    override suspend fun joinPublicChannel(channelName: String): Result<ChatChannel> = withContext(Dispatchers.IO) {
        val rawTrim = channelName.trim().lowercase()
        val normalizedName = if (rawTrim.startsWith("#")) rawTrim else "#$rawTrim"
        val isAllowed = OfficialOsuChannels.isOfficial(normalizedName) ||
            OfficialOsuChannels.isMultiplayerRoom(normalizedName)
        if (!isAllowed) {
            return@withContext Result.failure(
                IllegalArgumentException("Only official osu! public channels or #mp_ rooms can be joined")
            )
        }
        val normId = normalizeChannelId(normalizedName)

        val existing = channelDao.getChannelById(normId)
            ?: channelDao.getChannelByNameAndType(normalizedName, ChannelType.PUBLIC.name)
        val entity = existing?.copy(id = normId, isJoined = true) ?: ChannelEntity(
            id = normId,
            name = normalizedName,
            description = "Bancho IRC public channel",
            type = ChannelType.PUBLIC.name,
            isJoined = true
        )
        channelDao.upsertChannel(entity)
        ircClient.joinChannel(normalizedName)
        return@withContext Result.success(entity.toDomain(null))
    }

    override suspend fun leaveChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normId = normalizeChannelId(channelId)
        val channel = channelDao.getChannelById(normId) ?: return@withContext Result.success(Unit)
        if (channel.type == ChannelType.PUBLIC.name) {
            ircClient.partChannel(channel.name)
        }
        channelDao.setChannelJoined(normId, false)
        Result.success(Unit)
    }

    override suspend fun deleteDirectConversation(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normId = normalizeChannelId(channelId)
        channelDao.deleteChannelWithMessages(normId)
        notificationManager.clearChannelNotifications(normId)
        _channelHistoryStates.update { it - normId }
        Result.success(Unit)
    }

    override suspend fun startDirectMessage(targetUsernameOrId: String): Result<ChatChannel> = withContext(Dispatchers.IO) {
        val cleanTarget = targetUsernameOrId.trim()
        if (cleanTarget.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a username or user ID"))
        }

        val profile = getPlayerProfile(cleanTarget).getOrNull()
        val resolvedName = (profile?.username ?: cleanTarget).trim()
        val resolvedUserId = profile?.userId
        val resolvedAvatar = profile?.avatarUrl

        // Check existing DM channel first
        val dmChannelId = normalizeChannelId("dm:${BeatmapUrlParser.normalizeUsername(resolvedName)}")
        val existingByName = channelDao.getChannelById(dmChannelId)
            ?: channelDao.getChannelByNameAndType(resolvedName, ChannelType.DIRECT.name)
        if (existingByName != null) {
            val updated = existingByName.copy(
                name = if (profile != null) resolvedName else existingByName.name,
                isJoined = true,
                targetUserId = existingByName.targetUserId ?: resolvedUserId,
                avatarUrl = existingByName.avatarUrl ?: resolvedAvatar
            )
            channelDao.upsertChannel(updated)
            val alias = userAliasDao.getAlias(BeatmapUrlParser.normalizeUsername(resolvedName))
                ?.customNickname?.takeIf { it.isNotBlank() }
            return@withContext Result.success(updated.toDomain(alias))
        }

        val newEntity = ChannelEntity(
            id = dmChannelId,
            name = resolvedName,
            description = "Direct Message",
            type = ChannelType.DIRECT.name,
            targetUserId = resolvedUserId,
            avatarUrl = resolvedAvatar,
            lastMessageTimestamp = System.currentTimeMillis(),
            isJoined = true
        )
        channelDao.upsertChannel(newEntity)
        val alias = userAliasDao.getAlias(BeatmapUrlParser.normalizeUsername(resolvedName))
            ?.customNickname?.takeIf { it.isNotBlank() }
        Result.success(newEntity.toDomain(alias))
    }

    override suspend fun openFriendDmFast(
        username: String,
        userId: Long?,
        avatarUrl: String?
    ): Result<ChatChannel> = withContext(Dispatchers.IO) {
        val cleanName = username.trim()
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a username"))
        }
        val normUser = BeatmapUrlParser.normalizeUsername(cleanName)
        val dmChannelId = normalizeChannelId("dm:$normUser")
        val resolvedUid = userId?.takeIf { it > 0L }
        val resolvedAvatar = avatarUrl?.takeIf { it.isNotBlank() }
            ?: resolvedUid?.let { "https://a.ppy.sh/$it" }

        val existing = channelDao.getChannelById(dmChannelId)
            ?: channelDao.getChannelByNameAndType(cleanName, ChannelType.DIRECT.name)
        val entity = if (existing != null) {
            val updated = existing.copy(
                id = if (existing.id == dmChannelId) existing.id else dmChannelId,
                name = cleanName,
                isJoined = true,
                targetUserId = existing.targetUserId ?: resolvedUid,
                avatarUrl = existing.avatarUrl ?: resolvedAvatar
            )
            if (updated != existing) {
                channelDao.upsertChannel(updated)
            }
            updated
        } else {
            val created = ChannelEntity(
                id = dmChannelId,
                name = cleanName,
                description = "Direct Message",
                type = ChannelType.DIRECT.name,
                targetUserId = resolvedUid,
                avatarUrl = resolvedAvatar,
                lastMessageTimestamp = System.currentTimeMillis(),
                isJoined = true
            )
            channelDao.upsertChannel(created)
            created
        }

        if (entity.avatarUrl.isNullOrBlank() || entity.targetUserId == null) {
            scope.launch {
                val profile = getPlayerProfile(cleanName).getOrNull() ?: return@launch
                channelDao.updateDirectChannelMeta(
                    channelId = entity.id,
                    avatarUrl = profile.avatarUrl.takeIf { it.isNotBlank() },
                    userId = profile.userId.takeIf { it > 0L }
                )
            }
        }

        val alias = userAliasDao.getAlias(normUser)?.customNickname?.takeIf { it.isNotBlank() }
        Result.success(entity.toDomain(alias))
    }

    override suspend fun setChannelPinned(channelId: String, pinned: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val normId = normalizeChannelId(channelId)
            channelDao.setChannelPinned(normId, pinned)
            Result.success(Unit)
        }

    override suspend fun setChannelNotificationMode(
        channelId: String,
        mode: ChannelNotificationMode
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normId = normalizeChannelId(channelId)
        val existing = channelDao.getChannelById(normId)
        channelDao.setChannelNotificationMode(normId, mode.name)
        prefs.setChannelMuted(existing?.name ?: normId, mode == ChannelNotificationMode.MUTED)
        Result.success(Unit)
    }

    override suspend fun toggleChannelMute(channelIdOrName: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val normId = normalizeChannelId(channelIdOrName)
            val existing = channelDao.getChannelById(normId)
                ?: channelDao.getChannelByName(channelIdOrName.trim())
            val key = existing?.name ?: normId
            val nowMuted = prefs.toggleChannelMute(key)
            if (existing != null) {
                val targetMode = if (nowMuted) {
                    ChannelNotificationMode.MUTED
                } else if (
                    existing.type == ChannelType.DIRECT.name ||
                    OfficialOsuChannels.isMultiplayerRoom(existing.name) ||
                    OfficialOsuChannels.isMultiplayerRoom(existing.id)
                ) {
                    ChannelNotificationMode.ALL
                } else {
                    ChannelNotificationMode.MENTIONS_ONLY
                }
                channelDao.setChannelNotificationMode(existing.id, targetMode.name)
            }
            Result.success(nowMuted)
        }

    override suspend fun toggleChannelDotOnly(channelId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            Result.success(prefs.toggleChannelDotOnly(normalizeChannelId(channelId)))
        }

    override suspend fun markChannelAsRead(channelId: String) = withContext(Dispatchers.IO) {
        val normId = normalizeChannelId(channelId)
        channelDao.clearUnread(normId)
        notificationManager.clearChannelNotifications(normId)
        val current = prefs.settingsFlow.first()
        if (OAuthConfig.SUPPORTS_CHAT_READ_HISTORY && current.authMode == AuthMode.OAUTH_V2) {
            val numericId = normId.toLongOrNull() ?: return@withContext
            val token = apiPoller.getValidBearerToken() ?: return@withContext
            val lastMsgId = messageDao.getLatestMessageId(normId)
                ?.toLongOrNull() ?: return@withContext
            runCatching {
                apiPoller.withOptionalBearerRetry(token) { bearer ->
                    val resp = apiService.markAsRead(bearer, numericId, lastMsgId)
                    if (!resp.isSuccessful) throw HttpException(resp)
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    override suspend fun clearChannelMessages(channelId: String) = withContext(Dispatchers.IO) {
        val normId = normalizeChannelId(channelId)
        messageDao.deleteMessagesForChannel(normId)
        _channelHistoryStates.update { it - normId }
    }

    override suspend fun refreshPublicChannels(): Result<List<ChatChannel>> = withContext(Dispatchers.IO) {
        ensureDefaultIrcChannels()
        return@withContext Result.success(channels.first())
    }

    override suspend fun loadChannelHistory(channelId: String): Result<Unit> =
        syncChannelHistory(channelId = channelId, loadOlder = false, force = true)

    override suspend fun syncChannelHistory(
        channelId: String,
        loadOlder: Boolean,
        force: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!OAuthConfig.SUPPORTS_CHAT_READ_HISTORY) return@withContext Result.success(Unit)
        val normId = normalizeChannelId(channelId)
        if (normId.isBlank()) return@withContext Result.success(Unit)

        val currentSettings = prefs.settingsFlow.first()
        if (currentSettings.authMode != AuthMode.OAUTH_V2) return@withContext Result.success(Unit)
        if (_requiresChatReadReauth.value) return@withContext Result.success(Unit)
        apiPoller.getValidBearerToken() ?: return@withContext Result.success(Unit)

        val existingState = _channelHistoryStates.value[normId] ?: ChannelHistoryState()
        if (!loadOlder && existingState.initialSynced && !force) {
            return@withContext Result.success(Unit)
        }
        if (loadOlder && (!existingState.hasMore || existingState.oldestApiMessageId == null)) {
            return@withContext Result.success(Unit)
        }
        if (!inFlightHistoryChannels.add(normId)) {
            return@withContext Result.success(Unit)
        }

        updateChannelHistoryState(normId) { it.copy(isLoading = true) }
        try {
            val resolved = resolveApiChannelForHistory(normId, currentSettings)
            if (_requiresChatReadReauth.value) {
                return@withContext Result.success(Unit)
            }
            if (resolved == null) {
                updateChannelHistoryState(normId) {
                    it.copy(isLoading = false, hasMore = false, initialSynced = true)
                }
                return@withContext Result.success(Unit)
            }

            val recentSeed = if (!loadOlder) resolved.recentMessages.orEmpty() else emptyList()
            if (recentSeed.isNotEmpty()) {
                ingestApiHistoryBatch(normId, recentSeed, currentSettings)
            }

            val fetchedMessages = if (!loadOlder && !force && recentSeed.size >= ChatHistorySyncHelper.HISTORY_PAGE_SIZE) {
                recentSeed
            } else {
                val untilId = if (loadOlder) existingState.oldestApiMessageId else null
                when (
                    val callResult = executeChatHistoryCall { bearer ->
                        apiService.getChannelMessages(
                            bearerToken = bearer,
                            channelId = resolved.apiChannelId,
                            limit = ChatHistorySyncHelper.HISTORY_PAGE_SIZE,
                            since = null,
                            until = untilId
                        )
                    }
                ) {
                    is ChatHistoryCallResult.ForbiddenReauth -> return@withContext Result.success(Unit)
                    is ChatHistoryCallResult.NotFound -> {
                        updateChannelHistoryState(normId) {
                            it.copy(isLoading = false, hasMore = false, initialSynced = true)
                        }
                        return@withContext Result.success(Unit)
                    }
                    is ChatHistoryCallResult.Success -> callResult.body.orEmpty()
                }
            }

            if (fetchedMessages.isNotEmpty() && !(recentSeed.size >= ChatHistorySyncHelper.HISTORY_PAGE_SIZE && !loadOlder && !force)) {
                ingestApiHistoryBatch(normId, fetchedMessages, currentSettings)
            }

            val combinedForPagination = if (recentSeed.isNotEmpty() && !loadOlder) {
                recentSeed + fetchedMessages
            } else {
                fetchedMessages
            }
            val minFetchedId = combinedForPagination.asSequence()
                .map { it.messageId }
                .filter { it > 0L }
                .minOrNull()
            val maxFetchedId = combinedForPagination.asSequence()
                .map { it.messageId }
                .filter { it > 0L }
                .maxOrNull()

            if (maxFetchedId != null) {
                apiPoller.updateLastMessageId(maxFetchedId)
            }

            val prevOldest = existingState.oldestApiMessageId
            val nextOldest = when {
                prevOldest == null -> minFetchedId
                minFetchedId == null -> prevOldest
                else -> minOf(prevOldest, minFetchedId)
            }
            val madeProgress = !loadOlder || (minFetchedId != null && minFetchedId != prevOldest)
            val nextHasMore = fetchedMessages.size >= ChatHistorySyncHelper.HISTORY_PAGE_SIZE && madeProgress

            updateChannelHistoryState(normId) {
                it.copy(
                    isLoading = false,
                    hasMore = nextHasMore,
                    oldestApiMessageId = nextOldest,
                    initialSynced = true
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            inFlightHistoryChannels.remove(normId)
            updateChannelHistoryState(normId) {
                if (it.isLoading) it.copy(isLoading = false) else it
            }
        }
    }

    private fun resetChatHistorySessionState() {
        apiChannelIdCache.clear()
        apiDmChannelIdByUserId.clear()
        publicChannelsCatalogLoaded = false
        _channelHistoryStates.value = emptyMap()
        _requiresChatReadReauth.value = false
    }

    private fun updateChannelHistoryState(
        normChannelId: String,
        transform: (ChannelHistoryState) -> ChannelHistoryState
    ) {
        _channelHistoryStates.update { current ->
            val prev = current[normChannelId] ?: ChannelHistoryState()
            val next = transform(prev)
            if (next == prev) current else current + (normChannelId to next)
        }
    }

    private suspend fun <T> executeChatHistoryCall(
        block: suspend (bearerToken: String) -> Response<T>
    ): ChatHistoryCallResult<T> {
        return try {
            val response = apiPoller.withBearerRetry("Missing OAuth token") { bearer ->
                val resp = block(bearer)
                if (resp.code() == 401 || resp.code() == 429) {
                    throw HttpException(resp)
                }
                resp
            }
            when {
                response.code() == 403 -> {
                    _requiresChatReadReauth.value = true
                    ChatHistoryCallResult.ForbiddenReauth
                }
                response.isSuccessful -> ChatHistoryCallResult.Success(response.body())
                response.code() == 404 || response.code() == 422 -> ChatHistoryCallResult.NotFound
                else -> throw apiPoller.toFriendlyApiException(HttpException(response))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: OsuApiException) {
            if (e.httpCode == 403) {
                _requiresChatReadReauth.value = true
                ChatHistoryCallResult.ForbiddenReauth
            } else {
                throw e
            }
        } catch (e: HttpException) {
            if (e.code() == 403) {
                _requiresChatReadReauth.value = true
                ChatHistoryCallResult.ForbiddenReauth
            } else {
                throw apiPoller.toFriendlyApiException(e)
            }
        }
    }

    private suspend fun ensurePublicChannelsCatalogLoaded(selfUserId: Long): Boolean {
        if (!OAuthConfig.SUPPORTS_CHAT_READ_HISTORY) {
            return false
        }
        if (publicChannelsCatalogLoaded || _requiresChatReadReauth.value) {
            return !_requiresChatReadReauth.value
        }
        return publicChannelsFetchMutex.withLock {
            if (publicChannelsCatalogLoaded || _requiresChatReadReauth.value) {
                return@withLock !_requiresChatReadReauth.value
            }
            when (val result = executeChatHistoryCall { bearer -> apiService.getChannels(bearer) }) {
                is ChatHistoryCallResult.ForbiddenReauth -> false
                is ChatHistoryCallResult.NotFound -> {
                    publicChannelsCatalogLoaded = true
                    true
                }
                is ChatHistoryCallResult.Success -> {
                    for (dto in result.body.orEmpty()) {
                        if (dto.channelId <= 0L) continue
                        val isPm = dto.type.equals("PM", ignoreCase = true)
                        if (isPm) {
                            val otherUserId = dto.users?.firstOrNull { it > 0L && it != selfUserId }
                            if (otherUserId != null) {
                                apiDmChannelIdByUserId[otherUserId] = dto.channelId
                            }
                        } else {
                            val rawName = dto.name?.trim().orEmpty()
                            if (rawName.isNotEmpty()) {
                                val key = when {
                                    rawName.startsWith("#") -> rawName.lowercase(Locale.ROOT)
                                    dto.type.equals("PUBLIC", ignoreCase = true) -> "#${rawName.lowercase(Locale.ROOT)}"
                                    else -> rawName.lowercase(Locale.ROOT)
                                }
                                apiChannelIdCache[key] = dto.channelId
                            }
                        }
                    }
                    publicChannelsCatalogLoaded = true
                    true
                }
            }
        }
    }

    private suspend fun resolveApiChannelForHistory(
        normId: String,
        settings: AppSettings
    ): ResolvedApiChannel? {
        if (!OAuthConfig.SUPPORTS_CHAT_READ_HISTORY) return null
        normId.toLongOrNull()?.let { numeric ->
            apiChannelIdCache[normId] = numeric
            return ResolvedApiChannel(numeric)
        }
        apiChannelIdCache[normId]?.let { cached ->
            return ResolvedApiChannel(cached)
        }
        if (normId.startsWith("#")) {
            val loaded = ensurePublicChannelsCatalogLoaded(settings.userId)
            if (!loaded) return null
            return apiChannelIdCache[normId]?.let { ResolvedApiChannel(it) }
        }
        if (normId.startsWith("dm:")) {
            val existingChannel = channelDao.getChannelById(normId)
            val targetName = existingChannel?.name?.takeIf { it.isNotBlank() }
                ?: normId.removePrefix("dm:").replace('_', ' ').trim()
            val targetNorm = BeatmapUrlParser.normalizeOsuNick(targetName)

            val recipientUserId = existingChannel?.targetUserId?.takeIf { it > 0L }
                ?: _apiFriends.value.firstOrNull {
                    it.id > 0L && BeatmapUrlParser.normalizeOsuNick(it.username.orEmpty()) == targetNorm
                }?.id
                ?: playerProfileCacheDao.getByUsername(targetNorm)?.userId?.takeIf { it > 0L }
                ?: getPlayerProfile(targetName).getOrNull()?.userId?.takeIf { it > 0L }
                ?: return null

            if (existingChannel != null && existingChannel.targetUserId == null) {
                channelDao.updateDirectChannelMeta(normId, null, recipientUserId)
            }
            apiDmChannelIdByUserId[recipientUserId]?.let { cachedDmId ->
                apiChannelIdCache[normId] = cachedDmId
                return ResolvedApiChannel(cachedDmId)
            }

            return when (
                val createRes = executeChatHistoryCall { bearer ->
                    apiService.createChannel(
                        bearerToken = bearer,
                        request = OsuCreateChannelRequestDto(
                            type = "PM",
                            targetId = recipientUserId
                        )
                    )
                }
            ) {
                is ChatHistoryCallResult.Success -> {
                    val dto = createRes.body
                    if (dto != null && dto.channelId > 0L) {
                        apiChannelIdCache[normId] = dto.channelId
                        apiDmChannelIdByUserId[recipientUserId] = dto.channelId
                        ResolvedApiChannel(dto.channelId, dto.recentMessages)
                    } else {
                        null
                    }
                }
                is ChatHistoryCallResult.ForbiddenReauth,
                is ChatHistoryCallResult.NotFound -> null
            }
        }
        return null
    }

    private suspend fun ingestApiHistoryBatch(
        normChannelId: String,
        messages: List<ChatMessageDto>,
        settings: AppSettings
    ) {
        if (messages.isEmpty()) return
        val existingChannel = channelDao.getChannelById(normChannelId)
        val senderNameByUserId = HashMap<Long, String>()

        val candidates = ArrayList<MessageEntity>(messages.size)
        var minTs = Long.MAX_VALUE
        var maxTs = Long.MIN_VALUE

        for (dto in messages) {
            if (dto.messageId <= 0L) continue
            val ts = ChatHistorySyncHelper.parseIso8601TimestampMillis(dto.timestamp)
            if (ts < minTs) minTs = ts
            if (ts > maxTs) maxTs = ts

            val senderName = resolveApiSenderName(
                dto = dto,
                normChannelId = normChannelId,
                existingChannel = existingChannel,
                settings = settings,
                senderNameByUserId = senderNameByUserId
            )
            if (dto.sender != null &&
                (!dto.sender.profileColour.isNullOrBlank() || dto.sender.profileHue != null || dto.sender.isSupporter)
            ) {
                recordChatUserColor(
                    username = dto.sender.username ?: senderName,
                    profileColour = dto.sender.profileColour,
                    profileHue = dto.sender.profileHue,
                    isSupporter = dto.sender.isSupporter
                )
            }

            val rawContent = dto.content.orEmpty()
            val isOutgoing = isSelfSender(senderName, dto.senderId, settings)
            val isIgnored = !isOutgoing && isSenderIgnored(senderName, settings)
            val isHighlighted = !isOutgoing && !isIgnored && isMessageHighlighted(rawContent, settings)
            val senderAvatar = dto.sender?.avatarUrl
                ?: dto.senderId.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" }

            candidates.add(
                MessageEntity(
                    id = ChatHistorySyncHelper.apiMessageId(dto.messageId),
                    channelId = normChannelId,
                    senderId = dto.senderId,
                    senderName = senderName,
                    senderAvatarUrl = senderAvatar,
                    content = rawContent,
                    timestamp = ts,
                    isOutgoing = isOutgoing,
                    isAction = dto.isAction,
                    isHighlighted = isHighlighted,
                    deliveryStatus = if (isOutgoing) DeliveryStatus.SENT.name else DeliveryStatus.READ.name
                )
            )
        }

        if (candidates.isEmpty()) return

        val rangeMin = (minTs - ChatHistorySyncHelper.DEDUP_WINDOW_MS).coerceAtLeast(0L)
        val rangeMax = maxTs + ChatHistorySyncHelper.DEDUP_WINDOW_MS
        val existingInRange = messageDao.getMessagesInRange(normChannelId, rangeMin, rangeMax)
        val toInsert = ChatHistorySyncHelper.filterNewApiMessages(candidates, existingInRange)
        if (toInsert.isNotEmpty()) {
            messageDao.insertMessagesIfAbsent(toInsert)
        }

        val newestCandidate = candidates.maxByOrNull { it.timestamp }
        if (newestCandidate != null &&
            existingChannel != null &&
            newestCandidate.timestamp > existingChannel.lastMessageTimestamp
        ) {
            channelDao.updateChannelLastMessageIfNewer(
                channelId = normChannelId,
                text = if (newestCandidate.isAction) "* ${newestCandidate.content}" else newestCandidate.content,
                timestamp = newestCandidate.timestamp,
                senderName = newestCandidate.senderName
            )
        }
    }

    private suspend fun resolveApiSenderName(
        dto: ChatMessageDto,
        normChannelId: String,
        existingChannel: ChannelEntity?,
        settings: AppSettings,
        senderNameByUserId: MutableMap<Long, String>
    ): String {
        val fromDto = dto.sender?.username?.trim()?.takeIf { it.isNotEmpty() }
        if (fromDto != null) {
            if (dto.senderId > 0L) senderNameByUserId[dto.senderId] = fromDto
            return fromDto
        }
        if (dto.senderId > 0L) {
            senderNameByUserId[dto.senderId]?.let { return it }
            if (settings.userId > 0L && dto.senderId == settings.userId && settings.username.isNotBlank()) {
                senderNameByUserId[dto.senderId] = settings.username
                return settings.username
            }
            val fromFriend = _apiFriends.value.firstOrNull { it.id == dto.senderId }
                ?.username?.trim()?.takeIf { it.isNotEmpty() }
            if (fromFriend != null) {
                senderNameByUserId[dto.senderId] = fromFriend
                return fromFriend
            }
            val fromCache = playerProfileCacheDao.getByUserId(dto.senderId)
                ?.username?.trim()?.takeIf { it.isNotEmpty() }
            if (fromCache != null) {
                senderNameByUserId[dto.senderId] = fromCache
                return fromCache
            }
        }
        if (normChannelId.startsWith("dm:") || existingChannel?.type == ChannelType.DIRECT.name) {
            if (dto.senderId > 0L && settings.userId > 0L && dto.senderId == settings.userId) {
                val selfName = settings.username.trim().takeIf { it.isNotEmpty() }
                if (selfName != null) return selfName
            } else {
                val partnerName = existingChannel?.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: normChannelId.removePrefix("dm:").replace('_', ' ').trim().takeIf { it.isNotEmpty() }
                if (partnerName != null) return partnerName
            }
        }
        return "User ${dto.senderId}"
    }

    override suspend fun setUserAlias(
        username: String,
        customNickname: String?,
        userId: Long?,
        note: String?
    ) = withContext(Dispatchers.IO) {
        val norm = BeatmapUrlParser.normalizeUsername(username)
        val existing = userAliasDao.getAlias(norm)
        val finalNickname = customNickname?.trim().orEmpty()
        val finalNote = if (note != null) {
            note.trim()
        } else if (customNickname == null) {
            ""
        } else {
            existing?.note?.trim().orEmpty()
        }
        if (finalNickname.isEmpty() && finalNote.isEmpty()) {
            userAliasDao.deleteAlias(norm)
        } else {
            userAliasDao.upsertAlias(
                UserAliasEntity(
                    normalizedUsername = norm,
                    originalUsername = existing?.originalUsername?.takeIf { it.isNotBlank() } ?: username.trim(),
                    userId = userId ?: existing?.userId,
                    customNickname = finalNickname,
                    note = finalNote,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun toggleBookmarkMessage(
        message: ChatMessage,
        channelName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedChannelName = channelName.trim().ifBlank { message.channelId }
            val entity = BookmarkedMessageEntity(
                messageId = message.id,
                channelId = message.channelId,
                channelName = resolvedChannelName,
                senderName = message.senderName.ifBlank { "System" },
                content = message.content,
                messageTimestamp = message.timestamp,
                bookmarkedAt = System.currentTimeMillis()
            )
            bookmarkedMessageDao.toggleBookmark(entity)
        }.onFailure { if (it is CancellationException) throw it }
    }

    override suspend fun removeBookmarkMessage(messageId: String) = withContext(Dispatchers.IO) {
        bookmarkedMessageDao.deleteBookmark(messageId)
    }

    override suspend fun getCachedSelfProfile(forceRefresh: Boolean): Result<PlayerProfile> =
        withContext(Dispatchers.IO) {
            val current = prefs.settingsFlow.first()
            if (current.authMode == AuthMode.NONE) {
                return@withContext Result.failure(IllegalStateException("Not signed in"))
            }

            if (!forceRefresh) {
                selfProfileMemoryCache?.let { cached ->
                    val sameUser = (current.userId > 0L && cached.userId == current.userId) ||
                        (current.username.isNotBlank() && cached.username.equals(current.username, ignoreCase = true))
                    if (sameUser || current.username.isBlank()) {
                        return@withContext Result.success(cached)
                    }
                }
                selfProfileCacheDao.getByKey()?.let { entity ->
                    val norm = BeatmapUrlParser.normalizeUsername(entity.username)
                    val aliasEntity = userAliasDao.getAlias(norm)
                    val alias = aliasEntity?.customNickname?.takeIf { it.isNotBlank() }
                    val note = aliasEntity?.note?.takeIf { it.isNotBlank() }
                    val tracked = trackedFriendDao.getTrackedFriend(norm)
                    val domain = entity.toDomain(
                        customAlias = alias,
                        customNote = note,
                        isTrackedForPp = tracked != null && (tracked.lastCheckedAt > 0L || tracked.topScoreIdsCsv.isNotEmpty()),
                        notifyOnline = tracked?.notifyOnline == true,
                        friendStatus = FriendRelationStatus.NONE
                    )
                    selfProfileMemoryCache = domain
                    return@withContext Result.success(domain)
                }
            }

            if (current.authMode == AuthMode.OAUTH_V2) {
                val result = runCatching {
                    val dto = apiPoller.coalesceBearerGet(
                        key = "self_profile",
                        missingTokenMessage = "Sign in with osu! OAuth to load profile",
                        minIntervalMs = 250L
                    ) { bearer ->
                        apiService.getMe(bearer)
                    }
                    val safeName = dto.username?.takeIf { it.isNotBlank() }
                        ?: current.username.ifBlank { "User ${dto.id}" }
                    val norm = BeatmapUrlParser.normalizeUsername(safeName)
                    val aliasEntity = userAliasDao.getAlias(norm)
                    val alias = aliasEntity?.customNickname?.takeIf { it.isNotBlank() }
                    val note = aliasEntity?.note?.takeIf { it.isNotBlank() }
                    val tracked = trackedFriendDao.getTrackedFriend(norm)
                    val selfMode = dto.playmode?.takeIf { it in VALID_OSU_MODES } ?: "osu"
                    cacheUserProfile(dto, syncFriendsFlow = false)
                    val profile = dto.toProfileDomain(
                        customAlias = alias,
                        customNote = note,
                        isTrackedForPp = tracked != null && (tracked.lastCheckedAt > 0L || tracked.topScoreIdsCsv.isNotEmpty()),
                        notifyOnline = tracked?.notifyOnline == true,
                        friendStatus = FriendRelationStatus.NONE,
                        mode = selfMode
                    )
                    selfProfileMemoryCache = profile
                    selfProfileCacheDao.upsert(profile.toSelfCacheEntity())
                    recordDailyProfileSnapshot(profile)
                    syncSelfProfileToWidget(profile)
                    profile
                }.onFailure { if (it is CancellationException) throw it }

                if (result.isSuccess) return@withContext result
                selfProfileMemoryCache?.let { return@withContext Result.success(it) }
                selfProfileCacheDao.getByKey()?.let { entity ->
                    val domain = entity.toDomain(customAlias = null)
                    selfProfileMemoryCache = domain
                    return@withContext Result.success(domain)
                }
                return@withContext result
            }

            // IRC-only auth fallback
            val nick = current.username.trim()
            if (nick.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Missing username"))
            }
            val profileRes = getPlayerProfile(nick)
            profileRes.getOrNull()?.let { profile ->
                selfProfileMemoryCache = profile
                selfProfileCacheDao.upsert(profile.toSelfCacheEntity())
                recordDailyProfileSnapshot(profile)
            }
            profileRes
        }

    override suspend fun getPlayerProfile(
        usernameOrId: String,
        mode: String?
    ): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        val clean = usernameOrId.trim()
        if (clean.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty username or user ID"))
        }
        val norm = BeatmapUrlParser.normalizeUsername(clean)
        val requestedMode = mode?.trim()?.lowercase()?.takeIf { it in VALID_OSU_MODES }
        val aliasEntity = userAliasDao.getAlias(norm)
        val alias = aliasEntity?.customNickname?.takeIf { it.isNotBlank() }
        val note = aliasEntity?.note?.takeIf { it.isNotBlank() }
        val trackedEntity = trackedFriendDao.getTrackedFriend(norm)
        val isTracked = trackedEntity != null && (trackedEntity.lastCheckedAt > 0L || trackedEntity.topScoreIdsCsv.isNotEmpty())
        val isNotifyOnline = trackedEntity?.notifyOnline == true

        val modeCacheKey = "${norm}:${requestedMode ?: "default"}"
        val now = System.currentTimeMillis()
        if (modeProfileCache.size > 256) {
            modeProfileCache.entries.removeIf { (now - it.value.first) >= 60_000L }
        }
        modeProfileCache[modeCacheKey]?.let { (cachedAt, cachedProfile) ->
            if ((now - cachedAt) < 60_000L) {
                val resolvedNorm = BeatmapUrlParser.normalizeUsername(cachedProfile.username)
                val resolvedAliasEntity = userAliasDao.getAlias(resolvedNorm) ?: aliasEntity
                val resolvedAlias = resolvedAliasEntity?.customNickname?.takeIf { it.isNotBlank() }
                val resolvedNote = resolvedAliasEntity?.note?.takeIf { it.isNotBlank() }
                val resolvedTrackedEntity = trackedFriendDao.getTrackedFriend(resolvedNorm) ?: trackedEntity
                val tracked = resolvedTrackedEntity != null &&
                    (resolvedTrackedEntity.lastCheckedAt > 0L || resolvedTrackedEntity.topScoreIdsCsv.isNotEmpty())
                val notifyOnlineFlag = resolvedTrackedEntity?.notifyOnline == true
                val status = resolveFriendStatus(cachedProfile.userId, cachedProfile.username, null)
                return@withContext Result.success(
                    cachedProfile.copy(
                        customAlias = resolvedAlias,
                        customNote = resolvedNote,
                        isTrackedForPp = tracked,
                        notifyOnline = notifyOnlineFlag,
                        friendStatus = status
                    )
                )
            }
        }

        val cachedRoom = if (requestedMode == null) {
            clean.toLongOrNull()?.let { playerProfileCacheDao.getByUserId(it) }
                ?: playerProfileCacheDao.getByUsername(norm)
        } else null

        if (cachedRoom != null && (now - cachedRoom.cachedAt) < 60_000L) {
            val resolvedNorm = BeatmapUrlParser.normalizeUsername(cachedRoom.username)
            val resolvedAliasEntity = userAliasDao.getAlias(resolvedNorm) ?: aliasEntity
            val resolvedAlias = resolvedAliasEntity?.customNickname?.takeIf { it.isNotBlank() }
            val resolvedNote = resolvedAliasEntity?.note?.takeIf { it.isNotBlank() }
            val resolvedTrackedEntity = trackedFriendDao.getTrackedFriend(resolvedNorm) ?: trackedEntity
            val tracked = resolvedTrackedEntity != null &&
                (resolvedTrackedEntity.lastCheckedAt > 0L || resolvedTrackedEntity.topScoreIdsCsv.isNotEmpty())
            val notifyOnlineFlag = resolvedTrackedEntity?.notifyOnline == true
            val status = resolveFriendStatus(cachedRoom.userId, cachedRoom.username, null)
            val domain = cachedRoom.toDomain(
                customAlias = resolvedAlias,
                customNote = resolvedNote,
                isTrackedForPp = tracked,
                notifyOnline = notifyOnlineFlag,
                friendStatus = status
            )
            modeProfileCache[modeCacheKey] = now to domain
            return@withContext Result.success(domain)
        }

        var createdDeferred: Deferred<Result<PlayerProfile>>? = null
        val deferred = inFlightProfileLookups[modeCacheKey] ?: run {
            val newDeferred = scope.async {
                fetchPlayerProfileFromNetwork(
                    clean = clean,
                    norm = norm,
                    requestedMode = requestedMode,
                    alias = alias,
                    note = note,
                    isTracked = isTracked,
                    notifyOnline = isNotifyOnline,
                    cachedRoom = cachedRoom
                )
            }
            val existing = inFlightProfileLookups.putIfAbsent(modeCacheKey, newDeferred)
            if (existing != null) {
                newDeferred.cancel()
                existing
            } else {
                createdDeferred = newDeferred
                newDeferred
            }
        }
        return@withContext try {
            deferred.await()
        } finally {
            createdDeferred?.let { inFlightProfileLookups.remove(modeCacheKey, it) }
        }
    }

    private suspend fun fetchPlayerProfileFromNetwork(
        clean: String,
        norm: String,
        requestedMode: String?,
        alias: String?,
        note: String?,
        isTracked: Boolean,
        notifyOnline: Boolean,
        cachedRoom: PlayerProfileCacheEntity?
    ): Result<PlayerProfile> {
        val token = apiPoller.getValidBearerToken()
        if (token != null && !apiPoller.isRateLimited()) {
            val apiResult = runCatching {
                val suffix = requestedMode ?: "default"
                apiPoller.coalesceBearerGet(
                    key = "user:${clean.lowercase()}:$suffix",
                    minIntervalMs = 250L
                ) { activeToken ->
                    val keyType = if (clean.all { it.isDigit() }) "id" else "username"
                    val dto = if (requestedMode != null) {
                        apiService.getUserWithMode(activeToken, clean, requestedMode, keyType)
                    } else {
                        apiService.getUser(activeToken, clean, keyType)
                    }
                    val safeName = dto.username?.takeIf { it.isNotBlank() } ?: clean
                    val dtoNorm = BeatmapUrlParser.normalizeUsername(safeName)
                    val resolvedAliasEntity = userAliasDao.getAlias(dtoNorm)
                    val resolvedAlias = resolvedAliasEntity?.customNickname?.takeIf { it.isNotBlank() } ?: alias
                    val resolvedNote = resolvedAliasEntity?.note?.takeIf { it.isNotBlank() } ?: note
                    val dtoTrackedEntity = trackedFriendDao.getTrackedFriend(dtoNorm)
                    val tracked = (dtoTrackedEntity != null &&
                        (dtoTrackedEntity.lastCheckedAt > 0L || dtoTrackedEntity.topScoreIdsCsv.isNotEmpty())) || isTracked
                    val notifyOnlineFlag = dtoTrackedEntity?.notifyOnline == true || notifyOnline
                    val status = resolveFriendStatus(dto.id, safeName, dto.isMutual)
                    val resolvedMode = requestedMode ?: dto.playmode?.takeIf { it in VALID_OSU_MODES } ?: "osu"
                    val profile = if (requestedMode == null) {
                        cacheUserProfile(dto, syncFriendsFlow = true)
                            .toDomain(
                                customAlias = resolvedAlias,
                                customNote = resolvedNote,
                                isTrackedForPp = tracked,
                                notifyOnline = notifyOnlineFlag,
                                friendStatus = status,
                                mode = resolvedMode
                            )
                    } else {
                        dto.toProfileDomain(
                            customAlias = resolvedAlias,
                            customNote = resolvedNote,
                            isTrackedForPp = tracked,
                            notifyOnline = notifyOnlineFlag,
                            friendStatus = status,
                            mode = resolvedMode
                        )
                    }
                    val fetchedAt = System.currentTimeMillis()
                    recordChatUserColor(
                        username = safeName,
                        profileColour = dto.profileColour,
                        profileHue = dto.profileHue,
                        isSupporter = dto.isSupporter,
                        aliasKey = norm
                    )
                    modeProfileCache["$norm:$suffix"] = fetchedAt to profile
                    if (dto.id > 0L) modeProfileCache["${dto.id}:$suffix"] = fetchedAt to profile
                    modeProfileCache["$dtoNorm:$suffix"] = fetchedAt to profile
                    if (requestedMode == null || requestedMode == dto.playmode) {
                        syncSelfProfileToWidget(profile)
                    }
                    val currentSelf = prefs.settingsFlow.first()
                    val isSelfProfile = (currentSelf.userId > 0L && profile.userId == currentSelf.userId) ||
                        (currentSelf.username.isNotBlank() && profile.username.equals(currentSelf.username, ignoreCase = true))
                    if (isSelfProfile) {
                        recordDailyProfileSnapshot(profile)
                    }
                    profile
                }
            }.onFailure {
                if (it is CancellationException) throw it
            }
            if (apiResult.isSuccess) return apiResult
        }

        if (cachedRoom != null) {
            val status = resolveFriendStatus(cachedRoom.userId, cachedRoom.username, null)
            return Result.success(
                cachedRoom.toDomain(
                    customAlias = alias,
                    customNote = note,
                    isTrackedForPp = isTracked,
                    notifyOnline = notifyOnline,
                    friendStatus = status
                )
            )
        }

        // Minimal fallback when offline or lookup blocked
        return Result.success(
            PlayerProfile(
                userId = clean.toLongOrNull() ?: 0L,
                username = clean,
                customAlias = alias,
                customNote = note,
                avatarUrl = "https://osu.ppy.sh/images/layout/avatar-guest.png",
                countryCode = "XX",
                isTrackedForPp = isTracked,
                notifyOnline = notifyOnline,
                friendStatus = resolveFriendStatus(clean.toLongOrNull() ?: 0L, clean, null),
                mode = requestedMode ?: "osu"
            )
        )
    }

    override suspend fun addFriend(profile: PlayerProfile): Result<FriendRelationStatus> = withContext(Dispatchers.IO) {
        val resolvedProfile = if (profile.userId <= 0L) {
            getPlayerProfile(profile.username).getOrNull() ?: profile
        } else {
            profile
        }
        val uid = resolvedProfile.userId
        val token = apiPoller.getValidBearerToken()
        if (token != null && uid > 0L) {
            runCatching {
                apiPoller.withOptionalBearerRetry(token) { bearer ->
                    val resp = apiService.addFriend(bearer, uid)
                    if (!resp.isSuccessful) throw HttpException(resp)
                }
            }.onFailure { if (it is CancellationException) throw it }
        }

        val norm = BeatmapUrlParser.normalizeUsername(resolvedProfile.username)
        val dto = OsuUserDto(
            id = uid,
            username = resolvedProfile.username,
            avatarUrl = resolvedProfile.avatarUrl,
            countryCode = resolvedProfile.countryCode,
            isOnline = resolvedProfile.isOnline,
            isMutual = resolvedProfile.friendStatus == FriendRelationStatus.MUTUAL,
            statistics = com.osuchat.app.data.network.OsuUserStatsDto(
                globalRank = resolvedProfile.globalRank,
                countryRank = resolvedProfile.countryRank,
                pp = resolvedProfile.pp,
                hitAccuracy = resolvedProfile.accuracy,
                playCount = resolvedProfile.playCount
            )
        )

        if (uid > 0L) {
            val removed = prefs.getLocalRemovedFriendIds().toMutableSet()
            removed.remove(uid)
            prefs.saveLocalRemovedFriendIds(removed)
        }

        val added = parseUserDtoList(prefs.getLocalAddedFriendsJson()).toMutableList()
        added.removeAll {
            (uid > 0L && it.id == uid) ||
                BeatmapUrlParser.normalizeUsername(it.username.orEmpty()) == norm
        }
        added.add(dto)
        prefs.saveLocalAddedFriendsJson(gson.toJson(added))

        val cachedServer = parseUserDtoList(prefs.getCachedFriendsJson())
        _apiFriends.value = mergeFriendsWithLocalOverrides(cachedServer)

        Result.success(resolveFriendStatus(uid, resolvedProfile.username, dto.isMutual))
    }

    override suspend fun removeFriend(userId: Long, username: String): Result<FriendRelationStatus> =
        withContext(Dispatchers.IO) {
            val token = apiPoller.getValidBearerToken()
            if (token != null && userId > 0L) {
                runCatching {
                    apiPoller.withOptionalBearerRetry(token) { bearer ->
                        val resp = apiService.removeFriend(bearer, userId)
                        if (!resp.isSuccessful) throw HttpException(resp)
                    }
                }.onFailure { if (it is CancellationException) throw it }
            }

            val norm = BeatmapUrlParser.normalizeUsername(username)
            if (userId > 0L) {
                val removed = prefs.getLocalRemovedFriendIds().toMutableSet()
                removed.add(userId)
                prefs.saveLocalRemovedFriendIds(removed)
            }

            val added = parseUserDtoList(prefs.getLocalAddedFriendsJson()).filterNot {
                (userId > 0L && it.id == userId) ||
                    BeatmapUrlParser.normalizeUsername(it.username.orEmpty()) == norm
            }
            prefs.saveLocalAddedFriendsJson(gson.toJson(added))

            val cachedServer = parseUserDtoList(prefs.getCachedFriendsJson()).filterNot {
                BeatmapUrlParser.normalizeUsername(it.username.orEmpty()) == norm
            }
            prefs.saveCachedFriendsJson(gson.toJson(cachedServer))
            _apiFriends.value = mergeFriendsWithLocalOverrides(cachedServer)

            Result.success(FriendRelationStatus.NONE)
        }

    override suspend fun getBeatmapDetails(beatmapId: Long): Result<BeatmapMetadata> =
        getBeatmapMetadata(
            BeatmapLinkRef(
                url = "https://osu.ppy.sh/b/$beatmapId",
                beatmapId = beatmapId
            )
        )

    override suspend fun getCachedBeatmaps(beatmapIds: Collection<Long>): Map<Long, BeatmapMetadata> =
        withContext(Dispatchers.IO) {
            val validIds = beatmapIds.filter { it > 0L }.distinct()
            if (validIds.isEmpty()) return@withContext emptyMap()
            val result = LinkedHashMap<Long, BeatmapMetadata>(validIds.size)
            val missingKeys = mutableListOf<String>()
            for (id in validIds) {
                val key = "b:$id"
                val mem = beatmapMemoryCache.get(key)
                if (mem != null) {
                    result[id] = mem
                } else {
                    missingKeys.add(key)
                }
            }
            if (missingKeys.isNotEmpty()) {
                missingKeys.chunked(500).forEach { chunk ->
                    beatmapCacheDao.getByKeys(chunk).forEach { entity ->
                        val domain = entity.toDomain()
                        beatmapMemoryCache.put(entity.cacheKey, domain)
                        if (domain.beatmapId > 0L) {
                            result[domain.beatmapId] = domain
                        }
                    }
                }
            }
            result
        }

    override suspend fun getBeatmapMetadata(linkRef: BeatmapLinkRef): Result<BeatmapMetadata> =
        withContext(Dispatchers.IO) {
            val key = when {
                linkRef.beatmapId != null -> "b:${linkRef.beatmapId}"
                linkRef.beatmapsetId != null -> "s:${linkRef.beatmapsetId}"
                else -> return@withContext Result.failure(IllegalArgumentException("Invalid beatmap link"))
            }

            beatmapMemoryCache.get(key)?.let { cachedMem ->
                return@withContext Result.success(cachedMem)
            }

            beatmapCacheDao.getByKey(key)?.let { cached ->
                val domain = cached.toDomain()
                beatmapMemoryCache.put(key, domain)
                return@withContext Result.success(domain)
            }

            var createdDeferred: Deferred<Result<BeatmapMetadata>>? = null
            val deferred = inFlightBeatmapLookups[key] ?: run {
                val newDeferred = scope.async {
                    fetchBeatmapMetadataFromNetwork(key, linkRef)
                }
                val existing = inFlightBeatmapLookups.putIfAbsent(key, newDeferred)
                if (existing != null) {
                    newDeferred.cancel()
                    existing
                } else {
                    createdDeferred = newDeferred
                    newDeferred
                }
            }
            return@withContext try {
                deferred.await()
            } finally {
                createdDeferred?.let { inFlightBeatmapLookups.remove(key, it) }
            }
        }

    private suspend fun fetchBeatmapMetadataFromNetwork(
        key: String,
        linkRef: BeatmapLinkRef
    ): Result<BeatmapMetadata> {
        val metadata = runCatching {
            when {
                linkRef.beatmapId != null -> {
                    val dto = apiPoller.withBearerRetry("Sign in with osu! OAuth to load beatmap info") { activeToken ->
                        apiService.getBeatmap(activeToken, linkRef.beatmapId)
                    }
                    dto.toMetadata()
                }
                linkRef.beatmapsetId != null -> {
                    val dto = apiPoller.withBearerRetry("Sign in with osu! OAuth to load beatmap info") { activeToken ->
                        apiService.getBeatmapset(activeToken, linkRef.beatmapsetId)
                    }
                    dto.toMetadata()
                }
                else -> error("No beatmap identifier")
            }
        }.recoverCatching { err ->
            if (err is CancellationException) throw err
            if (apiPoller.shouldFallbackToMirror(err) || apiPoller.getValidBearerToken() == null) {
                val mirrorMeta = when {
                    linkRef.beatmapId != null -> apiPoller.getBeatmapFromMirrors(linkRef.beatmapId)?.toMetadata()
                    linkRef.beatmapsetId != null -> apiPoller.getBeatmapsetFromMirrors(linkRef.beatmapsetId)?.toMetadata()
                    else -> null
                }
                if (mirrorMeta != null) return@recoverCatching mirrorMeta
            }
            throw apiPoller.toFriendlyApiException(err)
        }

        return metadata.onSuccess { meta ->
            beatmapMemoryCache.put(key, meta)
            beatmapCacheDao.upsert(meta.toEntity(key))
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    override suspend fun setWidgetTheme(theme: WidgetTheme) = prefs.setWidgetTheme(theme)
    override suspend fun setWidgetTranslucent(translucent: Boolean) = prefs.setWidgetTranslucent(translucent)
    override suspend fun setGlobalWallpaper(config: WallpaperConfig) = prefs.setGlobalWallpaper(config)
    override suspend fun setChannelWallpaper(channelId: String, config: WallpaperConfig?) =
        prefs.setChannelWallpaper(normalizeChannelId(channelId), config)
    override suspend fun setHighlightKeywords(keywords: List<String>) = prefs.setHighlightKeywords(keywords)
    override suspend fun setKeepAliveService(enabled: Boolean) = prefs.setKeepAliveService(enabled)
    override suspend fun setAutoConnectOnBoot(enabled: Boolean) = prefs.setAutoConnectOnBoot(enabled)
    override suspend fun setNotificationPreferences(notifyDm: Boolean, notifyKeywords: Boolean) =
        prefs.setNotificationPreferences(notifyDm, notifyKeywords)
    override suspend fun ignoreUser(username: String) = prefs.ignoreUser(username)
    override suspend fun unignoreUser(username: String) = prefs.unignoreUser(username)
    override suspend fun setIgnoreDisplayMode(mode: String) = prefs.setIgnoreDisplayMode(mode)
    override suspend fun setAutoJoinConfig(enabled: Boolean, channels: List<String>) {
        val prev = prefs.settingsFlow.first()
        prefs.setAutoJoinConfig(enabled, channels)
        val updated = prefs.settingsFlow.first()
        val activeIds = resolveAutoJoinPublicChannels(updated).map { normalizeChannelId(it) }.toSet()
        val previousAutoIds = (prev.autoJoinChannels + AppSettings.DEFAULT_AUTO_JOIN_CHANNELS)
            .map { normalizeChannelId(it) }
            .distinct()
        previousAutoIds.forEach { id ->
            if (id !in activeIds) {
                channelDao.setChannelJoined(id, false)
            }
        }
        if (enabled) {
            ensureDefaultIrcChannels(updated)
        }
    }

    private suspend fun ensureBotChannelAndIrcOnline(currentSettings: AppSettings): Boolean {
        if (ircClient.status.value != ConnectionStatus.ONLINE) {
            setExplicitlyDisconnected(false)
            val creds = prefs.getIrcCredentials()
            if (creds != null && creds.password.isNotBlank()) {
                val nick = currentSettings.username.ifBlank { creds.username }
                ircClient.connectAndVerify(nick, creds.password, resolveAutoJoinPublicChannels(currentSettings))
            } else {
                reconnectIfAuthenticated()
            }
            if (ircClient.status.value != ConnectionStatus.ONLINE) {
                withTimeoutOrNull(8_000L) {
                    ircClient.status.first { it == ConnectionStatus.ONLINE }
                }
            }
        }

        val botChannelId = "dm:banchobot"
        val now = System.currentTimeMillis()
        if (channelDao.getChannelById(botChannelId) == null) {
            channelDao.upsertChannel(
                ChannelEntity(
                    id = botChannelId,
                    name = "BanchoBot",
                    description = "Bancho Referee Bot",
                    type = ChannelType.DIRECT.name,
                    avatarUrl = "https://a.ppy.sh/3",
                    lastMessageTimestamp = now,
                    isJoined = true
                )
            )
        }
        return ircClient.status.value == ConnectionStatus.ONLINE
    }

    override suspend fun createMultiplayerLobby(roomName: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanName = MpLobbyParser.sanitizeRoomName(roomName).ifBlank { "osu! Companion Lobby" }
        val currentSettings = prefs.settingsFlow.first()
        ensureBotChannelAndIrcOnline(currentSettings)

        val botChannelId = "dm:banchobot"
        val now = System.currentTimeMillis()
        val createdChannelDeferred = scope.async {
            withTimeoutOrNull(8_000L) { _createdMpChannelEvents.first() }
        }
        val sent = ircClient.sendPrivMsg("BanchoBot", "!mp make $cleanName", false)
        if (!sent) {
            createdChannelDeferred.cancel()
            return@withContext Result.failure(IllegalStateException("Not connected to Bancho IRC"))
        }

        messageDao.upsertMessage(
            MessageEntity(
                id = "local_${UUID.randomUUID()}",
                channelId = botChannelId,
                senderId = currentSettings.userId,
                senderName = currentSettings.username.ifBlank { "Me" },
                senderAvatarUrl = currentSettings.avatarUrl,
                content = "!mp make $cleanName",
                timestamp = now,
                isOutgoing = true,
                deliveryStatus = DeliveryStatus.SENT.name
            )
        )

        val createdId = createdChannelDeferred.await()
        Result.success(createdId ?: botChannelId)
    }

    override suspend fun createAndSetupTournamentLobby(
        config: MatchSetupRunConfig,
        onStepUpdate: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val roomName = config.resolvedRoomName
        val makeCommand = MpLobbyParser.buildMakeCommand(roomName)
            ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid room name"))
        val currentSettings = prefs.settingsFlow.first()

        onStepUpdate("Checking Bancho IRC connection...")
        if (!ensureBotChannelAndIrcOnline(currentSettings)) {
            return@withContext Result.failure(IllegalStateException("Not connected to Bancho IRC"))
        }

        val botChannelId = "dm:banchobot"
        val preExistingMpChannels = channelDao.observeJoinedChannels().first()
            .asSequence()
            .filter { it.id.startsWith("#mp_", ignoreCase = true) }
            .map { it.id.lowercase() }
            .toSet()

        val mpChannelId = coroutineScope {
            val selfJoinedMpFlow = ircClient.events
                .filter { ev ->
                    ev is IrcEvent.UserJoined &&
                        ev.channel.startsWith("#mp_", ignoreCase = true) &&
                        ev.channel.lowercase() !in preExistingMpChannels &&
                        isSelfSender(ev.nick, 0L, currentSettings)
                }
                .map { ev -> normalizeChannelId((ev as IrcEvent.UserJoined).channel) }

            val createdDeferred = async {
                withTimeoutOrNull(15_000L) {
                    merge(_createdMpChannelEvents, selfJoinedMpFlow).first()
                }
            }

            onStepUpdate("Creating lobby via !mp make...")
            val sent = ircClient.sendPrivMsg("BanchoBot", makeCommand, false)
            if (!sent) {
                createdDeferred.cancel()
                return@coroutineScope null
            }

            val now = System.currentTimeMillis()
            messageDao.upsertMessage(
                MessageEntity(
                    id = "local_${UUID.randomUUID()}",
                    channelId = botChannelId,
                    senderId = currentSettings.userId,
                    senderName = currentSettings.username.ifBlank { "Me" },
                    senderAvatarUrl = currentSettings.avatarUrl,
                    content = makeCommand,
                    timestamp = now,
                    isOutgoing = true,
                    deliveryStatus = DeliveryStatus.SENT.name
                )
            )
            createdDeferred.await()
        } ?: return@withContext Result.failure(
            IllegalStateException("Timed out waiting for BanchoBot to create lobby")
        )

        if (channelDao.getChannelById(mpChannelId) == null) {
            channelDao.upsertChannel(
                ChannelEntity(
                    id = mpChannelId,
                    name = mpChannelId,
                    description = roomName,
                    type = ChannelType.PUBLIC.name,
                    lastMessageTimestamp = System.currentTimeMillis(),
                    isJoined = true
                )
            )
        }
        ircClient.joinChannel(mpChannelId)

        updateMatchSeriesState(mpChannelId) { state ->
            state.copy(
                bestOf = config.bestOf.coerceIn(1, 21),
                redTeamName = config.redTeamName.trim().ifBlank { "Red" },
                blueTeamName = config.blueTeamName.trim().ifBlank { "Blue" }
            )
        }

        val stepDelayMs = 1_200L
        onStepUpdate("Waiting for lobby to initialize...")
        delay(stepDelayMs)

        val steps = mutableListOf<Pair<String, String>>()
        steps += "Setting mode & size (${config.roomSize} slots)..." to
            MpLobbyParser.buildSetCommand(config.teamMode, config.scoreMode, config.roomSize)
        if (config.password.isNotBlank()) {
            steps += "Applying lobby password..." to MpLobbyParser.buildPasswordCommand(config.password)
        }
        if (config.lockSlots) {
            steps += "Locking lobby slots..." to MpLobbyParser.buildLockCommand(true)
        }
        config.refs.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .forEach { ref ->
                MpLobbyParser.buildAddRefCommand(ref)?.let { cmd ->
                    steps += "Adding referee $ref..." to cmd
                }
            }
        config.invites.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .forEach { player ->
                MpLobbyParser.buildInviteCommand(player)?.let { cmd ->
                    steps += "Inviting $player..." to cmd
                }
            }
        steps += "Refreshing lobby state (!mp settings)..." to MpLobbyParser.buildSettingsCommand()

        for ((idx, step) in steps.withIndex()) {
            val (label, cmd) = step
            onStepUpdate(label)
            val stepResult = sendMpCommand(mpChannelId, cmd)
            if (stepResult.isFailure) {
                return@withContext Result.failure(
                    stepResult.exceptionOrNull() ?: IllegalStateException("Setup command failed: $cmd")
                )
            }
            if (idx < steps.lastIndex) {
                delay(stepDelayMs)
            }
        }

        Result.success(mpChannelId)
    }

    override suspend fun joinMultiplayerLobby(matchIdOrUrl: String): Result<ChatChannel> = withContext(Dispatchers.IO) {
        val matchId = MpLobbyParser.extractMatchId(matchIdOrUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Enter a match ID (e.g. 115241345) or osu.ppy.sh/mp/... link")
            )
        val mpName = "#mp_$matchId"
        val currentSettings = prefs.settingsFlow.first()
        if (ircClient.status.value != ConnectionStatus.ONLINE) {
            setExplicitlyDisconnected(false)
            val creds = prefs.getIrcCredentials()
            if (creds != null && creds.password.isNotBlank()) {
                val nick = currentSettings.username.ifBlank { creds.username }
                ircClient.connectAndVerify(
                    nick,
                    creds.password,
                    (resolveAutoJoinPublicChannels(currentSettings) + mpName).distinctBy { it.lowercase() }
                )
            }
        }

        val entity = channelDao.getChannelById(mpName)?.copy(isJoined = true) ?: ChannelEntity(
            id = mpName,
            name = mpName,
            description = "Multiplayer Lobby #$matchId",
            type = ChannelType.PUBLIC.name,
            lastMessageTimestamp = System.currentTimeMillis(),
            isJoined = true
        )
        channelDao.upsertChannel(entity)
        ircClient.joinChannel(mpName)
        scope.launch {
            delay(500L)
            sendMessage(mpName, "!mp settings")
        }
        Result.success(entity.toDomain(null))
    }

    override suspend fun sendMpCommand(channelId: String, mpCommand: String): Result<Unit> {
        val normId = normalizeChannelId(channelId)
        val sanitized = MpLobbyParser.sanitizeIrcArg(mpCommand)
        if (sanitized.isEmpty()) return Result.success(Unit)
        val cmd = if (sanitized.equals("!mp", ignoreCase = true) || sanitized.startsWith("!mp ", ignoreCase = true)) {
            sanitized
        } else {
            "!mp $sanitized"
        }
        return sendMessage(normId, cmd)
    }

    override suspend fun fetchMatchHistory(matchId: Long): Result<MpMatchHistory> = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val res = apiPoller.withBearerRetry("Sign in with osu! OAuth to load match history") {
                val response = apiService.getMatch(matchId = matchId)
                if (response.code() == 401 || response.code() == 429) {
                    throw HttpException(response)
                }
                if (!response.isSuccessful) {
                    val errText = when (response.code()) {
                        404 -> "Match #$matchId was not found or is private."
                        403 -> "Match #$matchId is private."
                        else -> "Could not load match history (HTTP ${response.code()})."
                    }
                    throw IllegalStateException(errText)
                }
                response.body() ?: throw IllegalStateException("Empty match response.")
            }
            val userMap = res.users.orEmpty().associateBy { it.id }
            val games = res.events.orEmpty().mapNotNull { evt ->
                val game = evt.game ?: return@mapNotNull null
                val mapLabel = game.beatmap?.let { bm ->
                    val artistTitle = listOfNotNull(bm.beatmapset?.artist, bm.beatmapset?.title)
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                    val base = artistTitle.ifBlank { "Beatmap #${bm.id}" }
                    val diffName = bm.version?.trim().orEmpty()
                    if (diffName.isNotBlank()) "$base [$diffName]" else base
                } ?: "Beatmap #${game.beatmapId ?: game.id}"

                var redTotal = 0L
                var blueTotal = 0L
                val mappedScores = game.scores.orEmpty().map { sc ->
                    val team = sc.match?.team
                    if (team.equals("red", ignoreCase = true)) redTotal += sc.score
                    if (team.equals("blue", ignoreCase = true)) blueTotal += sc.score
                    val accPct = if (sc.accuracy <= 1.0) sc.accuracy * 100.0 else sc.accuracy
                    MpGameScoreSummary(
                        username = userMap[sc.userId]?.username?.takeIf { it.isNotBlank() } ?: "User ${sc.userId}",
                        userId = sc.userId,
                        score = sc.score,
                        accuracy = accPct,
                        maxCombo = sc.maxCombo,
                        mods = sc.mods.orEmpty(),
                        team = team,
                        passed = sc.passed,
                        rank = sc.rank.orEmpty()
                    )
                }.sortedByDescending { it.score }

                MpCompletedGame(
                    gameId = game.id,
                    beatmapTitle = mapLabel,
                    beatmapId = game.beatmapId ?: game.beatmap?.id,
                    mode = game.mode ?: "osu",
                    mods = game.mods.orEmpty(),
                    redTeamTotal = redTotal,
                    blueTeamTotal = blueTotal,
                    scores = mappedScores
                )
            }
            MpMatchHistory(
                matchId = res.match?.id ?: matchId,
                roomName = res.match?.name?.takeIf { it.isNotBlank() } ?: "Match #$matchId",
                games = games
            )
        }
    }

    override suspend fun fetchFullMatchAnalysis(matchId: Long): Result<MatchAnalysisReport> =
        withContext(Dispatchers.IO) {
            if (matchId <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid match ID"))
            }
            val currentSettings = prefs.settingsFlow.first()
            if (currentSettings.authMode != AuthMode.OAUTH_V2 || apiPoller.getValidBearerToken() == null) {
                return@withContext Result.failure(
                    IllegalStateException("Sign in with osu! OAuth to analyze matches.")
                )
            }
            runCatching {
                var matchHeader: OsuMatchHeaderDto? = null
                var firstEventId: Long? = null
                var beforeCursor: Long? = null
                val mergedUsers = LinkedHashMap<Long, OsuUserDto>()
                val mergedEvents = LinkedHashMap<Long, OsuMatchEventDto>()

                for (page in 0 until 6) {
                    val body = apiPoller.withBearerRetry("Sign in with osu! OAuth to analyze match") {
                        val response = apiService.getMatch(
                            matchId = matchId,
                            before = beforeCursor,
                            limit = 100
                        )
                        if (response.code() == 401 || response.code() == 429) {
                            throw HttpException(response)
                        }
                        if (!response.isSuccessful) {
                            val errMsg = when (response.code()) {
                                404 -> "Match #$matchId was not found or is private."
                                403 -> "Match #$matchId is private."
                                else -> "Could not load match #$matchId (HTTP ${response.code()})."
                            }
                            throw IllegalStateException(errMsg)
                        }
                        response.body() ?: throw IllegalStateException("Empty match response.")
                    }

                    if (page == 0) {
                        matchHeader = body.match
                        firstEventId = body.firstEventId
                    }
                    body.users.orEmpty().forEach { user ->
                        if (user.id > 0L) {
                            mergedUsers[user.id] = user
                        }
                    }
                    val pageEvents = body.events.orEmpty()
                    pageEvents.forEach { evt ->
                        mergedEvents[evt.id] = evt
                    }
                    val oldestId = pageEvents.minOfOrNull { it.id } ?: break
                    val floorId = firstEventId ?: body.firstEventId
                    if (floorId != null && oldestId <= floorId) break
                    if (beforeCursor != null && oldestId >= beforeCursor) break
                    if (pageEvents.size < 100 && floorId == null) break
                    beforeCursor = oldestId
                }

                val sortedEvents = mergedEvents.values.sortedBy { it.id }
                var gameNumber = 0
                val games = sortedEvents.mapNotNull { evt ->
                    val game = evt.game ?: return@mapNotNull null
                    val rawScores = game.scores.orEmpty()
                    if (rawScores.isEmpty()) return@mapNotNull null
                    gameNumber++
                    val bm = game.beatmap
                    val resolvedBeatmapId = game.beatmapId ?: bm?.id
                    val resolvedSetId = bm?.beatmapsetId ?: bm?.beatmapset?.id
                    val coverUrl = bm?.beatmapset?.covers?.card
                        ?: bm?.beatmapset?.covers?.cover
                        ?: bm?.beatmapset?.covers?.slimcover
                        ?: resolvedSetId?.takeIf { it > 0L }?.let {
                            "https://assets.ppy.sh/beatmaps/$it/covers/card.jpg"
                        }
                        ?: ""
                    val previewAudioUrl = bm?.beatmapset?.previewUrl
                        ?: resolvedSetId?.takeIf { it > 0L }?.let { "https://b.ppy.sh/preview/$it.mp3" }
                        ?: ""

                    val playerScores = rawScores.map { sc ->
                        val user = mergedUsers[sc.userId]
                        val team = when {
                            sc.match?.team.equals("red", ignoreCase = true) -> MpTeam.RED
                            sc.match?.team.equals("blue", ignoreCase = true) -> MpTeam.BLUE
                            else -> MpTeam.NONE
                        }
                        val accPct = if (sc.accuracy <= 1.0) sc.accuracy * 100.0 else sc.accuracy
                        val isPassed = sc.passed && (sc.match?.pass ?: true)
                        MatchAnalysisPlayerScore(
                            userId = sc.userId,
                            username = user?.username?.takeIf { it.isNotBlank() } ?: "User #${sc.userId}",
                            avatarUrl = user?.avatarUrl ?: "https://a.ppy.sh/${sc.userId}",
                            countryCode = user?.country?.code ?: user?.countryCode ?: "XX",
                            slot = sc.match?.slot,
                            team = team,
                            score = sc.score,
                            accuracy = accPct,
                            maxCombo = sc.maxCombo,
                            mods = sc.mods.orEmpty(),
                            passed = isPassed,
                            perfect = (sc.perfect ?: 0) == 1,
                            rank = sc.rank
                        )
                    }

                    MatchAnalysisGame(
                        gameId = game.id,
                        gameIndex = gameNumber,
                        beatmapId = resolvedBeatmapId,
                        beatmapsetId = resolvedSetId,
                        beatmapTitle = bm?.beatmapset?.title?.takeIf { it.isNotBlank() }
                            ?: resolvedBeatmapId?.let { "Beatmap #$it" }
                            ?: "Game #$gameNumber",
                        beatmapArtist = bm?.beatmapset?.artist.orEmpty(),
                        beatmapVersion = bm?.version.orEmpty(),
                        beatmapCreator = bm?.beatmapset?.creator.orEmpty(),
                        starRating = bm?.difficultyRating ?: 0.0,
                        bpm = bm?.bpm ?: bm?.beatmapset?.bpm ?: 0.0,
                        totalLengthSeconds = bm?.totalLength ?: 0,
                        ar = bm?.ar ?: 0.0,
                        cs = bm?.cs ?: 0.0,
                        od = bm?.od ?: 0.0,
                        hp = bm?.hp ?: 0.0,
                        coverUrl = coverUrl,
                        previewAudioUrl = previewAudioUrl,
                        mode = game.mode ?: bm?.mode ?: "osu",
                        scoringType = game.scoringType ?: "score",
                        teamType = game.teamType ?: "head-to-head",
                        mods = game.mods.orEmpty(),
                        startTime = game.startTime,
                        endTime = game.endTime,
                        scores = playerScores
                    )
                }

                MatchAnalysisReport(
                    matchId = matchHeader?.id?.takeIf { it > 0L } ?: matchId,
                    roomName = matchHeader?.name?.takeIf { it.isNotBlank() } ?: "Match #$matchId",
                    startTime = matchHeader?.startTime,
                    endTime = matchHeader?.endTime,
                    isLive = matchHeader?.endTime.isNullOrBlank(),
                    games = games
                )
            }
        }

    override suspend fun saveMappool(
        poolId: Long?,
        name: String,
        slots: List<MappoolSlot>
    ): Result<Long> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a mappool name"))
        }
        val validSlots = slots.mapIndexedNotNull { idx, slot ->
            val tag = slot.slotTag.trim()
            if (tag.isEmpty() || slot.beatmapId <= 0L) return@mapIndexedNotNull null
            MappoolSlotEntity(
                poolId = poolId ?: 0L,
                slotTag = tag,
                beatmapId = slot.beatmapId,
                mods = MpLobbyParser.sanitizeIrcArg(slot.mods).ifEmpty { "NF" },
                orderIndex = idx
            )
        }
        if (validSlots.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Add at least one valid mappool slot"))
        }
        runCatching {
            mappoolDao.savePoolWithSlots(poolId, cleanName, validSlots)
        }
    }

    override suspend fun deleteMappool(poolId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mappoolDao.deletePoolById(poolId)
        }
    }

    override suspend fun applyMappoolSlot(channelId: String, slot: MappoolSlot): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (slot.beatmapId <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid beatmap ID"))
            }
            val mapCmd = MpLobbyParser.buildMapCommand(slot.beatmapId)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid beatmap ID"))
            val mapRes = sendMpCommand(channelId, mapCmd)
            if (mapRes.isFailure) return@withContext mapRes

            delay(350L)

            val sanitizedMods = MpLobbyParser.sanitizeIrcArg(slot.mods).ifEmpty { "NF" }
            val modsCmd = "!mp mods $sanitizedMods"
            sendMpCommand(channelId, modsCmd)
        }

    override fun observeMatchSeriesState(channelId: String): Flow<MatchSeriesState> {
        val normId = normalizeChannelId(channelId)
        return matchSeriesStates
            .map { states -> states[normId] ?: MatchSeriesState(channelId = normId) }
            .distinctUntilChanged()
    }

    override fun updateMatchSeriesState(
        channelId: String,
        transform: (MatchSeriesState) -> MatchSeriesState
    ) {
        val normId = normalizeChannelId(channelId)
        matchSeriesStates.update { current ->
            val prev = current[normId] ?: MatchSeriesState(channelId = normId)
            val updated = transform(prev).copy(channelId = normId)
            if (updated == prev) current else current + (normId to updated)
        }
    }

    override suspend fun saveRefereeMacros(macros: List<RefereeMacro>) {
        prefs.saveRefereeMacros(macros)
    }

    override suspend fun resetRefereeMacros() {
        prefs.resetRefereeMacros()
    }

    override suspend fun saveMatchSetupTemplates(templates: List<MatchSetupTemplate>) {
        prefs.saveMatchSetupTemplates(templates)
    }

    private fun handleMpTimerFromBotMessage(
        channelId: String,
        roomNameHint: String?,
        senderName: String,
        content: String
    ) {
        if (!channelId.startsWith("#mp_", ignoreCase = true)) return
        if (!senderName.equals("BanchoBot", ignoreCase = true)) return
        val roomName = roomNameHint
            ?.takeIf { it.isNotBlank() && !it.equals("Bancho IRC channel", ignoreCase = true) }
            ?: channelId
        content.lines().forEach { line ->
            when (val directive = MpLobbyParser.parseBanchoBotTimerDirective(line)) {
                is MpLobbyParser.MpTimerDirective.Start -> mpTimerManager.startOrSyncTimer(
                    channelId = channelId,
                    roomName = roomName,
                    label = directive.label,
                    totalSeconds = directive.totalSeconds,
                    isMatchStart = directive.isMatchStart
                )
                MpLobbyParser.MpTimerDirective.Clear -> mpTimerManager.clearTimer(channelId)
                null -> Unit
            }
        }
    }

    override suspend fun toggleTrackFriendPp(
        username: String,
        userId: Long,
        avatarUrl: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val norm = BeatmapUrlParser.normalizeUsername(username)
        val existing = trackedFriendDao.getTrackedFriend(norm)
        val alreadyPpTracked = existing != null &&
            (existing.lastCheckedAt > 0L || existing.topScoreIdsCsv.isNotEmpty())
        if (alreadyPpTracked) {
            if (existing!!.notifyOnline) {
                trackedFriendDao.upsertTrackedFriend(
                    existing.copy(topScoreIdsCsv = "", lastCheckedAt = 0L)
                )
            } else {
                trackedFriendDao.deleteTrackedFriend(norm)
            }
            return@withContext Result.success(false)
        }

        val resolvedProfile = if (userId <= 0L && (existing == null || existing.userId <= 0L)) {
            getPlayerProfile(username).getOrNull()
        } else {
            null
        }
        val uid = when {
            userId > 0L -> userId
            existing != null && existing.userId > 0L -> existing.userId
            else -> resolvedProfile?.userId ?: 0L
        }
        if (uid <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Could not resolve user ID for $username"))
        }
        val finalName = resolvedProfile?.username ?: existing?.username?.takeIf { it.isNotBlank() } ?: username.trim()
        val finalAvatar = avatarUrl.ifBlank {
            existing?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: resolvedProfile?.avatarUrl
                ?: "https://a.ppy.sh/$uid"
        }

        val finalNorm = BeatmapUrlParser.normalizeUsername(finalName)
        val apiFriend = _apiFriends.value.firstOrNull {
            it.id == uid || it.username.equals(finalName, ignoreCase = true)
        }
        val cachedProfile = playerProfileCacheDao.getByUsername(finalNorm)
        val mode = TrackedPlayerAlertConfig.normalizeMode(
            resolvedProfile?.mode ?: apiFriend?.playmode
        )
        val settings = prefs.settingsFlow.first()
        val existingConfig = prefs.getTrackedAlertConfigs()[
            TrackedPlayerAlertConfig.configKey(uid, mode)
        ] ?: TrackedPlayerAlertConfig()
        val effectiveLimit = TrackedPlayerAlertEngine.effectiveTopPlayLimit(
            existingConfig,
            settings.defaultTrackedTopPlayLimit
        )

        val token = apiPoller.getValidBearerToken()
        var seedPp: Double? = resolvedProfile?.pp ?: apiFriend?.statistics?.pp ?: cachedProfile?.pp
        var seedRank: Int? = resolvedProfile?.globalRank ?: apiFriend?.statistics?.globalRank ?: cachedProfile?.globalRank
        if ((seedPp == null || seedRank == null) && token != null && !apiPoller.isRateLimited()) {
            val fetchedUser = runCatching {
                apiPoller.withOptionalBearerRetry(token) { bearer ->
                    apiService.getUserWithMode(bearer, uid.toString(), mode, keyType = "id")
                }
            }.getOrNull()
            if (seedPp == null) seedPp = fetchedUser?.statistics?.pp
            if (seedRank == null) seedRank = fetchedUser?.statistics?.globalRank
        }

        val topScores = if (existingConfig.isTopPlaysEnabled() && token != null && !apiPoller.isRateLimited()) {
            runCatching {
                apiPoller.withOptionalBearerRetry(token) { bearer ->
                    apiService.getUserScores(bearer, uid, "best", includeFails = 0, mode = mode, limit = effectiveLimit)
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (topScores.isNotEmpty()) {
            persistFriendScoreEvents(finalName, uid, finalAvatar, topScores)
        }
        val nowMs = System.currentTimeMillis()
        val baselineIdsCsv = topScores.joinToString(",") { it.id.toString() }
        val seededSeenIds = topScores
            .mapNotNull { it.id.takeIf { id -> id > 0L } }
            .take(TrackedPlayerAlertConfig.MAX_SEEN_SCORE_IDS)
            .toSet()

        prefs.updateTrackedPlayerAlertConfig(uid, mode) { current ->
            current.copy(
                baselinePp = if (current.isPpEnabled()) {
                    seedPp?.takeIf { it.isFinite() && it >= 0.0 } ?: current.baselinePp
                } else {
                    current.baselinePp
                },
                baselineRank = if (current.isRankEnabled()) {
                    seedRank?.takeIf { it > 0 } ?: current.baselineRank
                } else {
                    current.baselineRank
                },
                lastSyncedTopLimit = if (seededSeenIds.isNotEmpty()) effectiveLimit else current.lastSyncedTopLimit,
                seenBestScoreIds = if (seededSeenIds.isNotEmpty()) seededSeenIds else current.seenBestScoreIds,
                lastSyncEpochMs = nowMs
            )
        }

        trackedFriendDao.upsertTrackedFriend(
            TrackedFriendEntity(
                normalizedUsername = finalNorm,
                userId = uid,
                username = finalName,
                avatarUrl = finalAvatar,
                topScoreIdsCsv = baselineIdsCsv,
                lastCheckedAt = nowMs,
                addedAt = existing?.addedAt ?: nowMs,
                notifyOnline = existing?.notifyOnline == true
            )
        )
        Result.success(true)
    }

    override suspend fun toggleFriendOnlineAlert(
        username: String,
        userId: Long,
        avatarUrl: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanName = username.trim()
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty username"))
        }
        val norm = BeatmapUrlParser.normalizeUsername(cleanName)
        val existing = trackedFriendDao.getTrackedFriend(norm)
        val nextEnabled = !(existing?.notifyOnline == true)
        val resolvedUid = userId.takeIf { it > 0L } ?: existing?.userId ?: 0L
        val resolvedAvatar = avatarUrl.ifBlank {
            existing?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: resolvedUid.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" }.orEmpty()
        }
        trackedFriendDao.setNotifyOnline(
            normalizedUsername = norm,
            enabled = nextEnabled,
            userId = resolvedUid,
            username = existing?.username?.takeIf { it.isNotBlank() } ?: cleanName,
            avatarUrl = resolvedAvatar
        )
        Result.success(nextEnabled)
    }

    override suspend fun checkTrackedFriendsTopPlays(forceNotifyLatest: Boolean): Result<Int> =
        withContext(Dispatchers.IO) {
            trackedAlertSyncMutex.withLock {
                if (apiPoller.getValidBearerToken() == null) {
                    return@withLock if (forceNotifyLatest) {
                        Result.failure(IllegalStateException("Sign in with osu! OAuth to check friend scores"))
                    } else {
                        Result.success(0)
                    }
                }

                val settings = prefs.settingsFlow.first()
                val tracked = trackedFriendDao.getAllTrackedFriends()
                    .filter { it.lastCheckedAt > 0L || it.topScoreIdsCsv.isNotEmpty() }
                if (tracked.isEmpty()) return@withLock Result.success(0)

                val configsMap = prefs.getTrackedAlertConfigs()
                val apiFriendsById = _apiFriends.value.associateBy { it.id }
                var alertsSent = 0
                var requestCount = 0
                var abortAll = false

                for (friend in tracked) {
                    if (abortAll || apiPoller.isRateLimited()) break
                    if (friend.userId <= 0L) continue

                    val apiFriend = apiFriendsById[friend.userId]
                    val activeModes = TrackedPlayerAlertEngine.resolveActiveTrackedModes(
                        userId = friend.userId,
                        defaultPlaymode = apiFriend?.playmode,
                        configsMap = configsMap
                    )
                    if (activeModes.isEmpty()) continue

                    for ((mode, originalConfig) in activeModes) {
                        if (apiPoller.isRateLimited()) {
                            abortAll = true
                            break
                        }
                        if (requestCount > 0) delay(350L)
                        requestCount++

                        var workingConfig = originalConfig
                        if (workingConfig.isTopPlaysEnabled() &&
                            workingConfig.seenBestScoreIds.isEmpty() &&
                            friend.topScoreIdsCsv.isNotBlank() &&
                            mode == TrackedPlayerAlertConfig.normalizeMode(apiFriend?.playmode)
                        ) {
                            val legacyIds = friend.topScoreIdsCsv
                                .split(",")
                                .mapNotNull { it.trim().toLongOrNull()?.takeIf { id -> id > 0L } }
                                .toSet()
                            if (legacyIds.isNotEmpty()) {
                                workingConfig = workingConfig.copy(
                                    seenBestScoreIds = legacyIds,
                                    lastSyncedTopLimit = TrackedPlayerAlertConfig.clampTopPlayLimit(legacyIds.size),
                                    lastSyncEpochMs = friend.lastCheckedAt
                                )
                            }
                        }

                        val needsProfileMetrics = workingConfig.isPpEnabled() || workingConfig.isRankEnabled()
                        val fetchedUser = if (needsProfileMetrics) {
                            val userResult = runCatching {
                                apiPoller.withBearerRetry("Sign in with osu! OAuth to check tracked players") { bearer ->
                                    apiService.getUserWithMode(
                                        bearerToken = bearer,
                                        userIdOrName = friend.userId.toString(),
                                        mode = mode,
                                        keyType = "id"
                                    )
                                }
                            }
                            val userErr = userResult.exceptionOrNull()
                            if (userErr != null) {
                                if (userErr is CancellationException) throw userErr
                                if (userErr is OsuApiException && (userErr.httpCode == 401 || userErr.httpCode == 429)) {
                                    abortAll = true
                                    break
                                }
                            }
                            userResult.getOrNull()
                        } else {
                            null
                        }

                        val (currentPp, currentRank) = TrackedPlayerAlertEngine.resolveModeProfileMetrics(
                            requestedMode = mode,
                            fetchedPp = fetchedUser?.statistics?.pp,
                            fetchedRank = fetchedUser?.statistics?.globalRank,
                            defaultPlaymode = apiFriend?.playmode,
                            fallbackPp = apiFriend?.statistics?.pp,
                            fallbackRank = apiFriend?.statistics?.globalRank
                        )
                        val canNotify = settings.trackedAlertsEnabled || forceNotifyLatest
                        val milestoneEval = if (needsProfileMetrics) {
                            TrackedPlayerAlertEngine.evaluateCustomMilestones(
                                config = workingConfig,
                                currentPp = currentPp,
                                currentRank = currentRank,
                                consumeHits = canNotify
                            )
                        } else {
                            MilestoneEvaluationResult(updatedConfig = workingConfig.sanitized(), hits = emptyList())
                        }

                        val alias = userAliasDao.getAlias(friend.normalizedUsername)?.customNickname?.takeIf { it.isNotBlank() }
                        val resolvedUsername = fetchedUser?.username?.takeIf { it.isNotBlank() } ?: friend.username
                        val displayName = if (!alias.isNullOrBlank()) "$alias ($resolvedUsername)" else resolvedUsername

                        if (canNotify && milestoneEval.hits.isNotEmpty()) {
                            val (milestoneTitle, milestoneBody) = TrackedPlayerAlertEngine.formatMilestoneNotification(
                                username = displayName,
                                mode = mode,
                                hits = milestoneEval.hits,
                                currentPp = currentPp,
                                currentRank = currentRank
                            )
                            val alertKey = milestoneEval.hits.joinToString("_") { it.targetKey }
                            notificationManager.showTrackedPlayerAlertNotification(
                                userId = friend.userId,
                                alertKey = "milestone_${mode}_$alertKey",
                                title = milestoneTitle,
                                body = milestoneBody,
                                avatarUrl = friend.avatarUrl
                            )
                            alertsSent++
                            prefs.saveTrackedPlayerAlertConfig(friend.userId, mode, milestoneEval.updatedConfig)
                        }

                        if (milestoneEval.updatedConfig.isTopPlaysEnabled()) {
                            val effectiveLimit = TrackedPlayerAlertEngine.effectiveTopPlayLimit(
                                milestoneEval.updatedConfig,
                                settings.defaultTrackedTopPlayLimit
                            )
                            val scoreResult = runCatching {
                                apiPoller.withBearerRetry("Sign in with osu! OAuth to check friend scores") { bearer ->
                                    apiService.getUserScores(
                                        bearerToken = bearer,
                                        userId = friend.userId,
                                        type = "best",
                                        includeFails = 0,
                                        mode = mode,
                                        limit = effectiveLimit
                                    )
                                }
                            }
                            val scoreErr = scoreResult.exceptionOrNull()
                            if (scoreErr != null) {
                                if (scoreErr is CancellationException) throw scoreErr
                                if (scoreErr is OsuApiException && (scoreErr.httpCode == 401 || scoreErr.httpCode == 429)) {
                                    if (milestoneEval.updatedConfig != originalConfig) {
                                        prefs.saveTrackedPlayerAlertConfig(friend.userId, mode, milestoneEval.updatedConfig)
                                    }
                                    abortAll = true
                                    break
                                }
                            }

                            if (scoreResult.isSuccess) {
                                val topScores = scoreResult.getOrNull().orEmpty()
                                if (topScores.isNotEmpty()) {
                                    persistFriendScoreEvents(resolvedUsername, friend.userId, friend.avatarUrl, topScores)
                                }
                                val candidates: List<TopPlayCandidate> = topScores.map { dto ->
                                    dto.toDomain(resolvedUsername).toTopPlayCandidate()
                                }
                                val nowMs = System.currentTimeMillis()
                                val topPlaysEval = TrackedPlayerAlertEngine.evaluateNewTopPlays(
                                    config = milestoneEval.updatedConfig,
                                    defaultLimit = settings.defaultTrackedTopPlayLimit,
                                    bestScores = candidates,
                                    nowEpochMs = nowMs
                                )

                                prefs.saveTrackedPlayerAlertConfig(friend.userId, mode, topPlaysEval.updatedConfig)

                                val playsToNotify = if (topPlaysEval.newPlays.isEmpty() && forceNotifyLatest && candidates.isNotEmpty()) {
                                    listOf(NewTopPlayMatch(rankPosition = 1, candidate = candidates.first()))
                                } else {
                                    topPlaysEval.newPlays
                                }
                                if (canNotify && playsToNotify.isNotEmpty()) {
                                    val (playTitle, playBody) = TrackedPlayerAlertEngine.formatTopPlaysNotification(
                                        username = displayName,
                                        newPlays = playsToNotify
                                    )
                                    val alertKey = playsToNotify.joinToString("_") { it.candidate.scoreId.toString() }
                                    notificationManager.showTrackedPlayerAlertNotification(
                                        userId = friend.userId,
                                        alertKey = "top_${mode}_$alertKey",
                                        title = playTitle,
                                        body = playBody,
                                        avatarUrl = friend.avatarUrl
                                    )
                                    alertsSent++
                                }

                                trackedFriendDao.upsertTrackedFriend(
                                    friend.copy(
                                        username = resolvedUsername,
                                        topScoreIdsCsv = if (topScores.isNotEmpty() && mode == TrackedPlayerAlertConfig.normalizeMode(apiFriend?.playmode)) {
                                            topScores.joinToString(",") { it.id.toString() }
                                        } else {
                                            friend.topScoreIdsCsv
                                        },
                                        lastCheckedAt = nowMs
                                    )
                                )
                            } else if (milestoneEval.updatedConfig != originalConfig) {
                                prefs.saveTrackedPlayerAlertConfig(friend.userId, mode, milestoneEval.updatedConfig)
                            }
                        } else {
                            if (milestoneEval.updatedConfig != originalConfig) {
                                prefs.saveTrackedPlayerAlertConfig(friend.userId, mode, milestoneEval.updatedConfig)
                            }
                            trackedFriendDao.upsertTrackedFriend(
                                friend.copy(
                                    username = resolvedUsername,
                                    lastCheckedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                Result.success(alertsSent)
            }
        }

    override suspend fun setTrackedAlertsEnabled(enabled: Boolean) {
        prefs.setTrackedAlertsEnabled(enabled)
    }

    override suspend fun setTrackedAlertsIntervalMinutes(minutes: Int) {
        prefs.setTrackedAlertsIntervalMinutes(minutes)
    }

    override suspend fun setDefaultTrackedTopPlayLimit(limit: Int) {
        prefs.setDefaultTrackedTopPlayLimit(limit)
    }

    override suspend fun saveTrackedPlayerAlertConfig(
        userId: Long,
        mode: String,
        config: TrackedPlayerAlertConfig,
        username: String,
        avatarUrl: String
    ) {
        withContext(Dispatchers.IO) {
            val normMode = TrackedPlayerAlertConfig.normalizeMode(mode)
            prefs.saveTrackedPlayerAlertConfig(userId, normMode, config)
            val cleanName = username.trim()
            val normName = cleanName.takeIf { it.isNotEmpty() }?.let(BeatmapUrlParser::normalizeUsername)
            val existing = normName?.let { trackedFriendDao.getTrackedFriend(it) }
                ?: trackedFriendDao.getAllTrackedFriends().firstOrNull { it.userId == userId }
            val alreadyPpTracked = existing != null &&
                (existing.lastCheckedAt > 0L || existing.topScoreIdsCsv.isNotEmpty())
            if (!alreadyPpTracked && (cleanName.isNotEmpty() || existing != null)) {
                toggleTrackFriendPp(
                    username = cleanName.ifBlank { existing?.username.orEmpty() },
                    userId = userId,
                    avatarUrl = avatarUrl.ifBlank { existing?.avatarUrl.orEmpty() }
                )
            } else if (alreadyPpTracked && config.isTopPlaysEnabled() && config.seenBestScoreIds.isEmpty()) {
                val token = apiPoller.getValidBearerToken()
                if (token != null && !apiPoller.isRateLimited()) {
                    val effectiveLimit = TrackedPlayerAlertEngine.effectiveTopPlayLimit(
                        config,
                        prefs.settingsFlow.first().defaultTrackedTopPlayLimit
                    )
                    val topScores = runCatching {
                        apiPoller.withOptionalBearerRetry(token) { bearer ->
                            apiService.getUserScores(
                                bearerToken = bearer,
                                userId = userId,
                                type = "best",
                                includeFails = 0,
                                mode = normMode,
                                limit = effectiveLimit
                            )
                        }
                    }.getOrDefault(emptyList())
                    val seededIds = topScores
                        .mapNotNull { it.id.takeIf { id -> id > 0L } }
                        .take(TrackedPlayerAlertConfig.MAX_SEEN_SCORE_IDS)
                        .toSet()
                    if (seededIds.isNotEmpty()) {
                        val nowMs = System.currentTimeMillis()
                        prefs.updateTrackedPlayerAlertConfig(userId, normMode) { current ->
                            current.copy(
                                lastSyncedTopLimit = effectiveLimit,
                                seenBestScoreIds = seededIds,
                                lastSyncEpochMs = nowMs
                            )
                        }
                        if (existing != null) {
                            trackedFriendDao.upsertTrackedFriend(
                                existing.copy(
                                    topScoreIdsCsv = topScores.joinToString(",") { it.id.toString() },
                                    lastCheckedAt = nowMs
                                )
                            )
                        }
                    }
                }
            } else if (alreadyPpTracked && !config.isTopPlaysEnabled() && existing != null && existing.topScoreIdsCsv.isNotEmpty()) {
                trackedFriendDao.upsertTrackedFriend(
                    existing.copy(
                        topScoreIdsCsv = "",
                        lastCheckedAt = existing.lastCheckedAt.coerceAtLeast(System.currentTimeMillis())
                    )
                )
            }
        }
    }

    override suspend fun refreshFriendScoreFeed(): Result<Int> = withContext(Dispatchers.IO) {
        if (apiPoller.getValidBearerToken() == null) {
            return@withContext Result.failure(IllegalStateException("Sign in with osu! OAuth to refresh friend score feed"))
        }
        val ppTracked = trackedFriendDao.getAllTrackedFriends()
            .filter { it.lastCheckedAt > 0L || it.topScoreIdsCsv.isNotEmpty() }
        var totalUpserted = 0

        if (ppTracked.isNotEmpty()) {
            checkTrackedFriendsTopPlays(forceNotifyLatest = false)
            val configsMap = prefs.getTrackedAlertConfigs()
            val apiFriendsById = _apiFriends.value.associateBy { it.id }
            for (friend in ppTracked) {
                if (apiPoller.isRateLimited()) break
                val apiFriend = apiFriendsById[friend.userId]
                val activeModes = TrackedPlayerAlertEngine.resolveActiveTrackedModes(
                    userId = friend.userId,
                    defaultPlaymode = apiFriend?.playmode,
                    configsMap = configsMap
                )
                if (activeModes.none { (_, cfg) -> cfg.isTopPlaysEnabled() }) continue
                val recentScores = runCatching {
                    apiPoller.withBearerRetry("Sign in with osu! OAuth to load scores") { bearer ->
                        apiService.getUserScores(bearer, friend.userId, "recent", includeFails = 0, limit = 5)
                    }
                }.getOrNull().orEmpty().filter { (it.pp ?: 0.0) > 0.0 }
                if (recentScores.isNotEmpty()) {
                    totalUpserted += persistFriendScoreEvents(friend.username, friend.userId, friend.avatarUrl, recentScores)
                }
            }
        } else {
            val sampleFriends = _apiFriends.value
                .filter { it.id > 0L && !it.username.isNullOrBlank() }
                .sortedByDescending { it.isOnline == true }
                .take(5)
            for (dto in sampleFriends) {
                if (apiPoller.isRateLimited()) break
                val uname = dto.username?.trim().orEmpty()
                val avatar = dto.avatarUrl ?: "https://a.ppy.sh/${dto.id}"
                val scores = runCatching {
                    apiPoller.withBearerRetry("Sign in with osu! OAuth to load scores") { bearer ->
                        apiService.getUserScores(bearer, dto.id, "best", includeFails = 0, limit = 5)
                    }
                }.getOrNull().orEmpty()
                if (scores.isNotEmpty()) {
                    totalUpserted += persistFriendScoreEvents(uname, dto.id, avatar, scores)
                }
            }
        }
        Result.success(totalUpserted)
    }

    private suspend fun persistFriendScoreEvents(
        username: String,
        userId: Long,
        avatarUrl: String,
        scores: List<OsuScoreDto>
    ): Int {
        val fallbackNow = System.currentTimeMillis()
        val entities = scores.mapNotNull { dto ->
            if (dto.id <= 0L) return@mapNotNull null
            val domain = dto.toDomain(username)
            val recordedAt = dto.createdAt?.takeIf { it.isNotBlank() }?.let { rawIso ->
                runCatching { Instant.parse(rawIso.trim()).toEpochMilli() }.getOrNull()
            } ?: fallbackNow
            FriendScoreEventEntity(
                scoreId = dto.id,
                userId = userId.takeIf { it > 0L } ?: domain.userId,
                username = domain.username.ifBlank { username },
                avatarUrl = avatarUrl.ifBlank { domain.userAvatarUrl.orEmpty() },
                beatmapId = domain.beatmapId ?: 0L,
                beatmapTitle = if (domain.beatmapArtist.isNotBlank()) {
                    "${domain.beatmapArtist} - ${domain.beatmapTitle}"
                } else {
                    domain.beatmapTitle
                },
                beatmapVersion = domain.beatmapVersion,
                starRating = domain.starRating,
                rank = domain.rank,
                pp = RankPpMath.sanitizeFiniteNonNegative(domain.pp) ?: 0.0,
                accuracy = RankPpMath.sanitizeFiniteNonNegative(domain.accuracy) ?: 0.0,
                modsCsv = domain.mods.joinToString(","),
                createdAtIso = dto.createdAt.orEmpty(),
                recordedAt = recordedAt
            )
        }
        if (entities.isEmpty()) return 0
        friendScoreEventDao.upsertAndPrune(entities, keepCount = 100)
        return entities.size
    }

    override suspend fun recordDailyProfileSnapshot(profile: SelfProfile) = withContext(Dispatchers.IO) {
        val mode = profile.mode.trim().lowercase(Locale.US).takeIf { it in VALID_OSU_MODES } ?: "osu"
        val globalRank = RankPpMath.sanitizeRank(profile.globalRank) ?: 0
        val countryRank = RankPpMath.sanitizeRank(profile.countryRank) ?: 0
        val pp = RankPpMath.sanitizeFiniteNonNegative(profile.pp) ?: 0.0
        val accuracy = RankPpMath.sanitizeFiniteNonNegative(profile.accuracy) ?: 0.0
        val playCount = (profile.playCount ?: 0).coerceAtLeast(0)
        if (globalRank <= 0 && pp <= 0.0) return@withContext

        val today = LocalDate.now(ZoneOffset.UTC)
        profileHistoryDao.upsertSnapshot(
            ProfileHistoryEntity(
                dayKey = today.toString(),
                mode = mode,
                globalRank = globalRank,
                countryRank = countryRank,
                pp = pp,
                accuracy = accuracy,
                playCount = playCount,
                recordedAt = System.currentTimeMillis()
            )
        )
        profileHistoryDao.pruneOlderThan(today.minusDays(365).toString())
    }

    override fun observeProfileHistory(mode: String, limitDays: Int): Flow<List<ProfileHistoryPoint>> {
        val cleanMode = mode.trim().lowercase(Locale.US).takeIf { it in VALID_OSU_MODES } ?: "osu"
        val safeDays = limitDays.coerceIn(1, 365)
        return profileHistoryDao.observeRecentHistoryForMode(cleanMode, safeDays)
            .map { entities ->
                entities.map { entity ->
                    ProfileHistoryPoint(
                        dayKey = entity.dayKey,
                        mode = entity.mode,
                        globalRank = entity.globalRank,
                        countryRank = entity.countryRank,
                        pp = entity.pp,
                        accuracy = entity.accuracy,
                        playCount = entity.playCount,
                        recordedAt = entity.recordedAt
                    )
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override suspend fun fetchOsuTrackHistory(
        userId: Long,
        mode: String,
        days: Int
    ): Result<List<OsuTrackPoint>> = osuTrackRepository.fetchStatsHistory(userId, mode, days)

    override suspend fun fetchOsuTrackPeak(
        userId: Long,
        mode: String
    ): Result<OsuTrackPeak?> = osuTrackRepository.fetchPeak(userId, mode)

    override suspend fun getUserScores(
        usernameOrId: String,
        type: String,
        includeFails: Boolean,
        mode: String?,
        limit: Int,
        offset: Int
    ): Result<List<OsuScoreItem>> = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val clean = usernameOrId.trim()
            val directId = clean.toLongOrNull()
            val profile = if (directId == null) {
                getPlayerProfile(clean).getOrNull()?.takeIf { it.userId > 0L }
                    ?: apiPoller.withBearerRetry("Sign in with osu! OAuth to load scores") { bearer ->
                        val dto = apiService.getUser(bearer, clean, "username")
                        cacheUserProfile(dto).toDomain(null)
                    }
            } else null

            val uid = directId ?: profile?.userId ?: error("User not found")
            val uname = profile?.username?.takeIf { it.isNotBlank() } ?: clean
            val scores = apiPoller.withBearerRetry("Sign in with osu! OAuth to load scores") { bearer ->
                apiService.getUserScores(
                    bearerToken = bearer,
                    userId = uid,
                    type = type,
                    includeFails = if (includeFails) 1 else 0,
                    mode = mode,
                    limit = limit.coerceIn(1, 100),
                    offset = offset.coerceAtLeast(0)
                )
            }
            scores.map { it.toDomain(uname) }
        }
    }

    override suspend fun fetchDailySessionSummary(
        userId: Long,
        mode: String,
        currentProfile: PlayerProfile?
    ): Result<DailySessionSummary> = withContext(Dispatchers.IO) {
        if (userId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Missing user ID"))
        }
        val normMode = mode.trim().lowercase().takeIf { it in VALID_OSU_MODES } ?: "osu"
        runCatching {
            val recent = getUserScores(
                usernameOrId = userId.toString(),
                type = "recent",
                includeFails = true,
                mode = normMode,
                limit = 50,
                offset = 0
            ).getOrThrow()

            val plays24h = SessionTrackerCalculator.filterLast24Hours(recent)
            var rosuCalculated = 0
            val enrichedPlays = plays24h.map { item ->
                val needsPp = item.passed &&
                    (item.pp == null || !item.pp.isFinite()) &&
                    (item.beatmapId ?: 0L) > 0L &&
                    rosuCalculated < 5
                if (!needsPp) {
                    item
                } else {
                    rosuCalculated++
                    val bmId = item.beatmapId ?: 0L
                    val modSet = item.mods
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() && it != "CL" && it != "NM" }
                        .toSet()
                    val calc = runCatching {
                        rosuPpEngine.calculateRosuPp(
                            beatmapId = bmId,
                            mode = item.mode.ifBlank { normMode },
                            mods = modSet,
                            accuracyPercent = item.accuracy,
                            nMisses = item.countMiss,
                            combo = item.maxCombo.takeIf { it > 0 },
                            isLazer = item.isLazer
                        )
                    }.getOrNull()
                    if (calc != null && calc.pp.isFinite() && calc.pp > 0.0) {
                        item.copy(pp = calc.pp)
                    } else {
                        item
                    }
                }
            }

            val historyPoints = profileHistoryDao.getRecentHistoryForMode(normMode, 7).map { e ->
                ProfileHistoryPoint(
                    dayKey = e.dayKey,
                    mode = e.mode,
                    globalRank = e.globalRank,
                    countryRank = e.countryRank,
                    pp = e.pp,
                    accuracy = e.accuracy,
                    playCount = e.playCount,
                    recordedAt = e.recordedAt
                )
            }
            SessionTrackerCalculator.computeSummary(
                mode = normMode,
                scores24h = enrichedPlays,
                currentProfile = currentProfile,
                historyPoints = historyPoints
            )
        }
    }

    override suspend fun getBeatmapScores(
        beatmapId: Long,
        mode: String?
    ): Result<List<OsuScoreItem>> = withContext(Dispatchers.IO) {
        if (beatmapId <= 0L) return@withContext Result.success(emptyList())
        val requestedMode = mode?.trim()?.lowercase()?.takeIf { it in VALID_OSU_MODES }
        val cacheKey = "$beatmapId:${requestedMode ?: "default"}"
        val now = System.currentTimeMillis()
        beatmapScoresCache[cacheKey]?.let { (cachedAt, cachedScores) ->
            if ((now - cachedAt) < 60_000L) {
                return@withContext Result.success(cachedScores)
            }
        }

        runCatching {
            val response = apiPoller.withBearerRetry("Sign in with osu! OAuth to load beatmap scores") { bearer ->
                apiService.getBeatmapScores(
                    bearerToken = bearer,
                    beatmapId = beatmapId,
                    mode = requestedMode
                )
            }
            val scores = response.scores.orEmpty()
                .take(50)
                .map { it.toDomain(it.user?.username?.takeIf { u -> u.isNotBlank() } ?: "Player") }
            beatmapScoresCache[cacheKey] = System.currentTimeMillis() to scores
            scores
        }
    }

    override suspend fun searchBeatmapsets(
        query: String,
        status: String?,
        modeInt: Int?
    ): Result<List<BeatmapSetSearchResult>> = searchBeatmapsets(
        BeatmapSearchFilter(
            query = query,
            mode = modeInt,
            status = status
        )
    )

    override suspend fun searchBeatmapsets(filter: BeatmapSearchFilter): Result<List<BeatmapSetSearchResult>> =
        withContext(Dispatchers.IO) {
            return@withContext runCatching {
                val cleanQuery = filter.query.trim()
                val cleanStatus = filter.status?.takeIf { it.isNotBlank() && !it.equals("any", true) }
                val key = buildString {
                    append("beatmap_search:")
                    append(cleanQuery)
                    append('|').append(cleanStatus.orEmpty())
                    append('|').append(filter.mode ?: -1)
                    append('|').append(filter.apiSortParam.orEmpty())
                    append('|').append(filter.genreId ?: -1)
                    append('|').append(filter.languageId ?: -1)
                    append('|').append(filter.apiExtrasParam.orEmpty())
                    append('|').append(filter.apiGeneralParam.orEmpty())
                }
                val sets = apiPoller.coalesceBearerGet(
                    key = key,
                    missingTokenMessage = "Sign in with osu! OAuth to search beatmaps",
                    minIntervalMs = 250L
                ) { bearer ->
                    apiService.searchBeatmapsets(
                        bearerToken = bearer,
                        query = cleanQuery.ifBlank { null },
                        status = cleanStatus,
                        modeInt = filter.mode,
                        sort = filter.apiSortParam,
                        genreId = filter.genreId,
                        languageId = filter.languageId,
                        extras = filter.apiExtrasParam,
                        general = filter.apiGeneralParam
                    ).beatmapsets.orEmpty()
                }
                sets.map { it.toSearchResult() }
            }.recoverCatching { err ->
                if (err is CancellationException) throw err
                val cleanQuery = filter.query.trim()
                if (apiPoller.shouldFallbackToMirror(err) || apiPoller.getValidBearerToken() == null) {
                    val mirrorSets = apiPoller.searchBeatmapsetsFromMirrors(
                        query = cleanQuery,
                        modeInt = filter.mode,
                        limit = 25
                    )
                    if (mirrorSets.isNotEmpty()) {
                        return@recoverCatching mirrorSets.map { it.toSearchResult() }
                    }
                }
                throw apiPoller.toFriendlyApiException(err)
            }.onFailure {
                if (it is CancellationException) throw it
            }
        }

    override suspend fun getRankings(mode: String, country: String?): Result<List<PlayerProfile>> =
        withContext(Dispatchers.IO) {
            return@withContext runCatching {
                val res = apiPoller.withBearerRetry("Sign in with osu! OAuth to view rankings") { bearer ->
                    apiService.getRankings(
                        bearerToken = bearer,
                        mode = mode,
                        type = "performance",
                        country = country?.trim()?.uppercase()?.takeIf { it.length == 2 }
                    )
                }
                mapRankingEntries(res.ranking.orEmpty(), mode)
            }
        }

    override suspend fun getRankingsPage(mode: String, page: Int): Result<List<PlayerProfile>> =
        withContext(Dispatchers.IO) {
            val safeMode = mode.trim().lowercase().takeIf { it in VALID_OSU_MODES } ?: "osu"
            val safePage = page.coerceIn(1, 200)
            val cacheKey = "$safeMode:$safePage"
            val now = System.currentTimeMillis()
            rankingsPageCache[cacheKey]?.let { (ts, cached) ->
                if ((now - ts) < 300_000L && cached.isNotEmpty()) {
                    return@withContext Result.success(cached)
                }
            }

            runCatching {
                val res = apiPoller.withBearerRetry("Sign in with osu! OAuth to view live rankings") { bearer ->
                    apiService.getRankings(
                        bearerToken = bearer,
                        mode = safeMode,
                        type = "performance",
                        country = null,
                        page = safePage
                    )
                }
                val mapped = mapRankingEntries(res.ranking.orEmpty(), safeMode)
                if (mapped.isNotEmpty()) {
                    rankingsPageCache[cacheKey] = System.currentTimeMillis() to mapped
                }
                mapped
            }
        }

    private suspend fun mapRankingEntries(
        entries: List<com.osuchat.app.data.network.OsuRankingEntryDto>,
        mode: String
    ): List<PlayerProfile> {
        val aliases = userAliasDao.observeAllAliases().first().associateBy { it.normalizedUsername }
        val tracked = trackedFriendDao.getAllTrackedFriends().map { it.normalizedUsername }.toSet()
        return entries.mapNotNull { entry ->
            val user = entry.user ?: return@mapNotNull null
            val safeUsername = user.username?.takeIf { it.isNotBlank() } ?: "User ${user.id}"
            val norm = BeatmapUrlParser.normalizeUsername(safeUsername)
            val safeRank = RankPpMath.sanitizeRank(entry.globalRank ?: user.statistics?.globalRank)
            val safePp = RankPpMath.sanitizeFiniteNonNegative(entry.pp ?: user.statistics?.pp)
            val safeAcc = RankPpMath.sanitizeFiniteNonNegative(entry.hitAccuracy ?: user.statistics?.hitAccuracy)
            val safePlayCount = (entry.playCount ?: user.statistics?.playCount)?.takeIf { it >= 0 }
            val stats = (user.statistics ?: com.osuchat.app.data.network.OsuUserStatsDto()).copy(
                globalRank = safeRank,
                pp = safePp,
                hitAccuracy = safeAcc,
                playCount = safePlayCount
            )
            user.copy(username = safeUsername, statistics = stats).toProfileDomain(
                customAlias = aliases[norm]?.customNickname?.takeIf { it.isNotBlank() },
                customNote = aliases[norm]?.note?.takeIf { it.isNotBlank() },
                isTrackedForPp = norm in tracked,
                friendStatus = resolveFriendStatus(user.id, safeUsername, user.isMutual),
                mode = mode
            ).copy(
                globalRank = safeRank,
                pp = safePp,
                accuracy = safeAcc,
                playCount = safePlayCount
            )
        }
    }

    override suspend fun searchPlayers(query: String): Result<List<PlayerProfile>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

        val aliases = userAliasDao.observeAllAliases().first().associateBy { it.normalizedUsername }
        val tracked = trackedFriendDao.getAllTrackedFriends().map { it.normalizedUsername }.toSet()
        val token = apiPoller.getValidBearerToken()
        if (token != null && !apiPoller.isRateLimited()) {
            val apiRes = runCatching {
                apiPoller.withOptionalBearerRetry(token) { bearer ->
                    apiService.searchUsers(bearer, query = trimmed).user?.data.orEmpty().mapNotNull { dto ->
                        val uname = dto.username?.trim().orEmpty()
                        if (dto.id <= 0L && uname.isBlank()) return@mapNotNull null
                        val safeName = uname.ifBlank { "User ${dto.id}" }
                        val norm = BeatmapUrlParser.normalizeUsername(safeName)
                        cacheUserProfile(dto).toDomain(
                            customAlias = aliases[norm]?.customNickname?.takeIf { it.isNotBlank() },
                            customNote = aliases[norm]?.note?.takeIf { it.isNotBlank() },
                            isTrackedForPp = norm in tracked,
                            friendStatus = resolveFriendStatus(dto.id, safeName, dto.isMutual)
                        )
                    }
                }
            }
            apiRes.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (apiRes.isSuccess) return@withContext apiRes
        }

        val single = getPlayerProfile(trimmed).getOrNull()?.takeIf { it.userId > 0L }
        Result.success(listOfNotNull(single))
    }

    override suspend fun toggleSaveBeatmap(item: SavedBeatmapItem): Result<Boolean> = withContext(Dispatchers.IO) {
        if (item.beatmapId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid beatmap ID"))
        }
        val resolvedLength = if (item.totalLengthSeconds > 0) {
            item.totalLengthSeconds
        } else {
            beatmapCacheDao.getByKey("b:${item.beatmapId}")?.totalLengthSeconds?.takeIf { it > 0 }
                ?: beatmapCacheDao.getByKey("s:${item.beatmapsetId}")?.totalLengthSeconds?.takeIf { it > 0 }
                ?: 0
        }
        val normalizedTags = SavedBeatmapItem.formatTagsCsv(SavedBeatmapItem.parseTagsCsv(item.tagsCsv))
        val enrichedItem = item.copy(
            tagsCsv = normalizedTags,
            totalLengthSeconds = resolvedLength
        )
        val nowSaved = savedBeatmapDao.toggleSaved(enrichedItem.toEntity())
        Result.success(nowSaved)
    }

    override suspend fun removeSavedBeatmap(beatmapId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        savedBeatmapDao.deleteByBeatmapId(beatmapId)
        Result.success(Unit)
    }

    override suspend fun updateSavedBeatmapTags(beatmapId: Long, tagsCsv: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (beatmapId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid beatmap ID"))
        }
        val normalized = SavedBeatmapItem.formatTagsCsv(SavedBeatmapItem.parseTagsCsv(tagsCsv))
        savedBeatmapDao.updateTags(beatmapId, normalized)
        Result.success(Unit)
    }

    override suspend fun sendBeatmapToSelfInGame(item: SavedBeatmapItem): Result<Unit> = withContext(Dispatchers.IO) {
        val selfNick = ensureSelfDmReady()
            ?: return@withContext Result.failure(IllegalStateException("Sign in first to send beatmaps to your in-game chat"))
        val dmChannelId = normalizeChannelId("dm:${BeatmapUrlParser.normalizeUsername(selfNick)}")
        val linkText = "[https://osu.ppy.sh/b/${item.beatmapId} ${item.artist} - ${item.title} [${item.version}]]"
        sendMessage(dmChannelId, linkText)
    }

    override suspend fun sendAllSavedBeatmapsToSelfInGame(): Result<Int> = withContext(Dispatchers.IO) {
        val saved = savedBeatmapDao.getAllSavedBeatmaps()
        if (saved.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Play Later queue is empty"))
        }
        val selfNick = ensureSelfDmReady()
            ?: return@withContext Result.failure(IllegalStateException("Sign in first to send beatmaps to your in-game chat"))
        val dmChannelId = normalizeChannelId("dm:${BeatmapUrlParser.normalizeUsername(selfNick)}")
        var sentCount = 0
        for (entity in saved) {
            val linkText = "[https://osu.ppy.sh/b/${entity.beatmapId} ${entity.artist} - ${entity.title} [${entity.version}]]"
            val res = sendMessage(dmChannelId, linkText)
            if (res.isFailure) {
                if (sentCount == 0) return@withContext Result.failure(res.exceptionOrNull() ?: IllegalStateException("IRC send failed"))
                break
            }
            sentCount++
            if (sentCount < saved.size) {
                delay(320L)
            }
        }
        Result.success(sentCount)
    }

    private suspend fun ensureSelfDmReady(): String? {
        val current = prefs.settingsFlow.first()
        val selfNick = current.username.trim()
            .ifBlank { current.ircUsername.trim() }
            .ifBlank { prefs.getIrcCredentials()?.username?.trim().orEmpty() }
        if (selfNick.isBlank()) return null
        val dmChannelId = normalizeChannelId("dm:${BeatmapUrlParser.normalizeUsername(selfNick)}")
        if (channelDao.getChannelById(dmChannelId) == null) {
            channelDao.upsertChannel(
                ChannelEntity(
                    id = dmChannelId,
                    name = selfNick,
                    description = "Direct Message (Self Queue Sync)",
                    type = ChannelType.DIRECT.name,
                    targetUserId = current.userId.takeIf { it > 0L },
                    avatarUrl = current.avatarUrl,
                    lastMessageTimestamp = System.currentTimeMillis(),
                    isJoined = true
                )
            )
        }
        return selfNick
    }

    override suspend fun estimatePpForRank(mode: String, targetRank: Int): Result<RankPpConversionResult> =
        withContext(Dispatchers.IO) {
            val safeMode = mode.trim().lowercase().takeIf { it in VALID_OSU_MODES } ?: "osu"
            val clampedRank = RankPpMath.clampRank(targetRank)

            runCatching {
                if (clampedRank <= 10_000) {
                    val page = ((clampedRank - 1) / 50) + 1
                    val entries = getRankingsPage(safeMode, page).getOrNull().orEmpty()
                    if (entries.isNotEmpty()) {
                        val exact = entries.firstOrNull { it.globalRank == clampedRank }
                            ?: entries.getOrNull((clampedRank - 1) % 50)
                            ?: entries.last()
                        val ppVal = RankPpMath.sanitizeFiniteNonNegative(exact.pp)
                            ?: RankPpMath.defaultAnchorsForMode(safeMode).second
                        return@runCatching RankPpConversionResult(
                            mode = safeMode,
                            targetRank = clampedRank,
                            estimatedPp = ppVal,
                            nearestPlayer = exact,
                            isExactLeaderboardMatch = true
                        )
                    }
                }

                val curve = buildCalibratedCurve(safeMode)
                val estPp = RankPpMath.estimatePpFromCurve(clampedRank, curve)
                RankPpConversionResult(
                    mode = safeMode,
                    targetRank = clampedRank,
                    estimatedPp = estPp,
                    nearestPlayer = null,
                    isExactLeaderboardMatch = false
                )
            }
        }

    override suspend fun estimateRankForPp(
        mode: String,
        targetPp: Double,
        currentRankHint: Int?,
        currentPpHint: Double?
    ): Result<RankPpConversionResult> = withContext(Dispatchers.IO) {
        val safeMode = mode.trim().lowercase().takeIf { it in VALID_OSU_MODES } ?: "osu"
        val cleanPp = RankPpMath.clampTotalPp(targetPp)

        runCatching {
            val curve = buildCalibratedCurve(safeMode)
            val hasToken = apiPoller.getValidBearerToken() != null && !apiPoller.isRateLimited()

            if (hasToken && cleanPp >= curve.pp10000 * 0.92) {
                val page1 = getRankingsPage(safeMode, 1).getOrNull().orEmpty()
                val topPlayer = page1.firstOrNull()
                val topPp = RankPpMath.sanitizeFinitePositive(topPlayer?.pp)
                if (topPp != null && cleanPp >= topPp) {
                    return@runCatching RankPpConversionResult(
                        mode = safeMode,
                        targetRank = 1,
                        estimatedPp = cleanPp,
                        nearestPlayer = topPlayer,
                        isExactLeaderboardMatch = true
                    )
                }

                val page200 = getRankingsPage(safeMode, 200).getOrNull().orEmpty()
                val boundary10kPp = RankPpMath.sanitizeFinitePositive(page200.lastOrNull()?.pp) ?: curve.pp10000
                if (cleanPp >= boundary10kPp) {
                    val guessRank = RankPpMath.estimateRankFromCurve(cleanPp, curve)
                    var lo = 1
                    var hi = 200
                    val nextMid = ((guessRank.coerceIn(1, 10_000) - 1) / 50 + 1).coerceIn(1, 200)
                    var step = 0
                    while (lo <= hi && step < 7) {
                        val mid = if (step == 0) nextMid else (lo + hi) / 2
                        step++
                        val entries = getRankingsPage(safeMode, mid).getOrNull().orEmpty()
                        if (entries.isEmpty()) break
                        val firstPp = RankPpMath.sanitizeFiniteNonNegative(entries.firstOrNull()?.pp) ?: break
                        val lastPp = RankPpMath.sanitizeFiniteNonNegative(entries.lastOrNull()?.pp) ?: break
                        when {
                            cleanPp > firstPp -> hi = mid - 1
                            cleanPp < lastPp -> lo = mid + 1
                            else -> {
                                var nearest = entries.first()
                                var minDiff = Double.MAX_VALUE
                                entries.forEach { entry ->
                                    val ep = RankPpMath.sanitizeFiniteNonNegative(entry.pp) ?: return@forEach
                                    val diff = abs(ep - cleanPp)
                                    if (diff < minDiff) {
                                        minDiff = diff
                                        nearest = entry
                                    }
                                }
                                val fallbackRank = RankPpMath.sanitizeRank(nearest.globalRank) ?: ((mid - 1) * 50 + 1)
                                val interpRank = RankPpMath.interpolateRankInPage(
                                    targetPp = cleanPp,
                                    entries = entries.map { it.globalRank to it.pp },
                                    page = mid,
                                    fallbackRank = fallbackRank
                                )
                                return@runCatching RankPpConversionResult(
                                    mode = safeMode,
                                    targetRank = interpRank.coerceIn(1, 10_000),
                                    estimatedPp = cleanPp,
                                    nearestPlayer = nearest,
                                    isExactLeaderboardMatch = true
                                )
                            }
                        }
                    }
                }
            }

            // Outside Top 10,000 or offline power-law curve
            val estimatedRank = RankPpMath.estimateRankFromCurve(
                targetPp = cleanPp,
                curve = curve,
                currentRankHint = currentRankHint,
                currentPpHint = currentPpHint
            )

            RankPpConversionResult(
                mode = safeMode,
                targetRank = estimatedRank,
                estimatedPp = cleanPp,
                nearestPlayer = null,
                isExactLeaderboardMatch = false
            )
        }
    }

    private suspend fun buildCalibratedCurve(mode: String): RankPpMath.CalibratedRankCurve {
        val (fallback1k, fallback10k) = RankPpMath.defaultAnchorsForMode(mode)
        return coroutineScope {
            val d1k = async { getRankingsPage(mode, 20).getOrNull()?.lastOrNull()?.pp }
            val d10k = async { getRankingsPage(mode, 200).getOrNull()?.lastOrNull()?.pp }
            RankPpMath.buildCalibratedCurve(
                mode = mode,
                observedPp1k = d1k.await() ?: fallback1k,
                observedPp10k = d10k.await() ?: fallback10k
            )
        }
    }

    override suspend fun getNewsAndSpotlights(forceRefresh: Boolean): Result<NewsAndSpotlightsFeed> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                newsFeedCache?.let { (cachedAt, cachedFeed) ->
                    if ((now - cachedAt) < 300_000L &&
                        (cachedFeed.news.isNotEmpty() || cachedFeed.spotlights.isNotEmpty())
                    ) {
                        return@withContext Result.success(cachedFeed)
                    }
                }
            }

            runCatching {
                apiPoller.coalesceGet("news_spotlights", minIntervalMs = 250L) {
                    val newsResult = runCatching {
                        apiPoller.withBearerRetry("Sign in with osu! OAuth to load osu! news") { bearer ->
                            apiService.getNews(bearer, limit = 12).newsPosts.orEmpty()
                        }
                    }
                    val spotlightsResult = runCatching {
                        apiPoller.withBearerRetry("Sign in with osu! OAuth to load spotlights") { bearer ->
                            apiService.getSpotlights(bearer).spotlights.orEmpty()
                        }
                    }
                    newsResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    spotlightsResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }

                        val newsItems = newsResult.getOrDefault(emptyList()).map { dto ->
                            val rawImg = dto.firstImage?.trim().orEmpty()
                            val resolvedImg = when {
                                rawImg.isBlank() -> null
                                rawImg.startsWith("http", ignoreCase = true) -> rawImg
                                rawImg.startsWith("/") -> "https://osu.ppy.sh$rawImg"
                                else -> "https://osu.ppy.sh/$rawImg"
                            }
                            val postUrl = if (!dto.slug.isNullOrBlank()) {
                                "https://osu.ppy.sh/home/news/${dto.slug}"
                            } else {
                                "https://osu.ppy.sh/home/news"
                            }
                            OsuNewsItem(
                                id = dto.id,
                                title = dto.title?.takeIf { it.isNotBlank() } ?: "osu! News",
                                author = dto.author.orEmpty(),
                                publishedAt = dto.publishedAt.orEmpty().take(10),
                                preview = dto.preview.orEmpty().trim(),
                                imageUrl = resolvedImg,
                                url = postUrl
                            )
                        }

                        val spotlightItems = spotlightsResult.getOrDefault(emptyList()).map { dto ->
                            OsuSpotlightItem(
                                id = dto.id,
                                name = dto.name?.takeIf { it.isNotBlank() } ?: "Spotlight #${dto.id}",
                                type = dto.type ?: "spotlight",
                                startDate = dto.startDate.orEmpty().take(10),
                                endDate = dto.endDate.orEmpty().take(10),
                                participantCount = dto.participantCount,
                                modeSpecific = dto.modeSpecific ?: false,
                                url = "https://osu.ppy.sh/rankings/osu/charts?spotlight=${dto.id}"
                            )
                        }.sortedWith(
                            compareByDescending<OsuSpotlightItem> { it.endDate }
                                .thenByDescending { it.startDate }
                                .thenByDescending { it.id }
                        )

                        if (newsItems.isEmpty() && spotlightItems.isEmpty()) {
                            val failure = newsResult.exceptionOrNull() ?: spotlightsResult.exceptionOrNull()
                            if (failure != null) throw failure
                        }

                        val feed = NewsAndSpotlightsFeed(
                            news = newsItems,
                            spotlights = spotlightItems
                        )
                        if (feed.news.isNotEmpty() || feed.spotlights.isNotEmpty()) {
                            newsFeedCache = System.currentTimeMillis() to feed
                        }
                        feed
                }
            }
        }

    override suspend fun fetchSeasonalBackgrounds(forceRefresh: Boolean): Result<List<SeasonalBackgroundItem>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                seasonalBackgroundsCache?.let { (cachedAt, cachedItems) ->
                    if ((now - cachedAt) < 1_800_000L && cachedItems.isNotEmpty()) {
                        return@withContext Result.success(cachedItems)
                    }
                }
            }

            if (!networkMonitor.isOnline.value) {
                return@withContext Result.success(seasonalBackgroundsCache?.second.orEmpty())
            }

            val fetched = runCatching {
                apiPoller.coalesceGet("seasonal_backgrounds", minIntervalMs = 250L) {
                    val bearer = apiPoller.getValidBearerToken()
                    val response = if (bearer != null) {
                        runCatching {
                            apiPoller.withBearerRetry("Load seasonal backgrounds") { token ->
                                apiService.getSeasonalBackgrounds(token)
                            }
                        }.getOrElse { err ->
                            if (err is CancellationException) throw err
                            apiService.getSeasonalBackgrounds(null)
                        }
                    } else {
                        apiService.getSeasonalBackgrounds(null)
                    }
                    response.backgrounds.orEmpty().mapNotNull { dto ->
                        val rawUrl = dto.url?.trim().orEmpty()
                        if (rawUrl.isBlank()) return@mapNotNull null
                        val resolvedUrl = when {
                            rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                            rawUrl.startsWith("/") -> "https://osu.ppy.sh$rawUrl"
                            else -> "https://osu.ppy.sh/$rawUrl"
                        }
                        val artist = dto.user?.username?.trim()?.takeIf { it.isNotBlank() } ?: "osu! artist"
                        SeasonalBackgroundItem(
                            url = resolvedUrl,
                            artistUsername = artist
                        )
                    }.distinctBy { it.url }
                }
            }

            fetched.fold(
                onSuccess = { items ->
                    if (items.isNotEmpty()) {
                        seasonalBackgroundsCache = System.currentTimeMillis() to items
                        Result.success(items)
                    } else {
                        Result.success(seasonalBackgroundsCache?.second.orEmpty())
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    Result.success(seasonalBackgroundsCache?.second.orEmpty())
                }
            )
        }

    override suspend fun downloadSeasonalWallpaper(imageUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanUrl = imageUrl.trim()
                require(cleanUrl.startsWith("https://", ignoreCase = true) || cleanUrl.startsWith("http://", ignoreCase = true)) {
                    "Invalid wallpaper URL"
                }
                val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                val hashPrefix = "seasonal_${cleanUrl.hashCode().toUInt().toString(16)}"
                val ext = cleanUrl.substringBefore('?').substringAfterLast('.', "")
                    .lowercase(Locale.ROOT)
                    .takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
                    ?: "jpg"
                val dest = File(dir, "$hashPrefix.$ext")
                if (!dest.exists() || dest.length() == 0L) {
                    val req = Request.Builder().url(cleanUrl).get().build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Failed to download wallpaper (HTTP ${resp.code})")
                        }
                        val body = resp.body ?: throw IllegalStateException("Empty wallpaper response")
                        val tmp = File(dir, "$hashPrefix.$ext.tmp")
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tmp.length() == 0L) {
                            tmp.delete()
                            throw IllegalStateException("Downloaded wallpaper is empty")
                        }
                        if (dest.exists()) dest.delete()
                        if (!tmp.renameTo(dest)) {
                            tmp.copyTo(dest, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
                Uri.fromFile(dest).toString()
            }
        }

    override suspend fun exportChatLog(
        channelId: String,
        format: ChatLogExportFormat,
        destinationUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedId = normalizeChannelId(channelId)
            val channelEntity = channelDao.getChannelById(normalizedId)
            val channelDisplayName = channelEntity?.name?.takeIf { it.isNotBlank() } ?: channelId.trim()
            val entities = messageDao.getAllMessagesForChannel(normalizedId)
            val aliasByNorm = userAliasDao.observeAllAliases().first()
                .mapNotNull { e -> e.customNickname.takeIf { it.isNotBlank() }?.let { e.normalizedUsername to it } }
                .toMap()
            val messages = entities.map { entity ->
                val alias = aliasByNorm[BeatmapUrlParser.normalizeUsername(entity.senderName)]
                entity.toDomain(senderAlias = alias)
            }
            val formatted = ChatLogFormatter.format(
                channelName = channelDisplayName,
                channelId = normalizedId,
                messages = messages,
                format = format
            )
            val stream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("Could not open selected file for writing.")
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(formatted)
            }
            messages.size
        }
    }

    private fun SavedBeatmapEntity.toDomain(): SavedBeatmapItem = SavedBeatmapItem(
        beatmapId = beatmapId,
        beatmapsetId = beatmapsetId,
        title = title,
        artist = artist,
        creator = creator,
        version = version,
        starRating = starRating,
        bpm = bpm,
        mode = mode,
        coverUrl = coverUrl,
        previewAudioUrl = previewAudioUrl,
        savedAt = savedAt,
        tagsCsv = tagsCsv,
        totalLengthSeconds = totalLengthSeconds
    )

    private fun SavedBeatmapItem.toEntity(): SavedBeatmapEntity = SavedBeatmapEntity(
        beatmapId = beatmapId,
        beatmapsetId = beatmapsetId,
        title = title,
        artist = artist,
        creator = creator,
        version = version,
        starRating = starRating,
        bpm = bpm,
        mode = mode,
        coverUrl = coverUrl,
        previewAudioUrl = previewAudioUrl,
        savedAt = savedAt,
        tagsCsv = SavedBeatmapItem.formatTagsCsv(SavedBeatmapItem.parseTagsCsv(tagsCsv)),
        totalLengthSeconds = totalLengthSeconds
    )

    private fun resolveAutoJoinPublicChannels(settings: AppSettings): List<String> {
        if (!settings.autoJoinEnabled) return emptyList()
        val configured = settings.autoJoinChannels
            .mapNotNull { raw ->
                val clean = raw.trim()
                if (clean.isBlank()) null else if (clean.startsWith("#")) clean else "#$clean"
            }
            .distinctBy { it.lowercase() }
        return configured.ifEmpty { AppSettings.DEFAULT_AUTO_JOIN_CHANNELS }
    }

    private suspend fun resolveConnectChannelList(settings: AppSettings): List<String> {
        val autoJoin = resolveAutoJoinPublicChannels(settings)
        val joinedPublic = channelDao.observeJoinedChannels().first()
            .filter { it.type == ChannelType.PUBLIC.name }
            .map { it.name }
        val filteredJoined = if (settings.autoJoinEnabled) {
            joinedPublic
        } else {
            // When auto-join is disabled, avoid rejoining default/auto-join public channels automatically,
            // while keeping active multiplayer lobbies (#mp_*) or manually joined public channels if not defaults.
            val suppressedDefaults = (settings.autoJoinChannels + AppSettings.DEFAULT_AUTO_JOIN_CHANNELS)
                .map { normalizeChannelId(it) }
                .toSet()
            joinedPublic.filter { normalizeChannelId(it) !in suppressedDefaults || it.startsWith("#mp_", ignoreCase = true) }
        }
        return (autoJoin + filteredJoined).distinctBy { it.lowercase() }
    }

    private suspend fun ensureDefaultIrcChannels(settings: AppSettings? = null) {
        val currentSettings = settings ?: prefs.settingsFlow.first()
        val channelsToJoin = resolveAutoJoinPublicChannels(currentSettings)
        if (channelsToJoin.isEmpty()) return

        val knownDescriptions = mapOf(
            "#turkish" to "Turkish osu! community",
            "#osu" to "General osu! chat",
            "#lobby" to "Multiplayer lobby & match invites",
            "#announce" to "Announcements & tournaments"
        )
        for (name in channelsToJoin) {
            val id = normalizeChannelId(name)
            val desc = knownDescriptions[id] ?: "Public osu! channel"
            if (channelDao.getChannelById(id) == null) {
                channelDao.upsertChannel(
                    ChannelEntity(
                        id = id,
                        name = name,
                        description = desc,
                        type = ChannelType.PUBLIC.name,
                        isJoined = true
                    )
                )
            } else {
                channelDao.setChannelJoined(id, true)
            }
        }
    }

    private suspend fun handleIrcEvent(event: IrcEvent) {
        val currentSettings = prefs.settingsFlow.first()
        when (event) {
            is IrcEvent.PrivMsg -> {
                _seenOnlineSenders.add(BeatmapUrlParser.normalizeUsername(event.sender))
                recomputeOnlineIrcNicks()

                if (event.sender.equals("BanchoBot", ignoreCase = true)) {
                    MpLobbyParser.parseCreatedMatchResponse(event.text)?.let { (matchId, roomName) ->
                        val mpChannelId = "#mp_$matchId"
                        val existingMp = channelDao.getChannelById(mpChannelId)
                        channelDao.upsertChannel(
                            (existingMp ?: ChannelEntity(
                                id = mpChannelId,
                                name = mpChannelId,
                                description = roomName.ifBlank { "Multiplayer Lobby #$matchId" },
                                type = ChannelType.PUBLIC.name,
                                isJoined = true
                            )).copy(
                                isJoined = true,
                                description = roomName.ifBlank { existingMp?.description ?: "Multiplayer Lobby #$matchId" },
                                lastMessageTimestamp = System.currentTimeMillis()
                            )
                        )
                        ircClient.joinChannel(mpChannelId)
                        _createdMpChannelEvents.tryEmit(mpChannelId)
                        scope.launch {
                            delay(600L)
                            sendMessage(mpChannelId, "!mp settings")
                        }
                    }
                }
                val isPublic = event.target.startsWith("#")
                val channelId = if (isPublic) {
                    normalizeChannelId(event.target)
                } else {
                    normalizeChannelId("dm:${BeatmapUrlParser.normalizeUsername(event.sender)}")
                }
                val channelName = if (isPublic) event.target.lowercase() else event.sender
                val channelType = if (isPublic) ChannelType.PUBLIC else ChannelType.DIRECT

                val existingChannel = channelDao.getChannelById(channelId)
                handleMpTimerFromBotMessage(
                    channelId = channelId,
                    roomNameHint = existingChannel?.description ?: channelName,
                    senderName = event.sender,
                    content = event.text
                )
                val now = System.currentTimeMillis()
                val isSelf = isSelfSender(event.sender, 0L, currentSettings)
                if (isSelf && consumeRecentLocalOutgoingDuplicate(channelId, event.text, now)) {
                    return
                }
                val isOpen = _activeChannelId.value == channelId
                val isIgnored = !isSelf && isSenderIgnored(event.sender, currentSettings)
                val unreadDelta = if (isSelf || isOpen || isIgnored) 0 else 1

                if (existingChannel == null) {
                    val avatarGuess = if (isSelf && !currentSettings.avatarUrl.isNullOrBlank()) {
                        currentSettings.avatarUrl
                    } else {
                        getOrGuessAvatarForUser(event.sender)
                    }
                    channelDao.upsertChannel(
                        ChannelEntity(
                            id = channelId,
                            name = channelName,
                            description = if (isPublic) "Bancho IRC channel" else "Direct Message",
                            type = channelType.name,
                            unreadCount = unreadDelta,
                            lastMessageText = if (event.isAction) "* ${event.text}" else event.text,
                            lastMessageTimestamp = now,
                            lastSenderName = event.sender,
                            avatarUrl = avatarGuess,
                            isJoined = true
                        )
                    )
                } else {
                    channelDao.updateChannelOnIncomingMessage(
                        channelId = channelId,
                        text = if (event.isAction) "* ${event.text}" else event.text,
                        timestamp = now,
                        senderName = event.sender,
                        unreadDelta = unreadDelta
                    )
                }

                val isHighlighted = !isSelf && isMessageHighlighted(event.text, currentSettings)
                val senderAvatar = if (isSelf && !currentSettings.avatarUrl.isNullOrBlank()) {
                    currentSettings.avatarUrl
                } else {
                    getOrGuessAvatarForUser(event.sender)
                }
                val msgEntity = MessageEntity(
                    id = "irc_${now}_${UUID.randomUUID().toString().take(8)}",
                    channelId = channelId,
                    senderId = if (isSelf && currentSettings.userId > 0L) currentSettings.userId else 0L,
                    senderName = event.sender,
                    senderAvatarUrl = senderAvatar,
                    content = event.text,
                    timestamp = now,
                    isOutgoing = isSelf,
                    isAction = event.isAction,
                    isHighlighted = isHighlighted,
                    deliveryStatus = if (isSelf) DeliveryStatus.SENT.name else DeliveryStatus.READ.name
                )
                messageDao.upsertMessage(msgEntity)

                if (senderAvatar == null) {
                    scope.launch {
                        val profile = getPlayerProfile(event.sender).getOrNull() ?: return@launch
                        if (profile.avatarUrl.isNotBlank()) {
                            messageDao.upsertMessage(
                                msgEntity.copy(
                                    senderAvatarUrl = profile.avatarUrl,
                                    senderId = profile.userId
                                )
                            )
                            if (!isPublic && !isSelf) {
                                channelDao.updateDirectChannelMeta(
                                    channelId = channelId,
                                    avatarUrl = profile.avatarUrl,
                                    userId = profile.userId
                                )
                            }
                        }
                    }
                }

                if (!isSelf && !isIgnored) {
                    maybeNotify(
                        channelId = channelId,
                        messageEntity = msgEntity,
                        isPublic = isPublic,
                        isHighlighted = isHighlighted,
                        settings = currentSettings
                    )
                }
            }
            is IrcEvent.NamesChunk -> {
                val chId = event.channel.lowercase()
                val set = ircNamesAccum.getOrPut(chId) { linkedSetOf() }
                synchronized(set) { set.addAll(event.nicks) }
            }
            is IrcEvent.NamesEnd -> {
                val chId = event.channel.lowercase()
                val nicks = ircNamesAccum.remove(chId)?.toList()?.sorted() ?: emptyList()
                channelUsersState.getOrPut(chId) { MutableStateFlow(emptyList()) }.value = nicks
                recomputeOnlineIrcNicks()
            }
            is IrcEvent.UserJoined -> {
                val chId = event.channel.lowercase()
                val flow = channelUsersState.getOrPut(chId) { MutableStateFlow(emptyList()) }
                if (flow.value.none { it.equals(event.nick, ignoreCase = true) }) {
                    flow.value = (flow.value + event.nick).sorted()
                }
                recomputeOnlineIrcNicks()
            }
            is IrcEvent.UserParted -> {
                val chId = event.channel.lowercase()
                channelUsersState[chId]?.let { flow ->
                    flow.value = flow.value.filterNot { it.equals(event.nick, ignoreCase = true) }
                    recomputeOnlineIrcNicks()
                }
            }
            is IrcEvent.UserQuit -> {
                channelUsersState.values.forEach { flow ->
                    flow.value = flow.value.filterNot { it.equals(event.nick, ignoreCase = true) }
                }
                _seenOnlineSenders.remove(BeatmapUrlParser.normalizeUsername(event.nick))
                recomputeOnlineIrcNicks()
            }
            is IrcEvent.NickChanged -> {
                val oldNorm = BeatmapUrlParser.normalizeUsername(event.oldNick)
                val newNorm = BeatmapUrlParser.normalizeUsername(event.newNick)
                channelUsersState.values.forEach { flow ->
                    val current = flow.value
                    if (current.any { it.equals(event.oldNick, ignoreCase = true) }) {
                        flow.value = (
                            current.filterNot {
                                it.equals(event.oldNick, ignoreCase = true) ||
                                    it.equals(event.newNick, ignoreCase = true)
                            } + event.newNick
                        ).sorted()
                    }
                }
                if (_seenOnlineSenders.remove(oldNorm) && newNorm.isNotBlank()) {
                    _seenOnlineSenders.add(newNorm)
                }
                recomputeOnlineIrcNicks()
            }
            is IrcEvent.NoSuchNick -> {
                val normTarget = BeatmapUrlParser.normalizeUsername(event.target)
                _seenOnlineSenders.remove(normTarget)
                recomputeOnlineIrcNicks()
                runCatching {
                    Log.d("BanchoIrc", "Ignoring 401 NoSuchNick for delivery status: target=${event.target} (${event.reason})")
                }
            }
            is IrcEvent.AuthFailed -> {
                _isIrcAuthError.value = true
                _connectionError.value = event.reason
                _connectionStatus.value = ConnectionStatus.ERROR
            }
            is IrcEvent.Connected -> {
                ircNamesAccum.clear()
                _isIrcAuthError.value = false
                _connectionError.value = null
                _sessionConnectedAt.value = System.currentTimeMillis()
                ensureDefaultIrcChannels(currentSettings)
                resolveConnectChannelList(currentSettings)
                    .forEach { ch -> ircClient.joinChannel(ch) }
                scope.launch { flushQueuedMessages() }
            }
            else -> Unit
        }
    }

    private suspend fun upsertApiChannel(dto: ChatChannelDto, selfUserId: Long): ChannelEntity {
        val channelId = dto.channelId.toString()
        val isPm = dto.type.equals("PM", ignoreCase = true)
        val existing = channelDao.getChannelById(channelId)
        val otherUserId = if (isPm) {
            dto.users?.firstOrNull { it != selfUserId } ?: existing?.targetUserId
        } else {
            null
        }
        val avatarUrl = existing?.avatarUrl ?: otherUserId?.let { "https://a.ppy.sh/$it" }
        val resolvedName = dto.name?.takeIf { it.isNotBlank() }
            ?: existing?.name
            ?: if (isPm) "DM #$channelId" else "#channel-$channelId"

        val entity = ChannelEntity(
            id = channelId,
            name = resolvedName,
            description = dto.description.orEmpty(),
            type = if (isPm) ChannelType.DIRECT.name else ChannelType.PUBLIC.name,
            unreadCount = existing?.unreadCount ?: 0,
            lastMessageText = existing?.lastMessageText,
            lastMessageTimestamp = existing?.lastMessageTimestamp ?: 0L,
            lastSenderName = existing?.lastSenderName,
            targetUserId = otherUserId,
            avatarUrl = avatarUrl,
            isJoined = true,
            notificationMode = existing?.notificationMode,
            isPinned = existing?.isPinned ?: false
        )
        channelDao.upsertChannel(entity)
        if (dto.channelId > 0L) {
            if (isPm) {
                if (otherUserId != null && otherUserId > 0L) {
                    apiDmChannelIdByUserId[otherUserId] = dto.channelId
                }
            } else if (!dto.name.isNullOrBlank()) {
                val raw = dto.name.trim().lowercase(Locale.ROOT)
                val cacheKey = if (raw.startsWith("#")) raw else "#$raw"
                apiChannelIdCache[cacheKey] = dto.channelId
            }
        }
        return entity
    }

    private suspend fun persistApiMessage(
        dto: ChatMessageDto,
        settings: AppSettings,
        allowNotification: Boolean
    ) {
        val channelId = dto.channelId.toString()
        val msgId = ChatHistorySyncHelper.apiMessageId(dto.messageId)
        val rawContent = dto.content.orEmpty()
        val timestamp = ChatHistorySyncHelper.parseIso8601TimestampMillis(dto.timestamp)

        val senderName = dto.sender?.username?.takeIf { it.isNotBlank() } ?: "User ${dto.senderId}"
        val senderAvatar = dto.sender?.avatarUrl ?: "https://a.ppy.sh/${dto.senderId}"
        if (dto.sender != null && (!dto.sender.profileColour.isNullOrBlank() || dto.sender.profileHue != null || dto.sender.isSupporter)) {
            recordChatUserColor(
                username = dto.sender.username ?: senderName,
                profileColour = dto.sender.profileColour,
                profileHue = dto.sender.profileHue,
                isSupporter = dto.sender.isSupporter
            )
        }
        val isOutgoing = isSelfSender(senderName, dto.senderId, settings)
        if (isOutgoing && consumeRecentLocalOutgoingDuplicate(channelId, rawContent, timestamp)) {
            return
        }
        val isHighlighted = !isOutgoing && isMessageHighlighted(rawContent, settings)

        val entity = MessageEntity(
            id = msgId,
            channelId = channelId,
            senderId = dto.senderId,
            senderName = senderName,
            senderAvatarUrl = senderAvatar,
            content = rawContent,
            timestamp = timestamp,
            isOutgoing = isOutgoing,
            isAction = dto.isAction,
            isHighlighted = isHighlighted,
            deliveryStatus = if (isOutgoing) DeliveryStatus.SENT.name else DeliveryStatus.READ.name
        )
        if (messageDao.insertIfAbsent(entity) == -1L) return

        val channel = channelDao.getChannelById(channelId)
        handleMpTimerFromBotMessage(
            channelId = channelId,
            roomNameHint = channel?.description ?: channel?.name,
            senderName = senderName,
            content = rawContent
        )
        val isPublic = channel?.type != ChannelType.DIRECT.name
        val isOpen = _activeChannelId.value == channelId
        val isIgnored = !isOutgoing && isSenderIgnored(senderName, settings)
        val unreadDelta = if (!isOutgoing && !isOpen && allowNotification && !isIgnored) 1 else 0

        if (channel != null) {
            channelDao.updateChannelOnIncomingMessage(
                channelId = channelId,
                text = if (dto.isAction) "* $rawContent" else rawContent,
                timestamp = timestamp,
                senderName = senderName,
                unreadDelta = unreadDelta
            )
            if (channel.type == ChannelType.DIRECT.name && !isOutgoing && channel.avatarUrl.isNullOrBlank()) {
                channelDao.updateDirectChannelMeta(channelId, senderAvatar, null)
            }
        }

        if (allowNotification && !isOutgoing && !isIgnored) {
            maybeNotify(
                channelId = channelId,
                messageEntity = entity,
                isPublic = isPublic,
                isHighlighted = isHighlighted,
                settings = settings
            )
        }
    }

    private fun isSenderIgnored(senderName: String, settings: AppSettings): Boolean {
        val norm = BeatmapUrlParser.normalizeOsuNick(senderName)
        return norm.isNotEmpty() && norm != "banchobot" && norm != "system" && norm in settings.ignoredUsers
    }

    private suspend fun maybeNotify(
        channelId: String,
        messageEntity: MessageEntity,
        isPublic: Boolean,
        isHighlighted: Boolean,
        settings: AppSettings
    ) {
        if (isSelfSender(messageEntity.senderName, messageEntity.senderId, settings)) return
        if (isSenderIgnored(messageEntity.senderName, settings)) return
        if (_activeChannelId.value == channelId) return
        val channelEntity = channelDao.getChannelById(channelId) ?: return
        val mutedSet = prefs.mutedChannels.first()
        val effectiveMode = resolveChannelNotificationMode(channelEntity, mutedSet)
        if (effectiveMode == ChannelNotificationMode.MUTED) return

        val shouldNotify = when (effectiveMode) {
            ChannelNotificationMode.MUTED -> false
            ChannelNotificationMode.ALL -> if (isPublic) true else settings.notifyDirectMessages
            ChannelNotificationMode.MENTIONS_ONLY -> if (isPublic) {
                settings.notifyKeywordMentions && isHighlighted
            } else {
                settings.notifyDirectMessages && isHighlighted
            }
        }
        if (!shouldNotify) return

        val channelAlias = if (channelEntity.type == ChannelType.DIRECT.name) {
            userAliasDao.getAlias(BeatmapUrlParser.normalizeUsername(channelEntity.name))
                ?.customNickname?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val senderAlias = userAliasDao.getAlias(
            BeatmapUrlParser.normalizeUsername(messageEntity.senderName)
        )?.customNickname?.takeIf { it.isNotBlank() }

        notificationManager.showIncomingMessageNotification(
            channel = channelEntity.toDomain(channelAlias, mutedSet),
            message = messageEntity.toDomain(senderAlias),
            selfUsername = settings.username,
            highlightKeywords = settings.highlightKeywords
        )
    }

    private fun isEntityInMutedSet(entity: ChannelEntity, mutedChannels: Set<String>): Boolean {
        if (mutedChannels.isEmpty()) return false
        val normName = entity.name.trim().lowercase(Locale.ROOT)
        val normId = entity.id.trim().lowercase(Locale.ROOT)
        if (normName in mutedChannels || normId in mutedChannels) return true
        if (entity.type == ChannelType.PUBLIC.name) {
            val bare = normName.removePrefix("#")
            if (bare.isNotEmpty() && (bare in mutedChannels || "#$bare" in mutedChannels)) {
                return true
            }
        }
        return false
    }

    private fun resolveChannelNotificationMode(
        entity: ChannelEntity,
        mutedChannels: Set<String> = emptySet()
    ): ChannelNotificationMode {
        if (isEntityInMutedSet(entity, mutedChannels)) {
            return ChannelNotificationMode.MUTED
        }
        val parsed = entity.notificationMode?.let { raw ->
            runCatching { ChannelNotificationMode.valueOf(raw) }.getOrNull()
        }
        return parsed ?: if (
            entity.type == ChannelType.DIRECT.name ||
            OfficialOsuChannels.isMultiplayerRoom(entity.name) ||
            OfficialOsuChannels.isMultiplayerRoom(entity.id)
        ) {
            ChannelNotificationMode.ALL
        } else {
            ChannelNotificationMode.MENTIONS_ONLY
        }
    }

    private fun isMessageHighlighted(content: String, settings: AppSettings): Boolean {
        if (content.isBlank()) return false
        val terms = buildList {
            listOf(settings.username, settings.ircUsername).forEach { raw ->
                val clean = raw.trim()
                if (clean.isNotBlank()) {
                    add(clean)
                    add(clean.replace(' ', '_'))
                    add(clean.replace('_', ' '))
                }
            }
            addAll(settings.highlightKeywords)
        }.distinct()

        return terms.any { kw ->
            kw.isNotBlank() && content.contains(kw.trim(), ignoreCase = true)
        }
    }

    private suspend fun getOrGuessAvatarForUser(username: String): String? {
        val norm = BeatmapUrlParser.normalizeUsername(username)
        val cached = playerProfileCacheDao.getByUsername(norm)
        if (cached != null) return cached.avatarUrl
        return null
    }

    private suspend fun cacheUserProfile(
        dto: OsuUserDto,
        syncFriendsFlow: Boolean = true
    ): PlayerProfileCacheEntity {
        val safeUsername = dto.username?.takeIf { it.isNotBlank() } ?: "User ${dto.id}"
        val norm = BeatmapUrlParser.normalizeUsername(safeUsername)
        if (dto.id > 0L && !dto.userAchievements.isNullOrEmpty()) {
            userAchievementsCache[dto.id] = dto.userAchievements.mapNotNull { ach ->
                if (ach.achievementId > 0) {
                    OsuUserMedalUnlock(
                        achievementId = ach.achievementId,
                        achievedAt = ach.achievedAt.orEmpty()
                    )
                } else {
                    null
                }
            }
        }
        val histMode = dto.rankHistory?.mode?.trim()?.lowercase(Locale.US)?.takeIf { it in VALID_OSU_MODES }
            ?: dto.playmode?.trim()?.lowercase(Locale.US)?.takeIf { it in VALID_OSU_MODES }
            ?: "osu"
        cacheUserRankHistory(dto.id, histMode, dto.rankHistory?.data)
        recordChatUserColor(
            username = safeUsername,
            profileColour = dto.profileColour,
            profileHue = dto.profileHue,
            isSupporter = dto.isSupporter
        )
        val entity = PlayerProfileCacheEntity(
            normalizedUsername = norm,
            userId = dto.id,
            username = safeUsername,
            avatarUrl = dto.avatarUrl ?: "https://a.ppy.sh/${dto.id}",
            coverUrl = dto.cover?.customUrl ?: dto.cover?.url ?: dto.coverUrl,
            countryCode = dto.country?.code ?: dto.countryCode ?: "XX",
            countryName = dto.country?.name,
            globalRank = dto.statistics?.globalRank,
            countryRank = dto.statistics?.countryRank ?: dto.statistics?.rank?.country,
            pp = dto.statistics?.pp,
            accuracy = dto.statistics?.hitAccuracy,
            playCount = dto.statistics?.playCount,
            isOnline = dto.isOnline ?: false,
            currentAction = null,
            cachedAt = System.currentTimeMillis()
        )
        playerProfileCacheDao.upsert(entity)
        if (syncFriendsFlow) {
            val currentFriends = _apiFriends.value
            val idx = currentFriends.indexOfFirst {
                (dto.id > 0L && it.id == dto.id) ||
                    BeatmapUrlParser.normalizeUsername(it.username.orEmpty()) == norm
            }
            if (idx >= 0) {
                val existing = currentFriends[idx]
                val updatedDto = existing.copy(
                    username = dto.username?.takeIf { it.isNotBlank() } ?: existing.username,
                    avatarUrl = dto.avatarUrl ?: existing.avatarUrl,
                    isOnline = dto.isOnline ?: existing.isOnline,
                    isMutual = dto.isMutual ?: existing.isMutual,
                    lastVisit = dto.lastVisit ?: existing.lastVisit,
                    statistics = dto.statistics ?: existing.statistics
                )
                val updatedList = currentFriends.toMutableList().apply { set(idx, updatedDto) }
                _apiFriends.value = updatedList
            }
        }
        return entity
    }

    private suspend fun isSelfSender(
        senderName: String,
        senderId: Long = 0L,
        settings: AppSettings
    ): Boolean {
        if (BeatmapUrlParser.isSelfOsuSender(
                senderName = senderName,
                senderId = senderId,
                currentUserId = settings.userId,
                currentUsername = settings.username,
                currentIrcUsername = settings.ircUsername
            )
        ) {
            return true
        }
        val ircCredUser = prefs.getIrcCredentials()?.username.orEmpty()
        return ircCredUser.isNotBlank() && BeatmapUrlParser.isSelfOsuSender(
            senderName = senderName,
            senderId = senderId,
            currentUserId = settings.userId,
            currentUsername = ircCredUser
        )
    }

    private suspend fun consumeRecentLocalOutgoingDuplicate(
        channelId: String,
        rawContent: String,
        referenceTimestamp: Long
    ): Boolean {
        val clean = rawContent.trim()
        if (clean.isEmpty()) return false
        val now = System.currentTimeMillis()
        recentLocalOutgoing.removeIf { (now - it.sentAt) > 10_000L }

        val match = recentLocalOutgoing.firstOrNull { entry ->
            entry.channelId.equals(channelId, ignoreCase = true) &&
                entry.trimmedContent == clean &&
                (abs(now - entry.sentAt) <= 5_000L || abs(referenceTimestamp - entry.sentAt) <= 5_000L)
        }
        if (match != null) {
            recentLocalOutgoing.remove(match)
            runCatching { messageDao.updateDeliveryStatus(match.localMessageId, DeliveryStatus.SENT.name) }
            return true
        }

        val recentDbMatch = runCatching {
            messageDao.getAllMessagesForChannel(channelId)
                .takeLast(10)
                .lastOrNull { row ->
                    row.id.startsWith("local_") &&
                        row.isOutgoing &&
                        row.content.trim() == clean &&
                        (abs(now - row.timestamp) <= 5_000L || abs(referenceTimestamp - row.timestamp) <= 5_000L)
                }
        }.getOrNull()
        if (recentDbMatch != null) {
            runCatching { messageDao.updateDeliveryStatus(recentDbMatch.id, DeliveryStatus.SENT.name) }
            return true
        }
        return false
    }

    private fun recordChatUserColor(
        username: String?,
        profileColour: String?,
        profileHue: Int?,
        isSupporter: Boolean,
        aliasKey: String? = null
    ) {
        val norm = BeatmapUrlParser.normalizeOsuNick(username.orEmpty())
        if (norm.isBlank() || norm == "banchobot" || norm == "system") return
        val info = ChatUserColorInfo(
            profileColourHex = profileColour?.trim()?.takeIf { it.isNotEmpty() },
            profileHue = profileHue,
            isSupporter = isSupporter
        )
        _chatUserColors.update { current ->
            val updated = current.toMutableMap()
            updated[norm] = info
            val normAlias = aliasKey?.let(BeatmapUrlParser::normalizeOsuNick).orEmpty()
            if (normAlias.isNotBlank() && normAlias != norm) {
                updated[normAlias] = info
            }
            updated
        }
        failedColorLookupAt.remove(norm)
    }

    override suspend fun prefetchChatUserColors(usernames: Collection<String>) {
        if (usernames.isEmpty()) return
        val currentSettings = prefs.settingsFlow.first()
        if (currentSettings.authMode != AuthMode.OAUTH_V2) return
        apiPoller.getValidBearerToken() ?: return

        val now = System.currentTimeMillis()
        val existingColors = _chatUserColors.value
        val candidates = linkedMapOf<String, String>()
        for (raw in usernames) {
            val trimmed = raw.trim()
            val norm = BeatmapUrlParser.normalizeOsuNick(trimmed)
            if (norm.isBlank() || norm == "banchobot" || norm == "system") continue
            if (norm in existingColors) continue
            val failedAt = failedColorLookupAt[norm]
            if (failedAt != null && (now - failedAt) < 10L * 60L * 1000L) continue
            candidates.putIfAbsent(norm, trimmed)
        }
        if (candidates.isEmpty()) return

        for ((norm, rawName) in candidates) {
            if (!inFlightColorLookups.add(norm)) continue
            scope.launch(Dispatchers.IO) {
                try {
                    colorLookupSemaphore.withPermit {
                        if (norm in _chatUserColors.value) return@withPermit
                        if (apiPoller.isRateLimited()) return@withPermit
                        val dto = apiPoller.coalesceBearerGet(
                            key = "user:$norm:default",
                            minIntervalMs = 300L
                        ) { activeToken ->
                            apiService.getUser(activeToken, rawName, "username")
                        }
                        cacheUserProfile(dto, syncFriendsFlow = false)
                        recordChatUserColor(
                            username = dto.username ?: rawName,
                            profileColour = dto.profileColour,
                            profileHue = dto.profileHue,
                            isSupporter = dto.isSupporter,
                            aliasKey = norm
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    failedColorLookupAt[norm] = System.currentTimeMillis()
                } finally {
                    inFlightColorLookups.remove(norm)
                }
            }
        }
    }

    private fun resolveFriendStatus(
        userId: Long,
        username: String,
        dtoIsMutual: Boolean? = null
    ): FriendRelationStatus {
        val norm = BeatmapUrlParser.normalizeUsername(username)
        val match = _apiFriends.value.firstOrNull {
            (userId > 0L && it.id == userId) ||
                (norm.isNotBlank() && BeatmapUrlParser.normalizeUsername(it.username.orEmpty()) == norm)
        }
        return when {
            dtoIsMutual == true -> FriendRelationStatus.MUTUAL
            match != null -> if ((dtoIsMutual ?: match.isMutual) == true) {
                FriendRelationStatus.MUTUAL
            } else {
                FriendRelationStatus.FRIEND
            }
            else -> FriendRelationStatus.NONE
        }
    }

    private fun ChannelEntity.toDomain(
        customAlias: String?,
        mutedChannels: Set<String> = emptySet()
    ): ChatChannel {
        return ChatChannel(
            id = id,
            name = name,
            description = description,
            type = if (type == ChannelType.DIRECT.name) ChannelType.DIRECT else ChannelType.PUBLIC,
            unreadCount = unreadCount,
            lastMessageText = lastMessageText,
            lastMessageTimestamp = lastMessageTimestamp,
            lastSenderName = lastSenderName,
            targetUserId = targetUserId,
            avatarUrl = avatarUrl,
            isJoined = isJoined,
            customAlias = customAlias,
            notificationMode = resolveChannelNotificationMode(this, mutedChannels),
            isPinned = isPinned
        )
    }

    private fun MessageEntity.toDomain(senderAlias: String?): ChatMessage {
        val status = runCatching { DeliveryStatus.valueOf(deliveryStatus) }
            .getOrDefault(DeliveryStatus.SENT)
        val beatmaps = BeatmapUrlParser.extractBeatmapLinks(content)
        val users = BeatmapUrlParser.extractUserLinks(content)
        val embeds = BeatmapUrlParser.extractOrderedEmbeds(content)
        val images = BeatmapUrlParser.extractImageUrls(content)
        return ChatMessage(
            id = id,
            channelId = channelId,
            senderId = senderId,
            senderName = senderName,
            senderAvatarUrl = senderAvatarUrl,
            senderAlias = senderAlias,
            content = content,
            timestamp = timestamp,
            isOutgoing = isOutgoing,
            isAction = isAction,
            isHighlighted = isHighlighted,
            deliveryStatus = status,
            beatmapUrls = beatmaps,
            userUrls = users,
            inlineEmbeds = embeds,
            imageUrls = images
        )
    }

    private fun cacheUserRankHistory(userId: Long, mode: String, data: List<Int>?) {
        if (userId <= 0L || data.isNullOrEmpty()) return
        val cleanMode = mode.trim().lowercase(Locale.US).takeIf { it in VALID_OSU_MODES } ?: "osu"
        if (userRankHistoryCache.size > 512) {
            userRankHistoryCache.clear()
        }
        userRankHistoryCache["$userId:$cleanMode"] = data.toList()
    }

    private fun PlayerProfileCacheEntity.toDomain(
        customAlias: String?,
        isTrackedForPp: Boolean = false,
        friendStatus: FriendRelationStatus = FriendRelationStatus.NONE,
        mode: String = "osu",
        customNote: String? = null,
        notifyOnline: Boolean = false
    ): PlayerProfile {
        val cleanMode = mode.trim().lowercase(Locale.US).takeIf { it in VALID_OSU_MODES } ?: "osu"
        return PlayerProfile(
            userId = userId,
            username = username,
            customAlias = customAlias?.takeIf { it.isNotBlank() },
            customNote = customNote?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            countryCode = countryCode,
            countryName = countryName,
            globalRank = globalRank,
            countryRank = countryRank,
            pp = pp,
            accuracy = accuracy,
            playCount = playCount,
            isOnline = isOnline,
            currentAction = currentAction,
            isTrackedForPp = isTrackedForPp,
            notifyOnline = notifyOnline,
            friendStatus = friendStatus,
            mode = mode,
            userAchievements = userAchievementsCache[userId].orEmpty(),
            rankHistory = userRankHistoryCache["$userId:$cleanMode"].orEmpty()
        )
    }

    private fun SelfProfileCacheEntity.toDomain(
        customAlias: String? = null,
        isTrackedForPp: Boolean = false,
        friendStatus: FriendRelationStatus = FriendRelationStatus.NONE,
        customNote: String? = null,
        notifyOnline: Boolean = false
    ): PlayerProfile {
        val cleanMode = mode.takeIf { it in VALID_OSU_MODES } ?: "osu"
        return PlayerProfile(
            userId = userId,
            username = username,
            customAlias = customAlias?.takeIf { it.isNotBlank() },
            customNote = customNote?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            countryCode = countryCode,
            countryName = countryName,
            globalRank = globalRank,
            countryRank = countryRank,
            pp = pp,
            accuracy = accuracy,
            playCount = playCount,
            isOnline = isOnline,
            currentAction = currentAction,
            isTrackedForPp = isTrackedForPp,
            notifyOnline = notifyOnline,
            friendStatus = friendStatus,
            mode = cleanMode,
            userAchievements = userAchievementsCache[userId].orEmpty(),
            rankHistory = userRankHistoryCache["$userId:$cleanMode"].orEmpty()
        )
    }

    private fun PlayerProfile.toSelfCacheEntity(): SelfProfileCacheEntity {
        return SelfProfileCacheEntity(
            cacheKey = "self",
            userId = userId,
            username = username,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            countryCode = countryCode,
            countryName = countryName,
            globalRank = globalRank,
            countryRank = countryRank,
            pp = pp,
            accuracy = accuracy,
            playCount = playCount,
            isOnline = isOnline,
            currentAction = currentAction,
            mode = mode.takeIf { it in VALID_OSU_MODES } ?: "osu",
            cachedAt = System.currentTimeMillis()
        )
    }

    private fun OsuUserDto.toProfileDomain(
        customAlias: String?,
        isTrackedForPp: Boolean,
        friendStatus: FriendRelationStatus,
        mode: String,
        customNote: String? = null,
        notifyOnline: Boolean = false
    ): PlayerProfile {
        val mappedAchievements = if (!userAchievements.isNullOrEmpty()) {
            val list = userAchievements.mapNotNull { ach ->
                if (ach.achievementId > 0) {
                    OsuUserMedalUnlock(
                        achievementId = ach.achievementId,
                        achievedAt = ach.achievedAt.orEmpty()
                    )
                } else {
                    null
                }
            }
            if (id > 0L) userAchievementsCache[id] = list
            list
        } else {
            userAchievementsCache[id].orEmpty()
        }
        val cleanMode = mode.trim().lowercase(Locale.US).takeIf { it in VALID_OSU_MODES } ?: "osu"
        val mappedRankHistory = if (!rankHistory?.data.isNullOrEmpty()) {
            cacheUserRankHistory(id, cleanMode, rankHistory.data)
            rankHistory.data
        } else {
            userRankHistoryCache["$id:$cleanMode"].orEmpty()
        }
        return PlayerProfile(
            userId = id,
            username = username?.takeIf { it.isNotBlank() } ?: "User $id",
            customAlias = customAlias?.takeIf { it.isNotBlank() },
            customNote = customNote?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl ?: "https://a.ppy.sh/$id",
            coverUrl = cover?.customUrl ?: cover?.url ?: coverUrl,
            countryCode = country?.code ?: countryCode ?: "XX",
            countryName = country?.name,
            globalRank = statistics?.globalRank,
            countryRank = statistics?.countryRank ?: statistics?.rank?.country,
            pp = statistics?.pp,
            accuracy = statistics?.hitAccuracy,
            playCount = statistics?.playCount,
            isOnline = isOnline ?: false,
            currentAction = null,
            isTrackedForPp = isTrackedForPp,
            notifyOnline = notifyOnline,
            friendStatus = friendStatus,
            mode = mode,
            userAchievements = mappedAchievements,
            rankHistory = mappedRankHistory
        )
    }

    private fun OsuScoreDto.toDomain(defaultUsername: String): OsuScoreItem {
        val setId = beatmapset?.id ?: beatmap?.beatmapsetId
        val cover = beatmapset?.covers?.card
            ?: beatmapset?.covers?.cover
            ?: beatmap?.beatmapset?.covers?.card
            ?: if (setId != null) "https://assets.ppy.sh/beatmaps/$setId/covers/card.jpg" else ""
        val resolvedUserId = user?.id ?: userId
        val accPct = if (accuracy <= 1.0) accuracy * 100.0 else accuracy
        val resolvedMode = beatmap?.mode?.takeIf { it.isNotBlank() } ?: when (rulesetId) {
            0 -> "osu"
            1 -> "taiko"
            2 -> "fruits"
            3 -> "mania"
            else -> "osu"
        }
        return OsuScoreItem(
            id = id,
            userId = resolvedUserId,
            username = user?.username?.takeIf { it.isNotBlank() } ?: defaultUsername,
            rank = rank.orEmpty(),
            pp = pp,
            accuracy = accPct,
            maxCombo = maxCombo,
            mods = mods.orEmpty(),
            score = if (score > 0L) score else (totalScore ?: classicTotalScore ?: 0L),
            passed = passed,
            createdAt = createdAt ?: endedAt.orEmpty(),
            beatmapId = beatmap?.id,
            beatmapsetId = setId,
            beatmapTitle = beatmapset?.title ?: beatmap?.beatmapset?.title ?: "Unknown Beatmap",
            beatmapArtist = beatmapset?.artist ?: beatmap?.beatmapset?.artist ?: "",
            beatmapVersion = beatmap?.version.orEmpty(),
            starRating = beatmap?.difficultyRating ?: 0.0,
            coverUrl = cover,
            mode = resolvedMode,
            userAvatarUrl = user?.avatarUrl ?: resolvedUserId.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" },
            userCountryCode = user?.country?.code ?: user?.countryCode ?: "XX",
            countMiss = statistics?.resolvedCountMiss ?: 0,
            beatmapMaxCombo = beatmap?.maxCombo,
            beatmapLengthSeconds = beatmap?.totalLength ?: 0,
            count300 = statistics?.resolvedCount300 ?: 0,
            count100 = statistics?.resolvedCount100 ?: 0,
            count50 = statistics?.resolvedCount50 ?: 0,
            bpm = beatmap?.bpm ?: beatmapset?.bpm ?: beatmap?.beatmapset?.bpm ?: 0.0,
            legacyScoreId = legacyScoreId,
            buildId = buildId
        )
    }

    private fun OsuBeatmapsetDto.toSearchResult(): BeatmapSetSearchResult {
        val cover = covers?.card ?: covers?.cover
            ?: "https://assets.ppy.sh/beatmaps/$id/covers/card.jpg"
        val primaryId = beatmaps?.maxByOrNull { it.difficultyRating }?.id ?: 0L
        val preview = apiPoller.buildAudioPreviewFallbackUrls(
            beatmapsetId = id,
            beatmapId = primaryId,
            primaryUrl = previewUrl
        ).firstOrNull() ?: "https://b.ppy.sh/preview/$id.mp3"
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Beatmapset #$id"
        val safeArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val safeCreator = creator?.takeIf { it.isNotBlank() } ?: "Unknown Mapper"
        val diffs = beatmaps.orEmpty().map { bm ->
            bm.copy(
                beatmapset = bm.beatmapset ?: com.osuchat.app.data.network.OsuBeatmapsetSummaryDto(
                    id = id,
                    title = safeTitle,
                    artist = safeArtist,
                    creator = safeCreator,
                    status = status,
                    bpm = bpm,
                    playCount = playCount,
                    favouriteCount = favouriteCount,
                    previewUrl = previewUrl,
                    covers = covers
                )
            ).toMetadata()
        }.sortedBy { it.starRating }
        val primary = diffs.maxByOrNull { it.starRating }
        return BeatmapSetSearchResult(
            beatmapsetId = id,
            primaryBeatmapId = primary?.beatmapId ?: 0L,
            title = safeTitle,
            artist = safeArtist,
            creator = safeCreator,
            status = status ?: "unknown",
            bpm = bpm,
            playCount = playCount,
            favouriteCount = favouriteCount,
            coverUrl = cover,
            previewAudioUrl = preview,
            difficulties = diffs
        )
    }

    private fun OsuBeatmapDto.toMetadata(): BeatmapMetadata {
        val setId = beatmapset?.id ?: beatmapsetId
        val cover = beatmapset?.covers?.card
            ?: beatmapset?.covers?.cover
            ?: "https://assets.ppy.sh/beatmaps/$setId/covers/card.jpg"
        val preview = apiPoller.buildAudioPreviewFallbackUrls(
            beatmapsetId = setId,
            beatmapId = id,
            primaryUrl = beatmapset?.previewUrl
        ).firstOrNull() ?: "https://b.ppy.sh/preview/$setId.mp3"

        return BeatmapMetadata(
            beatmapId = id,
            beatmapsetId = setId,
            title = beatmapset?.title ?: "Beatmap #$id",
            artist = beatmapset?.artist ?: "Unknown Artist",
            creator = beatmapset?.creator ?: "Unknown Mapper",
            version = version.orEmpty(),
            starRating = difficultyRating,
            bpm = bpm ?: beatmapset?.bpm ?: 0.0,
            totalLengthSeconds = totalLength,
            ar = ar,
            cs = cs,
            od = od,
            hp = hp,
            drainLengthSeconds = hitLength,
            maxCombo = maxCombo,
            status = status ?: beatmapset?.status ?: "ranked",
            playCount = playCount ?: beatmapset?.playCount,
            favouriteCount = beatmapset?.favouriteCount,
            coverUrl = cover,
            previewAudioUrl = preview,
            mode = mode ?: "osu"
        )
    }

    private fun OsuBeatmapsetDto.toMetadata(): BeatmapMetadata {
        val primary = beatmaps?.maxByOrNull { it.difficultyRating }
        val cover = covers?.card ?: covers?.cover
            ?: "https://assets.ppy.sh/beatmaps/$id/covers/card.jpg"
        val preview = apiPoller.buildAudioPreviewFallbackUrls(
            beatmapsetId = id,
            beatmapId = primary?.id ?: 0L,
            primaryUrl = previewUrl
        ).firstOrNull() ?: "https://b.ppy.sh/preview/$id.mp3"

        return BeatmapMetadata(
            beatmapId = primary?.id ?: 0L,
            beatmapsetId = id,
            title = title?.takeIf { it.isNotBlank() } ?: "Beatmapset #$id",
            artist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            creator = creator?.takeIf { it.isNotBlank() } ?: "Unknown Mapper",
            version = primary?.version ?: "Beatmapset",
            starRating = primary?.difficultyRating ?: 0.0,
            bpm = primary?.bpm ?: bpm,
            totalLengthSeconds = primary?.totalLength ?: 0,
            ar = primary?.ar ?: 0.0,
            cs = primary?.cs ?: 0.0,
            od = primary?.od ?: 0.0,
            hp = primary?.hp ?: 0.0,
            drainLengthSeconds = primary?.hitLength ?: 0,
            maxCombo = primary?.maxCombo,
            status = status ?: primary?.status ?: "ranked",
            playCount = playCount ?: primary?.playCount,
            favouriteCount = favouriteCount,
            coverUrl = cover,
            previewAudioUrl = preview,
            mode = primary?.mode ?: "osu"
        )
    }

    private fun BeatmapCacheEntity.toDomain(): BeatmapMetadata {
        return BeatmapMetadata(
            beatmapId = beatmapId,
            beatmapsetId = beatmapsetId,
            title = title,
            artist = artist,
            creator = creator,
            version = version,
            starRating = starRating,
            bpm = bpm,
            totalLengthSeconds = totalLengthSeconds,
            ar = ar,
            cs = cs,
            od = od,
            coverUrl = coverUrl,
            previewAudioUrl = previewAudioUrl,
            mode = mode
        )
    }

    private fun BeatmapMetadata.toEntity(key: String): BeatmapCacheEntity {
        return BeatmapCacheEntity(
            cacheKey = key,
            beatmapId = beatmapId,
            beatmapsetId = beatmapsetId,
            title = title,
            artist = artist,
            creator = creator,
            version = version,
            starRating = starRating,
            bpm = bpm,
            totalLengthSeconds = totalLengthSeconds,
            ar = ar,
            cs = cs,
            od = od,
            coverUrl = coverUrl,
            previewAudioUrl = previewAudioUrl,
            mode = mode,
            cachedAt = System.currentTimeMillis()
        )
    }

    private fun normalizeChannelId(channelId: String): String {
        val trimmed = channelId.trim()
        return when {
            trimmed.startsWith("#") -> trimmed.lowercase()
            trimmed.startsWith("dm:", ignoreCase = true) ->
                "dm:${BeatmapUrlParser.normalizeUsername(trimmed.substring(3))}"
            trimmed.startsWith("dm_", ignoreCase = true) ->
                "dm:${BeatmapUrlParser.normalizeUsername(trimmed.substring(3))}"
            else -> trimmed.lowercase()
        }
    }

    private fun recomputeOnlineIrcNicks() {
        val online = HashSet<String>(_seenOnlineSenders.size + 64)
        online.addAll(_seenOnlineSenders)
        channelUsersState.values.forEach { flow ->
            flow.value.forEach { nick ->
                online.add(BeatmapUrlParser.normalizeUsername(nick))
            }
        }
        _onlineIrcNicks.value = online
    }

    private fun formatLastVisit(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val instant = Instant.parse(raw.trim())
            val mins = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0L)
            when {
                mins < 2L -> "just now"
                mins < 60L -> "${mins}m ago"
                mins < 1440L -> "${mins / 60}h ago"
                else -> "${mins / 1440}d ago"
            }
        }.getOrNull()
    }

    companion object {
        private const val FRIEND_ONLINE_ALERT_COOLDOWN_MS = 15 * 60_000L
        private val DEFAULT_PUBLIC_CHANNELS = listOf("#osu", "#lobby", "#announce")
        private val VALID_OSU_MODES = setOf("osu", "taiko", "fruits", "mania")
        private val HTML_STRIP_BLOCK_REGEX = Regex(
            """<(script|style)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val VALID_TOP_MOD_CODES = setOf(
            "NF", "EZ", "TD", "HD", "HR", "SD", "DT", "RX", "HT", "NC", "FL", "SO", "AP", "PF", "CL"
        )
        private val KNOWN_TOP_SINGLE_MOD_COMBOS = setOf(
            "NM", "HD", "HR", "DT", "NC", "EZ", "FL", "HT", "NF", "SO", "SD", "PF",
            "HDHR", "HDDT", "HDNC", "DTHR", "NCHR", "HDDTHR", "HDNCHR",
            "EZHD", "EZDT", "EZHT", "HDHT", "HRHT", "HDFL", "HRFL", "DTFL", "HDDTFL", "HDHRFL"
        )
    }
}
