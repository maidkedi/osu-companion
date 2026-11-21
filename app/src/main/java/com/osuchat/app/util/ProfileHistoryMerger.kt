package com.osuchat.app.util

import androidx.compose.runtime.Immutable
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryMetric
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.ProfileHistorySource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Immutable
data class MergedProfileHistory(
    val points: List<ProfileHistoryPoint>,
    val usesOsuTrack: Boolean,
    val usesOfficialRankHistory: Boolean
)

/**
 * Pure merger combining osu!track history, official osu! API v2 rank_history, local Room snapshots,
 * and live current profile stats into chronological chart points.
 */
object ProfileHistoryMerger {

    fun merge(
        metric: ProfileHistoryMetric,
        days: Int,
        mode: String,
        currentProfile: PlayerProfile?,
        osuTrackPoints: List<OsuTrackPoint> = emptyList(),
        localSnapshots: List<ProfileHistoryPoint> = emptyList(),
        officialRankHistory: List<Int> = currentProfile?.rankHistory.orEmpty(),
        nowMs: Long = System.currentTimeMillis(),
        todayUtc: LocalDate = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
    ): MergedProfileHistory {
        val safeDays = days.coerceAtLeast(1)
        val normMode = mode.trim().lowercase(Locale.US).ifBlank { "osu" }
        val cutoffDate = todayUtc.minusDays((safeDays - 1).toLong())

        val matchingProfile = currentProfile?.takeIf {
            it.mode.isBlank() || it.mode.equals(normMode, ignoreCase = true)
        }
        val effectiveOfficialRanks = when {
            matchingProfile != null -> officialRankHistory
            currentProfile == null -> officialRankHistory
            else -> emptyList()
        }

        val livePp = RankPpMath.sanitizeFiniteNonNegative(matchingProfile?.pp)?.takeIf { it > 0.0 }
        val liveRank = RankPpMath.sanitizeRank(matchingProfile?.globalRank) ?: 0
        val liveCountryRank = RankPpMath.sanitizeRank(matchingProfile?.countryRank) ?: 0
        val liveAcc = RankPpMath.sanitizeFiniteNonNegative(matchingProfile?.accuracy) ?: 0.0
        val livePlayCount = (matchingProfile?.playCount ?: 0).coerceAtLeast(0)

        return when (metric) {
            ProfileHistoryMetric.PP -> mergePp(
                normMode = normMode,
                cutoffDate = cutoffDate,
                todayUtc = todayUtc,
                nowMs = nowMs,
                osuTrackPoints = osuTrackPoints,
                localSnapshots = localSnapshots,
                livePp = livePp,
                liveRank = liveRank,
                liveCountryRank = liveCountryRank,
                liveAcc = liveAcc,
                livePlayCount = livePlayCount
            )
            ProfileHistoryMetric.RANK -> mergeRank(
                normMode = normMode,
                cutoffDate = cutoffDate,
                todayUtc = todayUtc,
                nowMs = nowMs,
                officialRankHistory = effectiveOfficialRanks,
                osuTrackPoints = osuTrackPoints,
                localSnapshots = localSnapshots,
                livePp = livePp,
                liveRank = liveRank,
                liveCountryRank = liveCountryRank,
                liveAcc = liveAcc,
                livePlayCount = livePlayCount
            )
        }
    }

    private fun mergePp(
        normMode: String,
        cutoffDate: LocalDate,
        todayUtc: LocalDate,
        nowMs: Long,
        osuTrackPoints: List<OsuTrackPoint>,
        localSnapshots: List<ProfileHistoryPoint>,
        livePp: Double?,
        liveRank: Int,
        liveCountryRank: Int,
        liveAcc: Double,
        livePlayCount: Int
    ): MergedProfileHistory {
        val byDay = LinkedHashMap<LocalDate, ProfileHistoryPoint>()

        for (pt in osuTrackPoints) {
            if (pt.timestampMs <= 0L || !pt.pp.isFinite() || pt.pp <= 0.0) continue
            val day = Instant.ofEpochMilli(pt.timestampMs).atZone(ZoneOffset.UTC).toLocalDate()
            if (day.isBefore(cutoffDate) || day.isAfter(todayUtc)) continue
            val candidate = ProfileHistoryPoint(
                dayKey = day.toString(),
                mode = normMode,
                globalRank = RankPpMath.sanitizeRank(pt.globalRank) ?: 0,
                countryRank = 0,
                pp = pt.pp,
                accuracy = RankPpMath.sanitizeFiniteNonNegative(pt.accuracy) ?: 0.0,
                playCount = pt.playCount.coerceAtLeast(0),
                recordedAt = pt.timestampMs,
                source = ProfileHistorySource.OSU_TRACK
            )
            val existing = byDay[day]
            if (existing == null || candidate.recordedAt >= existing.recordedAt) {
                byDay[day] = candidate
            }
        }

        for (snap in localSnapshots) {
            if (!snap.mode.equals(normMode, ignoreCase = true)) continue
            if (!snap.pp.isFinite() || snap.pp <= 0.0) continue
            val day = parseDayKey(snap.dayKey) ?: continue
            if (day.isBefore(cutoffDate) || day.isAfter(todayUtc)) continue
            val ts = snap.recordedAt.takeIf { it > 0L }
                ?: day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val candidate = snap.copy(
                dayKey = day.toString(),
                mode = normMode,
                globalRank = RankPpMath.sanitizeRank(snap.globalRank) ?: 0,
                countryRank = RankPpMath.sanitizeRank(snap.countryRank) ?: 0,
                accuracy = RankPpMath.sanitizeFiniteNonNegative(snap.accuracy) ?: 0.0,
                playCount = snap.playCount.coerceAtLeast(0),
                recordedAt = ts,
                source = ProfileHistorySource.LOCAL_SNAPSHOT
            )
            val existing = byDay[day]
            if (existing == null || candidate.recordedAt >= existing.recordedAt) {
                byDay[day] = candidate
            }
        }

        if (livePp != null) {
            val todayExisting = byDay[todayUtc]
            byDay[todayUtc] = ProfileHistoryPoint(
                dayKey = todayUtc.toString(),
                mode = normMode,
                globalRank = liveRank,
                countryRank = liveCountryRank,
                pp = livePp,
                accuracy = liveAcc,
                playCount = livePlayCount,
                recordedAt = maxOf(nowMs, todayExisting?.recordedAt ?: 0L),
                source = ProfileHistorySource.CURRENT_LIVE
            )
        }

        val sorted = byDay.entries
            .sortedBy { it.key }
            .map { it.value }
        val usesOsuTrack = sorted.any { it.source == ProfileHistorySource.OSU_TRACK }
        return MergedProfileHistory(
            points = sorted,
            usesOsuTrack = usesOsuTrack,
            usesOfficialRankHistory = false
        )
    }

    private fun mergeRank(
        normMode: String,
        cutoffDate: LocalDate,
        todayUtc: LocalDate,
        nowMs: Long,
        officialRankHistory: List<Int>,
        osuTrackPoints: List<OsuTrackPoint>,
        localSnapshots: List<ProfileHistoryPoint>,
        livePp: Double?,
        liveRank: Int,
        liveCountryRank: Int,
        liveAcc: Double,
        livePlayCount: Int
    ): MergedProfileHistory {
        val officialByDay = LinkedHashMap<LocalDate, ProfileHistoryPoint>()
        val total = officialRankHistory.size
        for (i in officialRankHistory.indices) {
            val daysAgo = (total - 1 - i).toLong()
            val sanitizedRank = RankPpMath.sanitizeRank(officialRankHistory[i]) ?: continue
            val day = todayUtc.minusDays(daysAgo)
            if (day.isBefore(cutoffDate) || day.isAfter(todayUtc)) continue
            officialByDay[day] = ProfileHistoryPoint(
                dayKey = day.toString(),
                mode = normMode,
                globalRank = sanitizedRank,
                countryRank = 0,
                pp = 0.0,
                accuracy = 0.0,
                playCount = 0,
                recordedAt = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                source = ProfileHistorySource.OSU_API_RANK_HISTORY
            )
        }

        if (officialByDay.isNotEmpty()) {
            if (liveRank > 0) {
                val todayExisting = officialByDay[todayUtc]
                officialByDay[todayUtc] = ProfileHistoryPoint(
                    dayKey = todayUtc.toString(),
                    mode = normMode,
                    globalRank = liveRank,
                    countryRank = liveCountryRank,
                    pp = livePp ?: 0.0,
                    accuracy = liveAcc,
                    playCount = livePlayCount,
                    recordedAt = maxOf(nowMs, todayExisting?.recordedAt ?: 0L),
                    source = ProfileHistorySource.CURRENT_LIVE
                )
            }
            val sorted = officialByDay.entries
                .sortedBy { it.key }
                .map { it.value }
            return MergedProfileHistory(
                points = sorted,
                usesOsuTrack = false,
                usesOfficialRankHistory = true
            )
        }

        val fallbackByDay = LinkedHashMap<LocalDate, ProfileHistoryPoint>()
        for (pt in osuTrackPoints) {
            if (pt.timestampMs <= 0L) continue
            val rank = RankPpMath.sanitizeRank(pt.globalRank) ?: continue
            val day = Instant.ofEpochMilli(pt.timestampMs).atZone(ZoneOffset.UTC).toLocalDate()
            if (day.isBefore(cutoffDate) || day.isAfter(todayUtc)) continue
            val candidate = ProfileHistoryPoint(
                dayKey = day.toString(),
                mode = normMode,
                globalRank = rank,
                countryRank = 0,
                pp = RankPpMath.sanitizeFiniteNonNegative(pt.pp) ?: 0.0,
                accuracy = RankPpMath.sanitizeFiniteNonNegative(pt.accuracy) ?: 0.0,
                playCount = pt.playCount.coerceAtLeast(0),
                recordedAt = pt.timestampMs,
                source = ProfileHistorySource.OSU_TRACK
            )
            val existing = fallbackByDay[day]
            if (existing == null || candidate.recordedAt >= existing.recordedAt) {
                fallbackByDay[day] = candidate
            }
        }

        for (snap in localSnapshots) {
            if (!snap.mode.equals(normMode, ignoreCase = true)) continue
            val rank = RankPpMath.sanitizeRank(snap.globalRank) ?: continue
            val day = parseDayKey(snap.dayKey) ?: continue
            if (day.isBefore(cutoffDate) || day.isAfter(todayUtc)) continue
            val ts = snap.recordedAt.takeIf { it > 0L }
                ?: day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val candidate = snap.copy(
                dayKey = day.toString(),
                mode = normMode,
                globalRank = rank,
                countryRank = RankPpMath.sanitizeRank(snap.countryRank) ?: 0,
                pp = RankPpMath.sanitizeFiniteNonNegative(snap.pp) ?: 0.0,
                accuracy = RankPpMath.sanitizeFiniteNonNegative(snap.accuracy) ?: 0.0,
                playCount = snap.playCount.coerceAtLeast(0),
                recordedAt = ts,
                source = ProfileHistorySource.LOCAL_SNAPSHOT
            )
            val existing = fallbackByDay[day]
            if (existing == null || candidate.recordedAt >= existing.recordedAt) {
                fallbackByDay[day] = candidate
            }
        }

        if (liveRank > 0) {
            val todayExisting = fallbackByDay[todayUtc]
            fallbackByDay[todayUtc] = ProfileHistoryPoint(
                dayKey = todayUtc.toString(),
                mode = normMode,
                globalRank = liveRank,
                countryRank = liveCountryRank,
                pp = livePp ?: 0.0,
                accuracy = liveAcc,
                playCount = livePlayCount,
                recordedAt = maxOf(nowMs, todayExisting?.recordedAt ?: 0L),
                source = ProfileHistorySource.CURRENT_LIVE
            )
        }

        val sorted = fallbackByDay.entries
            .sortedBy { it.key }
            .map { it.value }
        val usesOsuTrack = sorted.any { it.source == ProfileHistorySource.OSU_TRACK }
        return MergedProfileHistory(
            points = sorted,
            usesOsuTrack = usesOsuTrack,
            usesOfficialRankHistory = false
        )
    }

    private fun parseDayKey(raw: String): LocalDate? {
        return runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
    }
}
