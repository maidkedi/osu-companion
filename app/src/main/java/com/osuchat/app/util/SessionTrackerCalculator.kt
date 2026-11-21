package com.osuchat.app.util

import com.osuchat.app.domain.model.DailySessionSummary
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

object SessionTrackerCalculator {

    private const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L

    fun filterLast24Hours(
        scores: List<OsuScoreItem>,
        nowMs: Long = System.currentTimeMillis()
    ): List<OsuScoreItem> {
        val cutoff = nowMs - TWENTY_FOUR_HOURS_MS
        return scores.filter { score ->
            val createdMs = parseIsoTimestampMs(score.createdAt) ?: return@filter false
            createdMs in cutoff..(nowMs + 300_000L)
        }
    }

    fun computeSummary(
        mode: String,
        scores24h: List<OsuScoreItem>,
        currentProfile: PlayerProfile?,
        historyPoints: List<ProfileHistoryPoint>,
        nowMs: Long = System.currentTimeMillis()
    ): DailySessionSummary {
        val totalPlays = scores24h.size
        val passCount = scores24h.count { it.passed }
        val failCount = (totalPlays - passCount).coerceAtLeast(0)
        val passRatePercent = if (totalPlays > 0) {
            (passCount.toDouble() / totalPlays.toDouble()) * 100.0
        } else {
            0.0
        }

        val accValues = scores24h.mapNotNull { s ->
            s.accuracy.takeIf { it.isFinite() && it in 0.0..100.0 }
        }
        val avgAcc = if (accValues.isNotEmpty()) accValues.average() else 0.0

        val estPlaytimeSec = scores24h.sumOf { score ->
            val mapLen = score.beatmapLengthSeconds.takeIf { it > 0 } ?: 120
            if (score.passed) mapLen else (mapLen * 0.45).roundToInt().coerceAtLeast(25)
        }

        val highestPpPlay = scores24h
            .filter { it.passed }
            .maxWithOrNull(
                compareBy<OsuScoreItem> {
                    it.pp?.takeIf { p -> p.isFinite() && p >= 0.0 } ?: -1.0
                }.thenBy { it.accuracy.takeIf { a -> a.isFinite() } ?: -1.0 }
                    .thenBy { it.score }
            ) ?: scores24h.maxByOrNull { it.accuracy.takeIf { a -> a.isFinite() } ?: -1.0 }

        val todayUtc = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        val baseline = selectBaselineSnapshot(historyPoints, todayUtc)
        val rankDelta = if (currentProfile?.globalRank != null &&
            currentProfile.globalRank > 0 &&
            baseline != null &&
            baseline.globalRank > 0
        ) {
            baseline.globalRank - currentProfile.globalRank
        } else {
            null
        }
        val ppDelta = if (currentProfile?.pp != null &&
            currentProfile.pp.isFinite() &&
            baseline != null &&
            baseline.pp.isFinite()
        ) {
            currentProfile.pp - baseline.pp
        } else {
            null
        }

        return DailySessionSummary(
            mode = mode,
            totalPlays = totalPlays,
            passCount = passCount,
            failCount = failCount,
            passRatePercent = passRatePercent,
            averageAccuracy = avgAcc,
            estimatedPlaytimeSeconds = estPlaytimeSec,
            highestPpPlay = highestPpPlay,
            rankDelta = rankDelta,
            ppDelta = ppDelta,
            recentSessionPlays = scores24h
        )
    }

    private fun selectBaselineSnapshot(
        historyPoints: List<ProfileHistoryPoint>,
        todayUtc: LocalDate
    ): ProfileHistoryPoint? {
        if (historyPoints.isEmpty()) return null
        val yesterdayKey = todayUtc.minusDays(1).toString()
        val todayKey = todayUtc.toString()
        return historyPoints.lastOrNull { it.dayKey == yesterdayKey }
            ?: historyPoints.lastOrNull { it.dayKey < todayKey }
            ?: historyPoints.firstOrNull { it.dayKey == todayKey }
            ?: historyPoints.lastOrNull()
    }

    private fun parseIsoTimestampMs(raw: String?): Long? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(clean).toEpochMilli() }.getOrNull()
    }

    fun formatPlaytime(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val hrs = mins / 60
        val remMins = mins % 60
        return when {
            hrs > 0 -> "${hrs}h ${remMins}m"
            mins > 0 -> "${mins}m"
            safe > 0 -> "<1m"
            else -> "0m"
        }
    }
}
