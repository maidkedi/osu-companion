package com.osuchat.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osuchat.app.data.repository.ImageUploadRepository
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.RenderedScoreCard
import com.osuchat.app.util.ScoreCardRenderer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScoreCardDialogEntryPoint {
    fun scoreCardRenderer(): ScoreCardRenderer
    fun imageUploadRepository(): ImageUploadRepository
    fun chatRepository(): ChatRepository
}

@Composable
fun ScoreCardShareDialog(
    score: OsuScoreItem,
    defaultChannelId: String? = null,
    onSendTextFallback: ((OsuScoreItem) -> Unit)? = null,
    onNotifyMessage: (String) -> Unit = {},
    onOpenedChannel: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ScoreCardDialogEntryPoint::class.java
        )
    }
    val renderer = remember(entryPoint) { entryPoint.scoreCardRenderer() }
    val imageUploader = remember(entryPoint) { entryPoint.imageUploadRepository() }
    val chatRepository = remember(entryPoint) { entryPoint.chatRepository() }

    val channels by chatRepository.channels.collectAsStateWithLifecycle(initialValue = emptyList())

    var isRendering by remember(score) { mutableStateOf(true) }
    var renderError by remember(score) { mutableStateOf<String?>(null) }
    var renderedCard by remember(score) { mutableStateOf<RenderedScoreCard?>(null) }
    var previewBitmap by remember(score) { mutableStateOf<ImageBitmap?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var uploadedImageUrl by remember(score) { mutableStateOf<String?>(null) }
    var actionError by remember(score) { mutableStateOf<String?>(null) }

    LaunchedEffect(score) {
        isRendering = true
        renderError = null
        actionError = null
        renderer.renderScoreCard(score)
            .onSuccess { card ->
                renderedCard = card
                previewBitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        BitmapFactory.decodeFile(card.file.absolutePath)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            .onFailure { err ->
                renderError = err.message ?: "Failed to render score card"
            }
        isRendering = false
    }

    val scoreTextSummary = remember(score) { formatScoreSummaryLine(score) }
    val dialogShape = RoundedCornerShape(OsuRadius.xl)
    val btnShape = RoundedCornerShape(OsuRadius.md)

    fun uploadAndPostToChannel(targetChannelId: String) {
        val card = renderedCard ?: return
        if (isUploading) return
        scope.launch {
            isUploading = true
            actionError = null
            val existingUrl = uploadedImageUrl
            val urlResult = if (!existingUrl.isNullOrBlank()) {
                Result.success(existingUrl)
            } else {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    card.file
                )
                imageUploader.compressAndUpload(uri)
            }
            urlResult
                .onSuccess { url ->
                    uploadedImageUrl = url
                    val msg = "$scoreTextSummary $url".trim()
                    chatRepository.sendMessage(targetChannelId, msg)
                        .onSuccess {
                            isUploading = false
                            onNotifyMessage("Score card posted to chat")
                            onDismiss()
                            onOpenedChannel?.invoke(targetChannelId)
                        }
                        .onFailure { err ->
                            isUploading = false
                            actionError = err.message ?: "Could not send message to channel"
                        }
                }
                .onFailure { err ->
                    isUploading = false
                    actionError = err.message ?: "Image upload failed"
                }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = osuHairlineBorder()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OsuSheetTopBar(
                    title = "Share score card",
                    subtitle = "${score.beatmapTitle} [${score.beatmapVersion}]",
                    onClose = onDismiss
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OsuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1200f / 630f)
                            .clip(RoundedCornerShape(OsuRadius.lg))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isRendering -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Rendering 1200×630 card…",
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            previewBitmap != null -> {
                                Image(
                                    bitmap = previewBitmap!!,
                                    contentDescription = "Rendered Score Card PNG",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Text(
                                    text = renderError ?: "Preview unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(OsuSpacing.md)
                                )
                            }
                        }
                    }

                    renderedCard?.ifFcPp?.let { fcPp ->
                        Text(
                            text = String.format(Locale.US, "Calculated if-FC: %dpp", fcPp.roundToInt()),
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!actionError.isNullOrBlank()) {
                        OsuInlineBanner(
                            text = actionError.orEmpty(),
                            tone = OsuBannerTone.ERROR
                        )
                    }

                    if (!uploadedImageUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uploadedImageUrl.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(uploadedImageUrl.orEmpty()))
                                    onNotifyMessage("Image link copied")
                                }
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Copy link")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val card = renderedCard ?: return@OutlinedButton
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    card.file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, scoreTextSummary)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share score card"))
                            },
                            enabled = renderedCard != null && !isRendering,
                            shape = btnShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text("Share PNG", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                val card = renderedCard ?: return@OutlinedButton
                                if (isSaving) return@OutlinedButton
                                scope.launch {
                                    isSaving = true
                                    actionError = null
                                    val safeTitle = score.beatmapTitle.take(24).ifBlank { "score" }
                                    renderer.saveToGallery(card.file, "${score.username}_$safeTitle")
                                        .onSuccess {
                                            onNotifyMessage("Saved score card to Pictures/OsuCompanion")
                                        }
                                        .onFailure { err ->
                                            actionError = err.message ?: "Failed to save to gallery"
                                        }
                                    isSaving = false
                                }
                            },
                            enabled = renderedCard != null && !isRendering && !isSaving,
                            shape = btnShape,
                            border = osuHairlineBorder(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(OsuSpacing.xs))
                            Text("Save PNG", maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (!defaultChannelId.isNullOrBlank()) {
                                    uploadAndPostToChannel(defaultChannelId)
                                } else {
                                    showChannelPicker = !showChannelPicker
                                }
                            },
                            enabled = renderedCard != null && !isRendering && !isUploading,
                            shape = btnShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text("Uploading…")
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(OsuSpacing.xs))
                                Text(
                                    text = if (!defaultChannelId.isNullOrBlank()) {
                                        "Send to $defaultChannelId"
                                    } else {
                                        "Send to chat"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (onSendTextFallback != null) {
                            TextButton(
                                onClick = {
                                    onSendTextFallback(score)
                                    onDismiss()
                                },
                                shape = btnShape
                            ) {
                                Text("Text only")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showChannelPicker && defaultChannelId.isNullOrBlank(),
                        enter = OsuMotion.expandEnter(),
                        exit = OsuMotion.expandExit()
                    ) {
                        Surface(
                            shape = btnShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = osuHairlineBorder(),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (channels.isEmpty()) {
                                Text(
                                    text = "No open chats yet. Use Copy link or Share PNG.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(OsuSpacing.md)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 172.dp),
                                    contentPadding = PaddingValues(vertical = OsuSpacing.xs)
                                ) {
                                    itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isUploading) {
                                                    uploadAndPostToChannel(ch.id)
                                                }
                                                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = ch.displayTitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(OsuSpacing.sm))
                                            Text(
                                                text = "Post",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (index < channels.lastIndex) {
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
    }
}

private fun formatScoreSummaryLine(score: OsuScoreItem): String {
    val modsPart = if (score.mods.isNotEmpty()) " +${score.mods.joinToString("")}" else ""
    val ppPart = score.pp?.takeIf { it.isFinite() }?.let { " ${it.roundToInt()}pp" } ?: ""
    val acc = String.format(Locale.US, "%.2f%%", score.accuracy)
    val url = score.beatmapId?.let { "https://osu.ppy.sh/b/$it" }
        ?: score.beatmapsetId?.let { "https://osu.ppy.sh/s/$it" }
        ?: ""
    return "${score.username} | ${score.beatmapArtist} - ${score.beatmapTitle} [${score.beatmapVersion}]$modsPart ${score.gradeLabel} $acc ${score.maxCombo}x$ppPart $url"
        .trim()
}
