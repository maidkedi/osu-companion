package com.osuchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MpLobbyState
import com.osuchat.app.domain.model.MpMatchHistory
import com.osuchat.app.domain.model.MpPickBanMode
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpSlot
import com.osuchat.app.domain.model.MpSlotStatus
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.service.MpTimerManager
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusConnecting
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOffline
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.StatusRead
import com.osuchat.app.ui.theme.TeamBlue
import com.osuchat.app.ui.theme.TeamRed
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.MappoolPickBanResolver
import com.osuchat.app.util.MpLobbyParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.NumberFormat
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MpTimerEntryPoint {
    fun mpTimerManager(): MpTimerManager
}

private enum class MpBoardTab(val label: String) {
    CONTROLS("Controls"),
    SLOTS("Slots"),
    SCORES("Scores"),
    MACROS("Macros")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MpLobbyBoard(
    lobby: MpLobbyState,
    selfUsername: String,
    matchHistory: MpMatchHistory?,
    isHistoryLoading: Boolean,
    onSendCommand: (String) -> Unit,
    onRefreshMatchHistory: () -> Unit,
    onOpenBeatmapSearch: () -> Unit,
    onOpenPlayerProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    historyError: String? = null,
    mappools: List<Mappool> = emptyList(),
    onSaveMappool: (poolId: Long?, name: String, slots: List<MappoolSlot>) -> Unit = { _, _, _ -> },
    onDeleteMappool: (Long) -> Unit = {},
    onSelectMappoolSlot: ((MappoolSlot) -> Unit)? = null,
    seriesState: MatchSeriesState = MatchSeriesState(),
    refereeMacros: List<RefereeMacro> = RefereeMacro.DEFAULT_MACROS,
    onSetSeriesBestOf: (Int) -> Unit = {},
    onSetSeriesTeamNames: (String, String) -> Unit = { _, _ -> },
    onAdjustSeriesScore: (MpTeam, Int) -> Unit = { _, _ -> },
    onUndoLastSeriesRound: () -> Unit = {},
    onResetSeries: () -> Unit = {},
    onSetAutoScoreTeamVs: (Boolean) -> Unit = {},
    onSetWarmupActive: (Boolean) -> Unit = {},
    onSetSelectedPoolId: (Long?) -> Unit = {},
    onSetPickBanMode: (MpPickBanMode) -> Unit = {},
    onUpdatePoolSlotState: (MappoolSlot, MpPoolSlotState, Boolean) -> Unit = { _, _, _ -> },
    onSendMacro: (String) -> Unit = {},
    onSaveMacros: (List<RefereeMacro>) -> Unit = {},
    onResetMacros: () -> Unit = {},
    onNotifyMessage: (String) -> Unit = {},
    onGetCachedBeatmaps: (suspend (Collection<Long>) -> Map<Long, BeatmapMetadata>)? = null,
    onResolveBeatmapDetails: (suspend (Long) -> Result<BeatmapMetadata>)? = null,
    onOpenMatchAnalyzer: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val timerManager = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MpTimerEntryPoint::class.java
            ).mpTimerManager()
        }.getOrNull()
    }
    val activeTimers by timerManager?.activeTimers?.collectAsStateWithLifecycle(initialValue = emptyMap())
        ?: remember { mutableStateOf(emptyMap()) }
    val activeTimer = remember(activeTimers, lobby.channelId) {
        val key = lobby.channelId.trim().lowercase(Locale.ROOT)
        activeTimers[key]
    }
    var activeTab by rememberSaveable(lobby.channelId) { mutableStateOf(MpBoardTab.CONTROLS) }
    var selectedSlotForActions by remember { mutableStateOf<MpSlot?>(null) }
    var emptySlotToMove by remember { mutableStateOf<Int?>(null) }
    var showMapDialog by remember { mutableStateOf(false) }
    var showModsDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showRefDialog by remember { mutableStateOf(false) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showSetModeDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showPoolDropdown by remember { mutableStateOf(false) }
    var showMappoolManager by remember { mutableStateOf(false) }
    var showBestOfDropdown by remember { mutableStateOf(false) }
    var showEditTeamsDialog by remember { mutableStateOf(false) }
    var showManageMacrosDialog by remember { mutableStateOf(false) }

    val activePool = remember(mappools, seriesState.selectedPoolId) {
        mappools.firstOrNull { it.id == seriesState.selectedPoolId } ?: mappools.firstOrNull()
    }
    var lastSelectedPoolSlotId by rememberSaveable(lobby.channelId) { mutableStateOf<Long?>(null) }
    var resolvingPoolBeatmapId by remember { mutableStateOf<Long?>(null) }
    val poolBeatmapMetadata = remember { mutableStateMapOf<Long, BeatmapMetadata>() }

    val activePoolBeatmapIds = remember(activePool) {
        activePool?.slots.orEmpty().map { it.beatmapId }.filter { it > 0L }.distinct()
    }
    LaunchedEffect(activePoolBeatmapIds, onGetCachedBeatmaps, onResolveBeatmapDetails) {
        if (activePoolBeatmapIds.isEmpty()) {
            resolvingPoolBeatmapId = null
            return@LaunchedEffect
        }
        val missingCached = activePoolBeatmapIds.filter { it !in poolBeatmapMetadata }
        if (missingCached.isNotEmpty() && onGetCachedBeatmaps != null) {
            runCatching { onGetCachedBeatmaps(missingCached) }
                .getOrNull()
                ?.let { cached -> poolBeatmapMetadata.putAll(cached) }
        }
        if (onResolveBeatmapDetails == null) return@LaunchedEffect
        for (beatmapId in activePoolBeatmapIds) {
            if (beatmapId in poolBeatmapMetadata) continue
            resolvingPoolBeatmapId = beatmapId
            runCatching { onResolveBeatmapDetails(beatmapId) }
                .getOrNull()
                ?.getOrNull()
                ?.let { meta -> poolBeatmapMetadata[beatmapId] = meta }
        }
        resolvingPoolBeatmapId = null
    }

    val copyDiscordSummary = {
        val summary = MpLobbyParser.formatDiscordMatchSummary(lobby, seriesState, activePool)
        clipboardManager.setText(AnnotatedString(summary))
        onNotifyMessage("Match summary copied to clipboard")
    }

    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val buttonShape = RoundedCornerShape(OsuRadius.md)
    val chipShape = RoundedCornerShape(OsuRadius.md)
    val compactBtnPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
    ) {
        SeriesScoreboardHeaderCard(
            lobby = lobby,
            seriesState = seriesState,
            showBestOfDropdown = showBestOfDropdown,
            onToggleBestOfDropdown = { showBestOfDropdown = it },
            onSelectBestOf = { bo ->
                showBestOfDropdown = false
                onSetSeriesBestOf(bo)
            },
            onOpenEditTeams = { showEditTeamsDialog = true },
            onAdjustScore = onAdjustSeriesScore,
            onUndoLastRound = onUndoLastSeriesRound,
            onResetSeries = onResetSeries,
            onToggleAutoScore = onSetAutoScoreTeamVs,
            onToggleWarmup = onSetWarmupActive,
            onCopySummary = copyDiscordSummary,
            onRefreshSettings = { onSendCommand(MpLobbyParser.buildSettingsCommand()) },
            onOpenMatchAnalyzer = onOpenMatchAnalyzer
        )

        AnimatedVisibility(
            visible = activeTimer != null,
            enter = OsuMotion.expandEnter(),
            exit = OsuMotion.expandExit()
        ) {
            activeTimer?.let { timer ->
                val remaining = timer.remainingSeconds.coerceAtLeast(0)
                val mins = remaining / 60
                val secs = remaining % 60
                val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)
                val total = timer.totalSeconds.coerceAtLeast(1)
                val progress = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                OsuAccentBarContainer(
                    showAccentBar = true,
                    accentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (timer.isMatchStart) {
                                        "Match starts in"
                                    } else {
                                        timer.label.ifBlank { "Timer" }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.titleMedium.tnum(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = { onSendCommand("!mp aborttimer") },
                                    shape = buttonShape,
                                    contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Abort", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
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

        OsuSegmentedBar(
            items = MpBoardTab.entries,
            selectedIndex = MpBoardTab.entries.indexOf(activeTab),
            onSelect = { activeTab = MpBoardTab.entries[it] },
            labelProvider = { tab ->
                val badgeCount = when (tab) {
                    MpBoardTab.SLOTS -> lobby.slots.take(lobby.roomSize.coerceIn(1, 16)).count { it.username != null }
                    MpBoardTab.SCORES -> seriesState.rounds.count { !it.isWarmup }
                    else -> null
                }
                if (badgeCount != null && badgeCount > 0) "${tab.label} ($badgeCount)" else tab.label
            },
            isNumeric = true
        )

        when (activeTab) {
            MpBoardTab.CONTROLS -> {
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = osuHairlineBorder(),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(OsuSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = lobby.beatmapTitle?.takeIf { it.isNotBlank() }
                                    ?: lobby.beatmapUrl
                                    ?: "No beatmap selected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (lobby.beatmapTitle.isNullOrBlank() && lobby.beatmapUrl.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(OsuSpacing.sm))
                            OutlinedButton(
                                onClick = onOpenBeatmapSearch,
                                shape = buttonShape,
                                border = osuHairlineBorder(),
                                contentPadding = PaddingValues(horizontal = OsuSpacing.md),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(OsuSpacing.xs))
                                Text(
                                    text = "Pick map",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.sm))
                        OsuHairlineDivider()
                        Spacer(modifier = Modifier.height(OsuSpacing.sm))

                        Text(
                            text = "Referee controls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.xs))

                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                MpControlGridButton(
                                    label = "Start 10s",
                                    icon = Icons.Rounded.PlayArrow,
                                    onClick = { onSendCommand(MpLobbyParser.buildStartCommand(10)) },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    borderColor = null,
                                    isNumeric = true,
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Force start",
                                    icon = Icons.Rounded.FastForward,
                                    onClick = { onSendCommand(MpLobbyParser.buildStartCommand()) },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                MpControlGridButton(
                                    label = "Abort",
                                    icon = Icons.Rounded.Stop,
                                    onClick = { onSendCommand(MpLobbyParser.buildAbortCommand()) },
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                    contentColor = MaterialTheme.colorScheme.error,
                                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = if (lobby.isLocked) "Unlock" else "Lock",
                                    icon = if (lobby.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    onClick = { onSendCommand(MpLobbyParser.buildLockCommand(!lobby.isLocked)) },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Close",
                                    icon = Icons.Rounded.Close,
                                    onClick = { onSendCommand(MpLobbyParser.buildCloseCommand()) },
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.error,
                                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.md))
                        OsuHairlineDivider()
                        Spacer(modifier = Modifier.height(OsuSpacing.sm))

                        Text(
                            text = "Room settings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.xs))

                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                MpControlGridButton(
                                    label = "Map",
                                    icon = Icons.Rounded.MusicNote,
                                    onClick = { showMapDialog = true },
                                    horizontalPadding = OsuSpacing.xs,
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Mods",
                                    icon = Icons.Rounded.Tune,
                                    onClick = { showModsDialog = true },
                                    horizontalPadding = OsuSpacing.xs,
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Invite",
                                    icon = Icons.Rounded.PersonAdd,
                                    onClick = { showInviteDialog = true },
                                    horizontalPadding = OsuSpacing.xs,
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Ref",
                                    icon = Icons.Rounded.Shield,
                                    onClick = { showRefDialog = true },
                                    horizontalPadding = OsuSpacing.xs,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                MpControlGridButton(
                                    label = "Size (${lobby.roomSize})",
                                    icon = Icons.Rounded.Groups,
                                    onClick = { showSizeDialog = true },
                                    isNumeric = true,
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Rules",
                                    icon = Icons.Rounded.Gavel,
                                    onClick = { showSetModeDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                MpControlGridButton(
                                    label = "Password",
                                    icon = Icons.Rounded.Key,
                                    onClick = { showPasswordDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.md))
                        OsuHairlineDivider()
                        Spacer(modifier = Modifier.height(OsuSpacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick macros",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { showManageMacrosDialog = true },
                                shape = chipShape,
                                contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Manage", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = refereeMacros,
                                key = { it.id }
                            ) { macro ->
                                Surface(
                                    shape = chipShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = osuHairlineBorder(),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .clickable { onSendMacro(macro.message) }
                                ) {
                                    Text(
                                        text = macro.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.md))
                        OsuHairlineDivider()
                        Spacer(modifier = Modifier.height(OsuSpacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tournament mappool",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showMappoolManager = true },
                                shape = chipShape,
                                contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Manage", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(OsuSpacing.xs))

                        if (mappools.isEmpty()) {
                            OutlinedButton(
                                onClick = { showMappoolManager = true },
                                shape = buttonShape,
                                border = osuHairlineBorder(),
                                contentPadding = compactBtnPadding,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create or paste mappool", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Box {
                                OutlinedButton(
                                    onClick = { showPoolDropdown = true },
                                    shape = buttonShape,
                                    border = osuHairlineBorder(),
                                    contentPadding = compactBtnPadding,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = activePool?.let { "${it.name} (${it.slots.size} slots)" } ?: "Select mappool",
                                        style = MaterialTheme.typography.labelMedium.tnum(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Select mappool",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showPoolDropdown,
                                    onDismissRequest = { showPoolDropdown = false },
                                    shape = RoundedCornerShape(OsuRadius.md),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = osuHairlineBorder()
                                ) {
                                    mappools.forEach { pool ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${pool.name} (${pool.slots.size})",
                                                    style = MaterialTheme.typography.bodyMedium.tnum(),
                                                    fontWeight = if (pool.id == activePool?.id) {
                                                        FontWeight.SemiBold
                                                    } else {
                                                        FontWeight.Normal
                                                    }
                                                )
                                            },
                                            onClick = {
                                                onSetSelectedPoolId(pool.id)
                                                showPoolDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (activePool != null && activePool.slots.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(OsuSpacing.sm))

                                val modeRows = listOf(
                                    listOf(MpPickBanMode.RED_PICK, MpPickBanMode.BLUE_PICK),
                                    listOf(MpPickBanMode.RED_BAN, MpPickBanMode.BLUE_BAN, MpPickBanMode.TB_NEUTRAL)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                    modeRows.forEach { rowModes ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                        ) {
                                            rowModes.forEach { mode ->
                                                val selected = seriesState.pickBanMode == mode
                                                val modeTint = when (mode) {
                                                    MpPickBanMode.RED_PICK, MpPickBanMode.RED_BAN -> TeamRed
                                                    MpPickBanMode.BLUE_PICK, MpPickBanMode.BLUE_BAN -> TeamBlue
                                                    MpPickBanMode.TB_NEUTRAL -> MaterialTheme.colorScheme.primary
                                                }
                                                val modeBg by animateColorAsState(
                                                    targetValue = if (selected) {
                                                        modeTint.copy(alpha = 0.16f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    },
                                                    animationSpec = OsuMotion.colorSpec(),
                                                    label = "pickBanModeBg"
                                                )
                                                val modeBorder by animateColorAsState(
                                                    targetValue = if (selected) {
                                                        modeTint.copy(alpha = 0.45f)
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant
                                                    },
                                                    animationSpec = OsuMotion.colorSpec(),
                                                    label = "pickBanModeBorder"
                                                )
                                                Surface(
                                                    shape = chipShape,
                                                    color = modeBg,
                                                    border = osuHairlineBorder(modeBorder),
                                                    tonalElevation = 0.dp,
                                                    shadowElevation = 0.dp,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .clip(chipShape)
                                                        .clickable(role = Role.Tab) { onSetPickBanMode(mode) }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = OsuSpacing.xs),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = mode.label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                            color = if (selected) modeTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(OsuSpacing.sm))

                                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                    activePool.slots.chunked(4).forEach { rowSlots ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                        ) {
                                            rowSlots.forEach { slot ->
                                                key(slot.id, slot.slotTag, slot.orderIndex) {
                                                    val currentState = seriesState.slotStates[slot.id] ?: MpPoolSlotState.AVAILABLE
                                                    var showSlotMenu by remember(slot.id) { mutableStateOf(false) }
                                                    val isActivePick = seriesState.activePickedSlotId == slot.id
                                                    val isBannedState = MappoolPickBanResolver.isBannedState(currentState)
                                                    val isPickLocked = MappoolPickBanResolver.isSlotPickLocked(
                                                        currentState = currentState,
                                                        activeMode = seriesState.pickBanMode
                                                    )

                                                    val chipContainerColor = when (currentState) {
                                                        MpPoolSlotState.AVAILABLE -> MaterialTheme.colorScheme.surfaceContainerHigh
                                                        MpPoolSlotState.PLAYED -> MaterialTheme.colorScheme.surfaceContainerLowest
                                                        MpPoolSlotState.PICKED_RED -> TeamRed.copy(alpha = 0.18f)
                                                        MpPoolSlotState.PICKED_BLUE -> TeamBlue.copy(alpha = 0.18f)
                                                        MpPoolSlotState.PLAYED_TB -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                        MpPoolSlotState.BANNED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                                        MpPoolSlotState.BANNED_RED -> TeamRed.copy(alpha = 0.12f)
                                                        MpPoolSlotState.BANNED_BLUE -> TeamBlue.copy(alpha = 0.12f)
                                                    }
                                                    val chipBorder = when {
                                                        isActivePick -> osuHairlineBorder(MaterialTheme.colorScheme.primary)
                                                        currentState == MpPoolSlotState.AVAILABLE -> osuHairlineBorder()
                                                        currentState == MpPoolSlotState.PLAYED -> osuHairlineBorder(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                                        currentState == MpPoolSlotState.PICKED_RED -> osuHairlineBorder(TeamRed.copy(alpha = 0.55f))
                                                        currentState == MpPoolSlotState.PICKED_BLUE -> osuHairlineBorder(TeamBlue.copy(alpha = 0.55f))
                                                        currentState == MpPoolSlotState.PLAYED_TB -> osuHairlineBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                                        currentState == MpPoolSlotState.BANNED -> osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.55f))
                                                        currentState == MpPoolSlotState.BANNED_RED -> osuHairlineBorder(TeamRed.copy(alpha = 0.45f))
                                                        currentState == MpPoolSlotState.BANNED_BLUE -> osuHairlineBorder(TeamBlue.copy(alpha = 0.45f))
                                                        else -> osuHairlineBorder()
                                                    }
                                                    val chipAlpha = when {
                                                        isPickLocked -> 0.45f
                                                        currentState == MpPoolSlotState.PLAYED -> 0.5f
                                                        isBannedState -> 0.72f
                                                        else -> 1f
                                                    }
                                                    val labelColor = when (currentState) {
                                                        MpPoolSlotState.AVAILABLE -> MaterialTheme.colorScheme.onSurface
                                                        MpPoolSlotState.PLAYED -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        MpPoolSlotState.PICKED_RED, MpPoolSlotState.BANNED_RED -> TeamRed
                                                        MpPoolSlotState.PICKED_BLUE, MpPoolSlotState.BANNED_BLUE -> TeamBlue
                                                        MpPoolSlotState.PLAYED_TB -> MaterialTheme.colorScheme.primary
                                                        MpPoolSlotState.BANNED -> MaterialTheme.colorScheme.error
                                                    }
                                                    val badgePrefix = when (currentState) {
                                                        MpPoolSlotState.PICKED_RED -> "R·"
                                                        MpPoolSlotState.PICKED_BLUE -> "B·"
                                                        MpPoolSlotState.BANNED_RED -> "RX·"
                                                        MpPoolSlotState.BANNED_BLUE -> "BX·"
                                                        MpPoolSlotState.PLAYED_TB -> "TB·"
                                                        else -> ""
                                                    }

                                                    Box(modifier = Modifier.weight(1f)) {
                                                        Surface(
                                                            shape = chipShape,
                                                            color = chipContainerColor,
                                                            border = chipBorder,
                                                            tonalElevation = 0.dp,
                                                            shadowElevation = 0.dp,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(36.dp)
                                                                .alpha(chipAlpha)
                                                                .clip(chipShape)
                                                                .combinedClickable(
                                                                    enabled = !isPickLocked,
                                                                    onClick = {
                                                                        val outcome = MappoolPickBanResolver.resolveTap(
                                                                            currentState = currentState,
                                                                            activeMode = seriesState.pickBanMode
                                                                        ) ?: return@combinedClickable
                                                                        lastSelectedPoolSlotId = if (outcome.nextState == MpPoolSlotState.AVAILABLE) {
                                                                            null
                                                                        } else {
                                                                            slot.id
                                                                        }
                                                                        onUpdatePoolSlotState(slot, outcome.nextState, outcome.shouldEmitMpCommands)
                                                                        if (outcome.shouldEmitMpCommands && onSelectMappoolSlot == null) {
                                                                            MpLobbyParser.buildMapCommand(slot.beatmapId)?.let(onSendCommand)
                                                                            val modsStr = slot.mods.trim().ifEmpty { "NF" }
                                                                            onSendCommand("!mp mods $modsStr")
                                                                        }
                                                                    },
                                                                    onLongClick = { showSlotMenu = true }
                                                                )
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.padding(horizontal = OsuSpacing.xs),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "$badgePrefix${slot.slotTag}",
                                                                    style = MaterialTheme.typography.labelMedium.tnum(),
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = labelColor,
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    textDecoration = if (isBannedState) {
                                                                        TextDecoration.LineThrough
                                                                    } else {
                                                                        TextDecoration.None
                                                                    }
                                                                )
                                                            }
                                                        }

                                                        DropdownMenu(
                                                            expanded = showSlotMenu,
                                                            onDismissRequest = { showSlotMenu = false },
                                                            shape = RoundedCornerShape(OsuRadius.md),
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                            tonalElevation = 0.dp,
                                                            shadowElevation = 0.dp,
                                                            border = osuHairlineBorder()
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("Red pick (${slot.mods.ifBlank { "NF" }})", color = TeamRed) },
                                                                onClick = {
                                                                    showSlotMenu = false
                                                                    onUpdatePoolSlotState(slot, MpPoolSlotState.PICKED_RED, true)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Blue pick (${slot.mods.ifBlank { "NF" }})", color = TeamBlue) },
                                                                onClick = {
                                                                    showSlotMenu = false
                                                                    onUpdatePoolSlotState(slot, MpPoolSlotState.PICKED_BLUE, true)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("TB / Neutral pick (${slot.mods.ifBlank { "NF" }})") },
                                                                onClick = {
                                                                    showSlotMenu = false
                                                                    onUpdatePoolSlotState(slot, MpPoolSlotState.PLAYED_TB, true)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Red ban", color = TeamRed) },
                                                                onClick = {
                                                                    showSlotMenu = false
                                                                    onUpdatePoolSlotState(slot, MpPoolSlotState.BANNED_RED, false)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Blue ban", color = TeamBlue) },
                                                                onClick = {
                                                                    showSlotMenu = false
                                                                    onUpdatePoolSlotState(slot, MpPoolSlotState.BANNED_BLUE, false)
                                                                }
                                                            )
                                                            if (currentState != MpPoolSlotState.AVAILABLE) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Reset to available") },
                                                                    onClick = {
                                                                        showSlotMenu = false
                                                                        onUpdatePoolSlotState(slot, MpPoolSlotState.AVAILABLE, false)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            repeat(4 - rowSlots.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                val previewSlot = activePool.slots.firstOrNull { it.id == seriesState.activePickedSlotId }
                                    ?: activePool.slots.firstOrNull { it.id == lastSelectedPoolSlotId }
                                    ?: activePool.slots.firstOrNull { lobby.beatmapId != null && it.beatmapId == lobby.beatmapId }
                                if (previewSlot != null) {
                                    val previewMeta = poolBeatmapMetadata[previewSlot.beatmapId]
                                    Spacer(modifier = Modifier.height(OsuSpacing.sm))
                                    Surface(
                                        shape = buttonShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = osuHairlineBorder(),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${previewSlot.slotTag} · ${previewSlot.mods.ifBlank { "NF" }}",
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = if (previewMeta != null) {
                                                        "${previewMeta.artist} - ${previewMeta.title} [${previewMeta.version}]"
                                                    } else if (resolvingPoolBeatmapId == previewSlot.beatmapId) {
                                                        "ID: ${previewSlot.beatmapId} · Loading…"
                                                    } else {
                                                        "ID: ${previewSlot.beatmapId}"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (previewMeta != null) {
                                                Spacer(Modifier.width(OsuSpacing.sm))
                                                Text(
                                                    text = String.format(
                                                        Locale.US,
                                                        "★ %.2f\n%.0f BPM",
                                                        previewMeta.starRating,
                                                        previewMeta.bpm
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.End
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

            MpBoardTab.SLOTS -> {
                val safeRoomSize = lobby.roomSize.coerceIn(1, 16)
                val visibleSlots = remember(lobby.slots, safeRoomSize) {
                    lobby.slots.take(safeRoomSize)
                }
                val occupiedCount = remember(visibleSlots) {
                    visibleSlots.count { it.username != null }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lobby slots",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$occupiedCount/$safeRoomSize",
                        style = MaterialTheme.typography.labelLarge.tnum(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (visibleSlots.isEmpty()) {
                    OsuGroupedContainer {
                        Text(
                            text = "No slot data received yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(OsuSpacing.md)
                        )
                    }
                } else {
                    visibleSlots.chunked(2).forEach { pair ->
                        key(pair.first().slotNumber) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                pair.forEach { slot ->
                                    key(slot.slotNumber) {
                                        MpSlotCell(
                                            slot = slot,
                                            onClick = {
                                                if (slot.username != null) {
                                                    selectedSlotForActions = slot
                                                } else {
                                                    emptySlotToMove = slot.slotNumber
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            MpBoardTab.SCORES -> {
                if (seriesState.rounds.isNotEmpty()) {
                    val fmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
                    Surface(
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(OsuSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Series rounds",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedButton(
                                    onClick = copyDiscordSummary,
                                    shape = chipShape,
                                    border = osuHairlineBorder(),
                                    contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text("Copy summary", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            seriesState.rounds.forEachIndexed { idx, round ->
                                val mapText = listOf(round.slotTag, round.beatmapLabel)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "Round ${idx + 1}" }
                                val winColor = when {
                                    round.isWarmup -> MaterialTheme.colorScheme.onSurfaceVariant
                                    round.winner == MpTeam.RED -> TeamRed
                                    round.winner == MpTeam.BLUE -> TeamBlue
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                val winLabel = when {
                                    round.isWarmup -> "Warmup"
                                    round.winner == MpTeam.RED -> "${seriesState.redTeamName} +1"
                                    round.winner == MpTeam.BLUE -> "${seriesState.blueTeamName} +1"
                                    else -> "Draw"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${idx + 1}. $mapText",
                                            style = MaterialTheme.typography.labelSmall.tnum(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (round.redTotalScore > 0L || round.blueTotalScore > 0L) {
                                            Text(
                                                text = "${fmt.format(round.redTotalScore)} vs ${fmt.format(round.blueTotalScore)}",
                                                style = MaterialTheme.typography.labelSmall.tnum(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text(
                                        text = winLabel,
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = winColor
                                    )
                                }
                            }
                        }
                    }
                }

                if (lobby.matchId != null) {
                    Surface(
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(OsuSpacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Match history",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (seriesState.rounds.isEmpty()) {
                                        OutlinedButton(
                                            onClick = copyDiscordSummary,
                                            shape = chipShape,
                                            border = osuHairlineBorder(),
                                            contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(OsuSpacing.xs))
                                            Text("Copy summary", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    TextButton(
                                        onClick = onRefreshMatchHistory,
                                        enabled = !isHistoryLoading,
                                        shape = chipShape,
                                        contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
                                    ) {
                                        Text(if (isHistoryLoading) "Loading…" else "Refresh")
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                            ) {
                                if (isHistoryLoading) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(OsuSpacing.xs))

                            if (isHistoryLoading && (matchHistory == null || matchHistory.games.isEmpty())) {
                                val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(OsuRadius.md))
                                                .background(blockColor)
                                        )
                                    }
                                }
                            } else if (!historyError.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sanitizeCardErrorMessage(historyError, "Could not load match history"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                                    OutlinedButton(
                                        onClick = onRefreshMatchHistory,
                                        shape = chipShape,
                                        border = osuHairlineBorder(),
                                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                                    ) {
                                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else if (matchHistory == null || matchHistory.games.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "No completed games loaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                                    OutlinedButton(
                                        onClick = onRefreshMatchHistory,
                                        shape = chipShape,
                                        border = osuHairlineBorder(),
                                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                                    ) {
                                        Text("Load", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                val fmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
                                val recentGames = remember(matchHistory) {
                                    matchHistory.games.takeLast(5).reversed()
                                }
                                recentGames.forEachIndexed { gameIdx, game ->
                                    key(if (game.gameId > 0L) game.gameId else "g_$gameIdx") {
                                        if (gameIdx > 0) {
                                            OsuHairlineDivider(modifier = Modifier.padding(vertical = OsuSpacing.sm))
                                        }
                                        Text(
                                            text = game.beatmapTitle.ifBlank { "Unknown beatmap" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (game.redTeamTotal > 0L || game.blueTeamTotal > 0L) {
                                            Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                                            Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)) {
                                                Text(
                                                    text = "Red ${fmt.format(game.redTeamTotal)}",
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    color = TeamRed,
                                                    fontWeight = if (game.redTeamTotal >= game.blueTeamTotal) {
                                                        FontWeight.SemiBold
                                                    } else {
                                                        FontWeight.Medium
                                                    }
                                                )
                                                Text(
                                                    text = "Blue ${fmt.format(game.blueTeamTotal)}",
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    color = TeamBlue,
                                                    fontWeight = if (game.blueTeamTotal > game.redTeamTotal) {
                                                        FontWeight.SemiBold
                                                    } else {
                                                        FontWeight.Medium
                                                    }
                                                )
                                            }
                                        }
                                        game.scores.take(6).forEachIndexed { scoreIdx, s ->
                                            key(if (s.userId > 0L) s.userId else "s_${scoreIdx}_${s.username}") {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = OsuSpacing.xxs),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val teamPrefix = when (s.team?.lowercase(Locale.US)) {
                                                        "red" -> "R "
                                                        "blue" -> "B "
                                                        else -> ""
                                                    }
                                                    val teamColor = when (s.team?.lowercase(Locale.US)) {
                                                        "red" -> TeamRed
                                                        "blue" -> TeamBlue
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (teamPrefix.isNotEmpty()) {
                                                            Text(
                                                                text = teamPrefix,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = teamColor
                                                            )
                                                        }
                                                        Text(
                                                            text = s.username,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                                                    val accStr = if (s.accuracy.isFinite()) {
                                                        String.format(Locale.US, "%.2f%%", s.accuracy)
                                                    } else {
                                                        "—"
                                                    }
                                                    Text(
                                                        text = "${fmt.format(s.score)} ($accStr)",
                                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                                        color = MaterialTheme.colorScheme.onSurface,
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
                } else if (seriesState.rounds.isEmpty()) {
                    Surface(
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(OsuSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No completed rounds yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = copyDiscordSummary,
                                shape = chipShape,
                                border = osuHairlineBorder(),
                                contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Copy summary", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            MpBoardTab.MACROS -> {
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = osuHairlineBorder(),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Referee macros",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap to send · {red}, {blue}, {score}, {mp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { showManageMacrosDialog = true },
                                shape = chipShape,
                                border = osuHairlineBorder(),
                                contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Manage", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        refereeMacros.forEach { macro ->
                            val resolved = MpLobbyParser.resolveRefereeMacro(macro.message, seriesState, lobby)
                            Surface(
                                shape = buttonShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = osuHairlineBorder(),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(buttonShape)
                                    .clickable { onSendMacro(macro.message) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = macro.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = resolved,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send ${macro.label}",
                                        tint = MaterialTheme.colorScheme.primary,
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

    if (showEditTeamsDialog) {
        var redInput by remember(seriesState.redTeamName) { mutableStateOf(seriesState.redTeamName) }
        var blueInput by remember(seriesState.blueTeamName) { mutableStateOf(seriesState.blueTeamName) }
        AlertDialog(
            onDismissRequest = { showEditTeamsDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Edit team names",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    OutlinedTextField(
                        value = redInput,
                        onValueChange = { redInput = it },
                        label = { Text("Red team") },
                        singleLine = true,
                        shape = buttonShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = blueInput,
                        onValueChange = { blueInput = it },
                        label = { Text("Blue team") },
                        singleLine = true,
                        shape = buttonShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetSeriesTeamNames(redInput, blueInput)
                        showEditTeamsDialog = false
                    },
                    shape = buttonShape
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTeamsDialog = false }, shape = buttonShape) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManageMacrosDialog) {
        ManageRefereeMacrosDialog(
            macros = refereeMacros,
            onSave = { updated ->
                onSaveMacros(updated)
                showManageMacrosDialog = false
            },
            onResetDefaults = {
                onResetMacros()
                showManageMacrosDialog = false
            },
            onDismiss = { showManageMacrosDialog = false }
        )
    }

    selectedSlotForActions?.let { slot ->
        val uname = slot.username ?: return@let
        var targetMoveSlot by remember(slot) { mutableStateOf(slot.slotNumber.toString()) }
        val parsedMoveSlot = targetMoveSlot.toIntOrNull()?.takeIf { it in 1..16 }
        AlertDialog(
            onDismissRequest = { selectedSlotForActions = null },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Slot #${slot.slotNumber}: $uname",
                    style = MaterialTheme.typography.titleMedium.tnum(),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Button(
                            onClick = {
                                MpLobbyParser.buildTeamCommand(uname, "red", slot.userId)?.let(onSendCommand)
                                selectedSlotForActions = null
                            },
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TeamRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("Team Red") }
                        Button(
                            onClick = {
                                MpLobbyParser.buildTeamCommand(uname, "blue", slot.userId)?.let(onSendCommand)
                                selectedSlotForActions = null
                            },
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TeamBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("Team Blue") }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        OutlinedTextField(
                            value = targetMoveSlot,
                            onValueChange = { targetMoveSlot = it.filter { c -> c.isDigit() } },
                            label = { Text("Slot (1–16)") },
                            shape = buttonShape,
                            singleLine = true,
                            isError = targetMoveSlot.isNotBlank() && parsedMoveSlot == null,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val num = parsedMoveSlot ?: return@OutlinedButton
                                MpLobbyParser.buildMoveCommand(uname, num, slot.userId)?.let(onSendCommand)
                                selectedSlotForActions = null
                            },
                            enabled = parsedMoveSlot != null,
                            shape = buttonShape,
                            border = osuHairlineBorder()
                        ) { Text("Move") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = {
                                MpLobbyParser.buildHostCommand(uname, slot.userId)?.let(onSendCommand)
                                selectedSlotForActions = null
                            },
                            shape = buttonShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Give host") }
                        OutlinedButton(
                            onClick = {
                                MpLobbyParser.buildKickCommand(uname, slot.userId)?.let(onSendCommand)
                                selectedSlotForActions = null
                            },
                            shape = buttonShape,
                            border = osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) { Text("Kick") }
                    }

                    TextButton(
                        onClick = {
                            selectedSlotForActions = null
                            onOpenPlayerProfile(uname)
                        },
                        shape = buttonShape
                    ) { Text("View profile") }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSlotForActions = null }, shape = buttonShape) {
                    Text("Done")
                }
            }
        )
    }

    emptySlotToMove?.let { slotNum ->
        var playerInput by remember(slotNum) { mutableStateOf(selfUsername) }
        val trimmedPlayer = playerInput.trim()
        AlertDialog(
            onDismissRequest = { emptySlotToMove = null },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Move player to slot #$slotNum",
                    style = MaterialTheme.typography.titleMedium.tnum(),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = playerInput,
                    onValueChange = { playerInput = it },
                    label = { Text("Username") },
                    shape = buttonShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val matchedUserId = lobby.slots.firstOrNull {
                            it.username.equals(trimmedPlayer, ignoreCase = true)
                        }?.userId
                        MpLobbyParser.buildMoveCommand(trimmedPlayer, slotNum, matchedUserId)?.let { cmd ->
                            onSendCommand(cmd)
                            emptySlotToMove = null
                        }
                    },
                    enabled = trimmedPlayer.isNotEmpty(),
                    shape = buttonShape
                ) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { emptySlotToMove = null }, shape = buttonShape) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMapDialog) {
        var mapIdInput by remember { mutableStateOf(lobby.beatmapId?.toString().orEmpty()) }
        var modeIdx by remember { mutableIntStateOf(0) }
        val modeNames = listOf("osu!", "Taiko", "Catch", "Mania")
        val parsedMapId = mapIdInput.toLongOrNull()?.takeIf { it > 0L }
        AlertDialog(
            onDismissRequest = { showMapDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Change beatmap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    OutlinedTextField(
                        value = mapIdInput,
                        onValueChange = { mapIdInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Beatmap ID") },
                        shape = buttonShape,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        modeNames.forEachIndexed { idx, name ->
                            OsuFilterChip(
                                label = name,
                                selected = modeIdx == idx,
                                onClick = { modeIdx = idx }
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            showMapDialog = false
                            onOpenBeatmapSearch()
                        },
                        shape = buttonShape
                    ) { Text("Browse beatmaps") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = parsedMapId ?: return@Button
                        MpLobbyParser.buildMapCommand(id, modeIdx)?.let { cmd ->
                            onSendCommand(cmd)
                            showMapDialog = false
                        }
                    },
                    enabled = parsedMapId != null,
                    shape = buttonShape
                ) { Text("Set map") }
            },
            dismissButton = {
                TextButton(onClick = { showMapDialog = false }, shape = buttonShape) { Text("Cancel") }
            }
        )
    }

    if (showModsDialog) {
        val allMods = listOf("NF", "HD", "HR", "DT", "EZ", "FL", "HT", "SO", "Freemod")
        val chosen = remember(showModsDialog, lobby.activeMods, lobby.isFreeMod) {
            val initial = mutableStateListOf<String>()
            val tokens = lobby.activeMods.split(',', ' ', '+')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
            allMods.forEach { mod ->
                if (tokens.any { it.equals(mod, ignoreCase = true) }) {
                    initial.add(mod)
                }
            }
            if (lobby.isFreeMod && "Freemod" !in initial) {
                initial.add("Freemod")
            }
            initial
        }
        AlertDialog(
            onDismissRequest = { showModsDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Match mods",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    allMods.forEach { mod ->
                        val selected = mod in chosen
                        OsuFilterChip(
                            label = mod,
                            selected = selected,
                            onClick = { if (selected) chosen.remove(mod) else chosen.add(mod) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSendCommand(MpLobbyParser.buildModsCommand(chosen))
                        showModsDialog = false
                    },
                    shape = buttonShape
                ) { Text("Apply") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onSendCommand(MpLobbyParser.buildModsCommand(emptyList()))
                            showModsDialog = false
                        },
                        shape = buttonShape
                    ) { Text("Clear") }
                    TextButton(onClick = { showModsDialog = false }, shape = buttonShape) { Text("Cancel") }
                }
            }
        )
    }

    if (showInviteDialog) {
        var target by remember { mutableStateOf("") }
        val trimmedTarget = target.trim()
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Invite player",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Username or #ID") },
                    shape = buttonShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        MpLobbyParser.buildInviteCommand(trimmedTarget)?.let { cmd ->
                            onSendCommand(cmd)
                            showInviteDialog = false
                        }
                    },
                    enabled = trimmedTarget.isNotEmpty(),
                    shape = buttonShape
                ) { Text("Invite") }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }, shape = buttonShape) { Text("Cancel") }
            }
        )
    }

    if (showRefDialog) {
        var refName by remember { mutableStateOf("") }
        val trimmedRef = refName.trim()
        AlertDialog(
            onDismissRequest = { showRefDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Manage referees",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = refName,
                    onValueChange = { refName = it },
                    label = { Text("Username or #ID") },
                    shape = buttonShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        MpLobbyParser.buildAddRefCommand(trimmedRef)?.let { cmd ->
                            onSendCommand(cmd)
                            showRefDialog = false
                        }
                    },
                    enabled = trimmedRef.isNotEmpty(),
                    shape = buttonShape
                ) { Text("Add ref") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            MpLobbyParser.buildRemoveRefCommand(trimmedRef)?.let { cmd ->
                                onSendCommand(cmd)
                                showRefDialog = false
                            }
                        },
                        enabled = trimmedRef.isNotEmpty(),
                        shape = buttonShape
                    ) { Text("Remove ref") }
                    TextButton(onClick = { showRefDialog = false }, shape = buttonShape) { Text("Cancel") }
                }
            }
        )
    }

    if (showSizeDialog) {
        var sizeStr by remember { mutableStateOf(lobby.roomSize.toString()) }
        val parsedSize = sizeStr.toIntOrNull()?.takeIf { it in 1..16 }
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Room size",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = sizeStr,
                    onValueChange = { sizeStr = it.filter { c -> c.isDigit() } },
                    label = { Text("Slots (1–16)") },
                    shape = buttonShape,
                    singleLine = true,
                    isError = sizeStr.isNotBlank() && parsedSize == null,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sz = parsedSize ?: return@Button
                        onSendCommand(MpLobbyParser.buildSizeCommand(sz))
                        showSizeDialog = false
                    },
                    enabled = parsedSize != null,
                    shape = buttonShape
                ) { Text("Set size") }
            },
            dismissButton = {
                TextButton(onClick = { showSizeDialog = false }, shape = buttonShape) { Text("Cancel") }
            }
        )
    }

    if (showSetModeDialog) {
        val teamOptions = listOf("HeadToHead", "TagCoop", "TeamVs", "TagTeamVs")
        val scoreOptions = listOf("Score", "Accuracy", "Combo", "ScoreV2")
        var teamMode by remember(lobby.teamMode) {
            mutableIntStateOf(
                teamOptions.indexOfFirst { it.equals(lobby.teamMode, ignoreCase = true) }
                    .takeIf { it >= 0 } ?: 2
            )
        }
        var scoreMode by remember(lobby.winCondition) {
            mutableIntStateOf(
                scoreOptions.indexOfFirst { it.equals(lobby.winCondition, ignoreCase = true) }
                    .takeIf { it >= 0 } ?: 3
            )
        }
        AlertDialog(
            onDismissRequest = { showSetModeDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Match rules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    Text("Team mode", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        teamOptions.forEachIndexed { idx, label ->
                            OsuFilterChip(
                                label = label,
                                selected = teamMode == idx,
                                onClick = { teamMode = idx }
                            )
                        }
                    }
                    Text("Win condition", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        scoreOptions.forEachIndexed { idx, label ->
                            OsuFilterChip(
                                label = label,
                                selected = scoreMode == idx,
                                onClick = { scoreMode = idx }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSendCommand(MpLobbyParser.buildSetCommand(teamMode, scoreMode, lobby.roomSize))
                        showSetModeDialog = false
                    },
                    shape = buttonShape
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showSetModeDialog = false }, shape = buttonShape) { Text("Cancel") }
            }
        )
    }

    if (showPasswordDialog) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Room password",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password (blank to clear)") },
                    shape = buttonShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSendCommand(MpLobbyParser.buildPasswordCommand(pass))
                        showPasswordDialog = false
                    },
                    shape = buttonShape
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }, shape = buttonShape) { Text("Cancel") }
            }
        )
    }

    if (showMappoolManager) {
        MappoolManagerDialog(
            mappools = mappools,
            onSavePool = onSaveMappool,
            onDeletePool = onDeleteMappool,
            onDismiss = { showMappoolManager = false },
            onGetCachedBeatmaps = onGetCachedBeatmaps,
            onResolveBeatmapDetails = onResolveBeatmapDetails
        )
    }
}

@Composable
private fun MpControlGridButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = MaterialTheme.colorScheme.outlineVariant,
    horizontalPadding: androidx.compose.ui.unit.Dp = OsuSpacing.sm,
    isNumeric: Boolean = false
) {
    val shape = RoundedCornerShape(OsuRadius.md)
    val baseStyle = MaterialTheme.typography.labelMedium
    val textStyle = if (isNumeric) baseStyle.tnum() else baseStyle
    Surface(
        shape = shape,
        color = containerColor,
        border = borderColor?.let { osuHairlineBorder(it) },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(OsuSpacing.xs))
            Text(
                text = label,
                style = textStyle,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeriesScoreboardHeaderCard(
    lobby: MpLobbyState,
    seriesState: MatchSeriesState,
    showBestOfDropdown: Boolean,
    onToggleBestOfDropdown: (Boolean) -> Unit,
    onSelectBestOf: (Int) -> Unit,
    onOpenEditTeams: () -> Unit,
    onAdjustScore: (MpTeam, Int) -> Unit,
    onUndoLastRound: () -> Unit,
    onResetSeries: () -> Unit,
    onToggleAutoScore: (Boolean) -> Unit,
    onToggleWarmup: (Boolean) -> Unit,
    onCopySummary: () -> Unit,
    onRefreshSettings: () -> Unit,
    onOpenMatchAnalyzer: ((Long) -> Unit)? = null
) {
    val surfaces = LocalOsuSurfaces.current
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val chipShape = RoundedCornerShape(OsuRadius.md)
    val stepperShape = RoundedCornerShape(OsuRadius.sm)
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                ) {
                    Text(
                        text = lobby.roomName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        Text(
                            text = lobby.channelId,
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = buildString {
                                append("· ${lobby.teamMode} · ${lobby.winCondition} · ${lobby.activeMods}")
                                if (lobby.isLocked) append(" · Locked")
                                if (lobby.isClosed) append(" · Closed")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Surface(
                            shape = chipShape,
                            color = surfaces.accentSubtle,
                            border = osuHairlineBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .height(34.dp)
                                .clip(chipShape)
                                .clickable { onToggleBestOfDropdown(true) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = OsuSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                            ) {
                                Text(
                                    text = "Bo${seriesState.bestOf} (FT${seriesState.firstTo})",
                                    style = MaterialTheme.typography.labelMedium.tnum(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Select Best-of",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showBestOfDropdown,
                            onDismissRequest = { onToggleBestOfDropdown(false) },
                            shape = chipShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = osuHairlineBorder()
                        ) {
                            MatchSeriesState.BEST_OF_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Bo$preset (First to ${(preset / 2) + 1})",
                                            style = MaterialTheme.typography.bodySmall.tnum(),
                                            fontWeight = if (preset == seriesState.bestOf) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    },
                                    onClick = { onSelectBestOf(preset) }
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeMatchId = lobby.matchId
                        if (activeMatchId != null && onOpenMatchAnalyzer != null) {
                            Surface(
                                shape = chipShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = osuHairlineBorder(),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(chipShape)
                                    .clickable { onOpenMatchAnalyzer(activeMatchId) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Analytics,
                                        contentDescription = "Analyze match",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = chipShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(chipShape)
                                .clickable(onClick = onRefreshSettings)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh lobby settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            OsuHairlineDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(chipShape)
                        .clickable(onClick = onOpenEditTeams)
                        .padding(vertical = OsuSpacing.xxs),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    Text(
                        text = seriesState.redTeamName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TeamRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canDecrementRed = seriesState.redScore > 0
                        Surface(
                            shape = stepperShape,
                            color = TeamRed.copy(alpha = 0.14f),
                            border = osuHairlineBorder(TeamRed.copy(alpha = 0.35f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .size(32.dp)
                                .alpha(if (canDecrementRed) 1f else 0.38f)
                                .clip(stepperShape)
                                .clickable(enabled = canDecrementRed) { onAdjustScore(MpTeam.RED, -1) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Red -1",
                                    tint = TeamRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Surface(
                            shape = stepperShape,
                            color = TeamRed.copy(alpha = 0.14f),
                            border = osuHairlineBorder(TeamRed.copy(alpha = 0.35f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(stepperShape)
                                .clickable { onAdjustScore(MpTeam.RED, 1) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Red +1",
                                    tint = TeamRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = chipShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = osuHairlineBorder(),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        Text(
                            text = "${seriesState.redScore}",
                            style = MaterialTheme.typography.titleLarge.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = TeamRed
                        )
                        Text(
                            text = "–",
                            style = MaterialTheme.typography.titleMedium.tnum(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${seriesState.blueScore}",
                            style = MaterialTheme.typography.titleLarge.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = TeamBlue
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(chipShape)
                        .clickable(onClick = onOpenEditTeams)
                        .padding(vertical = OsuSpacing.xxs),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    Text(
                        text = seriesState.blueTeamName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TeamBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canDecrementBlue = seriesState.blueScore > 0
                        Surface(
                            shape = stepperShape,
                            color = TeamBlue.copy(alpha = 0.14f),
                            border = osuHairlineBorder(TeamBlue.copy(alpha = 0.35f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .size(32.dp)
                                .alpha(if (canDecrementBlue) 1f else 0.38f)
                                .clip(stepperShape)
                                .clickable(enabled = canDecrementBlue) { onAdjustScore(MpTeam.BLUE, -1) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Blue -1",
                                    tint = TeamBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Surface(
                            shape = stepperShape,
                            color = TeamBlue.copy(alpha = 0.14f),
                            border = osuHairlineBorder(TeamBlue.copy(alpha = 0.35f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(stepperShape)
                                .clickable { onAdjustScore(MpTeam.BLUE, 1) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Blue +1",
                                    tint = TeamBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            val statusBadge: Pair<String, Color>? = when {
                seriesState.winnerTeam == MpTeam.RED -> "${seriesState.redTeamName} wins (${seriesState.redScore}–${seriesState.blueScore})" to TeamRed
                seriesState.winnerTeam == MpTeam.BLUE -> "${seriesState.blueTeamName} wins (${seriesState.redScore}–${seriesState.blueScore})" to TeamBlue
                seriesState.isRedMatchPoint && seriesState.isBlueMatchPoint -> "Double match point (TB)" to MaterialTheme.colorScheme.primary
                seriesState.isRedMatchPoint -> "Match point: ${seriesState.redTeamName}" to TeamRed
                seriesState.isBlueMatchPoint -> "Match point: ${seriesState.blueTeamName}" to TeamBlue
                else -> null
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (statusBadge != null) {
                        OsuMicroTag(
                            text = statusBadge.first,
                            containerColor = statusBadge.second.copy(alpha = 0.14f),
                            contentColor = statusBadge.second,
                            isNumeric = true
                        )
                    } else {
                        Text(
                            text = "Tap team name to edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onOpenEditTeams)
                        )
                    }
                }

                val canUndo = seriesState.rounds.isNotEmpty()
                val canReset = seriesState.redScore > 0 ||
                    seriesState.blueScore > 0 ||
                    seriesState.rounds.isNotEmpty() ||
                    seriesState.slotStates.isNotEmpty()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = chipShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .alpha(if (canUndo) 1f else 0.38f)
                            .clip(chipShape)
                            .clickable(enabled = canUndo, onClick = onUndoLastRound)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Undo last round",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Surface(
                        shape = chipShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .alpha(if (canReset) 1f else 0.38f)
                            .clip(chipShape)
                            .clickable(enabled = canReset, onClick = onResetSeries)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset series",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Surface(
                        shape = chipShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = osuHairlineBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(chipShape)
                            .clickable(onClick = onCopySummary)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = OsuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Copy summary",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("Auto-score TeamVs", seriesState.autoScoreTeamVs) {
                        onToggleAutoScore(!seriesState.autoScoreTeamVs)
                    },
                    Triple("Warmup", seriesState.isWarmupActive) {
                        onToggleWarmup(!seriesState.isWarmupActive)
                    }
                ).forEach { (label, selected, onToggle) ->
                    val toggleBg by animateColorAsState(
                        targetValue = if (selected) {
                            surfaces.accentSubtle
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "seriesToggleBg"
                    )
                    val toggleFg by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "seriesToggleFg"
                    )
                    val toggleBorder by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = OsuMotion.colorSpec(),
                        label = "seriesToggleBorder"
                    )
                    Surface(
                        shape = chipShape,
                        color = toggleBg,
                        border = osuHairlineBorder(toggleBorder),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(chipShape)
                            .clickable(role = Role.Switch, onClick = onToggle)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = OsuSpacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = toggleFg,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageRefereeMacrosDialog(
    macros: List<RefereeMacro>,
    onSave: (List<RefereeMacro>) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var nextKey by remember { mutableIntStateOf(macros.size + 1) }
    val rows = remember(macros) {
        mutableStateListOf<Pair<Int, RefereeMacro>>().apply {
            macros.forEachIndexed { idx, macro -> add((idx + 1) to macro) }
        }
    }
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val buttonShape = RoundedCornerShape(OsuRadius.md)
    val chipShape = RoundedCornerShape(OsuRadius.md)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = osuHairlineBorder(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.xxl)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage referee macros",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Placeholders: {red}, {blue}, {score}, {mp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                OsuHairlineDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    itemsIndexed(items = rows, key = { _, pair -> pair.first }) { idx, pair ->
                        val macro = pair.second
                        Surface(
                            shape = buttonShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(OsuSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    OutlinedTextField(
                                        value = macro.label,
                                        onValueChange = { updated ->
                                            rows[idx] = pair.first to macro.copy(label = updated.take(24))
                                        },
                                        label = { Text("Label") },
                                        singleLine = true,
                                        shape = chipShape,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { rows.removeAt(idx) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete macro",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = macro.message,
                                    onValueChange = { updated ->
                                        rows[idx] = pair.first to macro.copy(message = updated)
                                    },
                                    label = { Text("Message") },
                                    minLines = 1,
                                    maxLines = 3,
                                    shape = chipShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    item(key = "add_macro_btn") {
                        OutlinedButton(
                            onClick = {
                                val idNum = nextKey++
                                rows.add(
                                    idNum to RefereeMacro(
                                        id = "macro_$idNum",
                                        label = "Custom $idNum",
                                        message = ""
                                    )
                                )
                            },
                            shape = buttonShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text("Add macro")
                        }
                    }
                }
                OsuHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onResetDefaults, shape = buttonShape) {
                        Text("Reset defaults")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                        TextButton(onClick = onDismiss, shape = buttonShape) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSave(rows.map { it.second }) },
                            shape = buttonShape
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MpSlotCell(
    slot: MpSlot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = LocalOsuSurfaces.current
    val teamBarColor = when (slot.team) {
        MpTeam.RED -> TeamRed
        MpTeam.BLUE -> TeamBlue
        MpTeam.NONE -> MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (slot.username != null) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    OsuAccentBarContainer(
        showAccentBar = true,
        accentColor = teamBarColor,
        containerColor = containerColor,
        shape = RoundedCornerShape(OsuRadius.md),
        onClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    Text(
                        text = "#${slot.slotNumber}",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (slot.team != MpTeam.NONE) {
                        Text(
                            text = if (slot.team == MpTeam.RED) "R" else "B",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (slot.team == MpTeam.RED) TeamRed else TeamBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (slot.isHost) {
                        OsuMicroTag(
                            text = "Host",
                            containerColor = surfaces.accentSubtle,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                val badgeText = when (slot.status) {
                    MpSlotStatus.READY -> "Ready"
                    MpSlotStatus.NOT_READY -> "Wait"
                    MpSlotStatus.NO_MAP -> "No map"
                    MpSlotStatus.PLAYING -> "Playing"
                    MpSlotStatus.EMPTY -> "Open"
                }
                val badgeColor = when (slot.status) {
                    MpSlotStatus.READY -> StatusOnline
                    MpSlotStatus.NOT_READY -> StatusConnecting
                    MpSlotStatus.NO_MAP -> StatusError
                    MpSlotStatus.PLAYING -> StatusRead
                    MpSlotStatus.EMPTY -> StatusOffline
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(modifier = Modifier.height(OsuSpacing.xs))
            Text(
                text = slot.username ?: "Empty slot",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (slot.username != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (slot.username != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (slot.mods.isNotBlank()) {
                Text(
                    text = slot.mods,
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
