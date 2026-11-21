package com.osuchat.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.ConnectionStatus
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
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.ui.components.MatchSetupWizardDialog
import com.osuchat.app.ui.components.MpLobbyBoard
import com.osuchat.app.ui.components.OsuEmptyState
import com.osuchat.app.ui.components.OsuFilterChip
import com.osuchat.app.ui.components.OsuGroupedContainer
import com.osuchat.app.ui.components.OsuHairlineDivider
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusConnecting
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOffline
import com.osuchat.app.ui.theme.TnumTextStyle
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.MpLobbyParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MultiplayerHubSection(
    mpChannels: List<ChatChannel>,
    activeLobby: MpLobbyState?,
    recentMessages: List<ChatMessage>,
    matchHistory: MpMatchHistory?,
    isHistoryLoading: Boolean,
    historyError: String? = null,
    isBusy: Boolean,
    connectionStatus: ConnectionStatus = ConnectionStatus.ONLINE,
    selfUsername: String,
    onSelectMpChannel: (String) -> Unit,
    onCreateLobby: (String) -> Unit,
    onJoinLobbyByInput: (String) -> Unit,
    onSendCommandOrChat: (channelId: String, text: String) -> Unit,
    onRefreshMatchHistory: (Long) -> Unit,
    onOpenBeatmapSearch: () -> Unit,
    onOpenFullChat: (String) -> Unit,
    onOpenPlayerProfile: (String) -> Unit,
    onDismissClosedLobby: (String) -> Unit,
    onReconnect: () -> Unit = {},
    mappools: List<Mappool> = emptyList(),
    onSaveMappool: (Long?, String, List<MappoolSlot>) -> Unit = { _, _, _ -> },
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
    onOpenMatchAnalyzer: (Long) -> Unit = {},
    matchSetupTemplates: List<MatchSetupTemplate> = MatchSetupTemplate.DEFAULT_TEMPLATES,
    isWizardRunning: Boolean = false,
    wizardStepMessage: String? = null,
    wizardError: String? = null,
    onSaveMatchSetupTemplates: (List<MatchSetupTemplate>) -> Unit = {},
    onRunMatchSetupWizard: (MatchSetupRunConfig, (String) -> Unit) -> Unit = { _, _ -> },
    onCancelMatchSetupWizard: () -> Unit = {},
    onClearMatchSetupWizardError: () -> Unit = {},
    shouldOpenMatchWizard: Boolean = false,
    onConsumedOpenMatchWizard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var joinInput by rememberSaveable { mutableStateOf("") }
    var quickCommandInput by rememberSaveable(activeLobby?.channelId) { mutableStateOf("") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showMatchWizardDialog by rememberSaveable { mutableStateOf(false) }
    var isCreateJoinExpanded by rememberSaveable { mutableStateOf(activeLobby == null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(shouldOpenMatchWizard) {
        if (shouldOpenMatchWizard) {
            onClearMatchSetupWizardError()
            showMatchWizardDialog = true
            onConsumedOpenMatchWizard()
        }
    }

    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val controlShape = RoundedCornerShape(OsuRadius.md)
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(activeLobby?.channelId, activeLobby?.isClosed) {
        isCreateJoinExpanded = activeLobby == null
    }

    val submitJoin: () -> Unit = {
        val target = joinInput.trim()
        if (target.isNotEmpty() && !isBusy) {
            joinInput = ""
            onJoinLobbyByInput(target)
        }
    }

    if (showMatchWizardDialog) {
        MatchSetupWizardDialog(
            templates = matchSetupTemplates,
            isRunning = isWizardRunning,
            stepMessage = wizardStepMessage,
            errorMessage = wizardError,
            onSaveTemplates = onSaveMatchSetupTemplates,
            onRunWizard = { config ->
                onRunMatchSetupWizard(config) { createdChannelId ->
                    showMatchWizardDialog = false
                    onOpenFullChat(createdChannelId)
                }
            },
            onCancelRunning = onCancelMatchSetupWizard,
            onClearError = onClearMatchSetupWizardError,
            onDismiss = {
                onClearMatchSetupWizardError()
                showMatchWizardDialog = false
            }
        )
    }

    if (showCreateDialog) {
        var roomName by remember { mutableStateOf("${selfUsername.ifBlank { "osu" }}'s Lobby") }
        val sanitizedRoomName = MpLobbyParser.sanitizeRoomName(roomName)
        val submitCreate: () -> Unit = {
            if (sanitizedRoomName.isNotEmpty()) {
                showCreateDialog = false
                onCreateLobby(sanitizedRoomName)
            }
        }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            shape = dialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Create multiplayer lobby",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Room name") },
                        singleLine = true,
                        shape = controlShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Creates a room via BanchoBot (!mp make) and opens #mp_<id>",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = submitCreate,
                    enabled = sanitizedRoomName.isNotEmpty(),
                    shape = controlShape
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    shape = controlShape
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(OsuSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
    ) {
        if (connectionStatus != ConnectionStatus.ONLINE) {
            item(key = "mp_connection_notice", contentType = "mp_connection_notice") {
                val (statusTint, statusText) = when (connectionStatus) {
                    ConnectionStatus.CONNECTING -> StatusConnecting to "Connecting to Bancho IRC…"
                    ConnectionStatus.RECONNECTING -> StatusConnecting to "Reconnecting to Bancho IRC…"
                    ConnectionStatus.ERROR -> StatusError to "Bancho IRC error — lobby commands unavailable"
                    ConnectionStatus.OFFLINE -> StatusOffline to "Bancho IRC offline — viewing cached room log"
                    ConnectionStatus.ONLINE -> StatusOffline to ""
                }
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = osuHairlineBorder(statusTint.copy(alpha = 0.4f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (connectionStatus == ConnectionStatus.OFFLINE || connectionStatus == ConnectionStatus.ERROR) {
                            Spacer(Modifier.width(OsuSpacing.sm))
                            TextButton(
                                onClick = onReconnect,
                                shape = controlShape,
                                contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                            ) {
                                Text("Reconnect")
                            }
                        }
                    }
                }
            }
        }

        item(key = "mp_create_join", contentType = "mp_create_join") {
            val showPanel = activeLobby == null || isCreateJoinExpanded
            OsuGroupedContainer {
                Column(
                    modifier = Modifier.padding(OsuSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lobbies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (activeLobby == null) {
                            OutlinedButton(
                                onClick = { showCreateDialog = true },
                                enabled = !isBusy,
                                shape = controlShape,
                                border = osuHairlineBorder(),
                                contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                                modifier = Modifier.height(36.dp)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text(
                                    text = "Create lobby",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            TextButton(
                                onClick = { isCreateJoinExpanded = !isCreateJoinExpanded },
                                shape = controlShape
                            ) {
                                if (!isCreateJoinExpanded) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                }
                                Text(if (isCreateJoinExpanded) "Hide" else "Create / Join")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showPanel,
                        enter = OsuMotion.expandEnter(),
                        exit = OsuMotion.expandExit()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            Button(
                                onClick = {
                                    onClearMatchSetupWizardError()
                                    showMatchWizardDialog = true
                                },
                                enabled = !isBusy && !isWizardRunning,
                                shape = controlShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text(
                                    text = "Match setup wizard",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (activeLobby != null) {
                                OutlinedButton(
                                    onClick = { showCreateDialog = true },
                                    enabled = !isBusy,
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(OsuSpacing.sm))
                                    Text(
                                        text = "Create new lobby",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val parsedMatchId = remember(joinInput) {
                                MpLobbyParser.extractMatchId(joinInput)
                            }
                            OutlinedTextField(
                                value = joinInput,
                                onValueChange = { joinInput = it },
                                placeholder = {
                                    Text(
                                        text = "Match ID or URL",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = {
                                    if (joinInput.isNotEmpty()) {
                                        IconButton(onClick = { joinInput = "" }) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Clear match input",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = controlShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { submitJoin() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                OutlinedButton(
                                    onClick = submitJoin,
                                    enabled = joinInput.isNotBlank() && !isBusy,
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    contentPadding = PaddingValues(horizontal = OsuSpacing.md),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "Join",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        parsedMatchId?.let { id ->
                                            joinInput = ""
                                            onOpenMatchAnalyzer(id)
                                        }
                                    },
                                    enabled = parsedMatchId != null && !isBusy,
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    contentPadding = PaddingValues(horizontal = OsuSpacing.md),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Analytics,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "Analyze",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (mpChannels.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                            items(
                                items = mpChannels,
                                key = { it.id },
                                contentType = { "mp_channel_chip" }
                            ) { ch ->
                                val selected = ch.id.equals(activeLobby?.channelId, ignoreCase = true)
                                val labelText = if (ch.unreadCount > 0) {
                                    "${ch.name} (${ch.unreadCount})"
                                } else {
                                    ch.name
                                }
                                OsuFilterChip(
                                    label = labelText,
                                    selected = selected,
                                    onClick = { onSelectMpChannel(ch.id) },
                                    isNumeric = true
                                )
                            }
                        }
                    }
                }
            }
        }

        if (activeLobby == null) {
            item(key = "mp_empty", contentType = "mp_empty") {
                OsuGroupedContainer {
                    OsuEmptyState(
                        icon = Icons.Rounded.Groups,
                        title = "No active multiplayer lobby",
                        subtitle = "Use the match setup wizard or join a match by ID or URL above"
                    )
                }
            }
        } else if (activeLobby.isClosed) {
            item(key = "mp_closed_${activeLobby.channelId}", contentType = "mp_closed") {
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = osuHairlineBorder(MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Match closed (${activeLobby.channelId})",
                                    style = MaterialTheme.typography.titleSmall.tnum(),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = activeLobby.roomName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { onOpenFullChat(activeLobby.channelId) },
                                shape = controlShape
                            ) {
                                Text("Log")
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            Button(
                                onClick = {
                                    isCreateJoinExpanded = true
                                    coroutineScope.launch { listState.animateScrollToItem(0) }
                                },
                                shape = controlShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "New match",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { onDismissClosedLobby(activeLobby.channelId) },
                                shape = controlShape,
                                border = osuHairlineBorder()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "mp_board_${activeLobby.channelId}", contentType = "mp_board") {
                MpLobbyBoard(
                    lobby = activeLobby,
                    selfUsername = selfUsername,
                    matchHistory = matchHistory,
                    isHistoryLoading = isHistoryLoading,
                    historyError = historyError,
                    onSendCommand = { cmd -> onSendCommandOrChat(activeLobby.channelId, cmd) },
                    onRefreshMatchHistory = {
                        activeLobby.matchId?.let { onRefreshMatchHistory(it) }
                    },
                    onOpenBeatmapSearch = onOpenBeatmapSearch,
                    onOpenPlayerProfile = onOpenPlayerProfile,
                    mappools = mappools,
                    onSaveMappool = onSaveMappool,
                    onDeleteMappool = onDeleteMappool,
                    onSelectMappoolSlot = onSelectMappoolSlot,
                    seriesState = seriesState,
                    refereeMacros = refereeMacros,
                    onSetSeriesBestOf = onSetSeriesBestOf,
                    onSetSeriesTeamNames = onSetSeriesTeamNames,
                    onAdjustSeriesScore = onAdjustSeriesScore,
                    onUndoLastSeriesRound = onUndoLastSeriesRound,
                    onResetSeries = onResetSeries,
                    onSetAutoScoreTeamVs = onSetAutoScoreTeamVs,
                    onSetWarmupActive = onSetWarmupActive,
                    onSetSelectedPoolId = onSetSelectedPoolId,
                    onSetPickBanMode = onSetPickBanMode,
                    onUpdatePoolSlotState = onUpdatePoolSlotState,
                    onSendMacro = onSendMacro,
                    onSaveMacros = onSaveMacros,
                    onResetMacros = onResetMacros,
                    onNotifyMessage = onNotifyMessage,
                    onGetCachedBeatmaps = onGetCachedBeatmaps,
                    onResolveBeatmapDetails = onResolveBeatmapDetails,
                    onOpenMatchAnalyzer = onOpenMatchAnalyzer
                )
            }

            item(key = "mp_chat_${activeLobby.channelId}", contentType = "mp_chat_feed") {
                val submitQuickInput: () -> Unit = {
                    val text = MpLobbyParser.sanitizeIrcArg(quickCommandInput)
                    if (text.isNotEmpty()) {
                        quickCommandInput = ""
                        onSendCommandOrChat(activeLobby.channelId, text)
                    }
                }

                OsuGroupedContainer {
                    Column(
                        modifier = Modifier.padding(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Room log (${activeLobby.channelId})",
                                style = MaterialTheme.typography.titleSmall.tnum(),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = { onOpenFullChat(activeLobby.channelId) },
                                shape = controlShape
                            ) {
                                Text("Full chat")
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        OsuHairlineDivider()

                        if (recentMessages.isEmpty()) {
                            Text(
                                text = "Waiting for room messages…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = OsuSpacing.xs)
                            )
                        } else {
                            val recentFeed = remember(recentMessages) { recentMessages.takeLast(12) }
                            recentFeed.forEach { msg ->
                                key(msg.id) {
                                    val timeStr = remember(msg.timestamp) {
                                        timeFormatter.format(Date(msg.timestamp))
                                    }
                                    val isBot = msg.senderName.equals("BanchoBot", ignoreCase = true)
                                    val bodyColor = if (isBot) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(
                                                TnumTextStyle.toSpanStyle().copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                append("$timeStr ")
                                            }
                                            withStyle(
                                                SpanStyle(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = bodyColor
                                                )
                                            ) {
                                                append("${msg.senderDisplayName}: ")
                                            }
                                            append(msg.content)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(OsuSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                        ) {
                            OutlinedTextField(
                                value = quickCommandInput,
                                onValueChange = { quickCommandInput = it },
                                placeholder = { Text("Send command or message") },
                                singleLine = true,
                                shape = controlShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitQuickInput() }),
                                modifier = Modifier.weight(1f)
                            )
                            FilledIconButton(
                                onClick = submitQuickInput,
                                enabled = quickCommandInput.isNotBlank(),
                                shape = controlShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
