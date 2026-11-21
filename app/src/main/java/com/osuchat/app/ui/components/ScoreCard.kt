package com.osuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.ui.theme.GradeC
import com.osuchat.app.ui.theme.GradeD
import com.osuchat.app.ui.theme.GradeGold
import com.osuchat.app.ui.theme.GradeInkDark
import com.osuchat.app.ui.theme.GradeS
import com.osuchat.app.ui.theme.GradeSilver
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.StatusRead
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GradeBadge(
    grade: String,
    modifier: Modifier = Modifier
) {
    val raw = grade.trim().uppercase()
    val label = when (raw) {
        "XH", "X" -> "SS"
        "SH" -> "S"
        "" -> "—"
        else -> raw
    }
    val bgColor = when (raw) {
        "XH", "SH" -> GradeSilver
        "X" -> GradeGold
        "S" -> GradeS
        "A" -> StatusOnline
        "B" -> StatusRead
        "C" -> GradeC
        "D" -> GradeD
        "F" -> StatusError
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when (raw) {
        "XH", "X", "SH", "S", "A", "B" -> GradeInkDark
        "C", "D", "F" -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(width = 36.dp, height = 24.dp)
            .clip(RoundedCornerShape(OsuRadius.xs))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun OsuScoreCard(
    score: OsuScoreItem,
    onShareToChat: ((OsuScoreItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val surfaces = LocalOsuSurfaces.current
    val outerShape = RoundedCornerShape(if (embedded) OsuRadius.md else OsuRadius.lg)
    val coverShape = RoundedCornerShape(OsuRadius.md)
    val containerColor = if (embedded) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = outerShape,
        color = containerColor,
        border = if (embedded) null else osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(coverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (score.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = score.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(OsuSpacing.md))

            GradeBadge(grade = score.rank)
            Spacer(modifier = Modifier.width(OsuSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                val titleText = when {
                    score.beatmapTitle.isBlank() -> "Unknown beatmap"
                    score.beatmapArtist.isNotBlank() -> "${score.beatmapArtist} - ${score.beatmapTitle}"
                    else -> score.beatmapTitle
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val hasDiff = score.beatmapVersion.isNotBlank()
                val starsText = score.starRating.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { String.format(Locale.US, "★%.2f", it) }
                val modsText = score.gameplayModsLabel.takeIf { it.isNotBlank() }

                if (hasDiff || starsText != null || modsText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                    ) {
                        if (hasDiff) {
                            Text(
                                text = "[${score.beatmapVersion}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        val metricMeta = listOfNotNull(starsText, modsText).joinToString(" ")
                        if (metricMeta.isNotEmpty()) {
                            Text(
                                text = metricMeta,
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = surfaces.textMuted,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val accText = if (score.accuracy.isFinite()) {
                        String.format(Locale.US, "%.2f%%", score.accuracy)
                    } else {
                        "—"
                    }
                    Text(
                        text = accText,
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "${score.maxCombo.coerceAtLeast(0)}x",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    val ppText = score.pp?.takeIf { it.isFinite() }
                        ?.let { "${it.roundToInt()}pp" }
                        ?: if (!score.passed) "Fail" else "—"
                    Text(
                        text = ppText,
                        style = MaterialTheme.typography.labelMedium.tnum(),
                        fontWeight = FontWeight.SemiBold,
                        color = if (!score.passed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        softWrap = false
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
            }

            if (onShareToChat != null) {
                IconButton(
                    onClick = { onShareToChat(score) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share score to chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
