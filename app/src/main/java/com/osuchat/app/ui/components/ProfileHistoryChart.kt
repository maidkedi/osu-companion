package com.osuchat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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

    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val dateOutFmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }
    val modeNorm = mode.trim().lowercase(Locale.US).ifBlank { "osu" }
    val playerKey = remember(currentProfile?.userId, currentProfile?.username) {
        currentProfile?.userId?.takeIf { it > 0L }?.toString()
            ?: currentProfile?.username?.trim().orEmpty()
    }

    val merged = remember(snapshots, osuTrackPoints, currentProfile, modeNorm, selectedRange) {
        ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.RANK,
            days = selectedRange.days,
            mode = modeNorm,
            currentProfile = currentProfile,
            osuTrackPoints = osuTrackPoints,
            localSnapshots = snapshots
        )
    }
    val filteredPoints = merged.points

    var selectedIndex by remember(playerKey, modeNorm, selectedRange, filteredPoints.size) {
        mutableStateOf<Int?>(null)
    }
    var chartWidthPx by remember { mutableIntStateOf(0) }
    var tooltipWidthPx by remember { mutableIntStateOf(0) }

    val surfaces = LocalOsuSurfaces.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val hollowFillColor = MaterialTheme.colorScheme.surfaceContainer
    val tooltipSurfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val padXPx = remember(density) { with(density) { 8.dp.toPx() } }

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
                        text = "Rank history",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (filteredPoints.size >= 2) {
                        val first = filteredPoints.first()
                        val last = filteredPoints.last()
                        val diff = first.globalRank - last.globalRank
                        val sign = if (diff > 0) "+" else if (diff < 0) "-" else ""
                        val deltaText = "$sign${numFmt.format(abs(diff))} ranks"
                        val deltaColor = when {
                            diff > 0 -> StatusOnline
                            diff < 0 -> StatusError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = deltaColor
                        )
                    } else if (filteredPoints.size == 1) {
                        val only = filteredPoints.first()
                        Text(
                            text = "Baseline #${numFmt.format(only.globalRank)}",
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            color = accentColor,
                            fontWeight = FontWeight.Medium
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
                    val bestRank = filteredPoints.minOf { it.globalRank }
                    val worstRank = filteredPoints.maxOf { it.globalRank }
                    val rangeSummary = if (filteredPoints.size == 1) {
                        "#${numFmt.format(bestRank)}"
                    } else {
                        "#${numFmt.format(worstRank)} – #${numFmt.format(bestRank)}"
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
                val xRatios = remember(filteredPoints) {
                    if (filteredPoints.size <= 1) {
                        listOf(0.5f)
                    } else {
                        val minTs = filteredPoints.first().timestampMs
                        val maxTs = filteredPoints.last().timestampMs
                        val tsSpan = (maxTs - minTs).takeIf { it > 0L }
                        filteredPoints.indices.map { idx ->
                            if (tsSpan != null) {
                                ((filteredPoints[idx].timestampMs - minTs).toDouble() / tsSpan.toDouble())
                                    .toFloat()
                                    .let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
                            } else {
                                idx / (filteredPoints.size - 1).toFloat()
                            }
                        }
                    }
                }

                val activeIndex = selectedIndex?.takeIf { it in filteredPoints.indices }
                val activePoint = activeIndex?.let { filteredPoints[it] }

                val findNearestIndex: (Float) -> Int = { touchX ->
                    if (xRatios.size <= 1 || chartWidthPx <= 0) {
                        0
                    } else {
                        val plotW = (chartWidthPx.toFloat() - padXPx * 2f).coerceAtLeast(1f)
                        val touchRatio = ((touchX - padXPx) / plotW).coerceIn(0f, 1f)
                        var bestIdx = 0
                        var bestDist = Float.MAX_VALUE
                        for (i in xRatios.indices) {
                            val d = abs(xRatios[i] - touchRatio)
                            if (d < bestDist) {
                                bestDist = d
                                bestIdx = i
                            }
                        }
                        bestIdx
                    }
                }

                val todayUtc = remember(filteredPoints) { LocalDate.now(ZoneOffset.UTC) }

                Column(verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (activePoint != null && activeIndex != null) {
                            val pointX = if (chartWidthPx > 0) {
                                val plotW = (chartWidthPx.toFloat() - padXPx * 2f).coerceAtLeast(1f)
                                padXPx + plotW * xRatios[activeIndex]
                            } else {
                                0f
                            }
                            val maxOffsetX = (chartWidthPx - tooltipWidthPx).coerceAtLeast(0)
                            val clampedX = (pointX - tooltipWidthPx / 2f)
                                .roundToInt()
                                .coerceIn(0, maxOffsetX)

                            val pointDate = runCatching { LocalDate.parse(activePoint.dayKey) }.getOrNull()
                            val daysAgo = if (pointDate != null) {
                                ChronoUnit.DAYS.between(pointDate, todayUtc).toInt().coerceAtLeast(0)
                            } else {
                                (filteredPoints.lastIndex - activeIndex).coerceAtLeast(0)
                            }
                            val relativeDayLabel = when (daysAgo) {
                                0 -> "Today"
                                1 -> "Yesterday"
                                else -> "$daysAgo days ago"
                            }
                            val shortDate = formatDayKey(activePoint.dayKey, dateOutFmt)
                            val secondaryText = if (shortDate.isNotBlank() && shortDate != activePoint.dayKey) {
                                "$relativeDayLabel · $shortDate"
                            } else {
                                relativeDayLabel
                            }

                            Surface(
                                shape = RoundedCornerShape(OsuRadius.md),
                                color = tooltipSurfaceColor,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .onSizeChanged { tooltipWidthPx = it.width }
                                    .offset { IntOffset(clampedX, 0) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = OsuSpacing.md,
                                        vertical = OsuSpacing.xs
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                                ) {
                                    Text(
                                        text = "Global Ranking #${numFmt.format(activePoint.globalRank)}",
                                        style = MaterialTheme.typography.labelLarge.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = secondaryText,
                                        style = MaterialTheme.typography.labelSmall.tnum(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Tap or drag along graph to inspect daily rank",
                                style = MaterialTheme.typography.labelSmall,
                                color = surfaces.textMuted
                            )
                        }
                    }

                    val reusableLinePath = remember { Path() }
                    val reusableAreaPath = remember { Path() }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .onSizeChanged { chartWidthPx = it.width }
                            .pointerInput(xRatios, chartWidthPx) {
                                detectTapGestures { offset ->
                                    val tappedIdx = findNearestIndex(offset.x)
                                    selectedIndex = if (selectedIndex == tappedIdx) null else tappedIdx
                                }
                            }
                            .pointerInput(xRatios, chartWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        selectedIndex = findNearestIndex(offset.x)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        selectedIndex = findNearestIndex(change.position.x)
                                    }
                                )
                            }
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

                        if (filteredPoints.size == 1) {
                            val cy = padY + plotH / 2f
                            val cx = size.width / 2f
                            drawLine(
                                color = accentColor.copy(alpha = 0.3f),
                                start = Offset(padX, cy),
                                end = Offset(size.width - padX, cy),
                                strokeWidth = 1.dp.toPx()
                            )
                            if (activeIndex == 0) {
                                drawLine(
                                    color = accentColor,
                                    start = Offset(cx, 0f),
                                    end = Offset(cx, padY + plotH),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawCircle(
                                    color = hollowFillColor,
                                    radius = 5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = 5.dp.toPx(),
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            } else {
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
                            }
                        } else {
                            val values = filteredPoints.map { it.globalRank.toDouble() }
                            val finiteVals = values.filter { it.isFinite() }
                            if (finiteVals.size < 2) return@Canvas
                            val minVal = finiteVals.minOrNull() ?: 0.0
                            val maxVal = finiteVals.maxOrNull() ?: 0.0
                            val rawSpan = maxVal - minVal
                            val isFlat = !rawSpan.isFinite() || rawSpan <= 1e-6
                            val span = if (isFlat) 1.0 else rawSpan

                            val coords = values.mapIndexed { idx, rawVal ->
                                val v = if (rawVal.isFinite()) rawVal else minVal
                                val x = (padX + plotW * xRatios[idx]).coerceIn(padX, padX + plotW)
                                val y = if (isFlat) {
                                    padY + plotH / 2f
                                } else {
                                    val rawNorm = ((v - minVal) / span).toFloat()
                                    val yRatio = if (rawNorm.isFinite()) rawNorm.coerceIn(0f, 1f) else 0.5f
                                    (padY + plotH * yRatio).coerceIn(padY, padY + plotH)
                                }
                                Offset(x, y)
                            }

                            val linePath = reusableLinePath.apply {
                                rewind()
                                coords.forEachIndexed { idx, pt ->
                                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                                }
                            }

                            val bottomY = padY + plotH
                            val areaPath = reusableAreaPath.apply {
                                rewind()
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
                                    if (idx == activeIndex) return@forEachIndexed
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
                                val endpoints = listOf(0 to false, coords.lastIndex to true)
                                endpoints.forEach { (idx, isLast) ->
                                    if (idx == activeIndex) return@forEach
                                    val pt = coords[idx]
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

                            if (activeIndex != null && activeIndex in coords.indices) {
                                val selectedPt = coords[activeIndex]
                                drawLine(
                                    color = accentColor,
                                    start = Offset(selectedPt.x, 0f),
                                    end = Offset(selectedPt.x, bottomY),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawCircle(
                                    color = hollowFillColor,
                                    radius = 5.dp.toPx(),
                                    center = selectedPt
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = 5.dp.toPx(),
                                    center = selectedPt,
                                    style = Stroke(width = 2.5.dp.toPx())
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
                    merged.usesOfficialRankHistory -> "90-day global rank history via osu! API"
                    merged.usesOsuTrack -> "Rank history via osu!track & local snapshots"
                    else -> "Snapshots record automatically when viewing your profile or syncing the widget"
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
