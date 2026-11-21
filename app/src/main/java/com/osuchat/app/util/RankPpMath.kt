package com.osuchat.app.util

import com.osuchat.app.domain.model.OsuScoreItem
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure-Kotlin math helpers for osu! PP weighting, What-If score simulation,
 * bonus PP clamping, and bidirectional Rank <-> PP curve estimation.
 *
 * All public functions guard against NaN, Infinity, negative values, zero-division,
 * and Int overflow when converting Double ranks.
 */
object RankPpMath {

    const val MIN_RANK = 1
    const val MAX_RANK = 100_000_000
    const val TOP_SCORES_LIMIT = 100
    const val WEIGHT_BASE = 0.95
    const val MAX_WHAT_IF_SCORE_PP = 100_000.0
    const val MIN_TOTAL_PP = 1.0
    const val MAX_TOTAL_PP = 1_000_000.0

    data class CalibratedRankCurve(
        val pp1000: Double,
        val pp10000: Double,
        val topSlope: Double,
        val tailExponent: Double
    )

    data class WhatIfPpSimulation(
        val currentWeightedTop100: Double,
        val newWeightedTop100: Double,
        val netGain: Double,
        val bonusPp: Double,
        val newEstimatedTotal: Double,
        val hypotheticalRankInTop100: Int?,
        val updatedTopCount: Int
    )

    data class ModDistributionEntry(
        val label: String,
        val count: Int,
        val percentage: Double
    )

    data class TopPlayAnalytics(
        val playCount: Int,
        val modDistribution: List<ModDistributionEntry>,
        val averageAccuracy: Double,
        val averageLengthSeconds: Int?,
        val averageStarRating: Double?,
        val top1Pp: Double,
        val tailPp: Double,
        val ppSpread: Double
    )

    data class TargetRankScenario(
        val label: String,
        val scorePp: Double,
        val playsNeeded: Int,
        val cappedAtLimit: Boolean,
        val resultingTotalPp: Double
    )

    data class TargetRankPlan(
        val targetRank: Int,
        val targetPp: Double,
        val currentTotalPp: Double,
        val ppGap: Double,
        val bonusPp: Double,
        val scenarios: List<TargetRankScenario>
    )

    fun sanitizeFiniteNonNegative(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it >= 0.0 }

    fun sanitizeFinitePositive(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it > 0.0 }

    fun sanitizeRank(rank: Int?): Int? =
        rank?.takeIf { it >= MIN_RANK }?.coerceAtMost(MAX_RANK)

    fun clampRank(rank: Int): Int =
        rank.coerceIn(MIN_RANK, MAX_RANK)

    fun clampRank(rank: Long): Int =
        rank.coerceIn(MIN_RANK.toLong(), MAX_RANK.toLong()).toInt()

    /**
     * Rounds a floating-point rank into [minRank, maxRank] without throwing on NaN/Infinity
     * or overflowing Int.MAX_VALUE.
     */
    fun safeRoundRank(
        value: Double,
        minRank: Int = MIN_RANK,
        maxRank: Int = MAX_RANK
    ): Int {
        val safeMin = minRank.coerceIn(MIN_RANK, MAX_RANK)
        val safeMax = maxRank.coerceIn(safeMin, MAX_RANK)
        if (value.isNaN()) return safeMax
        if (value == Double.POSITIVE_INFINITY) return safeMax
        if (value == Double.NEGATIVE_INFINITY) return safeMin
        return value.coerceIn(safeMin.toDouble(), safeMax.toDouble()).roundToInt()
    }

    fun clampTotalPp(pp: Double): Double = when {
        pp.isNaN() -> MIN_TOTAL_PP
        pp == Double.POSITIVE_INFINITY -> MAX_TOTAL_PP
        pp == Double.NEGATIVE_INFINITY -> MIN_TOTAL_PP
        else -> pp.coerceIn(MIN_TOTAL_PP, MAX_TOTAL_PP)
    }

    /**
     * Parses a user-entered rank string (allowing commas/spaces/# prefix) and clamps
     * to [MIN_RANK, MAX_RANK]. Returns null for empty, non-numeric, zero, or negative input.
     */
    fun parsePositiveRankInput(raw: String): Int? {
        val cleaned = raw.trim().replace(",", "").replace("#", "").replace("_", "")
        if (cleaned.isEmpty() || !cleaned.all { it.isDigit() }) return null
        val parsedLong = cleaned.toLongOrNull() ?: return null
        if (parsedLong < MIN_RANK.toLong()) return null
        return clampRank(parsedLong)
    }

    /**
     * Parses a user-entered PP string, supporting both dot decimals and simple comma decimals
     * (e.g. "450,5") as well as thousands commas (e.g. "8,500"). Rejects NaN/Infinity/<= 0/out-of-range.
     */
    fun parsePositivePpInput(raw: String, maxPp: Double = MAX_TOTAL_PP): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = when {
            ',' in trimmed && '.' !in trimmed -> {
                val commaDecimal = Regex("""^\d+,\d{1,2}$""")
                if (commaDecimal.matches(trimmed)) trimmed.replace(',', '.') else trimmed.replace(",", "")
            }
            ',' in trimmed && '.' in trimmed -> trimmed.replace(",", "")
            else -> trimmed
        }
        val parsed = normalized.toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() && it > 0.0 && it <= maxPp }
    }

    /**
     * Extracts valid non-negative finite PP values sorted descending, capped to [limit].
     */
    fun extractTopScores(
        scores: Iterable<Double?>,
        limit: Int = TOP_SCORES_LIMIT
    ): List<Double> {
        if (limit <= 0) return emptyList()
        return scores
            .asSequence()
            .filterNotNull()
            .filter { it.isFinite() && it >= 0.0 }
            .sortedDescending()
            .take(limit)
            .toList()
    }

    /**
     * Computes osu! weighted top-plays sum: sum(pp * 0.95^index), ignoring non-finite and negative entries.
     */
    fun computeWeightedTopSum(
        scores: Iterable<Double?>,
        limit: Int = TOP_SCORES_LIMIT
    ): Double {
        val top = extractTopScores(scores, limit)
        var sum = 0.0
        for (idx in top.indices) {
            val term = top[idx] * WEIGHT_BASE.pow(idx)
            if (term.isFinite()) {
                sum += term
            }
        }
        return if (sum.isFinite() && sum >= 0.0) sum else 0.0
    }

    /**
     * Computes non-negative bonus PP, safely clamping unranked, low-playcount, or stale profiles
     * and ignoring NaN/Infinity inputs.
     */
    fun computeBonusPp(currentTotalPp: Double?, weightedTopSum: Double): Double {
        val safeTotal = sanitizeFiniteNonNegative(currentTotalPp) ?: return 0.0
        val safeWeighted = sanitizeFiniteNonNegative(weightedTopSum) ?: return 0.0
        val diff = safeTotal - safeWeighted
        return if (diff.isFinite()) diff.coerceAtLeast(0.0) else 0.0
    }

    /**
     * Simulates inserting [hypotheticalPp] into the player's top 100 plays.
     * Returns null if [hypotheticalPp] is not finite, <= 0, or exceeds [MAX_WHAT_IF_SCORE_PP].
     */
    fun simulateWhatIf(
        currentTotalPp: Double?,
        existingScores: Iterable<Double?>,
        hypotheticalPp: Double
    ): WhatIfPpSimulation? {
        if (!hypotheticalPp.isFinite() || hypotheticalPp <= 0.0 || hypotheticalPp > MAX_WHAT_IF_SCORE_PP) {
            return null
        }
        val cleanExisting = extractTopScores(existingScores, TOP_SCORES_LIMIT)
        val currentWeighted = computeWeightedTopSum(cleanExisting, TOP_SCORES_LIMIT)
        val updatedTop = (cleanExisting + hypotheticalPp)
            .sortedDescending()
            .take(TOP_SCORES_LIMIT)
        val newWeighted = computeWeightedTopSum(updatedTop, TOP_SCORES_LIMIT)

        val rawGain = newWeighted - currentWeighted
        val netGain = if (rawGain.isFinite()) rawGain.coerceAtLeast(0.0) else 0.0

        val safeTotal = sanitizeFiniteNonNegative(currentTotalPp)
        val bonusPp = computeBonusPp(safeTotal, currentWeighted)
        val newEstimatedTotal = if (safeTotal != null) {
            safeTotal + netGain
        } else {
            newWeighted + bonusPp
        }
        if (!newEstimatedTotal.isFinite()) return null

        val hypotheticalRank = updatedTop.indexOfFirst { it == hypotheticalPp }
            .takeIf { it >= 0 }
            ?.plus(1)

        return WhatIfPpSimulation(
            currentWeightedTop100 = currentWeighted,
            newWeightedTop100 = newWeighted,
            netGain = netGain,
            bonusPp = bonusPp,
            newEstimatedTotal = newEstimatedTotal,
            hypotheticalRankInTop100 = hypotheticalRank,
            updatedTopCount = updatedTop.size
        )
    }

    /**
     * Local fallback rank projection when live leaderboard lookup is unavailable.
     * Returns null when current rank or PP is missing/unranked/non-positive/non-finite.
     */
    fun estimateLocalFallbackRank(
        currentGlobalRank: Int?,
        currentTotalPp: Double?,
        newEstimatedTotal: Double
    ): Int? {
        val safeRank = sanitizeRank(currentGlobalRank) ?: return null
        val safeCurrentPp = sanitizeFinitePositive(currentTotalPp) ?: return null
        val safeNewPp = sanitizeFinitePositive(newEstimatedTotal) ?: return null
        val ratio = (safeCurrentPp / safeNewPp).coerceIn(0.0, 1.0)
        val exp = if (safeRank <= 10_000) 4.1 else 2.7
        val raw = safeRank.toDouble() * ratio.pow(exp)
        return safeRoundRank(raw, minRank = MIN_RANK, maxRank = safeRank)
    }

    fun defaultAnchorsForMode(mode: String): Pair<Double, Double> = when (mode.trim().lowercase()) {
        "taiko" -> 9_800.0 to 5_300.0
        "fruits", "catch", "ctb" -> 9_500.0 to 4_600.0
        "mania" -> 11_500.0 to 6_200.0
        else -> 12_200.0 to 7_000.0
    }

    fun buildCalibratedCurve(
        mode: String,
        observedPp1k: Double? = null,
        observedPp10k: Double? = null
    ): CalibratedRankCurve {
        val (fallback1k, fallback10k) = defaultAnchorsForMode(mode)
        val v1k = sanitizeFinitePositive(observedPp1k)
        val v10k = sanitizeFinitePositive(observedPp10k)
        val (p1k, p10k) = if (v1k != null && v10k != null && v1k > v10k && v10k > 50.0) {
            v1k to v10k
        } else {
            fallback1k to fallback10k
        }
        val ratio = (p1k / p10k).coerceAtLeast(1.01)
        val rawSlope = ln(ratio) / ln(10.0)
        val topSlope = if (rawSlope.isFinite()) rawSlope.coerceIn(0.18, 0.45) else 0.25
        val tailExponent = (topSlope * 1.45).coerceIn(0.28, 0.52)
        return CalibratedRankCurve(
            pp1000 = p1k,
            pp10000 = p10k,
            topSlope = topSlope,
            tailExponent = tailExponent
        )
    }

    fun estimatePpFromCurve(
        targetRank: Int,
        curve: CalibratedRankCurve
    ): Double {
        val clampedRank = clampRank(targetRank)
        val safeCurve = sanitizeCurve(curve)
        val estPp = when {
            clampedRank <= 1_000 -> {
                safeCurve.pp1000 * (clampedRank.toDouble() / 1_000.0).pow(-safeCurve.topSlope)
            }
            clampedRank <= 10_000 -> {
                val denom = ln(10_000.0) - ln(1_000.0)
                val t = ((ln(clampedRank.toDouble()) - ln(1_000.0)) / denom).coerceIn(0.0, 1.0)
                exp(ln(safeCurve.pp1000) * (1.0 - t) + ln(safeCurve.pp10000) * t)
            }
            else -> {
                safeCurve.pp10000 * (clampedRank.toDouble() / 10_000.0).pow(-safeCurve.tailExponent)
            }
        }
        return if (estPp.isFinite() && estPp >= 0.0) estPp else 0.0
    }

    fun estimateRankFromCurve(
        targetPp: Double,
        curve: CalibratedRankCurve,
        currentRankHint: Int? = null,
        currentPpHint: Double? = null
    ): Int {
        val cleanPp = clampTotalPp(targetPp)
        val safeCurve = sanitizeCurve(curve)
        val safeRankHint = sanitizeRank(currentRankHint)
        val safePpHint = sanitizeFinitePositive(currentPpHint)

        return if (
            safeRankHint != null &&
            safeRankHint > 10_000 &&
            safePpHint != null &&
            cleanPp >= safePpHint * 0.5 &&
            cleanPp <= safePpHint * 2.0
        ) {
            val raw = safeRankHint.toDouble() * (safePpHint / cleanPp).pow(1.0 / safeCurve.tailExponent)
            safeRoundRank(raw, minRank = 10_001, maxRank = MAX_RANK)
        } else if (cleanPp >= safeCurve.pp1000) {
            val raw = 1_000.0 * (safeCurve.pp1000 / cleanPp).pow(1.0 / safeCurve.topSlope)
            safeRoundRank(raw, minRank = MIN_RANK, maxRank = 1_000)
        } else if (cleanPp >= safeCurve.pp10000) {
            val rawDenom = ln(safeCurve.pp1000 / safeCurve.pp10000)
            val denom = if (rawDenom.isFinite()) rawDenom.coerceAtLeast(0.05) else 0.05
            val rawT = (ln(safeCurve.pp1000) - ln(cleanPp)) / denom
            val t = if (rawT.isFinite()) rawT.coerceIn(0.0, 1.0) else 0.0
            val raw = exp(ln(1_000.0) * (1.0 - t) + ln(10_000.0) * t)
            safeRoundRank(raw, minRank = 1_000, maxRank = 10_000)
        } else {
            val raw = 10_000.0 * (safeCurve.pp10000 / cleanPp).pow(1.0 / safeCurve.tailExponent)
            safeRoundRank(raw, minRank = 10_001, maxRank = MAX_RANK)
        }
    }

    /**
     * Interpolates rank linearly between adjacent leaderboard entries on a 50-entry page.
     * Guards against NaN/Infinity PP values, zero PP spans, and invalid ranks.
     */
    fun interpolateRankInPage(
        targetPp: Double,
        entries: List<Pair<Int?, Double?>>,
        page: Int,
        fallbackRank: Int
    ): Int {
        val cleanPp = clampTotalPp(targetPp)
        val safePage = page.coerceIn(1, 200)
        for (i in 0 until entries.lastIndex) {
            val (aRank, aPpRaw) = entries[i]
            val (bRank, bPpRaw) = entries[i + 1]
            val ap = sanitizeFiniteNonNegative(aPpRaw) ?: continue
            val bp = sanitizeFiniteNonNegative(bPpRaw) ?: continue
            if (cleanPp <= ap && cleanPp >= bp) {
                val ar = (sanitizeRank(aRank) ?: ((safePage - 1) * 50 + i + 1)).toDouble()
                val br = (sanitizeRank(bRank) ?: ((safePage - 1) * 50 + i + 2)).toDouble()
                val span = (ap - bp).coerceAtLeast(0.001)
                val frac = ((ap - cleanPp) / span).coerceIn(0.0, 1.0)
                return safeRoundRank(ar + (br - ar) * frac, minRank = MIN_RANK, maxRank = 10_000)
            }
        }
        return fallbackRank.coerceIn(MIN_RANK, 10_000)
    }

    private fun sanitizeCurve(curve: CalibratedRankCurve): CalibratedRankCurve {
        val (def1k, def10k) = defaultAnchorsForMode("osu")
        val p10k = sanitizeFinitePositive(curve.pp10000)?.coerceAtLeast(50.0) ?: def10k
        val p1k = sanitizeFinitePositive(curve.pp1000)?.takeIf { it > p10k } ?: maxOf(def1k, p10k * 1.1)
        val topSlope = if (curve.topSlope.isFinite() && curve.topSlope > 0.0) {
            curve.topSlope.coerceIn(0.18, 0.45)
        } else {
            0.25
        }
        val tailExponent = if (curve.tailExponent.isFinite() && curve.tailExponent > 0.0) {
            curve.tailExponent.coerceIn(0.28, 0.52)
        } else {
            0.36
        }
        return CalibratedRankCurve(
            pp1000 = p1k,
            pp10000 = p10k,
            topSlope = topSlope,
            tailExponent = tailExponent
        )
    }

    private val MOD_BUCKET_ORDER = listOf("NM", "HD", "HR", "DT/NC", "HDHR", "HDDT", "Other")

    fun classifyModBucket(mods: List<String>): String {
        val core = mods.asSequence()
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != "NM" && it != "NONE" && it != "CL" }
            .toSet()
        return when {
            core.isEmpty() -> "NM"
            core == setOf("HD") -> "HD"
            core == setOf("HR") -> "HR"
            core == setOf("DT") || core == setOf("NC") -> "DT/NC"
            core == setOf("HD", "HR") -> "HDHR"
            core == setOf("HD", "DT") || core == setOf("HD", "NC") -> "HDDT"
            else -> "Other"
        }
    }

    fun computeTopPlayAnalytics(scores: List<OsuScoreItem>): TopPlayAnalytics? {
        val topScores = scores.take(TOP_SCORES_LIMIT)
        if (topScores.isEmpty()) return null

        val counts = LinkedHashMap<String, Int>(MOD_BUCKET_ORDER.size)
        MOD_BUCKET_ORDER.forEach { counts[it] = 0 }

        var accSum = 0.0
        var accCount = 0
        var lenSum = 0L
        var lenCount = 0
        var srSum = 0.0
        var srCount = 0

        for (score in topScores) {
            val bucket = classifyModBucket(score.mods)
            counts[bucket] = (counts[bucket] ?: 0) + 1

            if (score.accuracy.isFinite() && score.accuracy >= 0.0) {
                accSum += score.accuracy
                accCount++
            }
            if (score.beatmapLengthSeconds > 0) {
                lenSum += score.beatmapLengthSeconds.toLong()
                lenCount++
            }
            if (score.starRating.isFinite() && score.starRating > 0.0) {
                srSum += score.starRating
                srCount++
            }
        }

        val totalPlays = topScores.size
        val distribution = MOD_BUCKET_ORDER.mapNotNull { label ->
            val cnt = counts[label] ?: 0
            if (cnt > 0) {
                ModDistributionEntry(
                    label = label,
                    count = cnt,
                    percentage = (cnt.toDouble() / totalPlays.toDouble()) * 100.0
                )
            } else {
                null
            }
        }.sortedByDescending { it.count }

        val validPps = extractTopScores(topScores.map { it.pp }, TOP_SCORES_LIMIT)
        val top1Pp = validPps.firstOrNull() ?: 0.0
        val tailPp = validPps.lastOrNull() ?: 0.0
        val ppSpread = (top1Pp - tailPp).coerceAtLeast(0.0)

        return TopPlayAnalytics(
            playCount = totalPlays,
            modDistribution = distribution,
            averageAccuracy = if (accCount > 0) accSum / accCount else 0.0,
            averageLengthSeconds = if (lenCount > 0) (lenSum.toDouble() / lenCount).roundToInt() else null,
            averageStarRating = if (srCount > 0) srSum / srCount else null,
            top1Pp = top1Pp,
            tailPp = tailPp,
            ppSpread = ppSpread
        )
    }

    fun calculateTargetRankPlan(
        targetRank: Int,
        targetPp: Double,
        currentTotalPp: Double?,
        existingScores: Iterable<Double?>,
        maxPlaysCap: Int = 50
    ): TargetRankPlan? {
        val cleanTargetPp = sanitizeFinitePositive(targetPp) ?: return null
        val clampedRank = clampRank(targetRank)
        val safeCap = maxPlaysCap.coerceIn(1, 200)

        val cleanExisting = extractTopScores(existingScores, TOP_SCORES_LIMIT)
        val currentWeighted = computeWeightedTopSum(cleanExisting, TOP_SCORES_LIMIT)
        val safeTotal = sanitizeFiniteNonNegative(currentTotalPp) ?: currentWeighted
        val bonusPp = computeBonusPp(safeTotal, currentWeighted)
        val effectiveTotal = if (safeTotal > 0.0) safeTotal else currentWeighted
        val ppGap = (cleanTargetPp - effectiveTotal).coerceAtLeast(0.0)

        val basePlayPp = cleanExisting.firstOrNull()?.takeIf { it > 0.0 }
            ?: (cleanTargetPp / 18.5).coerceAtLeast(50.0)

        val scenarioSpecs = listOf(
            "~Top #1" to basePlayPp,
            "~Top #1 +10%" to (basePlayPp * 1.10),
            "~Top #1 +20%" to (basePlayPp * 1.20)
        )

        val scenarios = scenarioSpecs.map { (label, rawScorePp) ->
            val scorePp = rawScorePp.coerceIn(1.0, MAX_WHAT_IF_SCORE_PP)
            if (ppGap <= 0.01) {
                TargetRankScenario(
                    label = label,
                    scorePp = scorePp,
                    playsNeeded = 0,
                    cappedAtLimit = false,
                    resultingTotalPp = effectiveTotal
                )
            } else {
                var runningList = cleanExisting
                var runningTotal = effectiveTotal
                var playsNeeded = 0
                var reached = false
                for (step in 1..safeCap) {
                    val sim = simulateWhatIf(runningTotal, runningList, scorePp) ?: break
                    runningList = (runningList + scorePp)
                        .sortedDescending()
                        .take(TOP_SCORES_LIMIT)
                    runningTotal = sim.newEstimatedTotal
                    playsNeeded = step
                    if (runningTotal + 1e-6 >= cleanTargetPp) {
                        reached = true
                        break
                    }
                    if (sim.netGain < 1e-6) {
                        break
                    }
                }
                TargetRankScenario(
                    label = label,
                    scorePp = scorePp,
                    playsNeeded = playsNeeded,
                    cappedAtLimit = !reached,
                    resultingTotalPp = runningTotal
                )
            }
        }

        return TargetRankPlan(
            targetRank = clampedRank,
            targetPp = cleanTargetPp,
            currentTotalPp = effectiveTotal,
            ppGap = ppGap,
            bonusPp = bonusPp,
            scenarios = scenarios
        )
    }
}
