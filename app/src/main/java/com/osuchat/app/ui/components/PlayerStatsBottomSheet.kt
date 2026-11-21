package com.osuchat.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.osuchat.app.data.local.SelfProfileCacheDao
import com.osuchat.app.data.repository.MedalsCatalogRepository
import com.osuchat.app.di.ExternalChatImageClient
import com.osuchat.app.domain.model.DailySessionSummary
import com.osuchat.app.domain.model.FriendRelationStatus
import com.osuchat.app.domain.model.OsuMedalCatalogItem
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.OsuUserMedalUnlock
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.RankPpConversionResult
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.ModDt
import com.osuchat.app.ui.theme.ModHd
import com.osuchat.app.ui.theme.ModHr
import com.osuchat.app.ui.theme.ModNm
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.StatusRead
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.util.RankPpMath
import com.osuchat.app.util.SessionTrackerCalculator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerCompareEntryPoint {
    fun selfProfileCacheDao(): SelfProfileCacheDao
    fun chatRepository(): ChatRepository
    fun medalsCatalogRepository(): MedalsCatalogRepository
    @ExternalChatImageClient
    fun externalImageLoader(): ImageLoader
}

private val PROFILE_MODES = listOf(
    "osu" to "osu!",
    "taiko" to "taiko",
    "fruits" to "catch",
    "mania" to "mania"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsBottomSheet(
    profile: PlayerProfile?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSaveAlias: (username: String, alias: String?, userId: Long?, note: String?) -> Unit,
    onStartDirectMessage: (username: String) -> Unit,
    onToggleTrackPp: ((PlayerProfile) -> Unit)? = null,
    selfUsername: String = "",
    onToggleFriend: ((PlayerProfile) -> Unit)? = null,
    isIgnored: Boolean = false,
    onToggleIgnore: ((PlayerProfile) -> Unit)? = null,
    pinnedScores: List<OsuScoreItem> = emptyList(),
    bestScores: List<OsuScoreItem> = emptyList(),
    recentScores: List<OsuScoreItem> = emptyList(),
    isScoresLoading: Boolean = false,
    isLoadingMoreBestScores: Boolean = false,
    canLoadMoreBestScores: Boolean = false,
    onLoadMoreBestScores: (() -> Unit)? = null,
    onShareScore: ((OsuScoreItem) -> Unit)? = null,
    selectedMode: String = profile?.mode ?: "osu",
    onSelectMode: ((String) -> Unit)? = null,
    onEstimateRankForPp: (suspend (mode: String, targetPp: Double, currentRankHint: Int?, currentPpHint: Double?) -> Result<RankPpConversionResult>)? = null,
    profileHistory: List<ProfileHistoryPoint>? = null,
    osuTrackHistory: List<OsuTrackPoint> = emptyList(),
    isOsuTrackLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = OsuRadius.xl, topEnd = OsuRadius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    ) {
        PlayerProfileContent(
            profile = profile,
            isLoading = isLoading,
            selectedMode = selectedMode,
            onSelectMode = onSelectMode,
            onSaveAlias = onSaveAlias,
            onStartDirectMessage = { username ->
                onStartDirectMessage(username)
                onDismiss()
            },
            onToggleTrackPp = onToggleTrackPp,
            selfUsername = selfUsername,
            onToggleFriend = onToggleFriend,
            isIgnored = isIgnored,
            onToggleIgnore = onToggleIgnore,
            pinnedScores = pinnedScores,
            bestScores = bestScores,
            recentScores = recentScores,
            isScoresLoading = isScoresLoading,
            isLoadingMoreBestScores = isLoadingMoreBestScores,
            canLoadMoreBestScores = canLoadMoreBestScores,
            onLoadMoreBestScores = onLoadMoreBestScores,
            onShareScore = onShareScore,
            onEstimateRankForPp = onEstimateRankForPp,
            profileHistory = profileHistory,
            osuTrackHistory = osuTrackHistory,
            isOsuTrackLoading = isOsuTrackLoading,
            errorMessage = errorMessage,
            onRetry = onRetry
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileContent(
    profile: PlayerProfile?,
    isLoading: Boolean,
    selectedMode: String = profile?.mode ?: "osu",
    onSelectMode: ((String) -> Unit)? = null,
    onSaveAlias: ((username: String, alias: String?, userId: Long?, note: String?) -> Unit)? = null,
    onStartDirectMessage: ((username: String) -> Unit)? = null,
    onToggleTrackPp: ((PlayerProfile) -> Unit)? = null,
    selfUsername: String = "",
    onToggleFriend: ((PlayerProfile) -> Unit)? = null,
    isIgnored: Boolean = false,
    onToggleIgnore: ((PlayerProfile) -> Unit)? = null,
    pinnedScores: List<OsuScoreItem> = emptyList(),
    bestScores: List<OsuScoreItem> = emptyList(),
    recentScores: List<OsuScoreItem> = emptyList(),
    isScoresLoading: Boolean = false,
    isLoadingMoreBestScores: Boolean = false,
    canLoadMoreBestScores: Boolean = false,
    onLoadMoreBestScores: (() -> Unit)? = null,
    onShareScore: ((OsuScoreItem) -> Unit)? = null,
    onEstimateRankForPp: (suspend (mode: String, targetPp: Double, currentRankHint: Int?, currentPpHint: Double?) -> Result<RankPpConversionResult>)? = null,
    profileHistory: List<ProfileHistoryPoint>? = null,
    osuTrackHistory: List<OsuTrackPoint> = emptyList(),
    isOsuTrackLoading: Boolean = false,
    dailySession: DailySessionSummary? = null,
    isDailySessionLoading: Boolean = false,
    onRefreshDailySession: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val surfaces = LocalOsuSurfaces.current

    if (isLoading && profile == null) {
        PlayerProfileSkeleton(modifier = modifier)
        return
    }

    if (profile == null) {
        OsuGroupedContainer(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = modifier.padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xl)
        ) {
            OsuEmptyState(
                icon = Icons.Rounded.EmojiEvents,
                title = "Profile unavailable",
                subtitle = sanitizeCardErrorMessage(errorMessage, "Could not load player profile"),
                primaryActionLabel = if (onRetry != null) "Retry" else null,
                onPrimaryAction = onRetry
            )
        }
        return
    }

    var aliasInput by remember(profile.username, profile.customAlias) {
        mutableStateOf(profile.customAlias.orEmpty())
    }
    var noteInput by remember(profile.username, profile.customNote) {
        mutableStateOf(profile.customNote.orEmpty())
    }
    var isEditingAlias by remember(profile.username) { mutableStateOf(false) }
    var showRemoveFriendConfirm by remember(profile.username) { mutableStateOf(false) }
    var visibleBestCount by remember(profile.userId, profile.username, selectedMode) {
        mutableIntStateOf(5)
    }
    var pendingExpandTo200 by remember(profile.userId, profile.username, selectedMode) {
        mutableStateOf(false)
    }
    LaunchedEffect(bestScores.size, isLoadingMoreBestScores, canLoadMoreBestScores) {
        if (pendingExpandTo200 && !isLoadingMoreBestScores) {
            if (bestScores.size > 100 || !canLoadMoreBestScores) {
                visibleBestCount = 200
            }
            pendingExpandTo200 = false
        }
    }

    val openInBrowser = {
        if (profile.userId > 0L || profile.username.isNotBlank()) {
            val target = if (profile.userId > 0L) profile.userId.toString() else Uri.encode(profile.username)
            val url = "https://osu.ppy.sh/users/$target"
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        Unit
    }

    if (showRemoveFriendConfirm && onToggleFriend != null) {
        AlertDialog(
            onDismissRequest = { showRemoveFriendConfirm = false },
            shape = RoundedCornerShape(OsuRadius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Remove friend?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text("Remove ${profile.displayName} from your osu! friends list?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveFriendConfirm = false
                        onToggleFriend(profile)
                    },
                    shape = RoundedCornerShape(OsuRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveFriendConfirm = false },
                    shape = RoundedCornerShape(OsuRadius.md)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val isSelf = selfUsername.isNotBlank() &&
        profile.username.equals(selfUsername, ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = OsuSpacing.xxl)
    ) {
        if (!profile.coverUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                AsyncImage(
                    model = profile.coverUrl,
                    contentDescription = null,
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
            }
            OsuHairlineDivider()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(
                name = profile.username,
                avatarUrl = profile.avatarUrl,
                size = 56.dp,
                showOnlineDot = true,
                isOnline = profile.isOnline
            )
            Spacer(modifier = Modifier.width(OsuSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val flag = countryCodeToFlagEmoji(profile.countryCode)
                val countryLabel = profile.countryName ?: profile.countryCode
                val statusMeta = buildString {
                    if (flag.isNotEmpty()) append("$flag ")
                    if (!countryLabel.isNullOrBlank()) append("$countryLabel · ")
                    append(if (profile.isOnline) "Online" else "Offline")
                }
                Text(
                    text = statusMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!profile.currentAction.isNullOrBlank()) {
                    Text(
                        text = profile.currentAction,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!profile.customNote.isNullOrBlank()) {
                    Text(
                        text = profile.customNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = OsuSpacing.xxs)
                    )
                }
            }
            Spacer(modifier = Modifier.width(OsuSpacing.sm))
            IconButton(
                onClick = openInBrowser,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open profile on osu.ppy.sh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        OsuHairlineDivider()

        Column(
            modifier = Modifier.padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            if (onSaveAlias != null && !isSelf) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                        ) {
                            Text(
                                text = "Local alias & note",
                                style = MaterialTheme.typography.labelSmall,
                                color = surfaces.textMuted
                            )
                            if (!isEditingAlias) {
                                Text(
                                    text = profile.customAlias?.takeIf { it.isNotBlank() } ?: "No alias",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!profile.customNote.isNullOrBlank()) {
                                    Text(
                                        text = profile.customNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (!isEditingAlias) {
                            IconButton(
                                onClick = { isEditingAlias = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit local alias and note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (isEditingAlias) {
                        Spacer(modifier = Modifier.height(OsuSpacing.xs))
                        OutlinedTextField(
                            value = aliasInput,
                            onValueChange = { aliasInput = it },
                            label = { Text("Local alias") },
                            shape = RoundedCornerShape(OsuRadius.md),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.sm))
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text("Player note (role, teammate, match history)") },
                            shape = RoundedCornerShape(OsuRadius.md),
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!profile.customAlias.isNullOrBlank() || !profile.customNote.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        onSaveAlias(profile.username, null, profile.userId, "")
                                        aliasInput = ""
                                        noteInput = ""
                                        isEditingAlias = false
                                    },
                                    shape = RoundedCornerShape(OsuRadius.md),
                                    contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                                ) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                TextButton(
                                    onClick = {
                                        aliasInput = profile.customAlias.orEmpty()
                                        noteInput = profile.customNote.orEmpty()
                                        isEditingAlias = false
                                    },
                                    shape = RoundedCornerShape(OsuRadius.md)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        val cleanedAlias = aliasInput.trim().takeIf { it.isNotEmpty() }
                                        val cleanedNote = noteInput.trim()
                                        onSaveAlias(profile.username, cleanedAlias, profile.userId, cleanedNote)
                                        aliasInput = cleanedAlias.orEmpty()
                                        noteInput = cleanedNote
                                        isEditingAlias = false
                                    },
                                    shape = RoundedCornerShape(OsuRadius.md)
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
                OsuHairlineDivider()
            }

            if (onSelectMode != null) {
                val activeMode = when (val raw = selectedMode.trim().lowercase(Locale.US)) {
                    "catch", "ctb" -> "fruits"
                    "" -> "osu"
                    else -> raw
                }
                val selectedIdx = PROFILE_MODES.indexOfFirst { it.first == activeMode }.coerceAtLeast(0)
                Column {
                    OsuSegmentedBar(
                        items = PROFILE_MODES,
                        selectedIndex = selectedIdx,
                        onSelect = { idx ->
                            val targetKey = PROFILE_MODES[idx].first
                            if (activeMode != targetKey) onSelectMode(targetKey)
                        },
                        labelProvider = { it.second }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = OsuSpacing.xs)
                            .height(2.dp)
                    ) {
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            }

            val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
            OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    OsuStatCell(
                        label = "Global rank",
                        value = RankPpMath.sanitizeRank(profile.globalRank)
                            ?.let { "#${numFmt.format(it)}" } ?: "—",
                        valueColor = MaterialTheme.colorScheme.primary,
                        emphasized = true,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = OsuRadius.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    OsuStatCell(
                        label = "Country rank",
                        value = RankPpMath.sanitizeRank(profile.countryRank)
                            ?.let { "#${numFmt.format(it)}" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
                OsuHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    OsuStatCell(
                        label = "PP",
                        value = RankPpMath.sanitizeFiniteNonNegative(profile.pp)
                            ?.let { "${numFmt.format(it.roundToLong())}pp" } ?: "—",
                        emphasized = true,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = OsuRadius.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    OsuStatCell(
                        label = "Accuracy",
                        value = RankPpMath.sanitizeFiniteNonNegative(profile.accuracy)
                            ?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = OsuRadius.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    OsuStatCell(
                        label = "Playcount",
                        value = profile.playCount?.takeIf { it >= 0 }
                            ?.let { numFmt.format(it) } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ProfileHistoryChart(
                snapshots = if (isSelf) profileHistory.orEmpty() else emptyList(),
                currentProfile = profile,
                mode = selectedMode,
                osuTrackPoints = osuTrackHistory,
                isOsuTrackLoading = isOsuTrackLoading
            )

            if (isSelf && (dailySession != null || isDailySessionLoading)) {
                DailySessionSummaryCard(
                    summary = dailySession,
                    isLoading = isDailySessionLoading,
                    onRefresh = onRefreshDailySession,
                    onShareScore = onShareScore
                )
            }

            WhatIfPpCalculatorCard(
                currentTotalPp = profile.pp,
                currentGlobalRank = profile.globalRank,
                mode = selectedMode,
                bestScores = bestScores,
                isScoresLoading = isScoresLoading,
                onEstimateRankForPp = onEstimateRankForPp
            )

            TopPlayAnalyticsCard(
                bestScores = bestScores,
                isScoresLoading = isScoresLoading
            )

            if (!isSelf) {
                HeadToHeadCompareCard(
                    targetProfile = profile,
                    targetBestScores = bestScores,
                    selectedMode = selectedMode,
                    selfUsername = selfUsername
                )
            }

            MedalsSectionCard(
                profile = profile,
                onRefreshProfile = onRetry
            )

            // Primary / Social / Tracking Actions
            val canIgnoreProfile = !isSelf &&
                onToggleIgnore != null &&
                !profile.username.equals("BanchoBot", ignoreCase = true) &&
                !profile.username.equals("System", ignoreCase = true)
            if (!isSelf && (onStartDirectMessage != null || onToggleFriend != null || (onToggleTrackPp != null && profile.userId > 0L) || canIgnoreProfile)) {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    if (onStartDirectMessage != null || onToggleFriend != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            if (onStartDirectMessage != null) {
                                Button(
                                    onClick = { onStartDirectMessage(profile.username) },
                                    shape = RoundedCornerShape(OsuRadius.md),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                    Text("Message", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (onToggleFriend != null) {
                                val (btnIcon, btnLabel) = when (profile.friendStatus) {
                                    FriendRelationStatus.MUTUAL -> Icons.Rounded.Favorite to "Mutual friend"
                                    FriendRelationStatus.FRIEND -> Icons.Rounded.HowToReg to "Friend"
                                    FriendRelationStatus.NONE -> Icons.Rounded.PersonAdd to "Add friend"
                                }
                                val isFriend = profile.friendStatus != FriendRelationStatus.NONE
                                OutlinedButton(
                                    onClick = {
                                        if (profile.friendStatus == FriendRelationStatus.NONE) {
                                            onToggleFriend(profile)
                                        } else {
                                            showRemoveFriendConfirm = true
                                        }
                                    },
                                    shape = RoundedCornerShape(OsuRadius.md),
                                    border = osuHairlineBorder(
                                        when (profile.friendStatus) {
                                            FriendRelationStatus.MUTUAL -> MaterialTheme.colorScheme.primary
                                            FriendRelationStatus.FRIEND -> StatusOnline
                                            FriendRelationStatus.NONE -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isFriend) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            Color.Transparent
                                        },
                                        contentColor = when (profile.friendStatus) {
                                            FriendRelationStatus.MUTUAL -> MaterialTheme.colorScheme.primary
                                            FriendRelationStatus.FRIEND -> StatusOnline
                                            FriendRelationStatus.NONE -> MaterialTheme.colorScheme.onSurface
                                        }
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(btnIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                    Text(btnLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    if ((onToggleTrackPp != null && profile.userId > 0L) || canIgnoreProfile) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            if (onToggleTrackPp != null && profile.userId > 0L) {
                                OsuFilterChip(
                                    label = if (profile.isTrackedForPp) "Tracking Top 5 PP" else "Track Top 5 PP",
                                    selected = profile.isTrackedForPp,
                                    onClick = { onToggleTrackPp(profile) },
                                    leadingIcon = if (profile.isTrackedForPp) {
                                        Icons.Rounded.NotificationsActive
                                    } else {
                                        Icons.Rounded.NotificationsNone
                                    }
                                )
                            }
                            if (canIgnoreProfile && onToggleIgnore != null) {
                                OsuFilterChip(
                                    label = if (isIgnored) "Unignore user" else "Ignore user",
                                    selected = isIgnored,
                                    onClick = { onToggleIgnore(profile) },
                                    leadingIcon = Icons.Rounded.Block
                                )
                            }
                        }
                    }
                }
            }

            if (isScoresLoading) {
                ScoreListSkeleton()
            } else if (pinnedScores.isEmpty() && bestScores.isEmpty() && recentScores.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = OsuSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "No plays recorded in this mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onRetry != null) {
                        TextButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(OsuRadius.md),
                            contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                if (pinnedScores.isNotEmpty()) {
                    ScoreSectionBlock(
                        title = "Pinned scores",
                        scores = pinnedScores.take(5),
                        onShareScore = onShareScore
                    )
                }
                if (bestScores.isNotEmpty()) {
                    val displayedBestScores = bestScores.take(visibleBestCount)
                    val nextBestStep = when {
                        visibleBestCount < 20 -> 20
                        visibleBestCount < 50 -> 50
                        visibleBestCount < 100 -> 100
                        else -> 200
                    }
                    val hasMoreLocal = bestScores.size > visibleBestCount
                    val canFetchPageTwo = visibleBestCount in 100 until 200 &&
                        bestScores.size <= 100 &&
                        canLoadMoreBestScores &&
                        onLoadMoreBestScores != null
                    val showSeeMore = visibleBestCount < 200 && (hasMoreLocal || canFetchPageTwo)
                    val showShowLess = visibleBestCount > 5 && displayedBestScores.size > 5

                    ScoreSectionBlock(
                        title = "Top ${displayedBestScores.size} PP plays",
                        scores = displayedBestScores,
                        onShareScore = onShareScore
                    )

                    if (showSeeMore || showShowLess || isLoadingMoreBestScores) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showSeeMore || isLoadingMoreBestScores) {
                                OutlinedButton(
                                    onClick = {
                                        if (nextBestStep < 200 || bestScores.size > 100) {
                                            visibleBestCount = nextBestStep
                                        } else if (canFetchPageTwo) {
                                            pendingExpandTo200 = true
                                            onLoadMoreBestScores?.invoke()
                                        }
                                    },
                                    enabled = !isLoadingMoreBestScores,
                                    shape = RoundedCornerShape(OsuRadius.md),
                                    border = osuHairlineBorder(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isLoadingMoreBestScores) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(OsuSpacing.sm))
                                        Text("Loading…", style = MaterialTheme.typography.labelLarge)
                                    } else {
                                        Text("See more", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            if (showShowLess) {
                                TextButton(
                                    onClick = {
                                        visibleBestCount = 5
                                        pendingExpandTo200 = false
                                    },
                                    shape = RoundedCornerShape(OsuRadius.md)
                                ) {
                                    Text("Show less", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                if (recentScores.isNotEmpty()) {
                    ScoreSectionBlock(
                        title = "Recent plays",
                        scores = recentScores.take(5),
                        onShareScore = onShareScore
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreSectionBlock(
    title: String,
    scores: List<OsuScoreItem>,
    onShareScore: ((OsuScoreItem) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.tnum(),
            fontWeight = FontWeight.SemiBold
        )
        scores.forEachIndexed { index, score ->
            key(score.id.takeIf { it > 0L } ?: index) {
                OsuScoreCard(
                    score = score,
                    onShareToChat = onShareScore
                )
            }
        }
    }
}

@Composable
private fun PlayerProfileSkeleton(modifier: Modifier = Modifier) {
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(blockColor)
            )
            Spacer(modifier = Modifier.width(OsuSpacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(blockColor)
                )
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(blockColor)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(OsuRadius.md))
                .background(blockColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(OsuRadius.lg))
                .background(blockColor)
        )
        ScoreListSkeleton()
    }
}

@Composable
private fun ScoreListSkeleton() {
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(OsuRadius.lg))
                    .background(blockColor)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopPlayAnalyticsCard(
    bestScores: List<OsuScoreItem>,
    isScoresLoading: Boolean
) {
    val analytics = remember(bestScores) { RankPpMath.computeTopPlayAnalytics(bestScores) }
    if (!isScoresLoading && analytics == null) return

    var expanded by remember { mutableStateOf(true) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val primaryColor = MaterialTheme.colorScheme.primary

    OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(OsuSpacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    Text(
                        text = "Top 100 analytics",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (analytics != null) {
                        Text(
                            text = "${analytics.playCount} plays",
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = LocalOsuSurfaces.current.textMuted
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse Top 100 analytics" else "Expand Top 100 analytics",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = OsuMotion.expandEnter(),
                exit = OsuMotion.expandExit()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    if (analytics == null) {
                        Text(
                            text = "Analyzing top plays…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val modColors = remember(primaryColor) {
                            mapOf(
                                "NM" to ModNm,
                                "HD" to ModHd,
                                "HR" to ModHr,
                                "DT/NC" to ModDt,
                                "HDHR" to StatusOnline,
                                "HDDT" to primaryColor,
                                "Other" to StatusRead
                            )
                        }
                        if (analytics.modDistribution.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(OsuRadius.xs))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    if (w <= 0f || h <= 0f) return@Canvas
                                    var cursorX = 0f
                                    analytics.modDistribution.forEachIndexed { idx, entry ->
                                        val segWidth = if (idx == analytics.modDistribution.lastIndex) {
                                            (w - cursorX).coerceAtLeast(0f)
                                        } else {
                                            ((entry.percentage / 100.0).toFloat() * w).coerceAtLeast(0f)
                                        }
                                        if (segWidth > 0f) {
                                            drawRect(
                                                color = modColors[entry.label] ?: StatusRead,
                                                topLeft = androidx.compose.ui.geometry.Offset(cursorX, 0f),
                                                size = androidx.compose.ui.geometry.Size(segWidth, h)
                                            )
                                            cursorX += segWidth
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    analytics.modDistribution.forEach { entry ->
                                        val swatch = modColors[entry.label] ?: StatusRead
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(OsuSpacing.sm)
                                                    .clip(CircleShape)
                                                    .background(swatch)
                                            )
                                            Text(
                                                text = "${entry.label} ${String.format(Locale.US, "%.0f%%", entry.percentage)}",
                                                style = MaterialTheme.typography.labelSmall.tnum(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OsuHairlineDivider()

                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatMiniCell(
                                label = "Avg acc",
                                value = String.format(Locale.US, "%.2f%%", analytics.averageAccuracy),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "Avg length",
                                value = analytics.averageLengthSeconds?.let { BeatmapUrlParser.formatDuration(it) } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "Avg stars",
                                value = analytics.averageStarRating?.let { String.format(Locale.US, "%.2f★", it) } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OsuHairlineDivider()

                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatMiniCell(
                                label = "#1 play",
                                value = "${numFmt.format(analytics.top1Pp.roundToInt())}pp",
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "#${analytics.playCount} play",
                                value = "${numFmt.format(analytics.tailPp.roundToInt())}pp",
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "PP cliff",
                                value = "${numFmt.format(analytics.ppSpread.roundToInt())}pp",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCell(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = OsuSpacing.xxs, horizontal = OsuSpacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalOsuSurfaces.current.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun HeadToHeadCompareCard(
    targetProfile: PlayerProfile,
    targetBestScores: List<OsuScoreItem>,
    selectedMode: String,
    selfUsername: String
) {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlayerCompareEntryPoint::class.java
        )
    }
    var expanded by remember(targetProfile.username) { mutableStateOf(false) }
    var selfProfile by remember(selectedMode) { mutableStateOf<PlayerProfile?>(null) }
    var selfTopPlayPp by remember(selectedMode) { mutableStateOf<Double?>(null) }
    var isLoadingSelf by remember(selectedMode) { mutableStateOf(false) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    LaunchedEffect(expanded, selectedMode, selfUsername) {
        if (!expanded) return@LaunchedEffect
        isLoadingSelf = true
        val cachedSelf = runCatching { entryPoint.selfProfileCacheDao().getByKey() }.getOrNull()
        if (cachedSelf != null && selfProfile == null) {
            selfProfile = PlayerProfile(
                userId = cachedSelf.userId,
                username = cachedSelf.username,
                avatarUrl = cachedSelf.avatarUrl,
                coverUrl = cachedSelf.coverUrl,
                countryCode = cachedSelf.countryCode,
                countryName = cachedSelf.countryName,
                globalRank = cachedSelf.globalRank,
                countryRank = cachedSelf.countryRank,
                pp = cachedSelf.pp,
                accuracy = cachedSelf.accuracy,
                playCount = cachedSelf.playCount,
                isOnline = cachedSelf.isOnline,
                mode = cachedSelf.mode
            )
        }
        val lookupTarget = cachedSelf?.userId?.takeIf { it > 0L }?.toString()
            ?: cachedSelf?.username?.takeIf { it.isNotBlank() }
            ?: selfUsername.trim()
        if (lookupTarget.isNotBlank()) {
            val freshModeSelf = runCatching {
                entryPoint.chatRepository().getPlayerProfile(lookupTarget, selectedMode).getOrNull()
            }.getOrNull()
            if (freshModeSelf != null) {
                selfProfile = freshModeSelf
            }
            val uid = freshModeSelf?.userId?.takeIf { it > 0L }
                ?: cachedSelf?.userId?.takeIf { it > 0L }
                ?: lookupTarget.toLongOrNull()
            if (uid != null && uid > 0L) {
                val topScore = runCatching {
                    entryPoint.chatRepository().getUserScores(
                        usernameOrId = uid.toString(),
                        type = "best",
                        includeFails = false,
                        mode = selectedMode,
                        limit = 1
                    ).getOrNull()?.firstOrNull()
                }.getOrNull()
                selfTopPlayPp = topScore?.pp?.takeIf { it.isFinite() }
            }
        }
        isLoadingSelf = false
    }

    val targetTopPlayPp = remember(targetBestScores) {
        targetBestScores.mapNotNull { it.pp?.takeIf { p -> p.isFinite() && p >= 0.0 } }.maxOrNull()
    }

    OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(OsuSpacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Compare vs you",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse comparison" else "Expand comparison",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = OsuMotion.expandEnter(),
                exit = OsuMotion.expandExit()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    if (isLoadingSelf && selfProfile == null) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }

                    val me = selfProfile
                    if (me == null && !isLoadingSelf) {
                        Text(
                            text = "Sign in with osu! to compare against your own stats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (me != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = me.username.ifBlank { "You" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = targetProfile.username,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Δ (You)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                        OsuHairlineDivider()

                        val myGlobalRank = RankPpMath.sanitizeRank(me.globalRank)
                        val tgtGlobalRank = RankPpMath.sanitizeRank(targetProfile.globalRank)
                        CompareMetricRow(
                            label = "Global",
                            myValue = myGlobalRank?.let { "#${numFmt.format(it)}" } ?: "—",
                            targetValue = tgtGlobalRank?.let { "#${numFmt.format(it)}" } ?: "—",
                            deltaValue = if (myGlobalRank != null && tgtGlobalRank != null) {
                                val diff = tgtGlobalRank - myGlobalRank
                                when {
                                    diff > 0 -> "+${numFmt.format(diff)}"
                                    diff < 0 -> "-${numFmt.format(abs(diff))}"
                                    else -> "0"
                                }
                            } else "—",
                            isPositive = if (myGlobalRank != null && tgtGlobalRank != null) {
                                (tgtGlobalRank - myGlobalRank).let { if (it > 0) true else if (it < 0) false else null }
                            } else null
                        )

                        val myCountryRank = RankPpMath.sanitizeRank(me.countryRank)
                        val tgtCountryRank = RankPpMath.sanitizeRank(targetProfile.countryRank)
                        CompareMetricRow(
                            label = "Country",
                            myValue = myCountryRank?.let { "#${numFmt.format(it)}" } ?: "—",
                            targetValue = tgtCountryRank?.let { "#${numFmt.format(it)}" } ?: "—",
                            deltaValue = if (myCountryRank != null && tgtCountryRank != null) {
                                val diff = tgtCountryRank - myCountryRank
                                when {
                                    diff > 0 -> "+${numFmt.format(diff)}"
                                    diff < 0 -> "-${numFmt.format(abs(diff))}"
                                    else -> "0"
                                }
                            } else "—",
                            isPositive = if (myCountryRank != null && tgtCountryRank != null) {
                                (tgtCountryRank - myCountryRank).let { if (it > 0) true else if (it < 0) false else null }
                            } else null
                        )

                        val myPp = RankPpMath.sanitizeFiniteNonNegative(me.pp)
                        val tgtPp = RankPpMath.sanitizeFiniteNonNegative(targetProfile.pp)
                        CompareMetricRow(
                            label = "Total PP",
                            myValue = myPp?.let { "${numFmt.format(it.roundToInt())}pp" } ?: "—",
                            targetValue = tgtPp?.let { "${numFmt.format(it.roundToInt())}pp" } ?: "—",
                            deltaValue = if (myPp != null && tgtPp != null) {
                                val diff = (myPp - tgtPp).roundToInt()
                                when {
                                    diff > 0 -> "+${numFmt.format(diff)}pp"
                                    diff < 0 -> "-${numFmt.format(abs(diff))}pp"
                                    else -> "0pp"
                                }
                            } else "—",
                            isPositive = if (myPp != null && tgtPp != null) {
                                val diff = myPp - tgtPp
                                if (diff > 0.5) true else if (diff < -0.5) false else null
                            } else null
                        )

                        val myAcc = RankPpMath.sanitizeFiniteNonNegative(me.accuracy)
                        val tgtAcc = RankPpMath.sanitizeFiniteNonNegative(targetProfile.accuracy)
                        CompareMetricRow(
                            label = "Accuracy",
                            myValue = myAcc?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—",
                            targetValue = tgtAcc?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—",
                            deltaValue = if (myAcc != null && tgtAcc != null) {
                                val diff = myAcc - tgtAcc
                                when {
                                    diff > 0.005 -> String.format(Locale.US, "+%.2f%%", diff)
                                    diff < -0.005 -> String.format(Locale.US, "-%.2f%%", abs(diff))
                                    else -> "0.00%"
                                }
                            } else "—",
                            isPositive = if (myAcc != null && tgtAcc != null) {
                                val diff = myAcc - tgtAcc
                                if (diff > 0.005) true else if (diff < -0.005) false else null
                            } else null
                        )

                        val myPc = me.playCount?.takeIf { it >= 0 }
                        val tgtPc = targetProfile.playCount?.takeIf { it >= 0 }
                        CompareMetricRow(
                            label = "Plays",
                            myValue = myPc?.let { numFmt.format(it) } ?: "—",
                            targetValue = tgtPc?.let { numFmt.format(it) } ?: "—",
                            deltaValue = if (myPc != null && tgtPc != null) {
                                val diff = myPc - tgtPc
                                when {
                                    diff > 0 -> "+${numFmt.format(diff)}"
                                    diff < 0 -> "-${numFmt.format(abs(diff))}"
                                    else -> "0"
                                }
                            } else "—",
                            isPositive = if (myPc != null && tgtPc != null) {
                                val diff = myPc - tgtPc
                                if (diff > 0) true else if (diff < 0) false else null
                            } else null
                        )

                        CompareMetricRow(
                            label = "Top play",
                            myValue = selfTopPlayPp?.let { "${numFmt.format(it.roundToInt())}pp" } ?: "—",
                            targetValue = targetTopPlayPp?.let { "${numFmt.format(it.roundToInt())}pp" } ?: "—",
                            deltaValue = if (selfTopPlayPp != null && targetTopPlayPp != null) {
                                val diff = (selfTopPlayPp!! - targetTopPlayPp).roundToInt()
                                when {
                                    diff > 0 -> "+${numFmt.format(diff)}pp"
                                    diff < 0 -> "-${numFmt.format(abs(diff))}pp"
                                    else -> "0pp"
                                }
                            } else "—",
                            isPositive = if (selfTopPlayPp != null && targetTopPlayPp != null) {
                                val diff = selfTopPlayPp!! - targetTopPlayPp
                                if (diff > 0.5) true else if (diff < -0.5) false else null
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareMetricRow(
    label: String,
    myValue: String,
    targetValue: String,
    deltaValue: String,
    isPositive: Boolean?
) {
    val deltaColor = when (isPositive) {
        true -> StatusOnline
        false -> StatusError
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = myValue,
            style = MaterialTheme.typography.labelSmall.tnum(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = targetValue,
            style = MaterialTheme.typography.labelSmall.tnum(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = deltaValue,
            style = MaterialTheme.typography.labelSmall.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = deltaColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.9f)
        )
    }
}

@Composable
private fun WhatIfPpCalculatorCard(
    currentTotalPp: Double?,
    currentGlobalRank: Int? = null,
    mode: String = "osu",
    bestScores: List<OsuScoreItem>,
    isScoresLoading: Boolean,
    onEstimateRankForPp: (suspend (mode: String, targetPp: Double, currentRankHint: Int?, currentPpHint: Double?) -> Result<RankPpConversionResult>)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var hypotheticalInput by remember(mode) { mutableStateOf("") }
    var rankRetryCount by remember(mode) { mutableIntStateOf(0) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    val safeGlobalRank = remember(currentGlobalRank) { RankPpMath.sanitizeRank(currentGlobalRank) }
    val safeCurrentTotalPp = remember(currentTotalPp) { RankPpMath.sanitizeFiniteNonNegative(currentTotalPp) }
    val existingTopPps = remember(bestScores) {
        RankPpMath.extractTopScores(bestScores.map { it.pp })
    }
    val parsedPp = remember(hypotheticalInput) {
        RankPpMath.parsePositivePpInput(hypotheticalInput, maxPp = RankPpMath.MAX_WHAT_IF_SCORE_PP)
    }
    val isInputInvalid = hypotheticalInput.isNotBlank() && parsedPp == null
    val simulation = remember(safeCurrentTotalPp, existingTopPps, parsedPp) {
        parsedPp?.let { RankPpMath.simulateWhatIf(safeCurrentTotalPp, existingTopPps, it) }
    }

    var liveRankResult by remember(safeGlobalRank, safeCurrentTotalPp, mode) {
        mutableStateOf<RankPpConversionResult?>(null)
    }
    var isEstimatingRank by remember(safeGlobalRank, safeCurrentTotalPp, mode) {
        mutableStateOf(false)
    }
    var rankEstimateError by remember(safeGlobalRank, safeCurrentTotalPp, mode) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(expanded, simulation?.newEstimatedTotal, mode, safeGlobalRank, safeCurrentTotalPp, rankRetryCount) {
        val targetPp = simulation?.newEstimatedTotal
        liveRankResult = null
        rankEstimateError = null
        if (!expanded || targetPp == null || !targetPp.isFinite() || targetPp <= 0.0 || onEstimateRankForPp == null) {
            isEstimatingRank = false
            return@LaunchedEffect
        }
        delay(450L)
        isEstimatingRank = true
        onEstimateRankForPp(mode, targetPp, safeGlobalRank, safeCurrentTotalPp)
            .onSuccess { res ->
                liveRankResult = res
                rankEstimateError = null
            }
            .onFailure { err ->
                rankEstimateError = sanitizeCardError(err, "Live rank unavailable.")
            }
        isEstimatingRank = false
    }

    OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(OsuSpacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "What-if PP simulator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse what-if PP simulator" else "Expand what-if PP simulator",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = OsuMotion.expandEnter(),
                exit = OsuMotion.expandExit()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    OutlinedTextField(
                        value = hypotheticalInput,
                        onValueChange = { next -> if (next.length <= 12) hypotheticalInput = next },
                        label = { Text("Score PP (e.g. 450)") },
                        shape = RoundedCornerShape(OsuRadius.md),
                        isError = isInputInvalid,
                        supportingText = if (isInputInvalid) {
                            { Text("Enter a valid PP amount (0.01 – 100,000)") }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isScoresLoading && existingTopPps.isEmpty()) {
                        Text(
                            text = "Loading top 100 plays…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (simulation != null) {
                        val hypotheticalRank = simulation.hypotheticalRankInTop100
                        val netGain = simulation.netGain
                        val newEstimatedTotal = simulation.newEstimatedTotal

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "New estimated PP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalOsuSurfaces.current.textMuted
                                )
                                Text(
                                    text = String.format(Locale.US, "%,.2f pp", newEstimatedTotal),
                                    style = MaterialTheme.typography.titleMedium.tnum(),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hypotheticalRank != null) {
                                        "Top #$hypotheticalRank of ${simulation.updatedTopCount} plays"
                                    } else {
                                        "Outside top 100 plays"
                                    },
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val hasGain = netGain >= 0.005
                            val gainColor = if (hasGain) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                text = String.format(Locale.US, "+%.2f pp", netGain),
                                style = MaterialTheme.typography.titleSmall.tnum(),
                                fontWeight = FontWeight.SemiBold,
                                color = gainColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        val localFallbackRank = remember(safeGlobalRank, safeCurrentTotalPp, newEstimatedTotal) {
                            RankPpMath.estimateLocalFallbackRank(safeGlobalRank, safeCurrentTotalPp, newEstimatedTotal)
                        }
                        val projectedRank = RankPpMath.sanitizeRank(liveRankResult?.targetRank) ?: localFallbackRank

                        if (projectedRank != null) {
                            OsuHairlineDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Projected global rank",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalOsuSurfaces.current.textMuted
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        if (safeGlobalRank != null) {
                                            Text(
                                                text = "#${numFmt.format(safeGlobalRank)}",
                                                style = MaterialTheme.typography.titleSmall.tnum(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "to",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "#${numFmt.format(projectedRank)}",
                                            style = MaterialTheme.typography.titleSmall.tnum(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        Text(
                                            text = when {
                                                isEstimatingRank -> "Querying live rankings…"
                                                rankEstimateError != null -> "$rankEstimateError Using local estimate"
                                                liveRankResult?.isExactLeaderboardMatch == true -> "Live rank curve"
                                                else -> "Local estimate"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (rankEstimateError != null && onEstimateRankForPp != null && !isEstimatingRank) {
                                            TextButton(
                                                onClick = { rankRetryCount++ },
                                                shape = RoundedCornerShape(OsuRadius.md),
                                                contentPadding = PaddingValues(horizontal = OsuSpacing.xs, vertical = 0.dp)
                                            ) {
                                                Text("Retry", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                if (safeGlobalRank != null) {
                                    val rankGain = safeGlobalRank.toLong() - projectedRank.toLong()
                                    val deltaText = when {
                                        rankGain > 0L -> "+${numFmt.format(rankGain)}"
                                        rankGain == 0L -> "0"
                                        else -> numFmt.format(rankGain)
                                    }
                                    val deltaColor = if (rankGain > 0L) {
                                        StatusOnline
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = deltaText,
                                        style = MaterialTheme.typography.labelLarge.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = deltaColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = if (existingTopPps.isNotEmpty()) {
                                "Weighted across top ${existingTopPps.size} plays"
                            } else {
                                "No ranked top plays available for this mode"
                            },
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySessionSummaryCard(
    summary: DailySessionSummary?,
    isLoading: Boolean,
    onRefresh: (() -> Unit)?,
    onShareScore: ((OsuScoreItem) -> Unit)?
) {
    val surfaces = LocalOsuSurfaces.current
    var expanded by remember { mutableStateOf(true) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(OsuSpacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Today's session (24h)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary != null) {
                        OsuMicroTag(
                            text = "${summary.totalPlays} plays",
                            containerColor = if (summary.totalPlays > 0) {
                                surfaces.accentSubtle
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (summary.totalPlays > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            isNumeric = true
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh 24h session",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = OsuMotion.expandEnter(),
                exit = OsuMotion.expandExit()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    if (isLoading && summary == null) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    } else if (summary == null || summary.totalPlays == 0) {
                        Text(
                            text = "No plays recorded in the last 24 hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (summary?.rankDelta != null || summary?.ppDelta != null) {
                            SessionDeltaRow(
                                rankDelta = summary.rankDelta,
                                ppDelta = summary.ppDelta,
                                numFmt = numFmt
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            StatMiniCell(
                                label = "Pass / Fail",
                                value = "${summary.passCount} / ${summary.failCount}",
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "Pass rate",
                                value = String.format(Locale.US, "%.0f%%", summary.passRatePercent),
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "Avg acc",
                                value = String.format(Locale.US, "%.2f%%", summary.averageAccuracy),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCell(
                                label = "Playtime",
                                value = SessionTrackerCalculator.formatPlaytime(summary.estimatedPlaytimeSeconds),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (summary.rankDelta != null || summary.ppDelta != null) {
                            OsuHairlineDivider()
                            SessionDeltaRow(
                                rankDelta = summary.rankDelta,
                                ppDelta = summary.ppDelta,
                                numFmt = numFmt
                            )
                        }

                        summary.highestPpPlay?.let { bestPlay ->
                            OsuHairlineDivider()
                            Text(
                                text = "Best session play",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OsuScoreCard(
                                score = bestPlay,
                                onShareToChat = onShareScore,
                                embedded = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDeltaRow(
    rankDelta: Int?,
    ppDelta: Double?,
    numFmt: NumberFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
    ) {
        rankDelta?.let { rd ->
            val sign = if (rd > 0) "+" else ""
            val color = when {
                rd > 0 -> StatusOnline
                rd < 0 -> StatusError
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            StatMiniCell(
                label = "24h Rank Δ",
                value = "$sign${numFmt.format(rd)}",
                valueColor = color,
                modifier = Modifier.weight(1f)
            )
        }
        ppDelta?.let { pd ->
            val sign = if (pd > 0.0) "+" else ""
            val color = when {
                pd > 0.05 -> StatusOnline
                pd < -0.05 -> StatusError
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            StatMiniCell(
                label = "24h PP Δ",
                value = String.format(Locale.US, "%s%.1fpp", sign, pd),
                valueColor = color,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedalsSectionCard(
    profile: PlayerProfile,
    onRefreshProfile: (() -> Unit)?
) {
    val surfaces = LocalOsuSurfaces.current
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlayerCompareEntryPoint::class.java
        )
    }
    val externalImageLoader = remember(entryPoint) { entryPoint.externalImageLoader() }
    var catalog by remember { mutableStateOf<List<OsuMedalCatalogItem>>(MedalsCatalogRepository.FALLBACK_MEDALS) }
    var expanded by remember(profile.userId) { mutableStateOf(false) }
    var selectedCategory by remember(profile.userId) { mutableStateOf("All") }
    var showAllInCategory by remember(profile.userId, selectedCategory) { mutableStateOf(false) }
    var selectedMedalDetail by remember {
        mutableStateOf<Pair<OsuMedalCatalogItem, OsuUserMedalUnlock?>?>(null)
    }

    LaunchedEffect(Unit) {
        catalog = entryPoint.medalsCatalogRepository().getCatalog()
    }

    val unlockedById = remember(profile.userAchievements) {
        profile.userAchievements.associateBy { it.achievementId }
    }
    val mergedCatalog = remember(catalog, unlockedById) {
        val knownIds = catalog.asSequence().map { it.id }.toHashSet()
        val extras = unlockedById.keys
            .filter { it > 0 && it !in knownIds }
            .sorted()
            .map { id ->
                OsuMedalCatalogItem(
                    id = id,
                    name = "Medal #$id",
                    description = "Unlocked on osu!",
                    grouping = "Skill & Dedication",
                    iconSlug = ""
                )
            }
        catalog + extras
    }
    val unlockedCount = unlockedById.size
    val totalCount = mergedCatalog.size.coerceAtLeast(unlockedCount.coerceAtLeast(1))
    val pct = ((unlockedCount.toFloat() / totalCount.toFloat()) * 100f).coerceIn(0f, 100f)

    val recentUnlocks = remember(profile.userAchievements, mergedCatalog) {
        val byId = mergedCatalog.associateBy { it.id }
        profile.userAchievements
            .sortedByDescending { it.achievedAt }
            .take(5)
            .map { unlock ->
                val item = byId[unlock.achievementId] ?: OsuMedalCatalogItem(
                    id = unlock.achievementId,
                    name = "Medal #${unlock.achievementId}",
                    description = "Unlocked on osu!",
                    grouping = "Skill & Dedication",
                    iconSlug = ""
                )
                item to unlock
            }
    }

    val filteredMedals = remember(mergedCatalog, unlockedById, selectedCategory) {
        val list = when (selectedCategory) {
            "All" -> mergedCatalog
            "Locked / Missing" -> mergedCatalog.filter { it.id !in unlockedById }
            else -> mergedCatalog.filter { it.grouping.equals(selectedCategory, ignoreCase = true) }
        }
        list.sortedWith(
            compareByDescending<OsuMedalCatalogItem> { it.id in unlockedById }
                .thenBy { it.id }
        )
    }

    selectedMedalDetail?.let { (medal, unlock) ->
        AlertDialog(
            onDismissRequest = { selectedMedalDetail = null },
            shape = RoundedCornerShape(OsuRadius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    MedalIconView(
                        iconUrl = medal.iconUrl,
                        unlocked = unlock != null,
                        imageLoader = externalImageLoader,
                        size = 44.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = medal.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = medal.grouping,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unlock != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    if (medal.description.isNotBlank()) {
                        Text(
                            text = medal.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!medal.instructions.isNullOrBlank()) {
                        OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Text(
                                text = medal.instructions,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(OsuSpacing.md)
                            )
                        }
                    }
                    Text(
                        text = if (unlock != null) {
                            val dateStr = unlock.achievedAt.take(10).ifBlank { "Recorded" }
                            "Unlocked: $dateStr"
                        } else {
                            "Locked — not yet earned"
                        },
                        style = MaterialTheme.typography.labelMedium.tnum(),
                        color = if (unlock != null) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMedalDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    OsuGroupedContainer(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(OsuSpacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Medals",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OsuMicroTag(
                        text = String.format(Locale.US, "%d / %d (%.0f%%)", unlockedCount, totalCount, pct),
                        containerColor = surfaces.accentSubtle,
                        contentColor = MaterialTheme.colorScheme.primary,
                        isNumeric = true
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(OsuSpacing.sm))
            LinearProgressIndicator(
                progress = { (pct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OsuSpacing.xs)
                    .clip(RoundedCornerShape(OsuRadius.xs)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            if (recentUnlocks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(OsuSpacing.md))
                Text(
                    text = "Recently unlocked",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(OsuSpacing.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    items(recentUnlocks, key = { it.first.id }) { (medal, unlock) ->
                        Surface(
                            shape = RoundedCornerShape(OsuRadius.md),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(OsuRadius.md))
                                .clickable { selectedMedalDetail = medal to unlock }
                        ) {
                            Row(
                                modifier = Modifier.padding(OsuSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                MedalIconView(
                                    iconUrl = medal.iconUrl,
                                    unlocked = true,
                                    imageLoader = externalImageLoader,
                                    size = 32.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = medal.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val dateStr = unlock.achievedAt.take(10)
                                    if (dateStr.isNotBlank()) {
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.labelSmall.tnum(),
                                            color = surfaces.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (onRefreshProfile != null && profile.userId > 0L) {
                Spacer(modifier = Modifier.height(OsuSpacing.xs))
                Text(
                    text = "Refresh profile to sync unlocked medals",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = OsuMotion.expandEnter(),
                exit = OsuMotion.expandExit()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                        items(MedalsCatalogRepository.MEDAL_CATEGORY_FILTERS, key = { it }) { cat ->
                            OsuFilterChip(
                                label = cat,
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                        }
                    }

                    val displayList = if (showAllInCategory) {
                        filteredMedals
                    } else {
                        filteredMedals.take(20)
                    }
                    if (displayList.isEmpty()) {
                        Text(
                            text = "No medals in this category",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            displayList.forEach { medal ->
                                val unlock = unlockedById[medal.id]
                                val isUnlocked = unlock != null
                                Surface(
                                    shape = RoundedCornerShape(OsuRadius.md),
                                    color = if (isUnlocked) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    border = osuHairlineBorder(
                                        if (isUnlocked) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(OsuRadius.md))
                                        .clickable { selectedMedalDetail = medal to unlock }
                                        .alpha(if (isUnlocked) 1f else 0.42f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        MedalIconView(
                                            iconUrl = medal.iconUrl,
                                            unlocked = isUnlocked,
                                            imageLoader = externalImageLoader,
                                            size = 24.dp
                                        )
                                        Text(
                                            text = medal.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (filteredMedals.size > 20 && !showAllInCategory) {
                            TextButton(
                                onClick = { showAllInCategory = true },
                                contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                            ) {
                                Text(
                                    text = "Show all (${filteredMedals.size})",
                                    style = MaterialTheme.typography.labelMedium.tnum(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedalIconView(
    iconUrl: String,
    unlocked: Boolean,
    imageLoader: ImageLoader,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (iconUrl.isNotBlank()) {
            AsyncImage(
                model = iconUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = if (unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
