package com.osuchat.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.MappoolBulkParser
import kotlinx.coroutines.delay
import java.util.Locale

private data class EditableSlotRow(
    val key: Long,
    val slotTag: String,
    val beatmapIdText: String,
    val mods: String
)

@Composable
fun MappoolManagerDialog(
    mappools: List<Mappool>,
    initialPoolToEdit: Mappool? = null,
    onSavePool: (poolId: Long?, name: String, slots: List<MappoolSlot>) -> Unit,
    onDeletePool: (poolId: Long) -> Unit,
    onDismiss: () -> Unit,
    onGetCachedBeatmaps: (suspend (Collection<Long>) -> Map<Long, BeatmapMetadata>)? = null,
    onResolveBeatmapDetails: (suspend (Long) -> Result<BeatmapMetadata>)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isEditing by remember(initialPoolToEdit) { mutableStateOf(initialPoolToEdit != null) }
    var editingPoolId by remember(initialPoolToEdit) { mutableStateOf(initialPoolToEdit?.id) }
    var poolName by remember(initialPoolToEdit) { mutableStateOf(initialPoolToEdit?.name.orEmpty()) }
    var bulkPasteInput by remember(initialPoolToEdit) { mutableStateOf("") }
    var nextRowKey by remember { mutableStateOf(1L) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var listNotice by remember { mutableStateOf<String?>(null) }
    var resolvingBeatmapId by remember { mutableStateOf<Long?>(null) }
    val resolvedBeatmaps = remember { mutableStateMapOf<Long, BeatmapMetadata>() }

    val slotRows = remember(initialPoolToEdit) {
        mutableStateListOf<EditableSlotRow>().apply {
            initialPoolToEdit?.slots.orEmpty().forEachIndexed { idx, slot ->
                add(
                    EditableSlotRow(
                        key = idx.toLong() + 1L,
                        slotTag = slot.slotTag,
                        beatmapIdText = slot.beatmapId.toString(),
                        mods = slot.mods
                    )
                )
            }
            nextRowKey = size.toLong() + 10L
        }
    }

    val activeSlotBeatmapIds = slotRows
        .mapNotNull { it.beatmapIdText.toLongOrNull()?.takeIf { id -> id > 0L } }
        .distinct()

    LaunchedEffect(isEditing, activeSlotBeatmapIds, onGetCachedBeatmaps, onResolveBeatmapDetails) {
        if (!isEditing || activeSlotBeatmapIds.isEmpty()) {
            resolvingBeatmapId = null
            return@LaunchedEffect
        }
        val missingCached = activeSlotBeatmapIds.filter { it !in resolvedBeatmaps }
        if (missingCached.isNotEmpty() && onGetCachedBeatmaps != null) {
            runCatching { onGetCachedBeatmaps(missingCached) }
                .getOrNull()
                ?.let { cachedMap -> resolvedBeatmaps.putAll(cachedMap) }
        }
        if (onResolveBeatmapDetails == null) return@LaunchedEffect
        delay(250L)
        for (beatmapId in activeSlotBeatmapIds) {
            if (beatmapId in resolvedBeatmaps) continue
            resolvingBeatmapId = beatmapId
            runCatching { onResolveBeatmapDetails(beatmapId) }
                .getOrNull()
                ?.getOrNull()
                ?.let { meta -> resolvedBeatmaps[beatmapId] = meta }
        }
        resolvingBeatmapId = null
    }

    val detectedJsonDto = remember(bulkPasteInput) {
        MappoolBulkParser.parseTransferJsonOrNull(bulkPasteInput)
    }

    fun startNewPool() {
        editingPoolId = null
        poolName = ""
        bulkPasteInput = ""
        editorError = null
        listNotice = null
        slotRows.clear()
        isEditing = true
    }

    fun startEditPool(pool: Mappool) {
        editingPoolId = pool.id
        poolName = pool.name
        bulkPasteInput = ""
        editorError = null
        listNotice = null
        slotRows.clear()
        pool.slots.forEach { slot ->
            slotRows.add(
                EditableSlotRow(
                    key = nextRowKey++,
                    slotTag = slot.slotTag,
                    beatmapIdText = slot.beatmapId.toString(),
                    mods = slot.mods
                )
            )
        }
        isEditing = true
    }

    fun applyImport(replaceExisting: Boolean) {
        val jsonDto = detectedJsonDto
        if (jsonDto != null) {
            editorError = null
            if ((replaceExisting || poolName.isBlank()) && jsonDto.name.isNotBlank()) {
                poolName = jsonDto.name
            }
            if (replaceExisting) {
                slotRows.clear()
            }
            jsonDto.slots.forEach { slot ->
                slotRows.add(
                    EditableSlotRow(
                        key = nextRowKey++,
                        slotTag = slot.slotTag,
                        beatmapIdText = slot.beatmapId.toString(),
                        mods = slot.mods
                    )
                )
            }
            bulkPasteInput = ""
            return
        }

        val parsed = MappoolBulkParser.parseBulkText(bulkPasteInput)
        if (parsed.isEmpty()) {
            editorError = if (bulkPasteInput.trim().startsWith("{")) {
                "Invalid mappool JSON or no valid beatmap IDs found"
            } else {
                "No valid beatmap IDs or URLs found in pasted text"
            }
        } else {
            editorError = null
            if (replaceExisting) {
                slotRows.clear()
            }
            parsed.forEach { slot ->
                slotRows.add(
                    EditableSlotRow(
                        key = nextRowKey++,
                        slotTag = slot.slotTag,
                        beatmapIdText = slot.beatmapId.toString(),
                        mods = slot.mods
                    )
                )
            }
            bulkPasteInput = ""
        }
    }

    val surfaces = LocalOsuSurfaces.current
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val controlShape = RoundedCornerShape(OsuRadius.md)
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
                    if (isEditing) {
                        IconButton(
                            onClick = {
                                isEditing = false
                                editorError = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to pools"
                            )
                        }
                        Spacer(Modifier.width(OsuSpacing.xs))
                    }
                    Text(
                        text = when {
                            !isEditing -> "Tournament mappools"
                            editingPoolId != null -> "Edit mappool"
                            else -> "New mappool"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isEditing) {
                        OutlinedButton(
                            onClick = ::startNewPool,
                            shape = chipShape,
                            border = osuHairlineBorder(),
                            contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text("New", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(OsuSpacing.xs))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                OsuHairlineDivider()

                if (!isEditing) {
                    if (mappools.isEmpty()) {
                        OsuEmptyState(
                            icon = Icons.Rounded.TableChart,
                            title = "No saved mappools",
                            subtitle = "Paste sheet rows or beatmap links to manage lobby maps in one tap",
                            primaryActionLabel = "Create mappool",
                            onPrimaryAction = ::startNewPool
                        )
                    } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                contentPadding = PaddingValues(OsuSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                if (!listNotice.isNullOrBlank()) {
                                    item(key = "list_notice") {
                                        Surface(
                                            shape = chipShape,
                                            color = surfaces.accentSubtle,
                                            border = osuHairlineBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = listNotice.orEmpty(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(
                                    items = mappools,
                                    key = { _, pool -> pool.id }
                                ) { _, pool ->
                                    var showExportMenu by remember(pool.id) { mutableStateOf(false) }
                                    Surface(
                                        shape = controlShape,
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
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = pool.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(OsuSpacing.xxs))
                                                val previewTags = pool.slots.take(5).joinToString(" · ") { it.slotTag }
                                                Text(
                                                    text = buildString {
                                                        append("${pool.slots.size} slots")
                                                        if (previewTags.isNotEmpty()) append(" · $previewTags")
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Box {
                                                IconButton(
                                                    onClick = { showExportMenu = true },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Share,
                                                        contentDescription = "Export ${pool.name}",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showExportMenu,
                                                    onDismissRequest = { showExportMenu = false },
                                                    shape = RoundedCornerShape(OsuRadius.md),
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    tonalElevation = 0.dp,
                                                    shadowElevation = 0.dp,
                                                    border = osuHairlineBorder()
                                                ) {
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        },
                                                        text = { Text("Copy JSON") },
                                                        onClick = {
                                                            showExportMenu = false
                                                            val json = MappoolBulkParser.exportToJson(pool)
                                                            clipboardManager.setText(AnnotatedString(json))
                                                            listNotice = "Copied JSON for \"${pool.name}\""
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        },
                                                        text = { Text("Copy sheet text") },
                                                        onClick = {
                                                            showExportMenu = false
                                                            val sheet = MappoolBulkParser.exportToSheetText(pool)
                                                            clipboardManager.setText(AnnotatedString(sheet))
                                                            listNotice = "Copied sheet text for \"${pool.name}\""
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        },
                                                        text = { Text("Share via Android") },
                                                        onClick = {
                                                            showExportMenu = false
                                                            val json = MappoolBulkParser.exportToJson(pool)
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_SUBJECT, pool.name)
                                                                putExtra(Intent.EXTRA_TEXT, json)
                                                            }
                                                            context.startActivity(
                                                                Intent.createChooser(shareIntent, "Share mappool")
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { startEditPool(pool) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Edit,
                                                    contentDescription = "Edit ${pool.name}",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeletePool(pool.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Delete ${pool.name}",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        contentPadding = PaddingValues(OsuSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        item(key = "pool_name_field") {
                            OutlinedTextField(
                                value = poolName,
                                onValueChange = {
                                    poolName = it
                                    editorError = null
                                },
                                label = { Text("Mappool name (e.g. OWC QF)") },
                                singleLine = true,
                                shape = controlShape,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "bulk_paste_card") {
                            Surface(
                                shape = controlShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = osuHairlineBorder(),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(OsuSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                ) {
                                    Text(
                                        text = "Smart import (JSON or sheet / links)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    OutlinedTextField(
                                        value = bulkPasteInput,
                                        onValueChange = {
                                            bulkPasteInput = it
                                            editorError = null
                                        },
                                        placeholder = {
                                            Text(
                                                "{\"version\":1,\"name\":...} or\nNM1 2637362\nHD2\thttps://osu.ppy.sh/b/2637363",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        minLines = 3,
                                        maxLines = 5,
                                        shape = controlShape,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (detectedJsonDto != null) {
                                        Surface(
                                            shape = chipShape,
                                            color = surfaces.accentSubtle,
                                            border = osuHairlineBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs),
                                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                                            ) {
                                                Text(
                                                    text = "JSON mappool detected: ${detectedJsonDto.name.ifBlank { "Untitled" }} (${detectedJsonDto.slots.size} slots)",
                                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = detectedJsonDto.slots.take(6).joinToString(" · ") { "${it.slotTag} (${it.mods})" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                    ) {
                                        OutlinedButton(
                                            onClick = { applyImport(replaceExisting = false) },
                                            enabled = bulkPasteInput.isNotBlank(),
                                            shape = chipShape,
                                            border = osuHairlineBorder(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentPaste,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(OsuSpacing.xs))
                                            Text(
                                                if (detectedJsonDto != null) "Import JSON" else "Parse & add",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        if (slotRows.isNotEmpty() && bulkPasteInput.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = { applyImport(replaceExisting = true) },
                                                shape = chipShape,
                                                border = osuHairlineBorder()
                                            ) {
                                                Text("Replace", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "slots_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Slots (${slotRows.size})",
                                    style = MaterialTheme.typography.labelLarge.tnum(),
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = {
                                        val nextIndex = slotRows.size + 1
                                        slotRows.add(
                                            EditableSlotRow(
                                                key = nextRowKey++,
                                                slotTag = "NM$nextIndex",
                                                beatmapIdText = "",
                                                mods = "NF"
                                            )
                                        )
                                    },
                                    shape = chipShape
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text("Add slot")
                                }
                            }
                        }

                        itemsIndexed(
                            items = slotRows,
                            key = { _, row -> row.key }
                        ) { idx, row ->
                            val parsedRowId = row.beatmapIdText.toLongOrNull()?.takeIf { it > 0L }
                            val beatmapMeta = parsedRowId?.let { resolvedBeatmaps[it] }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                                ) {
                                    OutlinedTextField(
                                        value = row.slotTag,
                                        onValueChange = { updated ->
                                            val trimmed = updated.take(10)
                                            slotRows[idx] = row.copy(
                                                slotTag = trimmed,
                                                mods = if (row.mods == MappoolBulkParser.defaultModsForSlotTag(row.slotTag)) {
                                                    MappoolBulkParser.defaultModsForSlotTag(trimmed)
                                                } else {
                                                    row.mods
                                                }
                                            )
                                            editorError = null
                                        },
                                        label = { Text("Slot") },
                                        singleLine = true,
                                        shape = chipShape,
                                        modifier = Modifier.width(80.dp)
                                    )
                                    OutlinedTextField(
                                        value = row.beatmapIdText,
                                        onValueChange = { updated ->
                                            slotRows[idx] = row.copy(beatmapIdText = updated.filter { it.isDigit() })
                                            editorError = null
                                        },
                                        label = { Text("Map ID") },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.tnum(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = chipShape,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = row.mods,
                                        onValueChange = { updated ->
                                            slotRows[idx] = row.copy(mods = updated)
                                        },
                                        label = { Text("Mods") },
                                        singleLine = true,
                                        shape = chipShape,
                                        modifier = Modifier.width(96.dp)
                                    )
                                    IconButton(
                                        onClick = { slotRows.removeAt(idx) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Remove slot",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (beatmapMeta != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = OsuSpacing.xs, end = 40.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${beatmapMeta.artist} - ${beatmapMeta.title} [${beatmapMeta.version}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(OsuSpacing.sm))
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "★ %.2f · %.0f BPM",
                                                beatmapMeta.starRating,
                                                beatmapMeta.bpm
                                            ),
                                            style = MaterialTheme.typography.labelSmall.tnum(),
                                            color = surfaces.textMuted,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                } else if (parsedRowId != null) {
                                    Text(
                                        text = if (resolvingBeatmapId == parsedRowId) {
                                            "ID: $parsedRowId · Loading metadata…"
                                        } else {
                                            "ID: $parsedRowId"
                                        },
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = OsuSpacing.xs)
                                    )
                                }
                            }
                        }

                        if (!editorError.isNullOrBlank()) {
                            item(key = "editor_error") {
                                Text(
                                    text = editorError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    OsuHairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                isEditing = false
                                editorError = null
                            },
                            shape = controlShape
                        ) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(OsuSpacing.sm))
                        Button(
                            onClick = {
                                val cleanName = poolName.trim()
                                if (cleanName.isEmpty()) {
                                    editorError = "Enter a mappool name"
                                    return@Button
                                }
                                val builtSlots = slotRows.mapIndexedNotNull { idx, row ->
                                    val tag = row.slotTag.trim()
                                    val mapId = row.beatmapIdText.toLongOrNull()?.takeIf { it > 0L }
                                    if (tag.isEmpty() || mapId == null) return@mapIndexedNotNull null
                                    MappoolSlot(
                                        poolId = editingPoolId ?: 0L,
                                        slotTag = tag,
                                        beatmapId = mapId,
                                        mods = row.mods.trim().ifEmpty { "NF" },
                                        orderIndex = idx
                                    )
                                }
                                if (builtSlots.isEmpty()) {
                                    editorError = "Add at least one valid slot with a numeric beatmap ID"
                                    return@Button
                                }
                                onSavePool(editingPoolId, cleanName, builtSlots)
                                isEditing = false
                                editorError = null
                            },
                            shape = controlShape
                        ) {
                            Text("Save pool")
                        }
                    }
                }
            }
        }
    }
}
