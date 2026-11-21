package com.osuchat.app.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.AudioQueueTrack
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapRecommendationPreset
import com.osuchat.app.domain.model.BeatmapSearchFilter
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.DailySessionSummary
import com.osuchat.app.domain.model.NewsAndSpotlightsFeed
import com.osuchat.app.domain.model.OsuNewsItem
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuSpotlightItem
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.OszDownloadState
import com.osuchat.app.domain.model.PlayLaterSortOption
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.PlayerRecommendationProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.model.RecommendedBeatmapItem
import com.osuchat.app.domain.model.SavedBeatmapItem
import com.osuchat.app.ui.components.BeatmapPpSimulatorPanel
import com.osuchat.app.ui.components.GradeBadge
import com.osuchat.app.ui.components.OsuBannerTone
import com.osuchat.app.ui.components.OsuEmptyState
import com.osuchat.app.ui.components.OsuFilterChip
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.components.OsuInlineBanner
import com.osuchat.app.ui.components.OsuMicroTag
import com.osuchat.app.ui.components.OsuModeBadge
import com.osuchat.app.ui.components.OsuSectionHeader
import com.osuchat.app.ui.components.OsuSegmentedBar
import com.osuchat.app.ui.components.PlayerAvatar
import com.osuchat.app.ui.components.PlayerCompareEntryPoint
import com.osuchat.app.ui.components.PlayerProfileContent
import com.osuchat.app.ui.components.RosuPpEngineEntryPoint
import com.osuchat.app.ui.components.StarRatingBadge
import com.osuchat.app.ui.components.countryCodeToFlagEmoji
import com.osuchat.app.ui.components.osuModeVisual
import com.osuchat.app.ui.components.rememberRosuSimulationState
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
import com.osuchat.app.util.BeatmapDiffspikeProfile
import com.osuchat.app.util.BeatmapRecommendationEngine
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.OsuBeatmapCalculator
import com.osuchat.app.util.OsuDiffspikeParser
import com.osuchat.app.util.RankPpMath
import dagger.hilt.android.EntryPointAccessors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class ExploreSubTab(val label: String) {
    BEATMAPS("Beatmaps"),
    RANK_PP("Rank ↔ PP"),
    PLAY_LATER("Play Later"),
    NEWS("News & Spotlights"),
    RANKINGS("Rankings & Search"),
    MY_PROFILE("My Profile")
}

private data class CountryOption(
    val code: String,
    val name: String,
    val flag: String
)

private val POPULAR_OSU_COUNTRY_CODES = listOf(
    "TR", "US", "JP", "KR", "DE", "PL", "RU", "GB", "CA", "BR",
    "FR", "AU", "TW", "ID", "PH", "NL", "SE", "FI", "ES", "IT",
    "UA", "MX", "CL", "AR", "CN", "HK", "SG", "MY", "TH", "VN"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreAndProfileSection(
    subTab: ExploreSubTab,
    onSelectSubTab: (ExploreSubTab) -> Unit,
    beatmapFilter: BeatmapSearchFilter,
    onBeatmapQueryChange: (String) -> Unit,
    onBeatmapFilterChange: (BeatmapSearchFilter, Boolean) -> Unit,
    onResetBeatmapFilters: () -> Unit,
    onClearBeatmapQueryAndFilters: () -> Unit = onResetBeatmapFilters,
    beatmapResults: List<BeatmapSetSearchResult>,
    isBeatmapsLoading: Boolean,
    hasSearchedBeatmaps: Boolean = false,
    beatmapsError: String? = null,
    onSearchBeatmaps: () -> Unit,
    isForYouMode: Boolean = false,
    onToggleForYouMode: (Boolean) -> Unit = {},
    recommendationPreset: BeatmapRecommendationPreset = BeatmapRecommendationPreset.FOR_YOU,
    recommendationProfile: PlayerRecommendationProfile = PlayerRecommendationProfile(),
    recommendedBeatmaps: List<RecommendedBeatmapItem> = emptyList(),
    isRecommendationsLoading: Boolean = false,
    recommendationsError: String? = null,
    onSelectRecommendationPreset: (BeatmapRecommendationPreset) -> Unit = {},
    onRefreshRecommendations: () -> Unit = {},
    onPlayRecommendedQueue: (List<RecommendedBeatmapItem>, Int) -> Unit = { _, _ -> },
    hasActiveMpLobby: Boolean,
    onSelectBeatmapForMp: (beatmapId: Long, modeInt: Int) -> Unit,
    onResolveBeatmapDetails: suspend (beatmapId: Long) -> Result<BeatmapMetadata>,
    onLoadBeatmapScores: suspend (beatmapId: Long, mode: String?) -> Result<List<OsuScoreItem>> = { _, _ -> Result.success(emptyList()) },
    onShowMessage: (String) -> Unit,
    audioState: AudioPlaybackState,
    onToggleAudio: (beatmapsetId: Long, previewUrl: String) -> Unit,
    onPlaySavedQueue: (List<SavedBeatmapItem>, Int) -> Unit = { _, _ -> },
    onPlaySearchResultsQueue: (List<BeatmapSetSearchResult>, Int, BeatmapMetadata?) -> Unit = { _, _, _ -> },
    rankingMode: String,
    onRankingModeChange: (String) -> Unit,
    rankingCountry: String,
    onRankingCountryChange: (String) -> Unit,
    playerSearchQuery: String,
    onPlayerSearchQueryChange: (String) -> Unit,
    onClearPlayerSearch: () -> Unit = {},
    isPlayerSearchActive: Boolean = false,
    players: List<PlayerProfile>,
    isPlayersLoading: Boolean,
    playersError: String? = null,
    onLoadRankings: () -> Unit,
    onSearchPlayers: () -> Unit,
    onOpenPlayerProfile: (String) -> Unit,
    onToggleTrackPp: (PlayerProfile) -> Unit,
    selfProfile: PlayerProfile? = null,
    isLoadingSelfProfile: Boolean = false,
    selfProfileError: String? = null,
    selfProfileMode: String = "osu",
    selfPinnedScores: List<OsuScoreItem> = emptyList(),
    selfBestScores: List<OsuScoreItem> = emptyList(),
    selfRecentScores: List<OsuScoreItem> = emptyList(),
    isLoadingSelfScores: Boolean = false,
    isLoadingMoreSelfBestScores: Boolean = false,
    canLoadMoreSelfBestScores: Boolean = false,
    onLoadMoreSelfBestScores: (() -> Unit)? = null,
    selfUsername: String = "",
    selfProfileHistory: List<ProfileHistoryPoint> = emptyList(),
    selfProfileOsuTrack: List<OsuTrackPoint> = emptyList(),
    isLoadingSelfOsuTrack: Boolean = false,
    selfDailySession: DailySessionSummary? = null,
    isLoadingDailySession: Boolean = false,
    onRefreshDailySession: (() -> Unit)? = null,
    onSelectSelfProfileMode: (String) -> Unit = {},
    onRefreshSelfProfile: () -> Unit = {},
    onShareSelfScore: ((OsuScoreItem) -> Unit)? = null,
    onEstimateRankForPp: (suspend (mode: String, targetPp: Double, currentRankHint: Int?, currentPpHint: Double?) -> Result<RankPpConversionResult>)? = null,
    onEstimatePpForRank: (suspend (mode: String, targetRank: Int) -> Result<RankPpConversionResult>)? = null,
    savedBeatmaps: List<SavedBeatmapItem> = emptyList(),
    filteredSavedBeatmaps: List<SavedBeatmapItem> = savedBeatmaps,
    availablePlayLaterTags: List<String> = emptyList(),
    playLaterSelectedTag: String? = null,
    playLaterSortOption: PlayLaterSortOption = PlayLaterSortOption.SAVED_DATE,
    playLaterSortDescending: Boolean = true,
    savedBeatmapIds: Set<Long> = emptySet(),
    isSendingQueueToInGame: Boolean = false,
    oszDownloadStates: Map<Long, OszDownloadState> = emptyMap(),
    onToggleSaveBeatmap: (SavedBeatmapItem) -> Unit = {},
    onRemoveSavedBeatmap: (Long) -> Unit = {},
    onSelectPlayLaterTag: (String?) -> Unit = {},
    onSelectPlayLaterSortOption: (PlayLaterSortOption) -> Unit = {},
    onTogglePlayLaterSortDirection: () -> Unit = {},
    onUpdateSavedBeatmapTags: (Long, List<String>) -> Unit = { _, _ -> },
    onDownloadOsz: (beatmapsetId: Long, artist: String, title: String) -> Unit = { _, _, _ -> },
    onShareOsz: (context: Context, beatmapsetId: Long, artist: String, title: String) -> Unit = { _, _, _, _ -> },
    onSendBeatmapToInGame: (SavedBeatmapItem) -> Unit = {},
    onSendAllSavedBeatmapsToInGame: () -> Unit = {},
    rankPpMode: String = "osu",
    isConvertingRankPp: Boolean = false,
    rankPpResult: RankPpConversionResult? = null,
    rankPpError: String? = null,
    onRankPpModeChange: (String) -> Unit = {},
    onLookupPpForRank: (Int) -> Unit = {},
    onLookupRankForPp: (Double) -> Unit = {},
    newsFeed: NewsAndSpotlightsFeed = NewsAndSpotlightsFeed(),
    isNewsLoading: Boolean = false,
    newsError: String? = null,
    onRefreshNews: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(audioState.error) {
        val err = audioState.error
        if (!err.isNullOrBlank()) {
            onShowMessage(err)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            items(
                items = ExploreSubTab.entries,
                key = { it.name },
                contentType = { "explore_sub_tab" }
            ) { tab ->
                val selected = subTab == tab
                val hasCount = tab == ExploreSubTab.PLAY_LATER && savedBeatmaps.isNotEmpty()
                val chipLabel = if (hasCount) "${tab.label} (${savedBeatmaps.size})" else tab.label
                OsuFilterChip(
                    label = chipLabel,
                    selected = selected,
                    onClick = { onSelectSubTab(tab) },
                    isNumeric = hasCount
                )
            }
        }
        OsuHairlineDivider()

        when (subTab) {
            ExploreSubTab.BEATMAPS -> {
                BeatmapsExploreTab(
                    filter = beatmapFilter,
                    onQueryChange = onBeatmapQueryChange,
                    onFilterChange = onBeatmapFilterChange,
                    onResetFilters = onResetBeatmapFilters,
                    onClearAll = onClearBeatmapQueryAndFilters,
                    results = beatmapResults,
                    isLoading = isBeatmapsLoading,
                    hasSearched = hasSearchedBeatmaps,
                    errorMessage = beatmapsError,
                    onSearch = onSearchBeatmaps,
                    isForYouMode = isForYouMode,
                    onToggleForYouMode = onToggleForYouMode,
                    recommendationPreset = recommendationPreset,
                    recommendationProfile = recommendationProfile,
                    recommendedBeatmaps = recommendedBeatmaps,
                    isRecommendationsLoading = isRecommendationsLoading,
                    recommendationsError = recommendationsError,
                    onSelectRecommendationPreset = onSelectRecommendationPreset,
                    onRefreshRecommendations = onRefreshRecommendations,
                    onPlayRecommendedQueue = onPlayRecommendedQueue,
                    hasActiveMpLobby = hasActiveMpLobby,
                    onSelectForMp = onSelectBeatmapForMp,
                    onResolveBeatmapDetails = onResolveBeatmapDetails,
                    onLoadBeatmapScores = onLoadBeatmapScores,
                    onShowMessage = onShowMessage,
                    onOpenPlayerProfile = onOpenPlayerProfile,
                    audioState = audioState,
                    onToggleAudio = onToggleAudio,
                    onPlaySearchResultsQueue = onPlaySearchResultsQueue,
                    savedBeatmaps = savedBeatmaps,
                    savedBeatmapIds = savedBeatmapIds,
                    isSendingQueueToInGame = isSendingQueueToInGame,
                    oszDownloadStates = oszDownloadStates,
                    onToggleSaveBeatmap = onToggleSaveBeatmap,
                    onDownloadOsz = onDownloadOsz,
                    onShareOsz = onShareOsz,
                    onOpenPlayLater = { onSelectSubTab(ExploreSubTab.PLAY_LATER) },
                    onSendAllSavedBeatmapsToInGame = onSendAllSavedBeatmapsToInGame
                )
            }
            ExploreSubTab.RANK_PP -> {
                RankPpCalculatorTab(
                    mode = rankPpMode,
                    isLoading = isConvertingRankPp,
                    result = rankPpResult,
                    errorMessage = rankPpError,
                    onModeChange = onRankPpModeChange,
                    onLookupPpForRank = onLookupPpForRank,
                    onLookupRankForPp = onLookupRankForPp,
                    onOpenPlayerProfile = onOpenPlayerProfile,
                    selfProfile = selfProfile,
                    selfProfileMode = selfProfileMode,
                    selfBestScores = selfBestScores,
                    selfUsername = selfUsername,
                    onEstimatePpForRank = onEstimatePpForRank
                )
            }
            ExploreSubTab.PLAY_LATER -> {
                PlayLaterQueueTab(
                    totalSavedCount = savedBeatmaps.size,
                    savedBeatmaps = filteredSavedBeatmaps,
                    availableTags = availablePlayLaterTags,
                    selectedTag = playLaterSelectedTag,
                    sortOption = playLaterSortOption,
                    sortDescending = playLaterSortDescending,
                    isSendingAll = isSendingQueueToInGame,
                    audioState = audioState,
                    oszDownloadStates = oszDownloadStates,
                    onToggleAudio = onToggleAudio,
                    onPlaySavedQueue = onPlaySavedQueue,
                    onSelectTag = onSelectPlayLaterTag,
                    onSelectSortOption = onSelectPlayLaterSortOption,
                    onToggleSortDirection = onTogglePlayLaterSortDirection,
                    onUpdateTags = onUpdateSavedBeatmapTags,
                    onDownloadOsz = onDownloadOsz,
                    onShareOsz = onShareOsz,
                    onSendOneToInGame = onSendBeatmapToInGame,
                    onSendAllToInGame = onSendAllSavedBeatmapsToInGame,
                    onRemove = onRemoveSavedBeatmap,
                    onBrowseBeatmaps = { onSelectSubTab(ExploreSubTab.BEATMAPS) },
                    onShowMessage = onShowMessage
                )
            }
            ExploreSubTab.NEWS -> {
                NewsAndSpotlightsTab(
                    feed = newsFeed,
                    isLoading = isNewsLoading,
                    errorMessage = newsError,
                    onRefresh = onRefreshNews,
                    onShowMessage = onShowMessage
                )
            }
            ExploreSubTab.RANKINGS -> {
                RankingsAndSearchTab(
                    rankingMode = rankingMode,
                    onRankingModeChange = onRankingModeChange,
                    rankingCountry = rankingCountry,
                    onRankingCountryChange = onRankingCountryChange,
                    searchQuery = playerSearchQuery,
                    onSearchQueryChange = onPlayerSearchQueryChange,
                    onClearSearch = onClearPlayerSearch,
                    isSearchActive = isPlayerSearchActive,
                    players = players,
                    isLoading = isPlayersLoading,
                    errorMessage = playersError,
                    onLoadRankings = onLoadRankings,
                    onSearchPlayers = onSearchPlayers,
                    onOpenProfile = onOpenPlayerProfile,
                    onToggleTrackPp = onToggleTrackPp
                )
            }
            ExploreSubTab.MY_PROFILE -> {
                if (selfUsername.isBlank() && selfProfile == null && !isLoadingSelfProfile) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(OsuSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        ExploreEmptyCard(
                            title = "Not signed in",
                            body = "Sign in with osu! in Settings to load your profile",
                            actionLabel = null,
                            onAction = null,
                            icon = Icons.Rounded.AccountCircle
                        )
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isLoadingSelfProfile && selfProfile != null,
                        onRefresh = onRefreshSelfProfile,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        PlayerProfileContent(
                            profile = selfProfile,
                            isLoading = isLoadingSelfProfile,
                            selectedMode = selfProfileMode,
                            onSelectMode = onSelectSelfProfileMode,
                            onSaveAlias = null,
                            onStartDirectMessage = null,
                            onToggleTrackPp = null,
                            selfUsername = selfProfile?.username?.ifBlank { selfUsername } ?: selfUsername,
                            onToggleFriend = null,
                            pinnedScores = selfPinnedScores,
                            bestScores = selfBestScores,
                            recentScores = selfRecentScores,
                            isScoresLoading = isLoadingSelfScores,
                            isLoadingMoreBestScores = isLoadingMoreSelfBestScores,
                            canLoadMoreBestScores = canLoadMoreSelfBestScores,
                            onLoadMoreBestScores = onLoadMoreSelfBestScores,
                            onShareScore = onShareSelfScore,
                            onEstimateRankForPp = onEstimateRankForPp,
                            profileHistory = selfProfileHistory,
                            osuTrackHistory = selfProfileOsuTrack,
                            isOsuTrackLoading = isLoadingSelfOsuTrack,
                            dailySession = selfDailySession,
                            isDailySessionLoading = isLoadingDailySession,
                            onRefreshDailySession = onRefreshDailySession,
                            errorMessage = selfProfileError,
                            onRetry = onRefreshSelfProfile,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun BeatmapSetSearchResult.toSavedItem(diff: BeatmapMetadata?): SavedBeatmapItem {
    val bid = diff?.beatmapId?.takeIf { it > 0L } ?: primaryBeatmapId
    val lengthSec = diff?.totalLengthSeconds?.takeIf { it > 0 }
        ?: difficulties.firstOrNull { it.totalLengthSeconds > 0 }?.totalLengthSeconds
        ?: 0
    return SavedBeatmapItem(
        beatmapId = bid,
        beatmapsetId = beatmapsetId,
        title = diff?.title?.ifBlank { title } ?: title,
        artist = diff?.artist?.ifBlank { artist } ?: artist,
        creator = diff?.creator?.ifBlank { creator } ?: creator,
        version = diff?.version?.ifBlank { "Default" } ?: "Default",
        starRating = diff?.starRating ?: 0.0,
        bpm = diff?.bpm ?: bpm,
        mode = diff?.mode ?: "osu",
        coverUrl = diff?.coverUrl?.ifBlank { coverUrl } ?: coverUrl,
        previewAudioUrl = diff?.previewAudioUrl?.ifBlank { previewAudioUrl } ?: previewAudioUrl,
        totalLengthSeconds = lengthSec
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeatmapsExploreTab(
    filter: BeatmapSearchFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (BeatmapSearchFilter, Boolean) -> Unit,
    onResetFilters: () -> Unit,
    onClearAll: () -> Unit,
    results: List<BeatmapSetSearchResult>,
    isLoading: Boolean,
    hasSearched: Boolean,
    errorMessage: String?,
    onSearch: () -> Unit,
    isForYouMode: Boolean,
    onToggleForYouMode: (Boolean) -> Unit,
    recommendationPreset: BeatmapRecommendationPreset,
    recommendationProfile: PlayerRecommendationProfile,
    recommendedBeatmaps: List<RecommendedBeatmapItem>,
    isRecommendationsLoading: Boolean,
    recommendationsError: String?,
    onSelectRecommendationPreset: (BeatmapRecommendationPreset) -> Unit,
    onRefreshRecommendations: () -> Unit,
    onPlayRecommendedQueue: (List<RecommendedBeatmapItem>, Int) -> Unit,
    hasActiveMpLobby: Boolean,
    onSelectForMp: (beatmapId: Long, modeInt: Int) -> Unit,
    onResolveBeatmapDetails: suspend (beatmapId: Long) -> Result<BeatmapMetadata>,
    onLoadBeatmapScores: suspend (beatmapId: Long, mode: String?) -> Result<List<OsuScoreItem>>,
    onOpenPlayerProfile: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    audioState: AudioPlaybackState,
    onToggleAudio: (beatmapsetId: Long, previewUrl: String) -> Unit,
    onPlaySearchResultsQueue: (List<BeatmapSetSearchResult>, Int, BeatmapMetadata?) -> Unit,
    savedBeatmaps: List<SavedBeatmapItem>,
    savedBeatmapIds: Set<Long>,
    isSendingQueueToInGame: Boolean,
    oszDownloadStates: Map<Long, OszDownloadState>,
    onToggleSaveBeatmap: (SavedBeatmapItem) -> Unit,
    onDownloadOsz: (beatmapsetId: Long, artist: String, title: String) -> Unit,
    onShareOsz: (context: Context, beatmapsetId: Long, artist: String, title: String) -> Unit,
    onOpenPlayLater: () -> Unit,
    onSendAllSavedBeatmapsToInGame: () -> Unit
) {
    val statuses = remember {
        listOf(
            null to "Any",
            "ranked" to "Ranked",
            "qualified" to "Qualified",
            "loved" to "Loved",
            "pending" to "Pending",
            "wip" to "WIP",
            "graveyard" to "Graveyard"
        )
    }
    val modes = remember {
        listOf(null to "Any", 0 to "osu!", 1 to "taiko", 2 to "catch", 3 to "mania")
    }
    val sorts = remember {
        listOf(
            "relevance" to "Relevance",
            "title" to "Title",
            "artist" to "Artist",
            "difficulty" to "Difficulty",
            "ranked" to "Ranked Date",
            "rating" to "Rating",
            "plays" to "Plays",
            "favourites" to "Favorites"
        )
    }
    val genres = remember {
        listOf(
            null to "Any",
            3 to "Anime",
            10 to "Electronic",
            4 to "Rock",
            5 to "Pop",
            2 to "Video Game",
            7 to "Novelty",
            9 to "Hip Hop",
            11 to "Metal",
            12 to "Classical",
            13 to "Folk",
            14 to "Jazz",
            6 to "Other"
        )
    }
    val languages = remember {
        listOf(
            null to "Any",
            2 to "English",
            3 to "Japanese",
            5 to "Instrumental",
            4 to "Chinese",
            6 to "Korean",
            8 to "German",
            7 to "French",
            10 to "Spanish",
            14 to "Other"
        )
    }

    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var genreMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var selectedSetForStats by remember { mutableStateOf<BeatmapSetSearchResult?>(null) }
    var selectedDiffIndexForStats by remember { mutableIntStateOf(0) }

    val surfaces = LocalOsuSurfaces.current
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    selectedSetForStats?.let { set ->
        BeatmapStatsBottomSheet(
            beatmapSet = set,
            initialDiffIndex = selectedDiffIndexForStats,
            onDismiss = { selectedSetForStats = null },
            onResolveDetails = onResolveBeatmapDetails,
            onLoadScores = onLoadBeatmapScores,
            onOpenPlayerProfile = onOpenPlayerProfile,
            onShowMessage = onShowMessage,
            savedBeatmapIds = savedBeatmapIds,
            onToggleSaveBeatmap = onToggleSaveBeatmap,
            oszDownloadState = oszDownloadStates[set.beatmapsetId] ?: OszDownloadState.Idle,
            onDownloadOsz = { onDownloadOsz(set.beatmapsetId, set.artist, set.title) },
            onShareOsz = { ctx -> onShareOsz(ctx, set.beatmapsetId, set.artist, set.title) },
            hasActiveMpLobby = hasActiveMpLobby,
            onSelectForMp = { mapId, modeInt ->
                selectedSetForStats = null
                onSelectForMp(mapId, modeInt)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OsuSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
    ) {
        item(key = "beatmap_mode_toggle", contentType = "mode_toggle") {
            val exploreModes = remember { listOf(false to "Search", true to "For You") }
            OsuSegmentedBar(
                items = exploreModes,
                selectedIndex = if (isForYouMode) 1 else 0,
                onSelect = { idx -> onToggleForYouMode(exploreModes[idx].first) },
                labelProvider = { it.second }
            )
        }

        if (isForYouMode) {
            item(key = "for_you_profile_header", contentType = "for_you_header") {
                FlatCard(
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (recommendationProfile.isFallback) {
                                        "Starter Recommendations"
                                    } else {
                                        String.format(
                                            Locale.US,
                                            "Tailored to %.2f★ avg",
                                            recommendationProfile.avgStarRating
                                        )
                                    },
                                    style = MaterialTheme.typography.titleSmall.tnum(),
                                    fontWeight = FontWeight.SemiBold
                                )
                                val avgLen = BeatmapRecommendationEngine.formatMinSec(
                                    recommendationProfile.avgLengthSeconds
                                ) ?: "2:15"
                                Text(
                                    text = if (recommendationProfile.isFallback) {
                                        "4.5–5.5★ window (${recommendationProfile.modeName}) • Sign in for Top 100 fit"
                                    } else {
                                        "${recommendationProfile.modeName} • $avgLen avg • ${recommendationProfile.primaryModBucket} focus"
                                    },
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onRefreshRecommendations,
                                enabled = !isRecommendationsLoading
                            ) {
                                if (isRecommendationsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh recommendations",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            items(
                                items = BeatmapRecommendationPreset.entries,
                                key = { it.name }
                            ) { preset ->
                                OsuFilterChip(
                                    label = preset.label,
                                    selected = recommendationPreset == preset,
                                    onClick = { onSelectRecommendationPreset(preset) }
                                )
                            }
                        }
                    }
                }
            }

            if (isRecommendationsLoading && recommendedBeatmaps.isEmpty()) {
                items(count = 4, key = { "rec_skel_$it" }, contentType = { "skeleton" }) {
                    BeatmapResultSkeletonCard()
                }
            } else if (recommendationsError != null && recommendedBeatmaps.isEmpty()) {
                item(key = "rec_error", contentType = "error") {
                    ExploreErrorCard(message = recommendationsError, onRetry = onRefreshRecommendations)
                }
            } else if (recommendedBeatmaps.isEmpty()) {
                item(key = "rec_empty", contentType = "empty") {
                    ExploreEmptyCard(
                        title = "No recommendations yet",
                        body = "Refresh to fetch beatmaps matched to your Top 100 skill profile",
                        actionLabel = "Find Beatmaps",
                        onAction = onRefreshRecommendations
                    )
                }
            } else {
                if (isRecommendationsLoading) {
                    item(key = "rec_refresh_bar", contentType = "loading_bar") {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(OsuSpacing.xxs),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
                itemsIndexed(
                    items = recommendedBeatmaps,
                    key = { idx, item -> "rec_${idx}_${item.beatmapSet.beatmapsetId}_${item.recommendedDiff.beatmapId}" },
                    contentType = { _, _ -> "recommended_beatmap_card" }
                ) { index, item ->
                    val set = item.beatmapSet
                    val chosenDiff = item.recommendedDiff
                    val isThisAudio = audioState.beatmapsetId == set.beatmapsetId
                    val isPlayingThis = isThisAudio && audioState.isPlaying
                    val isPreparingThis = isThisAudio && audioState.isPreparing
                    val cardTargetMapId = chosenDiff.beatmapId.takeIf { it > 0L } ?: set.primaryBeatmapId
                    val isSavedInQueue = cardTargetMapId > 0L && cardTargetMapId in savedBeatmapIds
                    val saveTint by animateColorAsState(
                        targetValue = if (isSavedInQueue) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "recSaveTint"
                    )
                    val playTint by animateColorAsState(
                        targetValue = if (isPlayingThis) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "recPlayTint"
                    )

                    FlatCard(
                        shape = cardShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDiffIndexForStats = item.recommendedDiffIndex
                                    .coerceIn(0, (set.difficulties.size - 1).coerceAtLeast(0))
                                selectedSetForStats = set
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(OsuSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = set.coverUrl,
                                    contentDescription = set.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(OsuRadius.sm))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                )
                                Spacer(Modifier.width(OsuSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${set.artist} - ${set.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val bpmVal = chosenDiff.bpm.takeIf { it > 0.0 } ?: set.bpm
                                    val bpmText = if (bpmVal.isFinite() && bpmVal > 0.0) {
                                        "${bpmVal.roundToInt()} BPM"
                                    } else {
                                        "— BPM"
                                    }
                                    Text(
                                        text = "by ${set.creator} • $bpmText",
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = surfaces.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(OsuSpacing.xs))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        OsuModeBadge(mode = chosenDiff.mode, compact = true)
                                        StarRatingBadge(starRating = chosenDiff.starRating)
                                        Text(
                                            text = "[${chosenDiff.version}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = surfaces.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }
                                IconButton(
                                    enabled = cardTargetMapId > 0L,
                                    onClick = {
                                        if (cardTargetMapId > 0L) {
                                            onToggleSaveBeatmap(set.toSavedItem(chosenDiff))
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSavedInQueue) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = if (isSavedInQueue) "Remove from Play Later" else "Save to Play Later",
                                        tint = saveTint
                                    )
                                }
                                IconButton(
                                    enabled = set.previewAudioUrl.isNotBlank() || set.beatmapsetId > 0L,
                                    onClick = { onPlayRecommendedQueue(recommendedBeatmaps, index) }
                                ) {
                                    if (isPreparingThis) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPlayingThis) "Pause audio preview" else "Preview 30s audio",
                                            tint = playTint
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OsuMicroTag(
                                    text = item.reasonTag,
                                    containerColor = surfaces.surfaceHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    isNumeric = true
                                )
                                if (hasActiveMpLobby && cardTargetMapId > 0L) {
                                    val modeInt = BeatmapRecommendationEngine.modeNameToInt(chosenDiff.mode)
                                    TextButton(
                                        onClick = { onSelectForMp(cardTargetMapId, modeInt) },
                                        contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = "!mp map",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {

        item(key = "beatmap_controls", contentType = "beatmap_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    OutlinedTextField(
                        value = filter.query,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                text = "Search beatmaps…",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filter.query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        onQueryChange("")
                                        onSearch()
                                    }
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        shape = controlShape,
                        modifier = Modifier.weight(1f)
                    )
                    BadgedBox(
                        badge = {
                            if (filter.activeFilterCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = filter.activeFilterCount.toString(),
                                        style = MaterialTheme.typography.labelSmall.tnum()
                                    )
                                }
                            }
                        }
                    ) {
                        OutlinedButton(
                            onClick = { filtersExpanded = !filtersExpanded },
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Toggle beatmap filters",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text(
                                text = "Filters",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Button(
                        onClick = onSearch,
                        shape = controlShape,
                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.md)
                    ) {
                        Text(
                            text = "Search",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = OsuMotion.expandEnter(),
                    exit = OsuMotion.expandExit()
                ) {
                    FlatCard(
                        shape = cardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(OsuSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = surfaces.textMuted
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    modes.forEach { (value, label) ->
                                        OsuFilterChip(
                                            label = label,
                                            selected = filter.mode == value,
                                            onClick = { onFilterChange(filter.copy(mode = value), true) }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = surfaces.textMuted
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    statuses.forEach { (value, label) ->
                                        OsuFilterChip(
                                            label = label,
                                            selected = filter.status == value,
                                            onClick = { onFilterChange(filter.copy(status = value), true) }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sort by",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = surfaces.textMuted
                                    )
                                    OsuFilterChip(
                                        label = if (filter.sortDescending) "Descending" else "Ascending",
                                        selected = true,
                                        leadingIcon = if (filter.sortDescending) {
                                            Icons.Rounded.ArrowDownward
                                        } else {
                                            Icons.Rounded.ArrowUpward
                                        },
                                        onClick = {
                                            onFilterChange(
                                                filter.copy(sortDescending = !filter.sortDescending),
                                                true
                                            )
                                        }
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    sorts.forEach { (value, label) ->
                                        OsuFilterChip(
                                            label = label,
                                            selected = filter.sort == value,
                                            onClick = { onFilterChange(filter.copy(sort = value), true) }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Genre & language",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = surfaces.textMuted
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    val selectedGenreLabel = genres.firstOrNull { it.first == filter.genreId }?.second ?: "Any"
                                    Box {
                                        val selected = filter.genreId != null
                                        DropdownTriggerChip(
                                            label = "Genre: $selectedGenreLabel",
                                            selected = selected,
                                            onClick = { genreMenuExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = genreMenuExpanded,
                                            onDismissRequest = { genreMenuExpanded = false },
                                            shape = RoundedCornerShape(OsuRadius.md),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            border = osuHairlineBorder()
                                        ) {
                                            genres.forEach { (id, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        genreMenuExpanded = false
                                                        onFilterChange(filter.copy(genreId = id), true)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    val selectedLangLabel = languages.firstOrNull { it.first == filter.languageId }?.second ?: "Any"
                                    Box {
                                        val selected = filter.languageId != null
                                        DropdownTriggerChip(
                                            label = "Language: $selectedLangLabel",
                                            selected = selected,
                                            onClick = { languageMenuExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = languageMenuExpanded,
                                            onDismissRequest = { languageMenuExpanded = false },
                                            shape = RoundedCornerShape(OsuRadius.md),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            border = osuHairlineBorder()
                                        ) {
                                            languages.forEach { (id, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        languageMenuExpanded = false
                                                        onFilterChange(filter.copy(languageId = id), true)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Text(
                                    text = "Extras",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = surfaces.textMuted
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    listOf(
                                        Triple(filter.hasVideo, "Has Video") {
                                            onFilterChange(filter.copy(hasVideo = !filter.hasVideo), true)
                                        },
                                        Triple(filter.hasStoryboard, "Has Storyboard") {
                                            onFilterChange(filter.copy(hasStoryboard = !filter.hasStoryboard), true)
                                        },
                                        Triple(filter.recommendedDifficulty, "Recommended Difficulty") {
                                            onFilterChange(filter.copy(recommendedDifficulty = !filter.recommendedDifficulty), true)
                                        }
                                    ).forEach { (selected, label, toggle) ->
                                        OsuFilterChip(
                                            label = label,
                                            selected = selected,
                                            onClick = toggle
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onResetFilters,
                                    enabled = filter.activeFilterCount > 0,
                                    shape = controlShape
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (savedBeatmaps.isNotEmpty()) {
            item(key = "play_later_bar", contentType = "play_later_bar") {
                FlatCard(
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Text(
                            text = "Play Later (${savedBeatmaps.size})",
                            style = MaterialTheme.typography.labelLarge.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onOpenPlayLater,
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                        ) {
                            Text("Queue", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onSendAllSavedBeatmapsToInGame,
                            enabled = !isSendingQueueToInGame,
                            shape = controlShape,
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                        ) {
                            if (isSendingQueueToInGame) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text("Sync All", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (isLoading && results.isNotEmpty()) {
            item(key = "beatmaps_refresh_bar", contentType = "loading_bar") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OsuSpacing.xxs),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }

        if (errorMessage != null && results.isNotEmpty()) {
            item(key = "beatmaps_error_inline", contentType = "error") {
                ExploreErrorCard(message = errorMessage, onRetry = onSearch)
            }
        }

        if (isLoading && results.isEmpty()) {
            items(count = 4, key = { "bm_skel_$it" }, contentType = { "skeleton" }) {
                BeatmapResultSkeletonCard()
            }
        } else if (errorMessage != null && results.isEmpty()) {
            item(key = "beatmaps_error", contentType = "error") {
                ExploreErrorCard(message = errorMessage, onRetry = onSearch)
            }
        } else if (results.isEmpty()) {
            item(key = "beatmaps_empty", contentType = "empty") {
                val hasActiveQueryOrFilters = filter.query.isNotBlank() || filter.activeFilterCount > 0
                ExploreEmptyCard(
                    title = if (hasSearched && hasActiveQueryOrFilters) {
                        "No matching beatmaps"
                    } else {
                        "No beatmaps loaded"
                    },
                    body = if (hasSearched && hasActiveQueryOrFilters) {
                        "No beatmaps matched your search or filter selection"
                    } else {
                        "Search beatmaps to inspect stats, preview audio, or set via !mp map"
                    },
                    actionLabel = if (hasActiveQueryOrFilters) "Reset Search & Filters" else "Browse Ranked",
                    onAction = if (hasActiveQueryOrFilters) onClearAll else onSearch
                )
            }
        } else {
            itemsIndexed(
                items = results,
                key = { idx, set -> "set_${idx}_${set.beatmapsetId}" },
                contentType = { _, _ -> "beatmap_set_card" }
            ) { index, set ->
                var selectedDiffIndex by remember(set.beatmapsetId) {
                    mutableIntStateOf((set.difficulties.lastIndex).coerceAtLeast(0))
                }
                val chosenDiff = set.difficulties.getOrNull(selectedDiffIndex)
                val isThisAudio = audioState.beatmapsetId == set.beatmapsetId
                val isPlayingThis = isThisAudio && audioState.isPlaying
                val isPreparingThis = isThisAudio && audioState.isPreparing
                val cardTargetMapId = chosenDiff?.beatmapId ?: set.primaryBeatmapId
                val isSavedInQueue = cardTargetMapId > 0L && cardTargetMapId in savedBeatmapIds
                val saveTint by animateColorAsState(
                    targetValue = if (isSavedInQueue) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "searchSaveTint"
                )
                val playTint by animateColorAsState(
                    targetValue = if (isPlayingThis) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "searchPlayTint"
                )

                FlatCard(
                    shape = cardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDiffIndexForStats = selectedDiffIndex
                            selectedSetForStats = set
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = set.coverUrl,
                                contentDescription = set.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(OsuRadius.sm))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            Spacer(Modifier.width(OsuSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${set.artist} - ${set.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val bpmText = if (set.bpm.isFinite()) "${set.bpm.roundToInt()} BPM" else "— BPM"
                                val statusDisplay = set.status.trim().lowercase(Locale.US).replaceFirstChar { it.uppercase() }
                                Text(
                                    text = "by ${set.creator} • $statusDisplay • $bpmText",
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(OsuSpacing.xs))
                                if (chosenDiff != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        OsuModeBadge(mode = chosenDiff.mode, compact = true)
                                        StarRatingBadge(starRating = chosenDiff.starRating)
                                        Text(
                                            text = "[${chosenDiff.version}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = surfaces.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                } else {
                                    OsuModeBadge(mode = "osu", compact = true)
                                }
                            }
                            IconButton(
                                enabled = cardTargetMapId > 0L,
                                onClick = {
                                    if (cardTargetMapId > 0L) {
                                        onToggleSaveBeatmap(set.toSavedItem(chosenDiff))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSavedInQueue) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                    contentDescription = if (isSavedInQueue) "Remove from Play Later" else "Save to Play Later",
                                    tint = saveTint
                                )
                            }
                            val canPlayAudio = set.previewAudioUrl.isNotBlank() || set.beatmapsetId > 0L
                            IconButton(
                                enabled = canPlayAudio,
                                onClick = { onPlaySearchResultsQueue(results, index, chosenDiff) }
                            ) {
                                if (isPreparingThis) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlayingThis) "Pause audio preview" else "Preview 30s audio",
                                        tint = playTint
                                    )
                                }
                            }
                        }

                        if (set.difficulties.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                itemsIndexed(
                                    items = set.difficulties,
                                    key = { idx, d -> "diff_${idx}_${d.beatmapId}" }
                                ) { idx, d ->
                                    val selected = selectedDiffIndex == idx
                                    val modeVis = osuModeVisual(d.mode)
                                    val diffShape = RoundedCornerShape(OsuRadius.sm)
                                    val bgColor by animateColorAsState(
                                        targetValue = if (selected) surfaces.accentSubtle else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        animationSpec = OsuMotion.colorSpec(),
                                        label = "diffBg"
                                    )
                                    val borderColor by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        animationSpec = OsuMotion.colorSpec(),
                                        label = "diffBorder"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clip(diffShape)
                                            .background(bgColor)
                                            .border(OsuRadius.hairline, borderColor, diffShape)
                                            .clickable { selectedDiffIndex = idx }
                                            .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = modeVis.iconRes),
                                            contentDescription = modeVis.label,
                                            tint = modeVis.color,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = d.version,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 120.dp)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.2f★", d.starRating),
                                            style = MaterialTheme.typography.labelSmall.tnum(),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                surfaces.textSecondary
                                            },
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        val targetMapId = chosenDiff?.beatmapId ?: set.primaryBeatmapId
                        if (hasActiveMpLobby && targetMapId > 0L) {
                            val modeInt = when (chosenDiff?.mode?.lowercase()) {
                                "taiko" -> 1
                                "fruits", "catch" -> 2
                                "mania" -> 3
                                else -> 0
                            }
                            Button(
                                onClick = { onSelectForMp(targetMapId, modeInt) },
                                shape = controlShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Select for MP (!mp map $targetMapId)",
                                    style = MaterialTheme.typography.labelLarge.tnum(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

@Composable
private fun DropdownTriggerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = LocalOsuSurfaces.current
    val shape = RoundedCornerShape(OsuRadius.md)
    val bgColor by animateColorAsState(
        targetValue = if (selected) surfaces.accentSubtle else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = OsuMotion.colorSpec(),
        label = "dropdownChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = OsuMotion.colorSpec(),
        label = "dropdownChipBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = OsuMotion.colorSpec(),
        label = "dropdownChipFg"
    )
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(bgColor)
            .border(OsuRadius.hairline, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = OsuSpacing.md, end = OsuSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

internal fun AudioQueueTrack.toBeatmapSetSearchResult(): BeatmapSetSearchResult {
    val diff = if (beatmapId > 0L) {
        BeatmapMetadata(
            beatmapId = beatmapId,
            beatmapsetId = beatmapsetId,
            title = title.ifBlank { "Beatmap #$beatmapsetId" },
            artist = artist,
            creator = creator,
            version = version.ifBlank { "Default" },
            starRating = starRating,
            bpm = bpm,
            totalLengthSeconds = totalLengthSeconds,
            ar = 0.0,
            cs = 0.0,
            od = 0.0,
            coverUrl = coverUrl,
            previewAudioUrl = previewAudioUrl,
            mode = mode.ifBlank { "osu" }
        )
    } else {
        null
    }
    return BeatmapSetSearchResult(
        beatmapsetId = beatmapsetId,
        primaryBeatmapId = beatmapId,
        title = title.ifBlank { "Beatmap #$beatmapsetId" },
        artist = artist,
        creator = creator,
        status = "ranked",
        bpm = bpm,
        coverUrl = coverUrl,
        previewAudioUrl = previewAudioUrl,
        difficulties = listOfNotNull(diff)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BeatmapStatsBottomSheet(
    beatmapSet: BeatmapSetSearchResult,
    initialDiffIndex: Int,
    onDismiss: () -> Unit,
    onResolveDetails: suspend (beatmapId: Long) -> Result<BeatmapMetadata>,
    onLoadScores: suspend (beatmapId: Long, mode: String?) -> Result<List<OsuScoreItem>>,
    onOpenPlayerProfile: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    savedBeatmapIds: Set<Long> = emptySet(),
    onToggleSaveBeatmap: (SavedBeatmapItem) -> Unit = {},
    oszDownloadState: OszDownloadState = OszDownloadState.Idle,
    onDownloadOsz: () -> Unit = {},
    onShareOsz: (Context) -> Unit = {},
    hasActiveMpLobby: Boolean = false,
    onSelectForMp: ((beatmapId: Long, modeInt: Int) -> Unit)? = null,
    initialIsLazer: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    var activeDiffIndex by remember(beatmapSet.beatmapsetId) {
        mutableIntStateOf(initialDiffIndex.coerceIn(0, (beatmapSet.difficulties.lastIndex).coerceAtLeast(0)))
    }
    val selectedSummary = beatmapSet.difficulties.getOrNull(activeDiffIndex)
    var enrichedMeta by remember(selectedSummary?.beatmapId) { mutableStateOf(selectedSummary) }
    var isRefreshingDetails by remember(selectedSummary?.beatmapId) { mutableStateOf(false) }

    val targetBeatmapId = enrichedMeta?.beatmapId ?: beatmapSet.primaryBeatmapId
    var selectedMods by remember(targetBeatmapId) { mutableStateOf(emptySet<String>()) }
    var accuracyPercent by remember(targetBeatmapId) { mutableDoubleStateOf(100.0) }
    var nMisses by remember(targetBeatmapId) { mutableIntStateOf(0) }
    var customCombo by remember(targetBeatmapId) { mutableStateOf<Int?>(null) }
    var customClockRate by remember(targetBeatmapId) { mutableStateOf<Double?>(null) }
    var isLazerMode by remember(targetBeatmapId, initialIsLazer) { mutableStateOf(initialIsLazer) }

    val rosuEngine = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RosuPpEngineEntryPoint::class.java
        ).rosuPpEngine()
    }
    var rawOsuText by remember(targetBeatmapId) { mutableStateOf<String?>(null) }
    var isOsuTextLoading by remember(targetBeatmapId) { mutableStateOf(targetBeatmapId > 0L) }

    LaunchedEffect(targetBeatmapId) {
        rawOsuText = null
        if (targetBeatmapId <= 0L) {
            isOsuTextLoading = false
            return@LaunchedEffect
        }
        isOsuTextLoading = true
        rawOsuText = runCatching { rosuEngine.getOrDownloadOsu(targetBeatmapId) }.getOrNull()
        isOsuTextLoading = false
    }

    LaunchedEffect(selectedSummary?.beatmapId) {
        val diff = selectedSummary ?: return@LaunchedEffect
        enrichedMeta = diff
        if (diff.beatmapId > 0L && (diff.maxCombo == null || diff.hp == 0.0 || diff.playCount == null)) {
            isRefreshingDetails = true
            onResolveDetails(diff.beatmapId).onSuccess { full ->
                enrichedMeta = full
            }
            isRefreshingDetails = false
        }
    }

    val targetMode = enrichedMeta?.mode ?: selectedSummary?.mode
    var leaderboardRetryNonce by remember(targetBeatmapId, targetMode) { mutableIntStateOf(0) }
    var leaderboardScores by remember(targetBeatmapId, targetMode) {
        mutableStateOf<List<OsuScoreItem>>(emptyList())
    }
    var isLoadingLeaderboard by remember(targetBeatmapId, targetMode) {
        mutableStateOf(false)
    }
    var leaderboardError by remember(targetBeatmapId, targetMode) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(targetBeatmapId, targetMode, leaderboardRetryNonce) {
        if (targetBeatmapId <= 0L) {
            leaderboardScores = emptyList()
            leaderboardError = null
            return@LaunchedEffect
        }
        isLoadingLeaderboard = true
        leaderboardError = null
        onLoadScores(targetBeatmapId, targetMode)
            .onSuccess { scores ->
                leaderboardScores = scores
            }
            .onFailure { err ->
                val raw = err.message?.trim().orEmpty()
                val looksTechnical = raw.isEmpty() ||
                    raw.contains("Exception", ignoreCase = true) ||
                    raw.contains("java.") ||
                    raw.contains("okhttp", ignoreCase = true) ||
                    raw.contains("http://", ignoreCase = true) ||
                    raw.contains("https://", ignoreCase = true) ||
                    raw.length > 120
                leaderboardError = if (looksTechnical) "Could not load global leaderboard." else raw
            }
        isLoadingLeaderboard = false
    }

    val beatmapUrl = if (targetBeatmapId > 0L) {
        "https://osu.ppy.sh/b/$targetBeatmapId"
    } else {
        "https://osu.ppy.sh/beatmapsets/${beatmapSet.beatmapsetId}"
    }

    val surfaces = LocalOsuSurfaces.current
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = OsuSpacing.xxl)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                AsyncImage(
                    model = beatmapSet.coverUrl,
                    contentDescription = beatmapSet.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(surfaces.mediaScrimTop, surfaces.mediaScrimBottom)
                            )
                        )
                )

                val statusLabel = (enrichedMeta?.status ?: beatmapSet.status)
                    .trim()
                    .lowercase(Locale.US)
                    .replaceFirstChar { it.uppercase() }
                OsuMicroTag(
                    text = statusLabel,
                    containerColor = surfaces.mediaControlSurface,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(OsuSpacing.md)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(OsuSpacing.md)
                ) {
                    Text(
                        text = beatmapSet.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${beatmapSet.artist} • ${beatmapSet.creator}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Layout-stable progress slot for difficulty detail fetch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OsuSpacing.xxs)
            ) {
                if (isRefreshingDetails) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
            ) {
                if (beatmapSet.difficulties.isNotEmpty()) {
                    Text(
                        text = "Difficulties (${beatmapSet.difficulties.size})",
                        style = MaterialTheme.typography.labelLarge.tnum(),
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        beatmapSet.difficulties.forEachIndexed { idx, d ->
                            val selected = activeDiffIndex == idx
                            val modeVis = osuModeVisual(d.mode)
                            val diffShape = RoundedCornerShape(OsuRadius.md)
                            val bgColor by animateColorAsState(
                                targetValue = if (selected) surfaces.accentSubtle else MaterialTheme.colorScheme.surfaceContainerLow,
                                animationSpec = OsuMotion.colorSpec(),
                                label = "sheetDiffBg"
                            )
                            val borderColor by animateColorAsState(
                                targetValue = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                animationSpec = OsuMotion.colorSpec(),
                                label = "sheetDiffBorder"
                            )
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 32.dp)
                                    .clip(diffShape)
                                    .background(bgColor)
                                    .border(OsuRadius.hairline, borderColor, diffShape)
                                    .clickable { activeDiffIndex = idx }
                                    .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                Icon(
                                    painter = painterResource(id = modeVis.iconRes),
                                    contentDescription = modeVis.label,
                                    tint = modeVis.color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = d.version,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                StarRatingBadge(starRating = d.starRating)
                            }
                        }
                    }
                }

                val meta = enrichedMeta
                if (meta != null) {
                    val simState = rememberRosuSimulationState(
                        meta = meta,
                        selectedMods = selectedMods,
                        accuracyPercent = accuracyPercent,
                        nMisses = nMisses,
                        combo = customCombo,
                        clockRate = customClockRate,
                        isLazer = isLazerMode
                    )
                    val sim = simState.stats
                    val hasMods = selectedMods.isNotEmpty()
                    val diffspikeProfile = remember(rawOsuText, sim.clockRate) {
                        rawOsuText?.let { text ->
                            OsuDiffspikeParser.parse(text, clockRate = sim.clockRate)
                        }
                    }

                    BeatmapPpSimulatorPanel(
                        meta = meta,
                        selectedMods = selectedMods,
                        accuracyPercent = accuracyPercent,
                        onToggleMod = { mod ->
                            selectedMods = OsuBeatmapCalculator.toggleMod(selectedMods, mod)
                        },
                        onAccuracyChange = { accuracyPercent = it },
                        onReset = {
                            selectedMods = emptySet()
                            accuracyPercent = 100.0
                            nMisses = 0
                            customCombo = null
                            customClockRate = null
                            isLazerMode = initialIsLazer
                        },
                        nMisses = nMisses,
                        onMissesChange = { nMisses = it.coerceAtLeast(0) },
                        combo = customCombo,
                        onComboChange = { customCombo = it },
                        clockRate = customClockRate,
                        onClockRateChange = { customClockRate = it },
                        isLazer = isLazerMode,
                        onLazerChange = { isLazerMode = it },
                        simState = simState
                    )

                    BeatmapDiffspikeGraphCard(
                        profile = diffspikeProfile,
                        isLoading = isOsuTextLoading,
                        beatmapId = targetBeatmapId
                    )

                    val plays = meta.playCount ?: beatmapSet.playCount
                    val favs = beatmapSet.favouriteCount ?: meta.favouriteCount
                    FlatCard(
                        shape = RoundedCornerShape(OsuRadius.lg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatGridCell(
                                label = if (hasMods) "Stars (Mod)" else "Stars",
                                value = if (sim.starRating.isFinite()) String.format(Locale.US, "%.2f★", sim.starRating) else "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatGridCell(
                                label = "BPM",
                                value = if (sim.bpm.isFinite()) sim.bpm.roundToInt().toString() else "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatGridCell(
                                label = "Length",
                                value = BeatmapUrlParser.formatDuration(sim.totalLengthSeconds),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OsuHairlineDivider()
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatGridCell("CS", String.format(Locale.US, "%.1f", sim.cs), Modifier.weight(1f))
                            StatGridCell("AR", String.format(Locale.US, "%.1f", sim.ar), Modifier.weight(1f))
                            StatGridCell("OD", String.format(Locale.US, "%.1f", sim.od), Modifier.weight(1f))
                            StatGridCell("HP", String.format(Locale.US, "%.1f", sim.hp), Modifier.weight(1f))
                        }
                        OsuHairlineDivider()
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatGridCell(
                                label = "Max Combo",
                                value = (sim.maxCombo ?: meta.maxCombo)?.let { "${numFmt.format(it)}x" } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatGridCell(
                                label = "Drain",
                                value = BeatmapUrlParser.formatDuration(sim.drainLengthSeconds),
                                modifier = Modifier.weight(1f)
                            )
                            if (plays != null || favs != null) {
                                StatGridCell(
                                    label = "Plays",
                                    value = plays?.let { numFmt.format(it) } ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                StatGridCell(
                                    label = "Favorites",
                                    value = favs?.let { numFmt.format(it) } ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (hasActiveMpLobby && onSelectForMp != null && targetBeatmapId > 0L) {
                    val modeInt = when (targetMode?.lowercase()) {
                        "taiko" -> 1
                        "fruits", "catch" -> 2
                        "mania" -> 3
                        else -> 0
                    }
                    Button(
                        onClick = { onSelectForMp(targetBeatmapId, modeInt) },
                        shape = controlShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Select for MP (!mp map $targetBeatmapId)",
                            style = MaterialTheme.typography.labelLarge.tnum(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val isSheetSaved = targetBeatmapId > 0L && targetBeatmapId in savedBeatmapIds
                if (targetBeatmapId > 0L) {
                    OutlinedButton(
                        onClick = {
                            onToggleSaveBeatmap(beatmapSet.toSavedItem(enrichedMeta ?: selectedSummary))
                        },
                        shape = controlShape,
                        border = osuHairlineBorder(
                            if (isSheetSaved) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = if (isSheetSaved) {
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isSheetSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Text(if (isSheetSaved) "Saved in Play Later" else "Play Later")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    val isDownloadingOsz = oszDownloadState is OszDownloadState.Downloading
                    val oszPct = (oszDownloadState as? OszDownloadState.Downloading)?.progressPercent
                    OutlinedButton(
                        onClick = onDownloadOsz,
                        enabled = beatmapSet.beatmapsetId > 0L && !isDownloadingOsz,
                        shape = controlShape,
                        border = osuHairlineBorder(
                            if (oszDownloadState is OszDownloadState.Downloaded) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        when (oszDownloadState) {
                            is OszDownloadState.Downloading -> {
                                if (oszPct != null) {
                                    CircularProgressIndicator(
                                        progress = { (oszPct / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text(
                                        text = "$oszPct%",
                                        style = MaterialTheme.typography.labelLarge.tnum(),
                                        maxLines = 1
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text("Downloading…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            is OszDownloadState.Downloaded -> {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Saved .osz", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Download .osz", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onShareOsz(context) },
                        enabled = beatmapSet.beatmapsetId > 0L && !isDownloadingOsz,
                        shape = controlShape,
                        border = osuHairlineBorder(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Text("Share .osz", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(beatmapUrl)))
                            }.onFailure {
                                onShowMessage("No browser available to open link")
                            }
                        },
                        shape = controlShape,
                        border = osuHairlineBorder(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Text("Open Browser", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(beatmapUrl))
                            onShowMessage("Copied link: $beatmapUrl")
                        },
                        shape = controlShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Text("Copy Link", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OsuHairlineDivider()

                OsuSectionHeader(
                    title = "Global Top 50",
                    trailingMeta = if (leaderboardScores.isNotEmpty()) "${leaderboardScores.size} scores" else null
                )

                when {
                    isLoadingLeaderboard && leaderboardScores.isEmpty() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            repeat(4) {
                                SkeletonLineRow()
                            }
                        }
                    }
                    leaderboardError != null -> {
                        ExploreErrorCard(
                            message = leaderboardError ?: "Could not load global leaderboard",
                            onRetry = { leaderboardRetryNonce++ }
                        )
                    }
                    leaderboardScores.isEmpty() -> {
                        Text(
                            text = if (targetBeatmapId <= 0L) {
                                "Select a difficulty to view global scores"
                            } else {
                                "No global scores on this difficulty yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                            modifier = Modifier.padding(vertical = OsuSpacing.sm)
                        )
                    }
                    else -> {
                        FlatCard(
                            shape = RoundedCornerShape(OsuRadius.lg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val visibleScores = leaderboardScores.take(50)
                            visibleScores.forEachIndexed { index, score ->
                                BeatmapLeaderboardRow(
                                    rank = index + 1,
                                    score = score,
                                    numFmt = numFmt,
                                    onClick = {
                                        val target = if (score.userId > 0L) {
                                            score.userId.toString()
                                        } else {
                                            score.username
                                        }
                                        if (target.isNotBlank()) {
                                            onDismiss()
                                            onOpenPlayerProfile(target)
                                        }
                                    }
                                )
                                if (index < visibleScores.lastIndex) {
                                    OsuHairlineDivider()
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
private fun BeatmapDiffspikeGraphCard(
    profile: BeatmapDiffspikeProfile?,
    isLoading: Boolean,
    beatmapId: Long
) {
    if (beatmapId <= 0L) return
    val surfaces = LocalOsuSurfaces.current
    FlatCard(
        shape = RoundedCornerShape(OsuRadius.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hit Object Density & Diffspikes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        isLoading -> "Parsing .osu hit objects…"
                        profile != null -> "Avg ${String.format(Locale.US, "%.1f", profile.averageObjectsPerSecond)} obj/s • ${profile.totalObjects} objects"
                        else -> "Density graph unavailable offline"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (profile != null) {
                    Spacer(Modifier.width(OsuSpacing.sm))
                    OsuMicroTag(
                        text = String.format(Locale.US, "Peak %.1f obj/s", profile.peakObjectsPerSecond),
                        containerColor = surfaces.accentSubtle,
                        contentColor = MaterialTheme.colorScheme.primary,
                        isNumeric = true
                    )
                }
            }

            if (isLoading && profile == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OsuSpacing.xxs),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(OsuRadius.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            } else if (profile != null && profile.buckets.size >= 2) {
                val trackGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                val graphBg = MaterialTheme.colorScheme.surfaceContainer
                val peakColor = MaterialTheme.colorScheme.primary
                val buckets = profile.buckets
                val reusableLinePath = remember { Path() }
                val reusableAreaPath = remember { Path() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(OsuRadius.sm))
                        .background(graphBg)
                        .padding(horizontal = OsuSpacing.xs, vertical = OsuSpacing.xs)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) return@Canvas

                        // Subtle horizontal baseline + midpoint guide
                        drawLine(
                            color = trackGridColor,
                            start = Offset(0f, h * 0.5f),
                            end = Offset(w, h * 0.5f),
                            strokeWidth = 1f
                        )

                        val bucketWidth = w / buckets.size.toFloat()
                        buckets.forEachIndexed { idx, bucket ->
                            if (bucket.isPeakDiffspike) {
                                val xLeft = idx * bucketWidth
                                drawRect(
                                    color = peakColor.copy(alpha = 0.14f),
                                    topLeft = Offset(xLeft, 0f),
                                    size = androidx.compose.ui.geometry.Size(bucketWidth.coerceAtLeast(2f), h)
                                )
                            }
                        }

                        val linePath = reusableLinePath.apply { rewind() }
                        val areaPath = reusableAreaPath.apply { rewind() }
                        val stepX = w / (buckets.size - 1).coerceAtLeast(1).toFloat()
                        fun safeNorm(v: Float): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f

                        buckets.forEachIndexed { idx, bucket ->
                            val x = idx * stepX
                            val y = h - (safeNorm(bucket.normalizedIntensity) * (h * 0.88f)) - (h * 0.04f)
                            if (idx == 0) {
                                linePath.moveTo(x, y)
                                areaPath.moveTo(x, h)
                                areaPath.lineTo(x, y)
                            } else {
                                val prevY = h - (safeNorm(buckets[idx - 1].normalizedIntensity) * (h * 0.88f)) - (h * 0.04f)
                                val prevX = (idx - 1) * stepX
                                val midX = (prevX + x) * 0.5f
                                linePath.cubicTo(midX, prevY, midX, y, x, y)
                                areaPath.cubicTo(midX, prevY, midX, y, x, y)
                            }
                            if (idx == buckets.lastIndex) {
                                areaPath.lineTo(x, h)
                                areaPath.close()
                            }
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    peakColor.copy(alpha = 0.34f),
                                    peakColor.copy(alpha = 0.04f)
                                ),
                                startY = 0f,
                                endY = h
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = peakColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Highlight peak diffspike crests
                        buckets.forEachIndexed { idx, bucket ->
                            if (bucket.isPeakDiffspike) {
                                val x = idx * stepX
                                val y = h - (safeNorm(bucket.normalizedIntensity) * (h * 0.88f)) - (h * 0.04f)
                                drawCircle(
                                    color = graphBg,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                                drawCircle(
                                    color = peakColor,
                                    radius = 1.6.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                }

                val midSeconds = (profile.drainDurationSeconds / 2).coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0:00",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted
                    )
                    Text(
                        text = BeatmapUrlParser.formatDuration(midSeconds),
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted
                    )
                    Text(
                        text = "Drain ${BeatmapUrlParser.formatDuration(profile.drainDurationSeconds)}",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun StatGridCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val surfaces = LocalOsuSurfaces.current
    Column(
        modifier = modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = surfaces.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(OsuSpacing.xxs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.tnum(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BeatmapLeaderboardRow(
    rank: Int,
    score: OsuScoreItem,
    numFmt: NumberFormat,
    onClick: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val rankColor = when (rank) {
        1 -> PodiumGold
        2 -> PodiumSilver
        3 -> PodiumBronze
        else -> surfaces.textMuted
    }
    val playerName = score.username.ifBlank { "Player" }
    val flag = countryCodeToFlagEmoji(score.userCountryCode)
    val playerLabel = if (flag.isNotEmpty()) "$flag $playerName" else playerName
    val gameplayMods = score.gameplayModsLabel
    val detailsLine = listOfNotNull(
        gameplayMods.takeIf { it.isNotEmpty() },
        String.format(Locale.US, "%.2f%%", score.accuracy),
        "${numFmt.format(score.maxCombo)}x",
        numFmt.format(score.score)
    ).joinToString(" • ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelMedium.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = rankColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp)
        )
        PlayerAvatar(
            name = playerName,
            avatarUrl = score.userAvatarUrl,
            size = 32.dp
        )
        Spacer(Modifier.width(OsuSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                Text(
                    text = playerLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                OsuMicroTag(
                    text = score.clientBadgeLabel,
                    containerColor = if (score.isLazer) {
                        surfaces.accentSubtle
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (score.isLazer) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = detailsLine,
                style = MaterialTheme.typography.labelSmall.tnum(),
                color = surfaces.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(OsuSpacing.sm))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
        ) {
            GradeBadge(grade = score.gradeLabel)
            score.pp?.let { pp ->
                Text(
                    text = String.format(Locale.US, "%.1f pp", pp),
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankingsAndSearchTab(
    rankingMode: String,
    onRankingModeChange: (String) -> Unit,
    rankingCountry: String,
    onRankingCountryChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    isSearchActive: Boolean,
    players: List<PlayerProfile>,
    isLoading: Boolean,
    errorMessage: String?,
    onLoadRankings: () -> Unit,
    onSearchPlayers: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onToggleTrackPp: (PlayerProfile) -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val modes = remember {
        listOf("osu" to "osu!", "taiko" to "taiko", "fruits" to "catch", "mania" to "mania")
    }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    val allCountries = remember { buildAllCountryOptions() }
    val selectedCountryOption = remember(rankingCountry, allCountries) {
        if (rankingCountry.isBlank()) null
        else allCountries.firstOrNull { it.code.equals(rankingCountry, ignoreCase = true) }
    }
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }

    val controlShape = RoundedCornerShape(OsuRadius.md)

    if (showCountryPicker) {
        CountryPickerBottomSheet(
            countries = allCountries,
            selectedCode = rankingCountry,
            onSelectCountry = { code ->
                showCountryPicker = false
                onRankingCountryChange(code)
            },
            onDismiss = { showCountryPicker = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = OsuSpacing.md)
    ) {
        item(key = "player_search_rankings", contentType = "player_search_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OsuSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search any osu! player…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = onClearSearch) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear player search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = controlShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchPlayers() }),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSearchPlayers,
                        shape = controlShape
                    ) {
                        Text("Find")
                    }
                }

                val selectedModeIdx = modes.indexOfFirst { it.first == rankingMode }.coerceAtLeast(0)
                OsuSegmentedBar(
                    items = modes,
                    selectedIndex = selectedModeIdx,
                    onSelect = { onRankingModeChange(modes[it].first) },
                    labelProvider = { it.second }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isGlobal = rankingCountry.isBlank() && !isSearchActive
                    OsuFilterChip(
                        label = "Global",
                        selected = isGlobal,
                        leadingIcon = Icons.Rounded.Public,
                        onClick = { onRankingCountryChange("") }
                    )

                    val isCountrySelected = rankingCountry.isNotBlank() && !isSearchActive
                    val countryLabel = selectedCountryOption?.let {
                        if (it.flag.isNotEmpty()) "${it.flag} ${it.name}" else it.name
                    } ?: "Country"
                    DropdownTriggerChip(
                        label = countryLabel,
                        selected = isCountrySelected,
                        onClick = { showCountryPicker = true },
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    OsuFilterChip(
                        label = if (isSearchActive) "Rankings" else "Refresh",
                        selected = false,
                        leadingIcon = Icons.Rounded.Refresh,
                        onClick = {
                            if (isSearchActive) onClearSearch() else onLoadRankings()
                        }
                    )
                }
                Spacer(Modifier.height(OsuSpacing.xxs))
            }
            OsuHairlineDivider()
        }

        if (isLoading && players.isNotEmpty()) {
            item(key = "players_refresh_bar", contentType = "loading_bar") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OsuSpacing.xxs),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }

        if (errorMessage != null && players.isNotEmpty()) {
            item(key = "players_error_inline", contentType = "error") {
                Box(modifier = Modifier.padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm)) {
                    ExploreErrorCard(
                        message = errorMessage,
                        onRetry = if (isSearchActive) onSearchPlayers else onLoadRankings
                    )
                }
            }
        }

        if (isLoading && players.isEmpty()) {
            items(count = 6, key = { "player_skel_$it" }, contentType = { "skeleton" }) {
                Column(modifier = Modifier.padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm)) {
                    SkeletonLineRow()
                }
                OsuHairlineDivider(modifier = Modifier.padding(start = 72.dp))
            }
        } else if (errorMessage != null && players.isEmpty()) {
            item(key = "players_error", contentType = "error") {
                Box(modifier = Modifier.padding(OsuSpacing.lg)) {
                    ExploreErrorCard(
                        message = errorMessage,
                        onRetry = if (isSearchActive) onSearchPlayers else onLoadRankings
                    )
                }
            }
        } else if (players.isEmpty()) {
            item(key = "players_empty", contentType = "empty") {
                Box(modifier = Modifier.padding(OsuSpacing.lg)) {
                    ExploreEmptyCard(
                        title = if (isSearchActive) "No matching players" else "No rankings loaded",
                        body = if (isSearchActive) {
                            "No players found matching \"${searchQuery.trim()}\""
                        } else {
                            "Browse Global or Country PP leaderboards, or search by username"
                        },
                        actionLabel = if (isSearchActive) "Back to Rankings" else "Load Rankings",
                        onAction = if (isSearchActive) onClearSearch else onLoadRankings,
                        icon = if (isSearchActive) Icons.Rounded.SearchOff else Icons.Rounded.Leaderboard
                    )
                }
            }
        } else {
            itemsIndexed(
                items = players,
                key = { idx, it -> "player_${idx}_${it.userId}_${it.username}" },
                contentType = { _, _ -> "player_rank_row" }
            ) { _, player ->
                val trackTint by animateColorAsState(
                    targetValue = if (player.isTrackedForPp) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "playerTrackTint"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile(player.username) }
                        .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    player.globalRank?.let { rank ->
                        Text(
                            text = "#${numFmt.format(rank)}",
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.widthIn(min = 56.dp)
                        )
                    }
                    PlayerAvatar(
                        name = player.username,
                        avatarUrl = player.avatarUrl,
                        size = 40.dp,
                        showOnlineDot = true,
                        isOnline = player.isOnline
                    )
                    Spacer(Modifier.width(OsuSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = player.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statsLine = buildList {
                            player.pp?.takeIf { it.isFinite() }?.let { add("${numFmt.format(it.roundToInt())}pp") }
                            player.accuracy?.takeIf { it.isFinite() }?.let { add(String.format(Locale.US, "%.2f%%", it)) }
                            if (player.countryCode != "XX" && player.countryCode.isNotBlank()) {
                                val flag = countryCodeToFlagEmoji(player.countryCode)
                                add(if (flag.isNotEmpty()) "$flag ${player.countryCode}" else player.countryCode)
                            }
                        }.joinToString(" • ")
                        if (statsLine.isNotEmpty()) {
                            Text(
                                text = statsLine,
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = surfaces.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (player.userId > 0L) {
                        IconButton(onClick = { onToggleTrackPp(player) }) {
                            Icon(
                                imageVector = if (player.isTrackedForPp) {
                                    Icons.Rounded.NotificationsActive
                                } else {
                                    Icons.Rounded.NotificationsNone
                                },
                                contentDescription = if (player.isTrackedForPp) {
                                    "Stop tracking player"
                                } else {
                                    "Track player"
                                },
                                tint = trackTint
                            )
                        }
                    }
                }
                OsuHairlineDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerBottomSheet(
    countries: List<CountryOption>,
    selectedCode: String,
    onSelectCountry: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(countries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            val popularOrder = POPULAR_OSU_COUNTRY_CODES.withIndex().associate { it.value to it.index }
            countries.sortedWith(
                compareBy<CountryOption> { popularOrder[it.code] ?: Int.MAX_VALUE }
                    .thenBy { it.name }
            )
        } else {
            countries.filter {
                it.name.contains(q, ignoreCase = true) || it.code.contains(q, ignoreCase = true)
            }.sortedBy { it.name }
        }
    }

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
                .imePadding()
                .padding(horizontal = OsuSpacing.lg)
                .padding(bottom = OsuSpacing.xxl)
        ) {
            Text(
                text = "Select Country",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(OsuSpacing.md))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search country or ISO code…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(OsuRadius.md),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(OsuSpacing.md))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                if (searchQuery.isBlank() || "global".contains(searchQuery.trim(), ignoreCase = true)) {
                    item(key = "global_option", contentType = "country_global") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onSelectCountry("") }
                                .padding(vertical = OsuSpacing.md, horizontal = OsuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (selectedCode.isBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(OsuSpacing.md))
                            Text(
                                text = "Global (All Countries)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedCode.isBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedCode.isBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OsuHairlineDivider()
                    }
                }

                items(
                    items = filteredCountries,
                    key = { it.code },
                    contentType = { "country_option" }
                ) { option ->
                    val isSelected = selectedCode.equals(option.code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectCountry(option.code) }
                            .padding(vertical = OsuSpacing.md, horizontal = OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (option.flag.isNotEmpty()) {
                                Text(
                                    text = option.flag,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        Spacer(Modifier.width(OsuSpacing.md))
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = option.code,
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalOsuSurfaces.current.textMuted
                        )
                    }
                    OsuHairlineDivider()
                }
            }
        }
    }
}

private fun buildAllCountryOptions(): List<CountryOption> {
    return Locale.getISOCountries()
        .asSequence()
        .map { it.uppercase(Locale.US) }
        .filter { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
        .distinct()
        .mapNotNull { code ->
            val name = Locale("", code).getDisplayCountry(Locale.ENGLISH).trim()
            if (name.isBlank()) null
            else CountryOption(
                code = code,
                name = name,
                flag = countryCodeToFlagEmoji(code)
            )
        }
        .sortedBy { it.name }
        .toList()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankPpCalculatorTab(
    mode: String,
    isLoading: Boolean,
    result: RankPpConversionResult?,
    errorMessage: String?,
    onModeChange: (String) -> Unit,
    onLookupPpForRank: (Int) -> Unit,
    onLookupRankForPp: (Double) -> Unit,
    onOpenPlayerProfile: (String) -> Unit,
    selfProfile: PlayerProfile? = null,
    selfProfileMode: String = "osu",
    selfBestScores: List<OsuScoreItem> = emptyList(),
    selfUsername: String = "",
    onEstimatePpForRank: (suspend (mode: String, targetRank: Int) -> Result<RankPpConversionResult>)? = null
) {
    val surfaces = LocalOsuSurfaces.current
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val modes = remember {
        listOf("osu" to "osu!", "taiko" to "taiko", "fruits" to "catch", "mania" to "mania")
    }
    val directions = remember {
        listOf(true to "Rank → PP", false to "PP → Rank")
    }
    val rankPresets = remember {
        listOf(
            100 to "#100",
            500 to "#500",
            1_000 to "#1k",
            5_000 to "#5k",
            10_000 to "#10k",
            50_000 to "#50k",
            100_000 to "#100k"
        )
    }
    val ppPresets = remember { listOf(2_000, 4_000, 6_000, 8_000, 10_000, 12_000, 15_000) }

    var isRankToPp by rememberSaveable { mutableStateOf(true) }
    var inputText by rememberSaveable { mutableStateOf("10000") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    val triggerLookup = {
        if (isRankToPp) {
            val parsedRank = RankPpMath.parsePositiveRankInput(inputText)
            if (parsedRank == null) {
                inputError = "Enter a rank (#1 – #100,000,000)"
            } else {
                inputError = null
                onLookupPpForRank(parsedRank)
            }
        } else {
            val parsedPp = RankPpMath.parsePositivePpInput(inputText, maxPp = RankPpMath.MAX_TOTAL_PP)
            if (parsedPp == null) {
                inputError = "Enter a valid PP amount (> 0)"
            } else {
                inputError = null
                onLookupRankForPp(parsedPp)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OsuSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
    ) {
        item(key = "rank_pp_controls", contentType = "rank_pp_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                Text(
                    text = "Live leaderboard for #1–#10,000; calibrated curve beyond #10,000",
                    style = MaterialTheme.typography.bodySmall.tnum(),
                    color = surfaces.textSecondary
                )

                val selectedModeIdx = modes.indexOfFirst { it.first == mode }.coerceAtLeast(0)
                OsuSegmentedBar(
                    items = modes,
                    selectedIndex = selectedModeIdx,
                    onSelect = { onModeChange(modes[it].first) },
                    labelProvider = { it.second }
                )

                OsuSegmentedBar(
                    items = directions,
                    selectedIndex = if (isRankToPp) 0 else 1,
                    onSelect = { idx ->
                        val wantRankToPp = directions[idx].first
                        if (wantRankToPp != isRankToPp) {
                            isRankToPp = wantRankToPp
                            inputText = if (wantRankToPp) "10000" else "8000"
                            inputError = null
                        }
                    },
                    labelProvider = { it.second }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            if (it.length <= 15) {
                                inputText = it
                                inputError = null
                            }
                        },
                        label = { Text(if (isRankToPp) "Target Global Rank" else "Target Total PP") },
                        placeholder = { Text(if (isRankToPp) "e.g. 5000" else "e.g. 8500") },
                        isError = inputError != null,
                        supportingText = inputError?.let { msg -> { Text(msg) } },
                        textStyle = LocalTextStyle.current.tnum(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isRankToPp) KeyboardType.Number else KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { triggerLookup() }),
                        shape = controlShape,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = triggerLookup,
                        enabled = !isLoading,
                        shape = controlShape
                    ) {
                        Text(
                            text = if (isLoading) "…" else "Convert",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    if (isRankToPp) {
                        rankPresets.forEach { (rank, label) ->
                            OsuFilterChip(
                                label = label,
                                selected = inputText.trim() == rank.toString(),
                                isNumeric = true,
                                onClick = {
                                    inputText = rank.toString()
                                    inputError = null
                                    onLookupPpForRank(rank)
                                }
                            )
                        }
                    } else {
                        ppPresets.forEach { pp ->
                            OsuFilterChip(
                                label = "${numFmt.format(pp)}pp",
                                selected = inputText.trim() == pp.toString(),
                                isNumeric = true,
                                onClick = {
                                    inputText = pp.toString()
                                    inputError = null
                                    onLookupRankForPp(pp.toDouble())
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && result != null) {
            item(key = "rank_pp_loading_bar", contentType = "loading_bar") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OsuSpacing.xxs),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }

        if (errorMessage != null) {
            item(key = "rank_pp_error", contentType = "error") {
                ExploreErrorCard(message = errorMessage, onRetry = triggerLookup)
            }
        }

        if (isLoading && result == null) {
            item(key = "rank_pp_skeleton", contentType = "skeleton") {
                BeatmapResultSkeletonCard()
            }
        } else if (result != null) {
            item(key = "rank_pp_result", contentType = "rank_pp_result") {
                FlatCard(
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OsuModeBadge(mode = result.mode)
                        Text(
                            text = if (result.isExactLeaderboardMatch) "Live Leaderboard" else "Curve Estimate",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (result.isExactLeaderboardMatch) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                surfaces.textSecondary
                            }
                        )
                    }
                    OsuHairlineDivider()
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatGridCell(
                            label = "Global Rank",
                            value = "#${numFmt.format(RankPpMath.clampRank(result.targetRank))}",
                            modifier = Modifier.weight(1f)
                        )
                        StatGridCell(
                            label = "Total PP",
                            value = RankPpMath.sanitizeFiniteNonNegative(result.estimatedPp)
                                ?.let { String.format(Locale.US, "%,.1f pp", it) } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    result.nearestPlayer?.let { p ->
                        OsuHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = if (p.userId > 0L) p.userId.toString() else p.username
                                    onOpenPlayerProfile(target)
                                }
                                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerAvatar(
                                name = p.username,
                                avatarUrl = p.avatarUrl,
                                size = 36.dp
                            )
                            Spacer(Modifier.width(OsuSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                val flag = countryCodeToFlagEmoji(p.countryCode)
                                Text(
                                    text = if (flag.isNotEmpty()) "$flag ${p.username}" else p.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val rankLabel = RankPpMath.sanitizeRank(p.globalRank)
                                    ?.let { "#${numFmt.format(it)}" } ?: "Unranked"
                                val ppLabel = RankPpMath.sanitizeFiniteNonNegative(p.pp)
                                    ?.let { String.format(Locale.US, "%,.1f pp", it) } ?: "—"
                                Text(
                                    text = "$rankLabel • $ppLabel",
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "View Profile",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item(key = "target_rank_roadmap", contentType = "target_rank_roadmap") {
            TargetRankRoadmapCard(
                mode = mode,
                selfProfile = selfProfile,
                selfProfileMode = selfProfileMode,
                selfBestScores = selfBestScores,
                selfUsername = selfUsername,
                onEstimatePpForRank = onEstimatePpForRank
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetRankRoadmapCard(
    mode: String,
    selfProfile: PlayerProfile?,
    selfProfileMode: String,
    selfBestScores: List<OsuScoreItem>,
    selfUsername: String,
    onEstimatePpForRank: (suspend (mode: String, targetRank: Int) -> Result<RankPpConversionResult>)?
) {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlayerCompareEntryPoint::class.java
        )
    }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val roadmapPresets = remember {
        listOf(
            100_000 to "#100k",
            50_000 to "#50k",
            25_000 to "#25k",
            10_000 to "#10k",
            5_000 to "#5k",
            1_000 to "#1k"
        )
    }

    var targetInput by rememberSaveable { mutableStateOf("10000") }
    var activeTargetRank by rememberSaveable { mutableStateOf<Int?>(null) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var requestNonce by remember { mutableIntStateOf(0) }

    var resolvedSelfProfile by remember(mode, selfProfile, selfProfileMode) {
        mutableStateOf(
            if (selfProfile != null && selfProfileMode.equals(mode, ignoreCase = true)) {
                selfProfile
            } else {
                null
            }
        )
    }
    var resolvedBestScores by remember(mode, selfBestScores, selfProfileMode) {
        mutableStateOf(
            if (selfProfileMode.equals(mode, ignoreCase = true)) selfBestScores else emptyList()
        )
    }

    var conversionResult by remember(mode) { mutableStateOf<RankPpConversionResult?>(null) }
    var isCalculating by remember(mode) { mutableStateOf(false) }
    var calcError by remember(mode) { mutableStateOf<String?>(null) }

    LaunchedEffect(mode, selfProfile, selfProfileMode, selfBestScores, selfUsername) {
        if (selfProfile != null && selfProfileMode.equals(mode, ignoreCase = true)) {
            resolvedSelfProfile = selfProfile
            resolvedBestScores = selfBestScores
            return@LaunchedEffect
        }
        val cached = runCatching { entryPoint.selfProfileCacheDao().getByKey() }.getOrNull()
        if (cached != null && resolvedSelfProfile == null && cached.mode.equals(mode, ignoreCase = true)) {
            resolvedSelfProfile = PlayerProfile(
                userId = cached.userId,
                username = cached.username,
                avatarUrl = cached.avatarUrl,
                coverUrl = cached.coverUrl,
                countryCode = cached.countryCode,
                countryName = cached.countryName,
                globalRank = cached.globalRank,
                countryRank = cached.countryRank,
                pp = cached.pp,
                accuracy = cached.accuracy,
                playCount = cached.playCount,
                isOnline = cached.isOnline,
                mode = cached.mode
            )
        }
        val target = cached?.userId?.takeIf { it > 0L }?.toString()
            ?: selfProfile?.userId?.takeIf { it > 0L }?.toString()
            ?: cached?.username?.takeIf { it.isNotBlank() }
            ?: selfUsername.trim()
        if (target.isNotBlank()) {
            val modeProf = runCatching {
                entryPoint.chatRepository().getPlayerProfile(target, mode).getOrNull()
            }.getOrNull()
            if (modeProf != null) {
                resolvedSelfProfile = modeProf
                if (modeProf.userId > 0L) {
                    val scores = runCatching {
                        entryPoint.chatRepository().getUserScores(
                            usernameOrId = modeProf.userId.toString(),
                            type = "best",
                            includeFails = false,
                            mode = mode,
                            limit = 100
                        ).getOrNull()
                    }.getOrNull()
                    if (scores != null) {
                        resolvedBestScores = scores
                    }
                }
            }
        }
    }

    LaunchedEffect(activeTargetRank, mode, requestNonce) {
        val rank = activeTargetRank ?: return@LaunchedEffect
        isCalculating = true
        calcError = null
        val estimator = onEstimatePpForRank ?: { m, r ->
            entryPoint.chatRepository().estimatePpForRank(m, r)
        }
        estimator(mode, rank)
            .onSuccess { res ->
                conversionResult = res
                calcError = null
            }
            .onFailure { err ->
                calcError = err.message?.take(120) ?: "Could not estimate target PP."
            }
        isCalculating = false
    }

    val plan = remember(conversionResult, resolvedSelfProfile, resolvedBestScores) {
        val res = conversionResult ?: return@remember null
        RankPpMath.calculateTargetRankPlan(
            targetRank = res.targetRank,
            targetPp = res.estimatedPp,
            currentTotalPp = resolvedSelfProfile?.pp,
            existingScores = resolvedBestScores.map { it.pp },
            maxPlaysCap = 50
        )
    }

    val triggerRoadmapLookup: () -> Unit = {
        val parsed = RankPpMath.parsePositiveRankInput(targetInput)
        if (parsed == null) {
            inputError = "Enter a rank (#1 – #100,000,000)"
        } else {
            inputError = null
            activeTargetRank = parsed
            requestNonce++
        }
    }

    val surfaces = LocalOsuSurfaces.current
    val controlShape = RoundedCornerShape(OsuRadius.md)

    FlatCard(
        shape = RoundedCornerShape(OsuRadius.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Target Rank Roadmap",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val selfSummary = resolvedSelfProfile?.let { prof ->
                        val rankTxt = RankPpMath.sanitizeRank(prof.globalRank)?.let { "#${numFmt.format(it)}" } ?: "Unranked"
                        val ppTxt = RankPpMath.sanitizeFiniteNonNegative(prof.pp)
                            ?.let { "${numFmt.format(it.roundToInt())}pp" } ?: "—"
                        "${prof.username}: $rankTxt • $ppTxt"
                    } ?: "Sign in with osu! for personalized PP gap"
                    Text(
                        text = selfSummary,
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OsuModeBadge(mode = mode, compact = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = {
                        if (it.length <= 12) {
                            targetInput = it
                            inputError = null
                        }
                    },
                    label = { Text("Goal Global Rank") },
                    placeholder = { Text("e.g. 10000") },
                    isError = inputError != null,
                    supportingText = inputError?.let { msg -> { Text(msg) } },
                    textStyle = LocalTextStyle.current.tnum(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { triggerRoadmapLookup() }),
                    shape = controlShape,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = triggerRoadmapLookup,
                    enabled = !isCalculating,
                    shape = controlShape
                ) {
                    Text(if (isCalculating) "…" else "Plan")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                roadmapPresets.forEach { (rank, label) ->
                    OsuFilterChip(
                        label = label,
                        selected = activeTargetRank == rank,
                        isNumeric = true,
                        onClick = {
                            targetInput = rank.toString()
                            inputError = null
                            activeTargetRank = rank
                            requestNonce++
                        }
                    )
                }
            }

            if (isCalculating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OsuSpacing.xxs),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            if (calcError != null) {
                Text(
                    text = calcError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (plan != null) {
                OsuHairlineDivider()

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatGridCell(
                        label = "Goal (#${numFmt.format(plan.targetRank)})",
                        value = String.format(Locale.US, "%,.0f pp", plan.targetPp),
                        modifier = Modifier.weight(1f)
                    )
                    StatGridCell(
                        label = "Current PP",
                        value = String.format(Locale.US, "%,.0f pp", plan.currentTotalPp),
                        modifier = Modifier.weight(1f)
                    )
                    StatGridCell(
                        label = "PP Gap",
                        value = if (plan.ppGap <= 0.01) {
                            "Reached"
                        } else {
                            String.format(Locale.US, "+%,.0f pp", plan.ppGap)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                OsuHairlineDivider()

                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    Text(
                        text = if (plan.ppGap <= 0.01) {
                            "Already above #${numFmt.format(plan.targetRank)}"
                        } else {
                            "Weighting-aware play paths (0.95^i + ${plan.bonusPp.roundToInt()} bonus pp)"
                        },
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textSecondary
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(OsuRadius.md))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        plan.scenarios.forEachIndexed { idx, scenario ->
                            if (idx > 0) {
                                OsuHairlineDivider()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${scenario.label} (${String.format(Locale.US, "%,.0f pp", scenario.scorePp)} plays)",
                                        style = MaterialTheme.typography.labelMedium.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Est. total ${String.format(Locale.US, "%,.0f pp", scenario.resultingTotalPp)}",
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = surfaces.textSecondary
                                    )
                                }
                                val playsText = when {
                                    scenario.playsNeeded == 0 -> "0 plays"
                                    scenario.cappedAtLimit -> "${scenario.playsNeeded}+ plays"
                                    scenario.playsNeeded == 1 -> "1 play"
                                    else -> "${scenario.playsNeeded} plays"
                                }
                                Text(
                                    text = playsText,
                                    style = MaterialTheme.typography.titleSmall.tnum(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (scenario.cappedAtLimit) {
                                        surfaces.textSecondary
                                    } else if (scenario.playsNeeded == 0) {
                                        StatusOnline
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayLaterQueueTab(
    totalSavedCount: Int,
    savedBeatmaps: List<SavedBeatmapItem>,
    availableTags: List<String>,
    selectedTag: String?,
    sortOption: PlayLaterSortOption,
    sortDescending: Boolean,
    isSendingAll: Boolean,
    audioState: AudioPlaybackState,
    oszDownloadStates: Map<Long, OszDownloadState>,
    onToggleAudio: (beatmapsetId: Long, previewUrl: String) -> Unit,
    onPlaySavedQueue: (List<SavedBeatmapItem>, Int) -> Unit,
    onSelectTag: (String?) -> Unit,
    onSelectSortOption: (PlayLaterSortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onUpdateTags: (Long, List<String>) -> Unit,
    onDownloadOsz: (beatmapsetId: Long, artist: String, title: String) -> Unit,
    onShareOsz: (context: Context, beatmapsetId: Long, artist: String, title: String) -> Unit,
    onSendOneToInGame: (SavedBeatmapItem) -> Unit,
    onSendAllToInGame: () -> Unit,
    onRemove: (Long) -> Unit,
    onBrowseBeatmaps: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val surfaces = LocalOsuSurfaces.current
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val controlShape = RoundedCornerShape(OsuRadius.md)
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var tagEditingItem by remember { mutableStateOf<SavedBeatmapItem?>(null) }

    tagEditingItem?.let { editing ->
        PlayLaterTagEditorDialog(
            item = editing,
            onDismiss = { tagEditingItem = null },
            onSave = { updatedTags ->
                onUpdateTags(editing.beatmapId, updatedTags)
                tagEditingItem = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OsuSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
    ) {
        item(key = "play_later_header", contentType = "play_later_header") {
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countText = when {
                        selectedTag != null -> "${savedBeatmaps.size}/$totalSavedCount maps"
                        savedBeatmaps.size == 1 -> "1 map queued"
                        else -> "${savedBeatmaps.size} maps queued"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelLarge.tnum(),
                        color = surfaces.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onPlaySavedQueue(savedBeatmaps, 0) },
                            enabled = savedBeatmaps.isNotEmpty(),
                            shape = controlShape,
                            border = osuHairlineBorder(),
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text("Play Queue")
                        }
                        Button(
                            onClick = onSendAllToInGame,
                            enabled = totalSavedCount > 0 && !isSendingAll,
                            shape = controlShape,
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                        ) {
                            if (isSendingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Sending…")
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Sync All")
                            }
                        }
                    }
                }

                if (totalSavedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Box {
                            DropdownTriggerChip(
                                label = "Sort: ${sortOption.label}",
                                selected = false,
                                onClick = { sortMenuExpanded = true }
                            )
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                                shape = RoundedCornerShape(OsuRadius.md),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = osuHairlineBorder()
                            ) {
                                PlayLaterSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        trailingIcon = {
                                            if (sortOption == option) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            sortMenuExpanded = false
                                            onSelectSortOption(option)
                                        }
                                    )
                                }
                            }
                        }

                        OsuFilterChip(
                            label = if (sortDescending) "Desc" else "Asc",
                            selected = true,
                            leadingIcon = if (sortDescending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            onClick = onToggleSortDirection
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        item(key = "tag_filter_all", contentType = "tag_filter") {
                            OsuFilterChip(
                                label = "All",
                                selected = selectedTag == null,
                                onClick = { onSelectTag(null) }
                            )
                        }
                        items(
                            items = availableTags,
                            key = { "tag_filter_${it.lowercase(Locale.US)}" },
                            contentType = { "tag_filter" }
                        ) { tag ->
                            OsuFilterChip(
                                label = tag,
                                selected = selectedTag?.equals(tag, ignoreCase = true) == true,
                                onClick = { onSelectTag(tag) }
                            )
                        }
                    }
                }

                OsuHairlineDivider()
            }
        }

        if (savedBeatmaps.isEmpty()) {
            item(key = "play_later_empty", contentType = "empty") {
                if (totalSavedCount > 0 && selectedTag != null) {
                    ExploreEmptyCard(
                        title = "No maps tagged \"$selectedTag\"",
                        body = "Select another tag filter or show all saved beatmaps",
                        actionLabel = "Show All",
                        onAction = { onSelectTag(null) },
                        icon = Icons.Rounded.SearchOff
                    )
                } else {
                    ExploreEmptyCard(
                        title = "Play Later queue is empty",
                        body = "Bookmark maps in Explore to sync clickable links to your in-game Bancho chat",
                        actionLabel = "Browse Beatmaps",
                        onAction = onBrowseBeatmaps,
                        icon = Icons.Rounded.BookmarkBorder
                    )
                }
            }
        } else {
            itemsIndexed(
                items = savedBeatmaps,
                key = { _, item -> "saved_${item.beatmapId}" },
                contentType = { _, _ -> "saved_beatmap_row" }
            ) { index, item ->
                val isThisAudio = audioState.beatmapsetId == item.beatmapsetId
                val isPlayingThis = isThisAudio && audioState.isPlaying
                val isPreparingThis = isThisAudio && audioState.isPreparing
                val oszState = oszDownloadStates[item.beatmapsetId] ?: OszDownloadState.Idle
                val isDownloadingOsz = oszState is OszDownloadState.Downloading
                val oszPct = (oszState as? OszDownloadState.Downloading)?.progressPercent
                val playTint by animateColorAsState(
                    targetValue = if (isPlayingThis) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "savedPlayTint"
                )
                val labelTint by animateColorAsState(
                    targetValue = if (item.tags.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "savedLabelTint"
                )
                val downloadTint by animateColorAsState(
                    targetValue = if (oszState is OszDownloadState.Downloaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "savedDownloadTint"
                )

                FlatCard(
                    shape = cardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://osu.ppy.sh/b/${item.beatmapId}"))
                                )
                            }.onFailure {
                                onShowMessage("No browser available to open beatmap")
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = item.coverUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(OsuRadius.sm))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            Spacer(Modifier.width(OsuSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.artist} - ${item.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val bpmLabel = if (item.bpm.isFinite()) "${item.bpm.roundToInt()} BPM" else "— BPM"
                                val durationSuffix = if (item.totalLengthSeconds > 0) {
                                    " • ${BeatmapUrlParser.formatDuration(item.totalLengthSeconds)}"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "by ${item.creator} • $bpmLabel$durationSuffix",
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(OsuSpacing.xs))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    OsuModeBadge(mode = item.mode, compact = true)
                                    StarRatingBadge(starRating = item.starRating)
                                    Text(
                                        text = "[${item.version}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = surfaces.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }

                            if (item.previewAudioUrl.isNotBlank() || item.beatmapsetId > 0L) {
                                IconButton(
                                    onClick = { onPlaySavedQueue(savedBeatmaps, index) }
                                ) {
                                    if (isPreparingThis) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Preview 30s Audio",
                                            tint = playTint
                                        )
                                    }
                                }
                            }
                        }

                        if (item.tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                item.tags.forEach { tag ->
                                    val isTagActive = selectedTag?.equals(tag, ignoreCase = true) == true
                                    OsuMicroTag(
                                        text = tag,
                                        containerColor = if (isTagActive) surfaces.accentSubtle else surfaces.surfaceHigh,
                                        contentColor = if (isTagActive) MaterialTheme.colorScheme.primary else surfaces.textSecondary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(OsuRadius.xs))
                                            .clickable { onSelectTag(tag) }
                                    )
                                }
                            }
                        }

                        OsuHairlineDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            IconButton(
                                onClick = { tagEditingItem = item },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Label,
                                    contentDescription = "Edit Tags",
                                    tint = labelTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isDownloadingOsz) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                                    modifier = Modifier.padding(horizontal = OsuSpacing.xs)
                                ) {
                                    if (oszPct != null) {
                                        CircularProgressIndicator(
                                            progress = { (oszPct / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "$oszPct%",
                                            style = MaterialTheme.typography.labelSmall.tnum(),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = ".osz",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = surfaces.textSecondary
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    enabled = item.beatmapsetId > 0L,
                                    onClick = { onDownloadOsz(item.beatmapsetId, item.artist, item.title) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (oszState is OszDownloadState.Downloaded) {
                                            Icons.Rounded.Check
                                        } else {
                                            Icons.Rounded.Download
                                        },
                                        contentDescription = "Download .osz",
                                        tint = downloadTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                enabled = item.beatmapsetId > 0L && !isDownloadingOsz,
                                onClick = { onShareOsz(context, item.beatmapsetId, item.artist, item.title) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share .osz",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            IconButton(
                                onClick = { onSendOneToInGame(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send to In-Game Chat",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onRemove(item.beatmapId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Remove from Play Later",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
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
private fun PlayLaterTagEditorDialog(
    item: SavedBeatmapItem,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    var selectedTags by remember(item.beatmapId, item.tagsCsv) {
        mutableStateOf(item.tags)
    }
    var customTagInput by remember { mutableStateOf("") }

    fun toggleTag(raw: String) {
        val norm = SavedBeatmapItem.normalizeTag(raw)
        if (norm.isEmpty()) return
        val exists = selectedTags.any { it.equals(norm, ignoreCase = true) }
        selectedTags = if (exists) {
            selectedTags.filterNot { it.equals(norm, ignoreCase = true) }
        } else {
            selectedTags + norm
        }
    }

    fun addCustomTag() {
        val norm = SavedBeatmapItem.normalizeTag(customTagInput)
        if (norm.isEmpty()) return
        if (selectedTags.none { it.equals(norm, ignoreCase = true) }) {
            selectedTags = selectedTags + norm
        }
        customTagInput = ""
    }

    val customTags = selectedTags.filter { tag ->
        SavedBeatmapItem.PRESET_TAGS.none { preset -> preset.equals(tag, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "Edit Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)) {
                Text(
                    text = "${item.artist} - ${item.title} [${item.version}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Preset Tags",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    SavedBeatmapItem.PRESET_TAGS.forEach { preset ->
                        val active = selectedTags.any { it.equals(preset, ignoreCase = true) }
                        OsuFilterChip(
                            label = preset,
                            selected = active,
                            onClick = { toggleTag(preset) }
                        )
                    }
                }

                if (customTags.isNotEmpty()) {
                    Text(
                        text = "Custom Tags",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        customTags.forEach { tag ->
                            OsuFilterChip(
                                label = tag,
                                selected = true,
                                leadingIcon = Icons.Rounded.Close,
                                onClick = { toggleTag(tag) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customTagInput,
                        onValueChange = { customTagInput = it },
                        placeholder = { Text("Custom tag…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addCustomTag() }),
                        shape = controlShape,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = ::addCustomTag,
                        enabled = SavedBeatmapItem.normalizeTag(customTagInput).isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add custom tag"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTags) },
                shape = controlShape
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = controlShape
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsAndSpotlightsTab(
    feed: NewsAndSpotlightsFeed,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val surfaces = LocalOsuSurfaces.current
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val controlShape = RoundedCornerShape(OsuRadius.md)

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            onShowMessage("No browser available to open link")
        }
    }

    val hasAnyItems = feed.news.isNotEmpty() || feed.spotlights.isNotEmpty()

    PullToRefreshBox(
        isRefreshing = isLoading && hasAnyItems,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(OsuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            item(key = "news_controls", contentType = "news_controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasAnyItems) {
                            "${feed.spotlights.size} spotlights • ${feed.news.size} posts"
                        } else {
                            "Official Feed"
                        },
                        style = if (hasAnyItems) {
                            MaterialTheme.typography.labelLarge.tnum()
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        color = surfaces.textSecondary
                    )
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        shape = controlShape,
                        border = osuHairlineBorder(),
                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Text("Refresh")
                    }
                }
            }

            if (errorMessage != null && hasAnyItems) {
                item(key = "news_error_inline", contentType = "error") {
                    ExploreErrorCard(message = errorMessage, onRetry = onRefresh)
                }
            }

            if (isLoading && !hasAnyItems) {
                items(count = 4, key = { "news_skel_$it" }, contentType = { "skeleton" }) {
                    BeatmapResultSkeletonCard()
                }
            } else if (errorMessage != null && !hasAnyItems) {
                item(key = "news_error", contentType = "error") {
                    ExploreErrorCard(message = errorMessage, onRetry = onRefresh)
                }
            } else {
                if (feed.spotlights.isNotEmpty()) {
                    item(key = "spotlights_section", contentType = "spotlights_row") {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            OsuSectionHeader(title = "Seasonal Spotlights")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)) {
                                items(
                                    items = feed.spotlights.distinctBy { it.id },
                                    key = { it.id },
                                    contentType = { "spotlight_card" }
                                ) { spotlight ->
                                    SpotlightFeedCard(
                                        spotlight = spotlight,
                                        numFmt = numFmt,
                                        onClick = { openUrl(spotlight.url) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (feed.news.isNotEmpty()) {
                    item(key = "news_section_label", contentType = "section_header") {
                        OsuSectionHeader(title = "Latest News")
                    }
                    items(
                        items = feed.news.distinctBy { it.id },
                        key = { it.id },
                        contentType = { "news_card" }
                    ) { post ->
                        NewsPostCard(
                            post = post,
                            onClick = { openUrl(post.url) }
                        )
                    }
                } else if (feed.spotlights.isEmpty()) {
                    item(key = "news_empty", contentType = "empty") {
                        ExploreEmptyCard(
                            title = "No news posts available",
                            body = "Tap Refresh to fetch official osu! news and spotlights",
                            actionLabel = "Refresh",
                            onAction = onRefresh,
                            icon = Icons.Rounded.Newspaper
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightFeedCard(
    spotlight: OsuSpotlightItem,
    numFmt: NumberFormat,
    onClick: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    val spotlightTypeLabel = spotlight.type
        .trim()
        .lowercase(Locale.US)
        .replaceFirstChar { it.uppercase() }
    FlatCard(
        shape = RoundedCornerShape(OsuRadius.lg),
        modifier = Modifier
            .width(232.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            if (spotlightTypeLabel.isNotEmpty()) {
                OsuMicroTag(
                    text = spotlightTypeLabel,
                    containerColor = surfaces.accentSubtle,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = spotlight.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val range = listOfNotNull(
                spotlight.startDate.take(10).takeIf { it.isNotBlank() },
                spotlight.endDate.take(10).takeIf { it.isNotBlank() }
            ).joinToString(" – ")
            if (range.isNotBlank()) {
                Text(
                    text = range,
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = surfaces.textMuted
                )
            }
            spotlight.participantCount?.let { count ->
                Text(
                    text = "${numFmt.format(count)} participants",
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = surfaces.textSecondary
                )
            }
        }
    }
}

@Composable
private fun NewsPostCard(
    post: OsuNewsItem,
    onClick: () -> Unit
) {
    val surfaces = LocalOsuSurfaces.current
    FlatCard(
        shape = RoundedCornerShape(OsuRadius.lg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        post.imageUrl?.takeIf { it.isNotBlank() }?.let { img ->
            AsyncImage(
                model = img,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        Column(
            modifier = Modifier.padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val authorPart = post.author.takeIf { it.isNotBlank() }?.let { "by $it" }
            val datePart = post.publishedAt.take(10).takeIf { it.isNotBlank() }
            if (authorPart != null || datePart != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    authorPart?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = surfaces.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (authorPart != null && datePart != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = surfaces.textMuted
                        )
                    }
                    datePart?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = surfaces.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
            if (post.preview.isNotBlank()) {
                Text(
                    text = post.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = surfaces.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExploreErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    OsuInlineBanner(
        text = message,
        tone = OsuBannerTone.ERROR,
        icon = Icons.Rounded.ErrorOutline,
        actionLabel = "Retry",
        onAction = onRetry
    )
}

@Composable
private fun ExploreEmptyCard(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    icon: ImageVector = Icons.Rounded.SearchOff
) {
    FlatCard(
        shape = RoundedCornerShape(OsuRadius.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        OsuEmptyState(
            icon = icon,
            title = title,
            subtitle = body,
            primaryActionLabel = actionLabel,
            onPrimaryAction = onAction
        )
    }
}

@Composable
private fun BeatmapResultSkeletonCard() {
    FlatCard(
        shape = RoundedCornerShape(OsuRadius.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OsuSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(OsuRadius.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(Modifier.width(OsuSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }
    }
}

@Composable
private fun SkeletonLineRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(OsuRadius.md))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(OsuSpacing.md))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(OsuRadius.xs))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(OsuRadius.xs))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

@Composable
private fun FlatCard(
    shape: Shape = RoundedCornerShape(OsuRadius.lg),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    OsuGroupedContainer(
        shape = shape,
        modifier = modifier,
        content = content
    )
}
