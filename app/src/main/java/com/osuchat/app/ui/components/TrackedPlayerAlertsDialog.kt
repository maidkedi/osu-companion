package com.osuchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.TrackedPlayerAlertEngine
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackedPlayerAlertsDialog(
    username: String,
    mode: String,
    currentPp: Double?,
    currentRank: Int?,
    initialConfig: TrackedPlayerAlertConfig,
    defaultTopPlayLimit: Int,
    isAlreadyTracked: Boolean = true,
    onUntrack: (() -> Unit)? = null,
    onSave: (TrackedPlayerAlertConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val cleanInitial = remember(initialConfig) { initialConfig.sanitized() }
    val safeDefaultLimit = TrackedPlayerAlertConfig.clampTopPlayLimit(defaultTopPlayLimit)
    val intFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    var trackTopPlays by rememberSaveable(username, mode, isAlreadyTracked) {
        mutableStateOf(cleanInitial.isTopPlaysEnabled())
    }
    var trackPp by rememberSaveable(username, mode, isAlreadyTracked) {
        mutableStateOf(cleanInitial.isPpEnabled())
    }
    var trackRank by rememberSaveable(username, mode, isAlreadyTracked) {
        mutableStateOf(cleanInitial.isRankEnabled())
    }

    var topPlayInput by rememberSaveable(username, mode) {
        mutableStateOf(cleanInitial.topPlayLimitOverride?.toString().orEmpty())
    }
    var ppTargets by remember(cleanInitial) {
        mutableStateOf(cleanInitial.customPpTargets)
    }
    var rankTargets by remember(cleanInitial) {
        mutableStateOf(cleanInitial.customRankTargets)
    }
    var ppInput by rememberSaveable(username, mode) { mutableStateOf("") }
    var ppError by remember(username, mode) { mutableStateOf<String?>(null) }
    var rankInput by rememberSaveable(username, mode) { mutableStateOf("") }
    var rankError by remember(username, mode) { mutableStateOf<String?>(null) }

    val parsedLimitOverride = topPlayInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val topPlayError = when {
        !trackTopPlays -> null
        topPlayInput.isBlank() -> null
        parsedLimitOverride == null ||
            parsedLimitOverride !in TrackedPlayerAlertConfig.MIN_TOP_PLAY_LIMIT..TrackedPlayerAlertConfig.MAX_TOP_PLAY_LIMIT ->
            "Enter a number from 1 to 100"
        else -> null
    }
    val effectivePreviewLimit = parsedLimitOverride
        ?.takeIf { it in TrackedPlayerAlertConfig.MIN_TOP_PLAY_LIMIT..TrackedPlayerAlertConfig.MAX_TOP_PLAY_LIMIT }
        ?: safeDefaultLimit

    val addPpTarget: () -> Unit = {
        val normalizedRaw = ppInput.trim().replace(',', '.')
        val rawValue = normalizedRaw.toDoubleOrNull()
        val normalizedTarget = rawValue?.let(TrackedPlayerAlertConfig::normalizePpTarget)
        val normalizedCurrentPp = currentPp
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let(TrackedPlayerAlertConfig::normalizePpTarget)
        ppError = when {
            normalizedTarget == null || !normalizedTarget.isFinite() || normalizedTarget <= 0.0 ->
                "Enter a PP target above 0"
            normalizedTarget in ppTargets ->
                "PP target already added"
            normalizedCurrentPp != null && normalizedTarget <= normalizedCurrentPp ->
                "Already passed"
            else -> {
                ppTargets = TrackedPlayerAlertConfig.normalizePpTargets(ppTargets + normalizedTarget)
                ppInput = ""
                null
            }
        }
    }

    val addRankTarget: () -> Unit = {
        val rawRank = rankInput.trim().removePrefix("#").toIntOrNull()
        val validCurrentRank = currentRank?.takeIf { it > 0 }
        rankError = when {
            rawRank == null || rawRank <= 0 ->
                "Enter a rank above 0"
            rawRank in rankTargets ->
                "Rank target already added"
            validCurrentRank != null && rawRank >= validCurrentRank ->
                "Already passed"
            else -> {
                rankTargets = TrackedPlayerAlertConfig.normalizeRankTargets(rankTargets + rawRank)
                rankInput = ""
                null
            }
        }
    }

    val hasAnyCategory = trackTopPlays || trackPp || trackRank
    val isTopPlayValid = !trackTopPlays || topPlayError == null
    val isPpValid = !trackPp || ppTargets.isNotEmpty()
    val isRankValid = !trackRank || rankTargets.isNotEmpty()
    val canSave = hasAnyCategory && isTopPlayValid && isPpValid && isRankValid

    val surfaces = LocalOsuSurfaces.current
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding(),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = osuHairlineBorder(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OsuSheetTopBar(
                    title = if (isAlreadyTracked) "Tracked player alerts" else "Track player",
                    subtitle = "$username · ${TrackedPlayerAlertEngine.formatModeDisplay(mode)}",
                    onClose = onDismiss
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(OsuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    // Section 1: Top plays
                    Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = OsuSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                            ) {
                                Text(
                                    text = "Top plays",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Alert on new Top $effectivePreviewLimit personal bests",
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted
                                )
                            }
                            Switch(
                                checked = trackTopPlays,
                                onCheckedChange = { trackTopPlays = it }
                            )
                        }

                        AnimatedVisibility(
                            visible = trackTopPlays,
                            enter = OsuMotion.expandEnter(),
                            exit = OsuMotion.expandExit()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Top plays to track (1–100)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = surfaces.textSecondary
                                    )
                                    if (topPlayInput.isNotBlank()) {
                                        TextButton(
                                            onClick = { topPlayInput = "" },
                                            shape = controlShape,
                                            contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = topPlayInput,
                                    onValueChange = { next ->
                                        topPlayInput = next.filter { it.isDigit() }.take(3)
                                    },
                                    placeholder = {
                                        Text(
                                            text = "Default: $safeDefaultLimit",
                                            style = MaterialTheme.typography.bodyMedium.tnum()
                                        )
                                    },
                                    singleLine = true,
                                    isError = topPlayError != null,
                                    textStyle = MaterialTheme.typography.bodyMedium.tnum(),
                                    shape = controlShape,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    supportingText = {
                                        Text(
                                            text = topPlayError ?: "Tracking Top $effectivePreviewLimit plays",
                                            style = MaterialTheme.typography.labelSmall.tnum(),
                                            color = if (topPlayError != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                surfaces.textMuted
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    OsuHairlineDivider()

                    // Section 2: Total PP
                    Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = OsuSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                            ) {
                                Text(
                                    text = "Total PP",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val currentPpLabel = currentPp
                                    ?.takeIf { it.isFinite() && it >= 0.0 }
                                    ?.let { "Current: ${TrackedPlayerAlertEngine.formatPpValue(it)} pp" }
                                    ?: "Alert when total PP crosses targets"
                                Text(
                                    text = currentPpLabel,
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted
                                )
                            }
                            Switch(
                                checked = trackPp,
                                onCheckedChange = { trackPp = it }
                            )
                        }

                        AnimatedVisibility(
                            visible = trackPp,
                            enter = OsuMotion.expandEnter(),
                            exit = OsuMotion.expandExit()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                                Text(
                                    text = "Custom PP targets",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = surfaces.textSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                ) {
                                    OutlinedTextField(
                                        value = ppInput,
                                        onValueChange = { next ->
                                            ppInput = next.filter { it.isDigit() || it == '.' || it == ',' }
                                            if (ppError != null) ppError = null
                                        },
                                        placeholder = {
                                            Text(
                                                text = "e.g. 14000",
                                                style = MaterialTheme.typography.bodyMedium.tnum()
                                            )
                                        },
                                        singleLine = true,
                                        isError = ppError != null,
                                        textStyle = MaterialTheme.typography.bodyMedium.tnum(),
                                        shape = controlShape,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = { addPpTarget() }),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = addPpTarget,
                                        shape = controlShape,
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Add")
                                    }
                                }

                                ppError?.let { errorText ->
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (ppTargets.isEmpty()) {
                                    Text(
                                        text = "Add at least one PP target",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = surfaces.textMuted
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        ppTargets.forEach { targetPp ->
                                            OsuFilterChip(
                                                label = "${TrackedPlayerAlertEngine.formatPpValue(targetPp)} pp",
                                                selected = false,
                                                leadingIcon = Icons.Rounded.Close,
                                                isNumeric = true,
                                                onClick = {
                                                    ppTargets = ppTargets.filterNot { it == targetPp }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OsuHairlineDivider()

                    // Section 3: Global rank
                    Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = OsuSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                            ) {
                                Text(
                                    text = "Global rank",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val currentRankLabel = currentRank
                                    ?.takeIf { it > 0 }
                                    ?.let { "Current: #${intFmt.format(it)}" }
                                    ?: "Alert when global rank reaches targets"
                                Text(
                                    text = currentRankLabel,
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted
                                )
                            }
                            Switch(
                                checked = trackRank,
                                onCheckedChange = { trackRank = it }
                            )
                        }

                        AnimatedVisibility(
                            visible = trackRank,
                            enter = OsuMotion.expandEnter(),
                            exit = OsuMotion.expandExit()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                                Text(
                                    text = "Custom global rank targets",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = surfaces.textSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                ) {
                                    OutlinedTextField(
                                        value = rankInput,
                                        onValueChange = { next ->
                                            rankInput = next.filter { it.isDigit() }
                                            if (rankError != null) rankError = null
                                        },
                                        placeholder = {
                                            Text(
                                                text = "500",
                                                style = MaterialTheme.typography.bodyMedium.tnum()
                                            )
                                        },
                                        prefix = {
                                            Text(
                                                text = "#",
                                                style = MaterialTheme.typography.bodyMedium.tnum()
                                            )
                                        },
                                        singleLine = true,
                                        isError = rankError != null,
                                        textStyle = MaterialTheme.typography.bodyMedium.tnum(),
                                        shape = controlShape,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = { addRankTarget() }),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = addRankTarget,
                                        shape = controlShape,
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Add")
                                    }
                                }

                                rankError?.let { errorText ->
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (rankTargets.isEmpty()) {
                                    Text(
                                        text = "Add at least one rank target",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = surfaces.textMuted
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs),
                                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                    ) {
                                        rankTargets.forEach { targetRank ->
                                            OsuFilterChip(
                                                label = "#${intFmt.format(targetRank)}",
                                                selected = false,
                                                leadingIcon = Icons.Rounded.Close,
                                                isNumeric = true,
                                                onClick = {
                                                    rankTargets = rankTargets.filterNot { it == targetRank }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!hasAnyCategory) {
                        Text(
                            text = "Select at least one tracking option",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OsuHairlineDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAlreadyTracked && onUntrack != null) {
                        TextButton(
                            onClick = onUntrack,
                            shape = controlShape,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "Stop tracking",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = controlShape,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (!canSave) return@Button
                                val cleanPpTargets = TrackedPlayerAlertConfig.normalizePpTargets(ppTargets)
                                val cleanRankTargets = TrackedPlayerAlertConfig.normalizeRankTargets(rankTargets)
                                val validKeys = buildSet {
                                    cleanPpTargets.forEach { add(TrackedPlayerAlertConfig.ppTargetKey(it)) }
                                    cleanRankTargets.forEach { add(TrackedPlayerAlertConfig.rankTargetKey(it)) }
                                }
                                val retainedNotified = cleanInitial.notifiedTargetKeys
                                    .filter { it in validKeys }
                                    .toSet()
                                val validCurrentPp = currentPp?.takeIf { it.isFinite() && it >= 0.0 }
                                val validCurrentRank = currentRank?.takeIf { it > 0 }
                                val nextBaselinePp = if (!trackPp) {
                                    null
                                } else if (cleanInitial.isPpEnabled()) {
                                    cleanInitial.baselinePp ?: validCurrentPp
                                } else {
                                    validCurrentPp
                                }
                                val nextBaselineRank = if (!trackRank) {
                                    null
                                } else if (cleanInitial.isRankEnabled()) {
                                    cleanInitial.baselineRank ?: validCurrentRank
                                } else {
                                    validCurrentRank
                                }
                                val keepSeenTopPlays = trackTopPlays && cleanInitial.isTopPlaysEnabled()
                                val saved = cleanInitial.copy(
                                    customPpTargets = cleanPpTargets,
                                    customRankTargets = cleanRankTargets,
                                    topPlayLimitOverride = if (trackTopPlays) {
                                        parsedLimitOverride
                                    } else {
                                        cleanInitial.topPlayLimitOverride
                                    },
                                    trackTopPlays = trackTopPlays,
                                    trackPp = trackPp,
                                    trackRank = trackRank,
                                    baselinePp = nextBaselinePp,
                                    baselineRank = nextBaselineRank,
                                    notifiedTargetKeys = retainedNotified,
                                    lastSyncedTopLimit = if (keepSeenTopPlays) {
                                        cleanInitial.lastSyncedTopLimit
                                    } else {
                                        0
                                    },
                                    seenBestScoreIds = if (keepSeenTopPlays) {
                                        cleanInitial.seenBestScoreIds
                                    } else {
                                        emptySet()
                                    }
                                ).sanitized()
                                onSave(saved)
                            },
                            enabled = canSave,
                            shape = controlShape,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (isAlreadyTracked) "Save" else "Start tracking")
                        }
                    }
                }
            }
        }
    }
}
