package com.osuchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osuchat.app.domain.model.MatchSetupRunConfig
import com.osuchat.app.domain.model.MatchSetupTemplate
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import java.util.UUID

private val DEFAULT_TEMPLATE_IDS = setOf("1v1_sv2_bo7", "2v2_sv2_bo9", "3v3_sv2_bo11", "4v4_sv2_bo13")
private val BEST_OF_OPTIONS = listOf(5, 7, 9, 11, 13)

@Composable
fun MatchSetupWizardDialog(
    templates: List<MatchSetupTemplate>,
    isRunning: Boolean,
    stepMessage: String?,
    errorMessage: String?,
    onSaveTemplates: (List<MatchSetupTemplate>) -> Unit,
    onRunWizard: (MatchSetupRunConfig) -> Unit,
    onCancelRunning: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    val availableTemplates = remember(templates) {
        templates.ifEmpty { MatchSetupTemplate.DEFAULT_TEMPLATES }
    }
    val initialTemplate = availableTemplates.getOrNull(1) ?: availableTemplates.first()

    var selectedTemplateId by rememberSaveable { mutableStateOf(initialTemplate.id) }
    var redTeamName by rememberSaveable { mutableStateOf("Red") }
    var blueTeamName by rememberSaveable { mutableStateOf("Blue") }
    var roomNamePattern by rememberSaveable { mutableStateOf(initialTemplate.roomNamePattern) }
    var bestOf by rememberSaveable { mutableIntStateOf(initialTemplate.bestOf) }
    var teamMode by rememberSaveable { mutableIntStateOf(initialTemplate.teamMode) }
    var scoreMode by rememberSaveable { mutableIntStateOf(initialTemplate.scoreMode) }
    var roomSize by rememberSaveable { mutableIntStateOf(initialTemplate.roomSize.coerceIn(2, 16)) }
    var lockSlots by rememberSaveable { mutableStateOf(initialTemplate.lockSlots) }
    var password by rememberSaveable { mutableStateOf(initialTemplate.password) }
    var refsInput by rememberSaveable {
        mutableStateOf(initialTemplate.defaultRefs.joinToString(", "))
    }
    var invitesInput by rememberSaveable {
        mutableStateOf(initialTemplate.defaultInvites.joinToString(", "))
    }

    var showSaveTemplateBar by rememberSaveable { mutableStateOf(false) }
    var newTemplateName by rememberSaveable { mutableStateOf("") }
    var showPreviewCommands by rememberSaveable { mutableStateOf(true) }

    val parsedRefs = remember(refsInput) { splitUserList(refsInput) }
    val parsedInvites = remember(invitesInput) { splitUserList(invitesInput) }

    val currentConfig = remember(
        roomNamePattern,
        redTeamName,
        blueTeamName,
        bestOf,
        teamMode,
        scoreMode,
        roomSize,
        lockSlots,
        password,
        parsedRefs,
        parsedInvites
    ) {
        MatchSetupRunConfig(
            roomNamePattern = roomNamePattern,
            redTeamName = redTeamName,
            blueTeamName = blueTeamName,
            bestOf = bestOf,
            teamMode = teamMode,
            scoreMode = scoreMode,
            roomSize = roomSize.coerceIn(2, 16),
            lockSlots = lockSlots,
            password = password,
            refs = parsedRefs,
            invites = parsedInvites
        )
    }

    val previewCommands = remember(currentConfig) { currentConfig.buildPreviewCommandQueue() }

    val applyTemplate: (MatchSetupTemplate) -> Unit = { tmpl ->
        selectedTemplateId = tmpl.id
        roomNamePattern = tmpl.roomNamePattern
        bestOf = tmpl.bestOf
        teamMode = tmpl.teamMode
        scoreMode = tmpl.scoreMode
        roomSize = tmpl.roomSize.coerceIn(2, 16)
        lockSlots = tmpl.lockSlots
        password = tmpl.password
        refsInput = tmpl.defaultRefs.joinToString(", ")
        invitesInput = tmpl.defaultInvites.joinToString(", ")
        onClearError()
    }

    val surfaces = LocalOsuSurfaces.current
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val cardShape = RoundedCornerShape(OsuRadius.lg)
    val controlShape = RoundedCornerShape(OsuRadius.md)

    Dialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .imePadding(),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = osuHairlineBorder()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = OsuSpacing.lg, end = OsuSpacing.sm, top = OsuSpacing.md, bottom = OsuSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tournament lobby setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Create !mp room, configure rules, invite players",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (isRunning) onCancelRunning() else onDismiss() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = if (isRunning) "Cancel setup" else "Close"
                        )
                    }
                }
                OsuHairlineDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(OsuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preset templates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                            val selectedTemplate = availableTemplates.firstOrNull { it.id == selectedTemplateId }
                            if (selectedTemplate != null && selectedTemplate.id !in DEFAULT_TEMPLATE_IDS) {
                                TextButton(
                                    onClick = {
                                        val remaining = availableTemplates.filterNot { it.id == selectedTemplate.id }
                                        onSaveTemplates(remaining)
                                        (remaining.firstOrNull() ?: MatchSetupTemplate.DEFAULT_TEMPLATES.first())
                                            .let(applyTemplate)
                                    },
                                    enabled = !isRunning,
                                    contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "Delete",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showSaveTemplateBar = !showSaveTemplateBar },
                                enabled = !isRunning,
                                contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text(
                                    text = if (showSaveTemplateBar) "Hide save" else "Save preset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        availableTemplates.forEach { tmpl ->
                            OsuFilterChip(
                                label = tmpl.name,
                                selected = tmpl.id == selectedTemplateId,
                                onClick = { if (!isRunning) applyTemplate(tmpl) }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showSaveTemplateBar,
                        enter = OsuMotion.expandEnter(),
                        exit = OsuMotion.expandExit()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = osuHairlineBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(OsuSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                OutlinedTextField(
                                    value = newTemplateName,
                                    onValueChange = { newTemplateName = it },
                                    placeholder = { Text("Preset name (e.g. OWC GF)") },
                                    singleLine = true,
                                    shape = controlShape,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val cleanName = newTemplateName.trim()
                                        if (cleanName.isNotEmpty()) {
                                            val newTemplate = MatchSetupTemplate(
                                                id = "custom_${UUID.randomUUID().toString().take(8)}",
                                                name = cleanName,
                                                roomNamePattern = roomNamePattern.ifBlank { "TOURNEY: ({red}) vs ({blue})" },
                                                bestOf = bestOf,
                                                teamMode = teamMode,
                                                scoreMode = scoreMode,
                                                roomSize = roomSize.coerceIn(2, 16),
                                                lockSlots = lockSlots,
                                                password = password.trim(),
                                                defaultRefs = parsedRefs,
                                                defaultInvites = parsedInvites
                                            )
                                            onSaveTemplates(availableTemplates + newTemplate)
                                            selectedTemplateId = newTemplate.id
                                            newTemplateName = ""
                                            showSaveTemplateBar = false
                                        }
                                    },
                                    enabled = newTemplateName.isNotBlank() && !isRunning,
                                    shape = controlShape
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        OutlinedTextField(
                            value = redTeamName,
                            onValueChange = { redTeamName = it },
                            label = { Text("Red team") },
                            singleLine = true,
                            enabled = !isRunning,
                            shape = controlShape,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = blueTeamName,
                            onValueChange = { blueTeamName = it },
                            label = { Text("Blue team") },
                            singleLine = true,
                            enabled = !isRunning,
                            shape = controlShape,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = roomNamePattern,
                        onValueChange = { roomNamePattern = it },
                        label = { Text("Room name pattern ({red}, {blue})") },
                        singleLine = true,
                        enabled = !isRunning,
                        shape = controlShape,
                        supportingText = {
                            Text(
                                text = "Preview: ${currentConfig.resolvedRoomName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Best of",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        BEST_OF_OPTIONS.forEach { bo ->
                            OsuFilterChip(
                                label = "Bo$bo",
                                selected = bestOf == bo,
                                onClick = { if (!isRunning) bestOf = bo },
                                isNumeric = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            Text(
                                text = "Team mode",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)) {
                                OsuFilterChip(
                                    label = "H2H",
                                    selected = teamMode == MatchSetupTemplate.TEAM_MODE_HEAD_TO_HEAD,
                                    onClick = { if (!isRunning) teamMode = MatchSetupTemplate.TEAM_MODE_HEAD_TO_HEAD }
                                )
                                OsuFilterChip(
                                    label = "TeamVs",
                                    selected = teamMode == MatchSetupTemplate.TEAM_MODE_TEAM_VS,
                                    onClick = { if (!isRunning) teamMode = MatchSetupTemplate.TEAM_MODE_TEAM_VS }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1.25f),
                            verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            Text(
                                text = "Score mode",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                listOf(
                                    MatchSetupTemplate.SCORE_MODE_SCORE to "Score",
                                    MatchSetupTemplate.SCORE_MODE_SCORE_V2 to "ScoreV2",
                                    MatchSetupTemplate.SCORE_MODE_ACCURACY to "Acc"
                                ).forEach { (modeVal, label) ->
                                    OsuFilterChip(
                                        label = label,
                                        selected = scoreMode == modeVal,
                                        onClick = { if (!isRunning) scoreMode = modeVal }
                                    )
                                }
                            }
                        }
                    }

                    OsuGroupedContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                Text(
                                    text = "Slots",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { if (!isRunning && roomSize > 2) roomSize -= 1 },
                                    enabled = !isRunning && roomSize > 2,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease slots", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = roomSize.toString(),
                                    style = MaterialTheme.typography.titleSmall.tnum(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { if (!isRunning && roomSize < 16) roomSize += 1 },
                                    enabled = !isRunning && roomSize < 16,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Increase slots", modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                Text(
                                    text = "Lock slots",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = lockSlots,
                                    onCheckedChange = { if (!isRunning) lockSlots = it }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Lobby password (optional)") },
                        singleLine = true,
                        enabled = !isRunning,
                        shape = controlShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = refsInput,
                        onValueChange = { refsInput = it },
                        label = { Text("Referees (comma-separated, optional)") },
                        singleLine = true,
                        enabled = !isRunning,
                        shape = controlShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = invitesInput,
                        onValueChange = { invitesInput = it },
                        label = { Text("Invite players (comma-separated, optional)") },
                        maxLines = 2,
                        enabled = !isRunning,
                        shape = controlShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = osuHairlineBorder(),
                        onClick = { showPreviewCommands = !showPreviewCommands }
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
                                    text = "Command queue (${previewCommands.size + 1})",
                                    style = MaterialTheme.typography.labelMedium.tnum(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (showPreviewCommands) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(
                                visible = showPreviewCommands,
                                enter = OsuMotion.expandEnter(),
                                exit = OsuMotion.expandExit()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)) {
                                    (previewCommands + "!mp settings").forEachIndexed { idx, cmd ->
                                        Text(
                                            text = "${idx + 1}. $cmd",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace).tnum(),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isRunning) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = controlShape,
                            color = surfaces.accentSubtle,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = osuHairlineBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stepMessage ?: "Setting up lobby…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        OsuInlineBanner(
                            text = errorMessage,
                            tone = OsuBannerTone.ERROR,
                            onDismiss = onClearError
                        )
                    }
                }

                OsuHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.lg, vertical = OsuSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = onCancelRunning,
                            shape = controlShape,
                            border = osuHairlineBorder()
                        ) {
                            Text("Cancel setup")
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            shape = controlShape
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onClearError()
                                onRunWizard(currentConfig)
                            },
                            enabled = currentConfig.resolvedRoomName.isNotBlank(),
                            shape = controlShape
                        ) {
                            Text("Create & setup lobby")
                        }
                    }
                }
            }
        }
    }
}

private fun splitUserList(raw: String): List<String> = raw
    .split(',', '\n', ';')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
