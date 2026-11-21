package com.osuchat.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.UserLinkRef
import com.osuchat.app.ui.theme.GradeInkDark
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StarEasy
import com.osuchat.app.ui.theme.StarExpert
import com.osuchat.app.ui.theme.StarExpertPlus
import com.osuchat.app.ui.theme.StarHard
import com.osuchat.app.ui.theme.StarInsane
import com.osuchat.app.ui.theme.StarNormal
import com.osuchat.app.ui.theme.StarUltraDark
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.BeatmapUrlParser
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal fun sanitizeCardError(throwable: Throwable?, fallback: String): String {
    return sanitizeCardErrorMessage(throwable?.message, fallback)
}

internal fun sanitizeCardErrorMessage(raw: String?, fallback: String): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return fallback
    val lower = trimmed.lowercase(Locale.US)
    return when {
        "unknownhost" in lower || "unreachable" in lower || "failed to connect" in lower ||
            "connection reset" in lower || "network" in lower || "offline" in lower ->
            "Offline — check your connection"
        "timeout" in lower || "timed out" in lower ->
            "Request timed out"
        "401" in lower || "unauthorized" in lower || "session expired" in lower ->
            "Session expired — sign in again"
        "403" in lower || "cloudflare" in lower ->
            "Access temporarily blocked"
        "404" in lower || "not found" in lower ->
            "Not found on osu!"
        "429" in lower || "rate limit" in lower ->
            "Rate limited — retry shortly"
        "500" in lower || "502" in lower || "503" in lower || "504" in lower ->
            "osu! server unavailable"
        "exception" in lower || "java." in lower || "kotlin." in lower ||
            "okhttp" in lower || "retrofit" in lower || '\n' in trimmed ->
            fallback
        else -> trimmed.take(120)
    }
}

@Composable
fun BeatmapPreviewCard(
    linkRef: BeatmapLinkRef,
    resolveMetadata: suspend (BeatmapLinkRef) -> Result<BeatmapMetadata>,
    audioState: AudioPlaybackState,
    onToggleAudio: (beatmapsetId: Long, previewUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surfaces = LocalOsuSurfaces.current
    var reloadKey by remember(linkRef.beatmapId, linkRef.beatmapsetId, linkRef.url) {
        mutableIntStateOf(0)
    }
    var metadata by remember(linkRef.beatmapId, linkRef.beatmapsetId, linkRef.url) {
        mutableStateOf<BeatmapMetadata?>(null)
    }
    var isLoading by remember(linkRef.beatmapId, linkRef.beatmapsetId, linkRef.url) {
        mutableStateOf(true)
    }
    var errorMessage by remember(linkRef.beatmapId, linkRef.beatmapsetId, linkRef.url) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(linkRef.beatmapId, linkRef.beatmapsetId, linkRef.url, reloadKey) {
        isLoading = true
        errorMessage = null
        val result = resolveMetadata(linkRef)
        metadata = result.getOrNull()
        errorMessage = if (result.isFailure || metadata == null) {
            sanitizeCardError(result.exceptionOrNull(), "Beatmap metadata unavailable")
        } else {
            null
        }
        isLoading = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkRef.url)))
                }
            },
        shape = RoundedCornerShape(OsuRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        if (isLoading) {
            BeatmapPreviewSkeleton()
        } else if (metadata == null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = errorMessage ?: "Beatmap metadata unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                        Text(
                            text = linkRef.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { reloadKey++ },
                        shape = RoundedCornerShape(OsuRadius.md),
                        border = osuHairlineBorder(),
                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(OsuSpacing.xs))
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OsuHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap card to open in browser",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val map = metadata!!
            val isThisSet = audioState.beatmapsetId == map.beatmapsetId
            val isPlayingThis = isThisSet && audioState.isPlaying
            val isPreparingThis = isThisSet && audioState.isPreparing
            val hasAudioError = isThisSet && !audioState.error.isNullOrBlank()
            val playBg by animateColorAsState(
                targetValue = if (isPlayingThis || isPreparingThis) {
                    MaterialTheme.colorScheme.primary
                } else {
                    surfaces.mediaControlSurface
                },
                animationSpec = OsuMotion.colorSpec(),
                label = "beatmapPlayBg"
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    if (map.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = map.coverUrl,
                            contentDescription = map.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(surfaces.mediaScrimTop, surfaces.mediaScrimBottom)
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = map.title.ifBlank { "Untitled beatmap" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${map.artist} · ${map.creator}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.84f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(OsuSpacing.xs))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                            ) {
                                StarRatingBadge(starRating = map.starRating)
                                OsuModeBadge(mode = map.mode, compact = true)
                                Text(
                                    text = "[${map.version}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(OsuSpacing.sm))

                        IconButton(
                            onClick = {
                                onToggleAudio(map.beatmapsetId, map.previewAudioUrl)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(OsuRadius.md))
                                .background(playBg)
                        ) {
                            if (isPreparingThis) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlayingThis) "Pause preview" else "Play preview",
                                    tint = if (isPlayingThis) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (hasAudioError) {
                    Text(
                        text = sanitizeCardErrorMessage(audioState.error, "Audio preview unavailable"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                    )
                } else if (isThisSet && (isPlayingThis || audioState.progress > 0f)) {
                    LinearProgressIndicator(
                        progress = { audioState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                OsuHairlineDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val safeBpm = if (map.bpm.isFinite() && map.bpm >= 0.0) map.bpm.roundToInt() else 0
                    val safeLen = map.totalLengthSeconds.coerceAtLeast(0)
                    Text(
                        text = "$safeBpm BPM · ${BeatmapUrlParser.formatDuration(safeLen)}",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(OsuSpacing.sm))
                    val csStr = if (map.cs.isFinite()) String.format(Locale.US, "%.1f", map.cs) else "—"
                    val arStr = if (map.ar.isFinite()) String.format(Locale.US, "%.1f", map.ar) else "—"
                    val odStr = if (map.od.isFinite()) String.format(Locale.US, "%.1f", map.od) else "—"
                    Text(
                        text = "CS $csStr  AR $arStr  OD $odStr",
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

@Composable
private fun BeatmapPreviewSkeleton() {
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(blockColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(OsuRadius.xs))
                        .background(blockColor)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(OsuRadius.xs))
                            .background(blockColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(OsuRadius.xs))
                            .background(blockColor)
                    )
                }
            }
            Spacer(modifier = Modifier.width(OsuSpacing.sm))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(OsuRadius.md))
                    .background(blockColor)
            )
        }
        OsuHairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(OsuRadius.xs))
                    .background(blockColor)
            )
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(OsuRadius.xs))
                    .background(blockColor)
            )
        }
    }
}

@Composable
fun StarRatingBadge(
    starRating: Double,
    modifier: Modifier = Modifier
) {
    val safeStars = starRating.takeIf { it.isFinite() && it >= 0.0 }
    val bgColor = if (safeStars != null) {
        starRatingColor(safeStars)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when {
        safeStars == null -> MaterialTheme.colorScheme.onSurfaceVariant
        safeStars >= 6.5 -> Color.White
        else -> GradeInkDark
    }

    val badgeShape = RoundedCornerShape(OsuRadius.xs)
    Row(
        modifier = modifier
            .clip(badgeShape)
            .background(bgColor)
            .let { base ->
                if (safeStars != null && safeStars >= 8.0) {
                    base.border(BorderStroke(OsuRadius.hairline, MaterialTheme.colorScheme.outline), badgeShape)
                } else {
                    base
                }
            }
            .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(OsuSpacing.xxs))
        Text(
            text = safeStars?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
            style = MaterialTheme.typography.labelSmall.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Official osu! difficulty star rating spectrum colors.
 */
fun starRatingColor(stars: Double): Color = when {
    stars < 2.0 -> StarEasy
    stars < 2.7 -> StarNormal
    stars < 4.0 -> StarHard
    stars < 5.3 -> StarInsane
    stars < 6.5 -> StarExpert
    stars < 8.0 -> StarExpertPlus
    else -> StarUltraDark
}

@Composable
fun UserProfilePreviewCard(
    linkRef: UserLinkRef,
    resolveProfile: suspend (Long) -> Result<PlayerProfile>,
    onOpenProfile: (PlayerProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var reloadKey by remember(linkRef.userId) { mutableIntStateOf(0) }
    var profile by remember(linkRef.userId) { mutableStateOf<PlayerProfile?>(null) }
    var isLoading by remember(linkRef.userId) { mutableStateOf(true) }
    var errorMessage by remember(linkRef.userId) { mutableStateOf<String?>(null) }

    LaunchedEffect(linkRef.userId, reloadKey) {
        isLoading = true
        errorMessage = null
        val result = resolveProfile(linkRef.userId)
        profile = result.getOrNull()
        errorMessage = if (result.isFailure || profile == null) {
            sanitizeCardError(result.exceptionOrNull(), "Profile unavailable")
        } else {
            null
        }
        isLoading = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val target = profile ?: PlayerProfile(
                    userId = linkRef.userId,
                    username = linkRef.userId.toString(),
                    avatarUrl = "https://a.ppy.sh/${linkRef.userId}"
                )
                onOpenProfile(target)
            },
        shape = RoundedCornerShape(OsuRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        if (isLoading) {
            val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(blockColor)
                )
                Spacer(modifier = Modifier.width(OsuSpacing.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(OsuRadius.xs))
                            .background(blockColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(OsuRadius.xs))
                            .background(blockColor)
                    )
                }
            }
        } else {
            val loaded = profile
            val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerAvatar(
                    name = loaded?.username ?: "User #${linkRef.userId}",
                    avatarUrl = loaded?.avatarUrl ?: "https://a.ppy.sh/${linkRef.userId}",
                    size = 44.dp,
                    showOnlineDot = loaded != null,
                    isOnline = loaded?.isOnline == true
                )
                Spacer(modifier = Modifier.width(OsuSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        Text(
                            text = loaded?.displayName ?: "Player #${linkRef.userId}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (loaded != null && loaded.countryCode != "XX") {
                            val flag = countryCodeToFlagEmoji(loaded.countryCode)
                            if (flag.isNotEmpty()) {
                                Text(
                                    text = flag,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    if (loaded != null) {
                        val metricsText = buildString {
                            loaded.globalRank?.let { append("#${numFmt.format(it)}") }
                            loaded.pp?.takeIf { it.isFinite() }?.let {
                                if (isNotEmpty()) append(" · ")
                                append("${numFmt.format(it.roundToInt())}pp")
                            }
                        }
                        if (metricsText.isNotEmpty()) {
                            Text(
                                text = metricsText,
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "osu.ppy.sh/users/${linkRef.userId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = errorMessage ?: "Profile unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(OsuSpacing.sm))
                if (loaded == null) {
                    OutlinedButton(
                        onClick = { reloadKey++ },
                        shape = RoundedCornerShape(OsuRadius.md),
                        border = osuHairlineBorder(),
                        contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Open profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
