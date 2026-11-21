package com.osuchat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryMetric
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOnline
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.ProfileHistoryMerger
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class HistoryRange(val label: String, val days: Int) {
    DAYS_7("7D", 7),
    DAYS_30("30D", 30),
    DAYS_90("90D", 90)
}

@Composable
fun ProfileHistoryChart(
    snapshots: List<ProfileHistoryPoint>,
    currentProfile: PlayerProfile?,
    mode: String,
    osuTrackPoints: List<OsuTrackPoint> = emptyList(),
    isOsuTrackLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(HistoryRange.DAYS_30) }
    var selectedMetric by remember { mutableStateOf(ProfileHistoryMetric.PP) }

    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val dateOutFmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }
    val modeNorm = mode.trim().lowercase(Locale.US).ifBlank { "osu" }

    val merged = remember(snapshots, osuTrackPoints, currentProfile, modeNorm, selectedRange, selectedMetric) {
        ProfileHistoryMerger.merge(
            metric = selectedMetric,
            days = selectedRange.days,
            mode = modeNorm,
            currentProfile = currentProfile,
            osuTrackPoints = osuTrackPoints,
            localSnapshots = snapshots
        )
    }
    val filteredPoints = merged.points

    val surfaces = LocalOsuSurfaces.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(OsuRadius.lg),
        color = surfaceColor,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OsuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)) {
                    Text(
                        text = "Rank & PP history",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (filteredPoints.size >= 2) {
                        val first = filteredPoints.first()
                        val last = filteredPoints.last()
                        val (deltaText, deltaColor) = when (selectedMetric) {
                            ProfileHistoryMetric.PP -> {
                                val diff = last.pp - first.pp
                                val formatted = String.format(Locale.US, "%+.1f pp", diff)
                                val color = when {
                                    diff > 0.05 -> StatusOnline
                                    diff < -0.05 -> StatusError
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                formatted to color
                            }
                            ProfileHistoryMetric.RANK -> {
                                val diff = first.globalRank - last.globalRank
                                val sign = if (diff > 0) "+" else if (diff < 0) "-" else ""
                                val formatted = "$sign${numFmt.format(abs(diff))} ranks"
                                val color = when {
                                    diff > 0 -> StatusOnline
                                    diff < 0 -> StatusError
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                formatted to color
                            }
                        }
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = deltaColor
                        )
                    } else if (filteredPoints.size == 1) {
                        val only = filteredPoints.first()
                        val baselineText = when (selectedMetric) {
                            ProfileHistoryMetric.PP -> String.format(Locale.US, "Baseline %,.1f pp", only.pp)
                            ProfileHistoryMetric.RANK -> "Baseline #${numFmt.format(only.globalRank)}"
                        }
                        Text(
                            text = baselineText,
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                    ProfileHistoryMetric.entries.forEach { metric ->
                        val label = when (metric) {
                            ProfileHistoryMetric.PP -> "PP"
                            ProfileHistoryMetric.RANK -> "Rank"
                        }
                        OsuFilterChip(
                            label = label,
                            selected = selectedMetric == metric,
                            onClick = { selectedMetric = metric }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                    HistoryRange.entries.forEach { range ->
                        OsuFilterChip(
                            label = range.label,
                            selected = selectedRange == range,
                            onClick = { selectedRange = range },
                            isNumeric = true
                        )
                    }
                }

                if (filteredPoints.isNotEmpty()) {
                    val rangeSummary = when (selectedMetric) {
                        ProfileHistoryMetric.PP -> {
                            val minVal = filteredPoints.minOf { it.pp }
                            val maxVal = filteredPoints.maxOf { it.pp }
                            if (filteredPoints.size == 1) {
                                String.format(Locale.US, "%.1f pp", maxVal)
                            } else {
                                String.format(Locale.US, "%.1f – %.1f pp", minVal, maxVal)
                            }
                        }
                        ProfileHistoryMetric.RANK -> {
                            val bestRank = filteredPoints.minOf { it.globalRank }
                            val worstRank = filteredPoints.maxOf { it.globalRank }
                            if (filteredPoints.size == 1) {
                                "#${numFmt.format(bestRank)}"
                            } else {
                                "#${numFmt.format(worstRank)} – #${numFmt.format(bestRank)}"
                            }
                        }
                    }
                    Text(
                        text = rangeSummary,
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OsuSpacing.xxs),
                contentAlignment = Alignment.Center
            ) {
                OsuHairlineDivider()
                androidx.compose.animation.AnimatedVisibility(
                    visible = isOsuTrackLoading && osuTrackPoints.isEmpty(),
                    enter = OsuMotion.fadeEnter(),
                    exit = OsuMotion.fadeExit()
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(OsuSpacing.xxs),
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            if (filteredPoints.isEmpty()) {
                Text(
                    text = if (isOsuTrackLoading) {
                        "Loading history…"
                    } else {
                        "No snapshots recorded for this mode yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = OsuSpacing.sm)
                )
            } else {
                val values = remember(filteredPoints, selectedMetric) {
                    filteredPoints.map { pt ->
                        when (selectedMetric) {
                            ProfileHistoryMetric.PP -> pt.pp
                            ProfileHistoryMetric.RANK -> pt.globalRank.toDouble()
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    if (size.width <= 0f || size.height <= 0f) return@Canvas
                    val padX = 8.dp.toPx()
                    val padY = 12.dp.toPx()
                    val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
                    val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = padY + (plotH * i / gridSteps.toFloat())
                        drawLine(
                            color = gridColor,
                            start = Offset(padX, y),
                            end = Offset(size.width - padX, y),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }

                    if (values.size == 1) {
                        val cy = padY + plotH / 2f
                        drawLine(
                            color = accentColor.copy(alpha = 0.3f),
                            start = Offset(padX, cy),
                            end = Offset(size.width - padX, cy),
                            strokeWidth = 1.dp.toPx()
                        )
                        val cx = size.width / 2f
                        drawCircle(
                            color = surfaceColor,
                            radius = 5.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = accentColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                    } else {
                        val finiteVals = values.filter { it.isFinite() }
                        if (finiteVals.size < 2) return@Canvas
                        val minVal = finiteVals.minOrNull() ?: 0.0
                        val maxVal = finiteVals.maxOrNull() ?: 0.0
                        val rawSpan = maxVal - minVal
                        val isFlat = !rawSpan.isFinite() || rawSpan <= 1e-6
                        val span = if (isFlat) 1.0 else rawSpan

                        val minTs = filteredPoints.first().timestampMs
                        val maxTs = filteredPoints.last().timestampMs
                        val tsSpan = (maxTs - minTs).takeIf { it > 0L }

                        val coords = values.mapIndexed { idx, rawVal ->
                            val v = if (rawVal.isFinite()) rawVal else minVal
                            val xRatio = if (tsSpan != null) {
                                ((filteredPoints[idx].timestampMs - minTs).toDouble() / tsSpan.toDouble())
                                    .toFloat()
                                    .let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
                            } else {
                                idx / (values.size - 1).toFloat()
                            }
                            val x = (padX + plotW * xRatio).coerceIn(padX, padX + plotW)

                            val y = if (isFlat) {
                                padY + plotH / 2f
                            } else {
                                val rawNorm = ((v - minVal) / span).toFloat()
                                val normalized = if (rawNorm.isFinite()) rawNorm.coerceIn(0f, 1f) else 0.5f
                                val yRatio = if (selectedMetric == ProfileHistoryMetric.RANK) {
                                    normalized
                                } else {
                                    1f - normalized
                                }
                                (padY + plotH * yRatio).coerceIn(padY, padY + plotH)
                            }
                            Offset(x, y)
                        }

                        val linePath = Path().apply {
                            coords.forEachIndexed { idx, pt ->
                                if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                            }
                        }

                        val bottomY = padY + plotH
                        val areaPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().x, bottomY)
                            lineTo(coords.first().x, bottomY)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                startY = padY,
                                endY = bottomY
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = accentColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        if (coords.size <= 16) {
                            coords.forEachIndexed { idx, pt ->
                                val isLast = idx == coords.lastIndex
                                drawCircle(
                                    color = surfaceColor,
                                    radius = if (isLast) 4.dp.toPx() else 2.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = if (isLast) 3.dp.toPx() else 1.75.dp.toPx(),
                                    center = pt
                                )
                            }
                        } else {
                            val endpoints = listOf(coords.first() to false, coords.last() to true)
                            endpoints.forEach { (pt, isLast) ->
                                drawCircle(
                                    color = surfaceColor,
                                    radius = if (isLast) 4.dp.toPx() else 2.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = if (isLast) 3.dp.toPx() else 1.75.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val startLabel = formatDayKey(filteredPoints.first().dayKey, dateOutFmt)
                    val endLabel = formatDayKey(filteredPoints.last().dayKey, dateOutFmt)
                    Text(
                        text = startLabel,
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted
                    )
                    if (filteredPoints.size > 1) {
                        val countLabel = when {
                            merged.usesOfficialRankHistory -> "${filteredPoints.size} days"
                            merged.usesOsuTrack -> "${filteredPoints.size} points"
                            else -> "${filteredPoints.size} snapshots"
                        }
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.labelSmall.tnum(),
                            color = surfaces.textMuted
                        )
                    }
                    Text(
                        text = if (filteredPoints.size == 1) "Today" else endLabel,
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted
                    )
                }

                val sourceCaption = when {
                    selectedMetric == ProfileHistoryMetric.PP && merged.usesOsuTrack ->
                        "History via osu!track & local snapshots"
                    selectedMetric == ProfileHistoryMetric.RANK && merged.usesOfficialRankHistory ->
                        "90-day global rank history via osu! API"
                    selectedMetric == ProfileHistoryMetric.RANK && merged.usesOsuTrack ->
                        "History via osu!track & local snapshots"
                    else ->
                        "Snapshots record automatically when viewing your profile or syncing the widget"
                }
                Spacer(modifier = Modifier.height(OsuSpacing.xxs))
                Text(
                    text = sourceCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = surfaces.textMuted
                )
            }
        }
    }
}

private fun formatDayKey(dayKey: String, formatter: DateTimeFormatter): String {
    return runCatching {
        LocalDate.parse(dayKey).format(formatter)
    }.getOrDefault(dayKey)
}
