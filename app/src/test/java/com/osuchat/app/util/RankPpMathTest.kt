package com.osuchat.app.util

import com.osuchat.app.domain.model.OsuScoreItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class RankPpMathTest {

    @Test
    fun computeWeightedTopSum_filtersNonFiniteAndNegativeAndCapsAt100() {
        val scores = listOf(
            500.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -250.0,
            null,
            400.0
        ) + List(120) { 100.0 }

        val weighted = RankPpMath.computeWeightedTopSum(scores)
        val expected = 500.0 * 0.95.pow(0) +
            400.0 * 0.95.pow(1) +
            (2 until 100).sumOf { idx -> 100.0 * 0.95.pow(idx) }

        assertTrue(weighted.isFinite())
        assertEquals(expected, weighted, 1e-6)
    }

    @Test
    fun computeBonusPp_neverNegativeForUnrankedLowPlaycountOrNonFinite() {
        assertEquals(0.0, RankPpMath.computeBonusPp(null, 500.0), 0.0)
        assertEquals(0.0, RankPpMath.computeBonusPp(0.0, 500.0), 0.0)
        assertEquals(0.0, RankPpMath.computeBonusPp(450.0, 500.0), 0.0)
        assertEquals(0.0, RankPpMath.computeBonusPp(-100.0, 500.0), 0.0)
        assertEquals(0.0, RankPpMath.computeBonusPp(Double.NaN, 500.0), 0.0)
        assertEquals(0.0, RankPpMath.computeBonusPp(Double.POSITIVE_INFINITY, 500.0), 0.0)
        assertEquals(0.0, RankPpMath.computeBonusPp(500.0, Double.NaN), 1e-6)
        assertEquals(42.5, RankPpMath.computeBonusPp(542.5, 500.0), 1e-6)
    }

    @Test
    fun simulateWhatIf_handlesUnrankedAndRejectsInvalidScores() {
        // Unranked / 0-playcount player entering a 250pp score
        val unranked = RankPpMath.simulateWhatIf(
            currentTotalPp = 0.0,
            existingScores = emptyList(),
            hypotheticalPp = 250.0
        )
        assertNotNull(unranked)
        unranked!!
        assertEquals(0.0, unranked.bonusPp, 1e-6)
        assertEquals(250.0, unranked.netGain, 1e-6)
        assertEquals(250.0, unranked.newEstimatedTotal, 1e-6)
        assertEquals(1, unranked.hypotheticalRankInTop100)

        // Null total PP (e.g. unranked mode) with dirty score list
        val dirtyExisting = listOf(300.0, Double.NaN, -50.0, Double.POSITIVE_INFINITY)
        val fromNull = RankPpMath.simulateWhatIf(
            currentTotalPp = null,
            existingScores = dirtyExisting,
            hypotheticalPp = 400.0
        )
        assertNotNull(fromNull)
        fromNull!!
        val expectedNewWeighted = 400.0 + 300.0 * 0.95
        assertEquals(expectedNewWeighted, fromNull.newWeightedTop100, 1e-6)
        assertEquals(expectedNewWeighted - 300.0, fromNull.netGain, 1e-6)
        assertEquals(0.0, fromNull.bonusPp, 1e-6)
        assertEquals(1, fromNull.hypotheticalRankInTop100)

        // Invalid / non-finite / huge hypothetical scores are rejected
        assertNull(RankPpMath.simulateWhatIf(1000.0, emptyList(), 0.0))
        assertNull(RankPpMath.simulateWhatIf(1000.0, emptyList(), -10.0))
        assertNull(RankPpMath.simulateWhatIf(1000.0, emptyList(), Double.NaN))
        assertNull(RankPpMath.simulateWhatIf(1000.0, emptyList(), Double.POSITIVE_INFINITY))
        assertNull(RankPpMath.simulateWhatIf(1000.0, emptyList(), 100_000.01))
    }

    @Test
    fun estimateLocalFallbackRank_handlesRankZeroNegativeAndNonFinite() {
        assertNull(RankPpMath.estimateLocalFallbackRank(null, 5000.0, 5200.0))
        assertNull(RankPpMath.estimateLocalFallbackRank(0, 5000.0, 5200.0))
        assertNull(RankPpMath.estimateLocalFallbackRank(-5, 5000.0, 5200.0))
        assertNull(RankPpMath.estimateLocalFallbackRank(10_000, 0.0, 500.0))
        assertNull(RankPpMath.estimateLocalFallbackRank(10_000, Double.NaN, 500.0))
        assertNull(RankPpMath.estimateLocalFallbackRank(10_000, 5000.0, Double.POSITIVE_INFINITY))

        val projected = RankPpMath.estimateLocalFallbackRank(25_000, 5_000.0, 6_000.0)
        assertNotNull(projected)
        assertTrue(projected!! in 1..25_000)
    }

    @Test
    fun calibratedCurveAndConversions_guardOverflowNaNInfinityAndRankZero() {
        // Non-finite observed anchors fall back cleanly to mode defaults
        val fallbackCurve = RankPpMath.buildCalibratedCurve(
            mode = "osu",
            observedPp1k = Double.POSITIVE_INFINITY,
            observedPp10k = Double.NaN
        )
        assertTrue(fallbackCurve.pp1000.isFinite())
        assertTrue(fallbackCurve.pp10000.isFinite())
        assertTrue(fallbackCurve.topSlope in 0.18..0.45)
        assertTrue(fallbackCurve.tailExponent in 0.28..0.52)

        // Rank 0 / negative clamps to #1; huge rank clamps to MAX_RANK
        val rankZeroPp = RankPpMath.estimatePpFromCurve(0, fallbackCurve)
        val rankNegPp = RankPpMath.estimatePpFromCurve(-99, fallbackCurve)
        val rankOnePp = RankPpMath.estimatePpFromCurve(1, fallbackCurve)
        assertEquals(rankOnePp, rankZeroPp, 1e-6)
        assertEquals(rankOnePp, rankNegPp, 1e-6)
        assertTrue(rankOnePp.isFinite() && rankOnePp > fallbackCurve.pp1000)

        val maxRankPp = RankPpMath.estimatePpFromCurve(Int.MAX_VALUE, fallbackCurve)
        assertTrue(maxRankPp.isFinite() && maxRankPp > 0.0)

        // Tiny PP (1.0) would exceed Int.MAX_VALUE without pre-clamp: must stay within 1..MAX_RANK
        val tinyPpRank = RankPpMath.estimateRankFromCurve(1.0, fallbackCurve)
        assertEquals(RankPpMath.MAX_RANK, tinyPpRank)

        // NaN, Infinity, -Infinity, huge PP never throw and stay in 1..MAX_RANK
        val nanRank = RankPpMath.estimateRankFromCurve(Double.NaN, fallbackCurve)
        val posInfRank = RankPpMath.estimateRankFromCurve(Double.POSITIVE_INFINITY, fallbackCurve)
        val negInfRank = RankPpMath.estimateRankFromCurve(Double.NEGATIVE_INFINITY, fallbackCurve)
        val hugePpRank = RankPpMath.estimateRankFromCurve(10_000_000.0, fallbackCurve)

        assertTrue(nanRank in RankPpMath.MIN_RANK..RankPpMath.MAX_RANK)
        assertEquals(1, posInfRank)
        assertEquals(RankPpMath.MAX_RANK, negInfRank)
        assertEquals(1, hugePpRank)

        // Invalid hints (rank 0, NaN/Infinity PP) do not poison result
        val badHintRank = RankPpMath.estimateRankFromCurve(
            targetPp = 7_500.0,
            curve = fallbackCurve,
            currentRankHint = 0,
            currentPpHint = Double.POSITIVE_INFINITY
        )
        assertTrue(badHintRank in 1_000..10_000)
    }

    @Test
    fun safeRoundRank_handlesNaNInfinityAndLargeDoublesWithoutOverflow() {
        assertEquals(RankPpMath.MAX_RANK, RankPpMath.safeRoundRank(Double.NaN))
        assertEquals(RankPpMath.MAX_RANK, RankPpMath.safeRoundRank(Double.POSITIVE_INFINITY))
        assertEquals(RankPpMath.MIN_RANK, RankPpMath.safeRoundRank(Double.NEGATIVE_INFINITY))
        assertEquals(RankPpMath.MAX_RANK, RankPpMath.safeRoundRank(1e30))
        assertEquals(RankPpMath.MIN_RANK, RankPpMath.safeRoundRank(-500.0))
    }

    @Test
    fun interpolateRankInPage_handlesNaNAndZeroSpan() {
        val entries = listOf(
            1 to 20_000.0,
            2 to Double.NaN,
            3 to 18_000.0,
            4 to 18_000.0
        )
        // Zero span between rank 3 and 4
        val flatRank = RankPpMath.interpolateRankInPage(
            targetPp = 18_000.0,
            entries = entries,
            page = 1,
            fallbackRank = 3
        )
        assertTrue(flatRank in 3..4)

        // NaN target PP never throws
        val nanInterp = RankPpMath.interpolateRankInPage(
            targetPp = Double.NaN,
            entries = entries,
            page = 1,
            fallbackRank = 1
        )
        assertTrue(nanInterp in 1..10_000)
    }

    @Test
    fun inputParsers_handleEmptyInvalidCommasOverflowAndNonFinite() {
        assertNull(RankPpMath.parsePositiveRankInput(""))
        assertNull(RankPpMath.parsePositiveRankInput("0"))
        assertNull(RankPpMath.parsePositiveRankInput("-42"))
        assertNull(RankPpMath.parsePositiveRankInput("abc"))
        assertEquals(10_000, RankPpMath.parsePositiveRankInput(" #10,000 "))
        assertEquals(RankPpMath.MAX_RANK, RankPpMath.parsePositiveRankInput("999999999999"))

        assertNull(RankPpMath.parsePositivePpInput(""))
        assertNull(RankPpMath.parsePositivePpInput("0"))
        assertNull(RankPpMath.parsePositivePpInput("-100"))
        assertNull(RankPpMath.parsePositivePpInput("NaN"))
        assertNull(RankPpMath.parsePositivePpInput("Infinity"))
        assertEquals(450.5, RankPpMath.parsePositivePpInput("450,5")!!, 1e-6)
        assertEquals(8500.0, RankPpMath.parsePositivePpInput("8,500")!!, 1e-6)
        assertEquals(8500.25, RankPpMath.parsePositivePpInput("8,500.25")!!, 1e-6)
        assertNull(RankPpMath.parsePositivePpInput("1000001"))
    }

    @Test
    fun sanitizeAndClampHelpers_enforceFiniteBounds() {
        assertNull(RankPpMath.sanitizeFiniteNonNegative(null))
        assertNull(RankPpMath.sanitizeFiniteNonNegative(-0.01))
        assertNull(RankPpMath.sanitizeFiniteNonNegative(Double.NaN))
        assertEquals(0.0, RankPpMath.sanitizeFiniteNonNegative(0.0)!!, 0.0)

        assertNull(RankPpMath.sanitizeFinitePositive(0.0))
        assertNull(RankPpMath.sanitizeFinitePositive(-5.0))
        assertNull(RankPpMath.sanitizeFinitePositive(Double.POSITIVE_INFINITY))
        assertEquals(12.5, RankPpMath.sanitizeFinitePositive(12.5)!!, 0.0)

        assertNull(RankPpMath.sanitizeRank(null))
        assertNull(RankPpMath.sanitizeRank(0))
        assertNull(RankPpMath.sanitizeRank(-1))
        assertEquals(RankPpMath.MAX_RANK, RankPpMath.sanitizeRank(Int.MAX_VALUE))

        assertEquals(RankPpMath.MIN_RANK, RankPpMath.clampRank(0))
        assertEquals(RankPpMath.MAX_RANK, RankPpMath.clampRank(Long.MAX_VALUE))
        assertEquals(RankPpMath.MIN_RANK, RankPpMath.clampRank(-10L))

        assertEquals(RankPpMath.MIN_TOTAL_PP, RankPpMath.clampTotalPp(Double.NaN), 0.0)
        assertEquals(RankPpMath.MAX_TOTAL_PP, RankPpMath.clampTotalPp(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(RankPpMath.MIN_TOTAL_PP, RankPpMath.clampTotalPp(Double.NEGATIVE_INFINITY), 0.0)
        assertEquals(RankPpMath.MIN_TOTAL_PP, RankPpMath.clampTotalPp(0.1), 0.0)
        assertEquals(RankPpMath.MAX_TOTAL_PP, RankPpMath.clampTotalPp(5_000_000.0), 0.0)
    }

    @Test
    fun extractTopScoresAndWeightedSum_sortUnsortedScoresAndRespectLimit() {
        assertTrue(RankPpMath.extractTopScores(listOf(100.0, 200.0), limit = 0).isEmpty())
        assertEquals(0.0, RankPpMath.computeWeightedTopSum(emptyList()), 0.0)
        assertEquals(0.0, RankPpMath.computeWeightedTopSum(listOf(300.0), limit = 0), 0.0)

        val unsorted = listOf(100.0, 400.0, 0.0, 250.0)
        assertEquals(listOf(400.0, 250.0, 100.0), RankPpMath.extractTopScores(unsorted, limit = 3))

        val expected = 400.0 + 250.0 * 0.95 + 100.0 * 0.95.pow(2)
        assertEquals(expected, RankPpMath.computeWeightedTopSum(unsorted, limit = 3), 1e-6)
    }

    @Test
    fun simulateWhatIf_handlesFull100ListDisplacementsBonusPpAndMaxBoundary() {
        val top100 = (1..100).map { (101 - it) * 10.0 } // 1000.0 down to 10.0
        val currentWeighted = RankPpMath.computeWeightedTopSum(top100)
        val bonus = 250.0
        val currentTotal = currentWeighted + bonus

        // Score smaller than #100 (10.0pp) yields zero gain and null top-100 placement
        val pushedOut = RankPpMath.simulateWhatIf(currentTotal, top100, 5.0)
        assertNotNull(pushedOut)
        pushedOut!!
        assertEquals(0.0, pushedOut.netGain, 1e-9)
        assertNull(pushedOut.hypotheticalRankInTop100)
        assertEquals(100, pushedOut.updatedTopCount)
        assertEquals(bonus, pushedOut.bonusPp, 1e-6)
        assertEquals(currentTotal, pushedOut.newEstimatedTotal, 1e-6)

        // Score beating #1 (1200.0pp) places #1 and preserves bonus PP
        val newTop1 = RankPpMath.simulateWhatIf(currentTotal, top100, 1200.0)!!
        assertEquals(1, newTop1.hypotheticalRankInTop100)
        assertEquals(100, newTop1.updatedTopCount)
        assertTrue(newTop1.netGain > 0.0)
        assertEquals(currentTotal + newTop1.netGain, newTop1.newEstimatedTotal, 1e-6)

        // Exact MAX_WHAT_IF_SCORE_PP boundary is accepted
        val maxBoundary = RankPpMath.simulateWhatIf(1000.0, emptyList(), RankPpMath.MAX_WHAT_IF_SCORE_PP)
        assertNotNull(maxBoundary)
    }

    @Test
    fun estimateLocalFallbackRank_handlesRegressionAndTop10kVsTailExponents() {
        // When new PP <= current PP, rank does not improve past currentGlobalRank
        assertEquals(5_000, RankPpMath.estimateLocalFallbackRank(5_000, 8_000.0, 7_500.0))
        assertEquals(1, RankPpMath.estimateLocalFallbackRank(1, 20_000.0, 22_000.0))

        // Rank <= 10k uses steeper exponent (4.1) than rank > 10k (2.7)
        val top10kRatio = RankPpMath.estimateLocalFallbackRank(10_000, 5_000.0, 5_500.0)!! / 10_000.0
        val tailRatio = RankPpMath.estimateLocalFallbackRank(20_000, 5_000.0, 5_500.0)!! / 20_000.0
        assertTrue(top10kRatio < tailRatio)
    }

    @Test
    fun calibratedCurve_coversAllModesMonotonicityAndHintWindow() {
        val osu = RankPpMath.buildCalibratedCurve("osu")
        val taiko = RankPpMath.buildCalibratedCurve(" TAIKO ")
        val fruits = RankPpMath.buildCalibratedCurve("fruits")
        val catchAlias = RankPpMath.buildCalibratedCurve("catch")
        val ctbAlias = RankPpMath.buildCalibratedCurve(" CTB ")
        val mania = RankPpMath.buildCalibratedCurve("mania")
        val unknown = RankPpMath.buildCalibratedCurve("unknown_mode")

        assertEquals(osu, unknown)
        assertEquals(fruits, catchAlias)
        assertEquals(fruits, ctbAlias)
        assertTrue(taiko.pp1000 < osu.pp1000)
        assertTrue(fruits.pp10000 < mania.pp10000)

        // Invalid custom anchors (v1k <= v10k or v10k <= 50) fall back to mode defaults
        val invalidRelation = RankPpMath.buildCalibratedCurve("osu", observedPp1k = 4_000.0, observedPp10k = 5_000.0)
        assertEquals(osu, invalidRelation)
        val tinyAnchor = RankPpMath.buildCalibratedCurve("osu", observedPp1k = 500.0, observedPp10k = 50.0)
        assertEquals(osu, tinyAnchor)

        // Valid custom anchors are used and clamped
        val custom = RankPpMath.buildCalibratedCurve("osu", observedPp1k = 14_000.0, observedPp10k = 7_500.0)
        assertEquals(14_000.0, custom.pp1000, 1e-6)
        assertEquals(7_500.0, custom.pp10000, 1e-6)

        // Monotonically decreasing PP as rank increases across all segments
        val ranks = listOf(1, 100, 1_000, 5_000, 10_000, 50_000, 500_000)
        val pps = ranks.map { RankPpMath.estimatePpFromCurve(it, custom) }
        for (i in 0 until pps.lastIndex) {
            assertTrue("Expected PP at #${ranks[i]} > #${ranks[i + 1]}", pps[i] > pps[i + 1])
        }

        // Corrupted curve object is sanitized cleanly without NaN
        val dirtyCurve = RankPpMath.CalibratedRankCurve(
            pp1000 = Double.NaN,
            pp10000 = -10.0,
            topSlope = -1.0,
            tailExponent = Double.POSITIVE_INFINITY
        )
        val safePp = RankPpMath.estimatePpFromCurve(1_000, dirtyCurve)
        assertTrue(safePp.isFinite() && safePp > 0.0)

        // Hint outside [0.5x, 2.0x] is ignored; hint inside [0.5x, 2.0x] for rank > 10k anchors locally
        val localHintRank = RankPpMath.estimateRankFromCurve(
            targetPp = 4_500.0,
            curve = osu,
            currentRankHint = 80_000,
            currentPpHint = 4_000.0
        )
        assertTrue(localHintRank in 10_001 until 80_000)

        val ignoredFarHintRank = RankPpMath.estimateRankFromCurve(
            targetPp = 15_000.0,
            curve = osu,
            currentRankHint = 80_000,
            currentPpHint = 4_000.0
        )
        assertTrue(ignoredFarHintRank in 1..1_000)
    }

    @Test
    fun interpolateRankInPage_usesPageOffsetWhenEntryRanksNullAndFallsBackOutOfRange() {
        val pageEntries = listOf<Pair<Int?, Double?>>(
            null to 10_000.0,
            null to 9_000.0
        )
        // Page 2 -> implicit ranks 51 and 52; midpoint 9500 -> ~51.5 -> 52
        val interp = RankPpMath.interpolateRankInPage(
            targetPp = 9_500.0,
            entries = pageEntries,
            page = 2,
            fallbackRank = 51
        )
        assertTrue(interp in 51..52)

        // Out of range clamps fallbackRank into 1..10_000
        val outOfRange = RankPpMath.interpolateRankInPage(
            targetPp = 1_000.0,
            entries = pageEntries,
            page = 999,
            fallbackRank = 25_000
        )
        assertEquals(10_000, outOfRange)
    }

    @Test
    fun computeTopPlayAnalytics_computesModDistributionAveragesAndPpCliff() {
        assertNull(RankPpMath.computeTopPlayAnalytics(emptyList()))

        val scores = listOf(
            OsuScoreItem(id = 1, userId = 1, username = "u", rank = "S", pp = 500.0, accuracy = 99.0, maxCombo = 500, mods = listOf("HD", "DT"), starRating = 7.2, beatmapLengthSeconds = 120),
            OsuScoreItem(id = 2, userId = 1, username = "u", rank = "S", pp = 400.0, accuracy = 98.0, maxCombo = 400, mods = emptyList(), starRating = 6.2, beatmapLengthSeconds = 180),
            OsuScoreItem(id = 3, userId = 1, username = "u", rank = "A", pp = 300.0, accuracy = 97.0, maxCombo = 300, mods = listOf("HD", "HR"), starRating = 6.4, beatmapLengthSeconds = 150),
            OsuScoreItem(id = 4, userId = 1, username = "u", rank = "S", pp = 200.0, accuracy = 100.0, maxCombo = 200, mods = listOf("FL"), starRating = 5.0, beatmapLengthSeconds = 90)
        )
        val analytics = RankPpMath.computeTopPlayAnalytics(scores)!!
        assertEquals(4, analytics.playCount)
        assertEquals(98.5, analytics.averageAccuracy, 1e-6)
        assertEquals(135, analytics.averageLengthSeconds)
        assertEquals(6.2, analytics.averageStarRating!!, 1e-6)
        assertEquals(500.0, analytics.top1Pp, 1e-6)
        assertEquals(200.0, analytics.tailPp, 1e-6)
        assertEquals(300.0, analytics.ppSpread, 1e-6)
        assertTrue(analytics.modDistribution.any { it.label == "HDDT" && it.percentage == 25.0 })
        assertTrue(analytics.modDistribution.any { it.label == "NM" && it.percentage == 25.0 })
        assertTrue(analytics.modDistribution.any { it.label == "HDHR" && it.percentage == 25.0 })
        assertTrue(analytics.modDistribution.any { it.label == "Other" && it.percentage == 25.0 })
    }

    @Test
    fun calculateTargetRankPlan_computesWeightingAwarePlayCountsAndCap() {
        val top100 = (1..100).map { 300.0 - it * 1.5 } // #1 = 298.5, #100 = 150.0
        val weighted = RankPpMath.computeWeightedTopSum(top100)
        val total = weighted + 200.0

        val plan = RankPpMath.calculateTargetRankPlan(
            targetRank = 10_000,
            targetPp = total + 400.0,
            currentTotalPp = total,
            existingScores = top100,
            maxPlaysCap = 50
        )!!
        assertEquals(400.0, plan.ppGap, 1e-6)
        assertEquals(200.0, plan.bonusPp, 1e-6)
        assertEquals(3, plan.scenarios.size)
        // Higher PP scenario requires fewer or equal plays
        assertTrue(plan.scenarios[2].playsNeeded <= plan.scenarios[0].playsNeeded)
    }
}
