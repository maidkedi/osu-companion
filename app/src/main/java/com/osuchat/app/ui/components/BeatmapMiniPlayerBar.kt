package com.osuchat.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.AudioQueueTrack
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.tnum
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BeatmapMiniPlayerBar(
    audioState: AudioPlaybackState,
    isBookmarked: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleBookmark: (AudioQueueTrack) -> Unit,
    onClose: () -> Unit,
    onOpenTrackStats: (AudioQueueTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = audioState.currentTrack ?: return
    val coverUrl = track.coverUrl.ifBlank {
        "https://assets.ppy.sh/beatmaps/${track.beatmapsetId}/covers/list.jpg"
    }
    val surfaces = LocalOsuSurfaces.current
    val animatedProgress by animateFloatAsState(
        targetValue = audioState.progress.coerceIn(0f, 1f),
        animationSpec = OsuMotion.floatSpec(),
        label = "miniPlayerProgress"
    )
    val bookmarkTint by animateColorAsState(
        targetValue = if (isBookmarked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = OsuMotion.colorSpec(),
        label = "miniPlayerBookmark"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OsuHairlineDivider()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(OsuRadius.md))
                        .clickable { onOpenTrackStats(track) }
                        .padding(end = OsuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = track.title.ifBlank { "Beatmap cover" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(OsuRadius.md))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Spacer(Modifier.width(OsuSpacing.sm))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                    ) {
                        Text(
                            text = track.title.ifBlank { "Beatmap #${track.beatmapsetId}" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        val subtitle = buildString {
                            if (track.artist.isNotBlank()) append(track.artist)
                            if (track.version.isNotBlank()) {
                                if (isNotEmpty()) append(" ")
                                append("[${track.version}]")
                            }
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val stats = buildList {
                            if (track.starRating > 0.0) {
                                add(String.format(Locale.US, "%.2f★", track.starRating))
                            }
                            if (track.bpm > 0.0) {
                                add("${track.bpm.roundToInt()} BPM")
                            }
                            if (audioState.queueSize > 1 && audioState.queueIndex >= 0) {
                                add("${audioState.queueIndex + 1}/${audioState.queueSize}")
                            }
                        }.joinToString(" · ")
                        if (stats.isNotBlank()) {
                            Text(
                                text = stats,
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = surfaces.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = audioState.hasPrevious || audioState.queueSize > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous track",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (audioState.isPreparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            AnimatedContent(
                                targetState = audioState.isPlaying,
                                transitionSpec = {
                                    fadeIn(tween(OsuMotion.FastMs, easing = OsuMotion.EaseOut)) togetherWith
                                        fadeOut(tween(OsuMotion.FastMs, easing = OsuMotion.EaseOut))
                                },
                                label = "miniPlayerPlayPause"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        enabled = audioState.hasNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { onToggleBookmark(track) },
                        enabled = track.beatmapId > 0L,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove from Play Later" else "Save to Play Later",
                            tint = bookmarkTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close mini-player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
