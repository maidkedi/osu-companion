package com.osuchat.app.ui.screens.match

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.FriendRelationStatus
import com.osuchat.app.domain.model.MatchAnalysisGame
import com.osuchat.app.domain.model.MatchAnalysisReport
import com.osuchat.app.domain.model.MatchComputedAnalytics
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.model.SavedBeatmapItem
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.MatchAnalyzerCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MatchAnalyzerTab(val label: String) {
    PLAYERS("Players"),
    MAPS("Maps")
}

private data class MatchAnalyzerLocalState(
    val activeTab: MatchAnalyzerTab = MatchAnalyzerTab.PLAYERS,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val report: MatchAnalysisReport? = null,
    val warmupGameIds: Set<Long> = emptySet(),
    val selectedWarmupPreset: Int? = 0,
    val expandedGameIds: Set<Long> = emptySet(),
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
    val selectedBeatmapSet: BeatmapSetSearchResult? = null
)

@Immutable
data class MatchAnalyzerUiState(
    val matchId: Long = 0L,
    val activeTab: MatchAnalyzerTab = MatchAnalyzerTab.PLAYERS,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val analytics: MatchComputedAnalytics? = null,
    val warmupGameIds: Set<Long> = emptySet(),
    val selectedWarmupPreset: Int? = 0,
    val expandedGameIds: Set<Long> = emptySet(),
    val selfUsername: String = "",
    val ignoredUsers: Set<String> = emptySet(),
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
    val selectedBeatmapSet: BeatmapSetSearchResult? = null,
    val savedBeatmapIds: Set<Long> = emptySet(),
    val trackedAlertConfigs: Map<String, TrackedPlayerAlertConfig> = emptyMap(),
    val defaultTrackedTopPlayLimit: Int = TrackedPlayerAlertConfig.DEFAULT_TOP_PLAY_LIMIT
)

sealed class MatchAnalyzerEffect {
    data class ShowMessage(val message: String) : MatchAnalyzerEffect()
    data class OpenDirectMessageChannel(val channelId: String) : MatchAnalyzerEffect()
}

@HiltViewModel
class MatchAnalyzerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val matchId: Long = savedStateHandle.get<Long>("matchId")
        ?: savedStateHandle.get<String>("matchId")?.toLongOrNull()
        ?: 0L

    private val localState = MutableStateFlow(MatchAnalyzerLocalState())
    private var loadJob: Job? = null
    private var profileJob: Job? = null
    private var selectedOsuTrackJob: Job? = null
    private var loadMoreScoresJob: Job? = null
    private var profileRequestSeq = 0L
    private var selectedOsuTrackSeq = 0L

    private val _effects = MutableSharedFlow<MatchAnalyzerEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<MatchAnalyzerEffect> = _effects.asSharedFlow()

    val uiState: StateFlow<MatchAnalyzerUiState> = combine(
        localState,
        chatRepository.settings,
        chatRepository.savedBeatmaps.map { items -> items.map { it.beatmapId }.toSet() },
        chatRepository.trackedAlertConfigs
    ) { local, settings, savedIds, trackedAlertConfigs ->
        val report = local.report
        val computed = report?.let {
            MatchAnalyzerCalculator.compute(it, local.warmupGameIds)
        }
        MatchAnalyzerUiState(
            matchId = matchId,
            activeTab = local.activeTab,
            isLoading = local.isLoading,
            errorMessage = local.errorMessage,
            analytics = computed,
            warmupGameIds = local.warmupGameIds,
            selectedWarmupPreset = local.selectedWarmupPreset,
            expandedGameIds = local.expandedGameIds,
            selfUsername = settings.username,
            ignoredUsers = settings.ignoredUsers,
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
            selectedBeatmapSet = local.selectedBeatmapSet,
            savedBeatmapIds = savedIds,
            trackedAlertConfigs = trackedAlertConfigs,
            defaultTrackedTopPlayLimit = settings.defaultTrackedTopPlayLimit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MatchAnalyzerUiState(matchId = matchId))

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            localState.update { it.copy(isLoading = true, errorMessage = null) }
            chatRepository.fetchFullMatchAnalysis(matchId)
                .onSuccess { report ->
                    localState.update { current ->
                        val nextWarmups = if (current.selectedWarmupPreset != null) {
                            MatchAnalyzerCalculator.warmupIdsForSkipCount(report, current.selectedWarmupPreset)
                        } else {
                            val validIds = report.games.map { it.gameId }.toSet()
                            current.warmupGameIds.intersect(validIds)
                        }
                        current.copy(
                            isLoading = false,
                            errorMessage = null,
                            report = report,
                            warmupGameIds = nextWarmups
                        )
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    localState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message?.takeIf { msg -> msg.isNotBlank() }
                                ?: "Could not load match #$matchId."
                        )
                    }
                }
        }
    }

    fun selectTab(tab: MatchAnalyzerTab) {
        localState.update { it.copy(activeTab = tab) }
    }

    fun applyWarmupSkipPreset(skipCount: Int) {
        localState.update { current ->
            val report = current.report
            val warmups = if (report != null) {
                MatchAnalyzerCalculator.warmupIdsForSkipCount(report, skipCount)
            } else {
                emptySet()
            }
            current.copy(
                selectedWarmupPreset = skipCount,
                warmupGameIds = warmups
            )
        }
    }

    fun toggleGameWarmup(gameId: Long) {
        localState.update { current ->
            val next = if (gameId in current.warmupGameIds) {
                current.warmupGameIds - gameId
            } else {
                current.warmupGameIds + gameId
            }
            current.copy(
                selectedWarmupPreset = null,
                warmupGameIds = next
            )
        }
    }

    fun toggleGameExpanded(gameId: Long) {
        localState.update { current ->
            val next = if (gameId in current.expandedGameIds) {
                current.expandedGameIds - gameId
            } else {
                current.expandedGameIds + gameId
            }
            current.copy(expandedGameIds = next)
        }
    }

    fun openBeatmapDetails(game: MatchAnalysisGame) {
        val bid = game.beatmapId ?: return
        val setId = game.beatmapsetId ?: 0L
        val summaryMeta = BeatmapMetadata(
            beatmapId = bid,
            beatmapsetId = setId,
            title = game.beatmapTitle,
            artist = game.beatmapArtist,
            creator = game.beatmapCreator,
            version = game.beatmapVersion,
            starRating = game.starRating,
            bpm = game.bpm,
            totalLengthSeconds = game.totalLengthSeconds,
            ar = game.ar,
            cs = game.cs,
            od = game.od,
            hp = game.hp,
            coverUrl = game.coverUrl,
            previewAudioUrl = game.previewAudioUrl,
            mode = game.mode
        )
        localState.update {
            it.copy(
                selectedBeatmapSet = BeatmapSetSearchResult(
                    beatmapsetId = setId,
                    primaryBeatmapId = bid,
                    title = game.beatmapTitle,
                    artist = game.beatmapArtist,
                    creator = game.beatmapCreator,
                    status = "ranked",
                    bpm = game.bpm,
                    coverUrl = game.coverUrl,
                    previewAudioUrl = game.previewAudioUrl,
                    difficulties = listOf(summaryMeta)
                )
            )
        }
    }

    fun dismissBeatmapDetails() {
        localState.update { it.copy(selectedBeatmapSet = null) }
    }

    suspend fun resolveBeatmapDetails(beatmapId: Long): Result<BeatmapMetadata> {
        return chatRepository.getBeatmapDetails(beatmapId)
    }

    suspend fun loadBeatmapScores(beatmapId: Long, mode: String?): Result<List<OsuScoreItem>> {
        return chatRepository.getBeatmapScores(beatmapId, mode)
    }

    fun toggleSaveBeatmap(item: SavedBeatmapItem) {
        viewModelScope.launch {
            chatRepository.toggleSaveBeatmap(item)
                .onSuccess { saved ->
                    _effects.emit(
                        MatchAnalyzerEffect.ShowMessage(
                            if (saved) "Saved to Play Later" else "Removed from Play Later"
                        )
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(MatchAnalyzerEffect.ShowMessage("Could not update Play Later."))
                }
        }
    }

    fun notifyMessage(msg: String) {
        viewModelScope.launch {
            _effects.emit(MatchAnalyzerEffect.ShowMessage(msg))
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
        profileJob?.cancel()
        selectedOsuTrackJob?.cancel()
        loadMoreScoresJob?.cancel()
        val requestSeq = ++profileRequestSeq
        selectedOsuTrackSeq++
        profileJob = viewModelScope.launch {
            localState.update {
                val sameTarget = it.selectedProfileTarget.equals(usernameOrId, ignoreCase = true)
                val sameMode = mode == null || mode.equals(it.selectedProfileMode, ignoreCase = true)
                it.copy(
                    selectedProfileTarget = usernameOrId,
                    selectedProfile = if (sameTarget) it.selectedProfile else null,
                    selectedProfileMode = mode ?: if (sameTarget) it.selectedProfileMode else "osu",
                    isProfileLoading = true,
                    isProfileScoresLoading = false,
                    isLoadingMoreBestScores = false,
                    canLoadMoreBestScores = false,
                    profileError = null,
                    selectedProfileLocalHistory = if (sameTarget && sameMode) it.selectedProfileLocalHistory else emptyList(),
                    selectedProfileOsuTrack = if (sameTarget && sameMode) it.selectedProfileOsuTrack else emptyList(),
                    isSelectedProfileOsuTrackLoading = false,
                    pinnedScores = emptyList(),
                    bestScores = emptyList(),
                    recentScores = emptyList()
                )
            }
            val profileRes = chatRepository.getPlayerProfile(usernameOrId, mode)
            if (requestSeq != profileRequestSeq) return@launch
            val profile = profileRes.getOrNull()
            if (profile == null) {
                val err = profileRes.exceptionOrNull()
                if (err is CancellationException) throw err
                localState.update {
                    if (requestSeq != profileRequestSeq) it
                    else it.copy(
                        isProfileLoading = false,
                        isProfileScoresLoading = false,
                        profileError = err?.message ?: "Could not load player profile."
                    )
                }
                return@launch
            }
            val activeMode = mode ?: profile.mode.ifBlank { "osu" }
            localState.update {
                if (requestSeq != profileRequestSeq) it
                else it.copy(
                    selectedProfile = profile,
                    selectedProfileMode = activeMode,
                    isProfileLoading = false,
                    isProfileScoresLoading = profile.userId > 0L,
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
                    mode = activeMode,
                    isSelfTarget = isSelfTarget,
                    expectedProfileSeq = requestSeq
                )
                if (mode == null && profile.rankHistory.isEmpty() && settings.authMode == AuthMode.OAUTH_V2) {
                    viewModelScope.launch {
                        chatRepository.getPlayerProfile(profile.userId.toString(), activeMode).onSuccess { fresh ->
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
                val targetId = profile.userId.toString()
                coroutineScope {
                    val pinnedDeferred = async {
                        chatRepository.getUserScores(targetId, "pinned", false, activeMode, 5)
                    }
                    val bestDeferred = async {
                        chatRepository.getUserScores(targetId, "best", false, activeMode, 100, 0)
                    }
                    val recentDeferred = async {
                        chatRepository.getUserScores(targetId, "recent", true, activeMode, 5)
                    }
                    val pinned = pinnedDeferred.await().getOrDefault(emptyList())
                    val best = bestDeferred.await().getOrDefault(emptyList())
                    val recent = recentDeferred.await().getOrDefault(emptyList())
                    if (requestSeq == profileRequestSeq) {
                        localState.update {
                            it.copy(
                                pinnedScores = pinned,
                                bestScores = best,
                                recentScores = recent,
                                isProfileScoresLoading = false,
                                canLoadMoreBestScores = best.size >= 100
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadMoreBestScores() {
        val local = localState.value
        val uid = local.selectedProfile?.userId?.takeIf { it > 0L } ?: return
        if (local.isLoadingMoreBestScores || !local.canLoadMoreBestScores) return
        val requestSeq = profileRequestSeq
        val activeMode = local.selectedProfileMode
        loadMoreScoresJob?.cancel()
        loadMoreScoresJob = viewModelScope.launch {
            localState.update { it.copy(isLoadingMoreBestScores = true) }
            chatRepository.getUserScores(uid.toString(), "best", false, activeMode, 100, 100)
                .onSuccess { pageTwo ->
                    if (requestSeq != profileRequestSeq) return@onSuccess
                    localState.update { state ->
                        val seen = state.bestScores.mapNotNull { it.id.takeIf { id -> id > 0L } }.toHashSet()
                        val merged = state.bestScores.toMutableList()
                        for (item in pageTwo) {
                            if (item.id <= 0L || seen.add(item.id)) merged.add(item)
                            if (merged.size >= 200) break
                        }
                        state.copy(
                            bestScores = merged,
                            isLoadingMoreBestScores = false,
                            canLoadMoreBestScores = false
                        )
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    if (requestSeq == profileRequestSeq) {
                        localState.update { it.copy(isLoadingMoreBestScores = false) }
                    }
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
        profileJob?.cancel()
        selectedOsuTrackJob?.cancel()
        loadMoreScoresJob?.cancel()
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

    fun saveUserAlias(username: String, alias: String?, userId: Long?, note: String? = null) {
        viewModelScope.launch {
            chatRepository.setUserAlias(username, alias, userId, note)
            val refreshed = chatRepository.getPlayerProfile(username, localState.value.selectedProfileMode).getOrNull()
            if (refreshed != null) {
                localState.update {
                    if (it.selectedProfile?.username?.equals(username, ignoreCase = true) == true) {
                        it.copy(selectedProfile = refreshed)
                    } else it
                }
            }
            _effects.emit(MatchAnalyzerEffect.ShowMessage("Player alias saved"))
        }
    }

    fun startDirectMessage(username: String) {
        viewModelScope.launch {
            chatRepository.startDirectMessage(username)
                .onSuccess { channel ->
                    dismissPlayerProfile()
                    _effects.emit(MatchAnalyzerEffect.OpenDirectMessageChannel(channel.id))
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _effects.emit(MatchAnalyzerEffect.ShowMessage("Could not open DM with $username."))
                }
        }
    }

    fun toggleFriendStatus(profile: PlayerProfile) {
        viewModelScope.launch {
            val res = if (profile.friendStatus == FriendRelationStatus.NONE) {
                chatRepository.addFriend(profile)
            } else {
                chatRepository.removeFriend(profile.userId, profile.username)
            }
            res.onSuccess { status ->
                localState.update {
                    val cur = it.selectedProfile
                    if (cur != null && cur.username.equals(profile.username, ignoreCase = true)) {
                        it.copy(selectedProfile = cur.copy(friendStatus = status))
                    } else it
                }
            }
        }
    }

    fun toggleTrackFriendPp(profile: PlayerProfile) {
        viewModelScope.launch {
            chatRepository.toggleTrackFriendPp(profile.username, profile.userId, profile.avatarUrl)
                .onSuccess { tracked ->
                    localState.update {
                        val cur = it.selectedProfile
                        if (cur != null && cur.username.equals(profile.username, ignoreCase = true)) {
                            it.copy(selectedProfile = cur.copy(isTrackedForPp = tracked))
                        } else it
                    }
                    _effects.emit(
                        MatchAnalyzerEffect.ShowMessage(
                            if (tracked) {
                                "Tracking alerts enabled for ${profile.username}"
                            } else {
                                "Stopped tracking ${profile.username}"
                            }
                        )
                    )
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
            localState.update {
                val cur = it.selectedProfile
                if (cur != null &&
                    ((userId > 0L && cur.userId == userId) ||
                        (username.isNotBlank() && cur.username.equals(username, ignoreCase = true)))
                ) {
                    it.copy(selectedProfile = cur.copy(isTrackedForPp = true))
                } else {
                    it
                }
            }
            _effects.emit(
                MatchAnalyzerEffect.ShowMessage(
                    if (username.isNotBlank()) {
                        "Tracking rules saved for $username"
                    } else {
                        "Tracked player alert rules saved"
                    }
                )
            )
        }
    }

    fun toggleIgnoreUser(username: String) {
        val norm = BeatmapUrlParser.normalizeOsuNick(username)
        if (norm.isEmpty() || norm == "banchobot" || norm == "system") return
        viewModelScope.launch {
            if (norm in uiState.value.ignoredUsers) {
                chatRepository.unignoreUser(username)
                _effects.emit(MatchAnalyzerEffect.ShowMessage("Unignored $username"))
            } else {
                chatRepository.ignoreUser(username)
                _effects.emit(MatchAnalyzerEffect.ShowMessage("Ignored $username"))
            }
        }
    }

    suspend fun estimateRankForPp(
        mode: String,
        targetPp: Double,
        currentRankHint: Int?,
        currentPpHint: Double?
    ): Result<RankPpConversionResult> {
        return chatRepository.estimateRankForPp(mode, targetPp, currentRankHint, currentPpHint)
    }
}
