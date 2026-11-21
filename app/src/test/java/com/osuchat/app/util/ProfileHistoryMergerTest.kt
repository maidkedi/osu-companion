package com.osuchat.app.util

import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.ProfileHistoryMetric
import com.osuchat.app.domain.model.ProfileHistoryPoint
import com.osuchat.app.domain.model.ProfileHistorySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProfileHistoryMergerTest {

    private val todayUtc: LocalDate = LocalDate.of(2026, 6, 15)
    private val nowMs: Long = todayUtc.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun utcMs(daysAgo: Long, hour: Int = 12): Long {
        return todayUtc.minusDays(daysAgo).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun sampleProfile(
        mode: String = "osu",
        pp: Double? = 5_250.0,
        globalRank: Int? = 14_800,
        rankHistory: List<Int> = emptyList()
    ): PlayerProfile = PlayerProfile(
        userId = 12345L,
        username = "TestPlayer",
        avatarUrl = "https://a.ppy.sh/12345",
        globalRank = globalRank,
        countryRank = 420,
        pp = pp,
        accuracy = 98.7,
        playCount = 10_000,
        mode = mode,
        rankHistory = rankHistory
    )

    @Test
    fun mergePp_combinesOsuTrackLocalSnapshotsAndLiveProfile_dedupesSameUtcDay() {
        val osuTrack = listOf(
            // Two points on day -10; later hour should win
            OsuTrackPoint(timestampMs = utcMs(10, hour = 6), pp = 5_000.0, globalRank = 16_000),
            OsuTrackPoint(timestampMs = utcMs(10, hour = 20), pp = 5_025.0, globalRank = 15_900),
            // Day -4 on osu!track, older than local snapshot on same day
            OsuTrackPoint(timestampMs = utcMs(4, hour = 8), pp = 5_100.0, globalRank = 15_500),
            // Outside 30D window should be dropped
            OsuTrackPoint(timestampMs = utcMs(45, hour = 12), pp = 4_700.0, globalRank = 20_000)
        )
        val localSnapshots = listOf(
            ProfileHistoryPoint(
                dayKey = todayUtc.minusDays(4).toString(),
                mode = "osu",
                globalRank = 15_300,
                pp = 5_140.0,
                recordedAt = utcMs(4, hour = 16)
            ),
            // Different mode should be ignored
            ProfileHistoryPoint(
                dayKey = todayUtc.minusDays(2).toString(),
                mode = "taiko",
                globalRank = 900,
                pp = 2_000.0,
                recordedAt = utcMs(2, hour = 12)
            )
        )

        val merged = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.PP,
            days = 30,
            mode = "osu",
            currentProfile = sampleProfile(pp = 5_250.0, globalRank = 14_800),
            osuTrackPoints = osuTrack,
            localSnapshots = localSnapshots,
            nowMs = nowMs,
            todayUtc = todayUtc
        )

        assertTrue(merged.usesOsuTrack)
        assertFalse(merged.usesOfficialRankHistory)
        assertEquals(3, merged.points.size)
        assertEquals(todayUtc.minusDays(10).toString(), merged.points[0].dayKey)
        assertEquals(5_025.0, merged.points[0].pp, 1e-6)
        assertEquals(ProfileHistorySource.OSU_TRACK, merged.points[0].source)

        assertEquals(todayUtc.minusDays(4).toString(), merged.points[1].dayKey)
        assertEquals(5_140.0, merged.points[1].pp, 1e-6)
        assertEquals(ProfileHistorySource.LOCAL_SNAPSHOT, merged.points[1].source)

        assertEquals(todayUtc.toString(), merged.points[2].dayKey)
        assertEquals(5_250.0, merged.points[2].pp, 1e-6)
        assertEquals(ProfileHistorySource.CURRENT_LIVE, merged.points[2].source)
    }

    @Test
    fun mergePp_fallsBackToLocalSnapshotsAndLiveBaselineWhenOsuTrackEmpty() {
        val local = listOf(
            ProfileHistoryPoint(
                dayKey = todayUtc.minusDays(5).toString(),
                mode = "osu",
                globalRank = 15_400,
                pp = 5_080.0,
                recordedAt = utcMs(5, hour = 10)
            )
        )
        val merged = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.PP,
            days = 7,
            mode = "osu",
            currentProfile = sampleProfile(pp = 5_100.0, globalRank = 15_200),
            osuTrackPoints = emptyList(),
            localSnapshots = local,
            nowMs = nowMs,
            todayUtc = todayUtc
        )

        assertFalse(merged.usesOsuTrack)
        assertFalse(merged.usesOfficialRankHistory)
        assertEquals(2, merged.points.size)
        assertEquals(5_080.0, merged.points[0].pp, 1e-6)
        assertEquals(5_100.0, merged.points[1].pp, 1e-6)

        // With no snapshots and no osu!track, produces 1 live baseline point
        val baselineOnly = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.PP,
            days = 30,
            mode = "osu",
            currentProfile = sampleProfile(pp = 5_100.0, globalRank = 15_200),
            osuTrackPoints = emptyList(),
            localSnapshots = emptyList(),
            nowMs = nowMs,
            todayUtc = todayUtc
        )
        assertFalse(baselineOnly.usesOsuTrack)
        assertEquals(1, baselineOnly.points.size)
        assertEquals(5_100.0, baselineOnly.points[0].pp, 1e-6)
    }

    @Test
    fun mergeRank_mapsOfficialRankHistoryWithoutShiftingZerosAndSlicesWindow() {
        // 5-day history: [day-4=16000, day-3=0 (inactive/invalid), day-2=15500, day-1=15100, today=14900]
        val ranks = listOf(16_000, 0, 15_500, 15_100, 14_900)
        val profile = sampleProfile(globalRank = 14_800, rankHistory = ranks)

        val merged7d = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.RANK,
            days = 7,
            mode = "osu",
            currentProfile = profile,
            nowMs = nowMs,
            todayUtc = todayUtc
        )

        assertTrue(merged7d.usesOfficialRankHistory)
        assertFalse(merged7d.usesOsuTrack)
        assertEquals(4, merged7d.points.size)
        assertEquals(todayUtc.minusDays(4).toString(), merged7d.points[0].dayKey)
        assertEquals(16_000, merged7d.points[0].globalRank)
        // day-3 (0) skipped without shifting day-2
        assertEquals(todayUtc.minusDays(2).toString(), merged7d.points[1].dayKey)
        assertEquals(15_500, merged7d.points[1].globalRank)
        assertEquals(todayUtc.minusDays(1).toString(), merged7d.points[2].dayKey)
        assertEquals(15_100, merged7d.points[2].globalRank)
        // Today overwritten with live globalRank
        assertEquals(todayUtc.toString(), merged7d.points[3].dayKey)
        assertEquals(14_800, merged7d.points[3].globalRank)
        assertEquals(ProfileHistorySource.CURRENT_LIVE, merged7d.points[3].source)

        // 90-day array sliced to 7D contains exactly 7 days ending today
        val ninetyDays = (1..90).map { 20_000 - it * 50 }
        val sliced7d = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.RANK,
            days = 7,
            mode = "osu",
            currentProfile = sampleProfile(globalRank = 15_500, rankHistory = ninetyDays),
            nowMs = nowMs,
            todayUtc = todayUtc
        )
        assertEquals(7, sliced7d.points.size)
        assertEquals(todayUtc.minusDays(6).toString(), sliced7d.points.first().dayKey)
        assertEquals(todayUtc.toString(), sliced7d.points.last().dayKey)
    }

    @Test
    fun mergeRank_fallsBackToOsuTrackAndLocalWhenOfficialRankHistoryEmpty() {
        val osuTrack = listOf(
            OsuTrackPoint(timestampMs = utcMs(6, hour = 12), pp = 4_900.0, globalRank = 16_500),
            OsuTrackPoint(timestampMs = utcMs(2, hour = 12), pp = 5_000.0, globalRank = 15_900)
        )
        val merged = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.RANK,
            days = 30,
            mode = "osu",
            currentProfile = sampleProfile(globalRank = 15_600, rankHistory = emptyList()),
            osuTrackPoints = osuTrack,
            nowMs = nowMs,
            todayUtc = todayUtc
        )

        assertFalse(merged.usesOfficialRankHistory)
        assertTrue(merged.usesOsuTrack)
        assertEquals(3, merged.points.size)
        assertEquals(16_500, merged.points[0].globalRank)
        assertEquals(15_900, merged.points[1].globalRank)
        assertEquals(15_600, merged.points[2].globalRank)
    }

    @Test
    fun merge_ignoresCurrentProfileIfModeMismatches() {
        val osuProfile = sampleProfile(
            mode = "osu",
            pp = 8_000.0,
            globalRank = 5_000,
            rankHistory = listOf(5_200, 5_100, 5_000)
        )
        val taikoRankMerged = ProfileHistoryMerger.merge(
            metric = ProfileHistoryMetric.RANK,
            days = 30,
            mode = "taiko",
            currentProfile = osuProfile,
            nowMs = nowMs,
            todayUtc = todayUtc
        )
        assertTrue(taikoRankMerged.points.isEmpty())
        assertFalse(taikoRankMerged.usesOfficialRankHistory)
    }
}
