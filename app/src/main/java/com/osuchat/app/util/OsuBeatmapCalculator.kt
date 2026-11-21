package com.osuchat.app.util

import com.osuchat.app.domain.model.BeatmapMetadata
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class SimulatedBeatmapStats(
    val starRating: Double,
    val cs: Double,
    val ar: Double,
    val od: Double,
    val hp: Double,
    val bpm: Double,
    val totalLengthSeconds: Int,
    val drainLengthSeconds: Int,
    val estimatedFcPp: Double,
    val activeMods: Set<String>,
    val accuracyPercent: Double,
    val isExactRosuPp: Boolean = false,
    val nMisses: Int = 0,
    val combo: Int? = null,
    val maxCombo: Int? = null,
    val clockRate: Double = 1.0,
    val isLazer: Boolean = true
)

object OsuBeatmapCalculator {

    val SUPPORTED_MODS = listOf("HD", "HR", "DT", "HT", "EZ", "FL")

    fun toggleMod(current: Set<String>, mod: String): Set<String> {
        val norm = mod.trim().uppercase()
        if (norm.isEmpty()) return current
        val next = current.toMutableSet()
        if (norm in next) {
            next.remove(norm)
            return next
        }
        when (norm) {
            "HR" -> next.remove("EZ")
            "EZ" -> next.remove("HR")
            "DT" -> next.remove("HT")
            "HT" -> next.remove("DT")
        }
        next.add(norm)
        return next
    }

    fun simulate(
        meta: BeatmapMetadata,
        mods: Set<String>,
        accuracyPercent: Double,
        nMisses: Int = 0,
        combo: Int? = null,
        clockRate: Double? = null,
        isLazer: Boolean = true
    ): SimulatedBeatmapStats {
        val normMods = mods.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        val hasHr = "HR" in normMods
        val hasEz = "EZ" in normMods && !hasHr
        val hasDt = "DT" in normMods || "NC" in normMods
        val hasHt = "HT" in normMods && !hasDt
        val hasHd = "HD" in normMods
        val hasFl = "FL" in normMods

        val acc = if (accuracyPercent.isFinite()) accuracyPercent.coerceIn(50.0, 100.0) else 100.0
        val customRate = clockRate?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.5, 2.0)
        val effectiveClockRate = customRate ?: when {
            hasDt -> 1.5
            hasHt -> 0.75
            else -> 1.0
        }

        val safeCs = meta.cs.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeHp = meta.hp.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeAr = meta.ar.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeOd = meta.od.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeBpm = meta.bpm.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeSr = meta.starRating.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

        val scaledCs = when {
            hasHr -> min(10.0, safeCs * 1.3)
            hasEz -> safeCs * 0.5
            else -> safeCs
        }
        val scaledHp = when {
            hasHr -> min(10.0, safeHp * 1.4)
            hasEz -> safeHp * 0.5
            else -> safeHp
        }
        val baseAr = when {
            hasHr -> min(10.0, safeAr * 1.4)
            hasEz -> safeAr * 0.5
            else -> safeAr
        }
        val baseOd = when {
            hasHr -> min(10.0, safeOd * 1.4)
            hasEz -> safeOd * 0.5
            else -> safeOd
        }

        val hasRateChange = abs(effectiveClockRate - 1.0) > 1e-4
        val effectiveAr = if (hasRateChange) convertArForClockRate(baseAr, effectiveClockRate) else baseAr
        val modeNorm = meta.mode.trim().lowercase()
        val effectiveOd = if (hasRateChange) convertOdForClockRate(baseOd, effectiveClockRate, modeNorm) else baseOd

        val effectiveBpm = safeBpm * effectiveClockRate
        val effectiveTotalSec = (meta.totalLengthSeconds / effectiveClockRate).roundToInt().coerceAtLeast(1)
        val effectiveDrainSec = if (meta.drainLengthSeconds > 0) {
            (meta.drainLengthSeconds / effectiveClockRate).roundToInt().coerceAtLeast(1)
        } else {
            effectiveTotalSec
        }

        val effectiveSr = estimateStarRating(
            baseSr = safeSr,
            mode = modeNorm,
            hasHr = hasHr,
            hasEz = hasEz,
            hasDt = hasDt,
            hasHt = hasHt,
            customRate = customRate,
            effectiveClockRate = effectiveClockRate
        )
        val rawFcPp = estimateFcPp(
            starRating = effectiveSr,
            mode = modeNorm,
            accuracyPercent = acc,
            od = effectiveOd,
            ar = effectiveAr,
            lengthSeconds = effectiveDrainSec,
            maxCombo = meta.maxCombo,
            hasHd = hasHd,
            hasHr = hasHr,
            hasDt = effectiveClockRate > 1.05,
            hasEz = hasEz,
            hasFl = hasFl
        )

        val safeMisses = nMisses.coerceAtLeast(0)
        val resolvedMaxCombo = meta.maxCombo?.takeIf { it > 0 }
        val safeCombo = combo?.takeIf { it > 0 }?.let { c ->
            resolvedMaxCombo?.let { c.coerceIn(1, it) } ?: c
        }
        val comboScale = if (resolvedMaxCombo != null && resolvedMaxCombo > 0) {
            val targetCombo = (safeCombo ?: (resolvedMaxCombo - safeMisses)).coerceIn(1, resolvedMaxCombo)
            (targetCombo.toDouble() / resolvedMaxCombo.toDouble()).pow(0.8)
        } else {
            1.0
        }
        val missScale = if (safeMisses > 0) 0.97.pow(safeMisses.toDouble()) else 1.0
        val adjustedPp = (rawFcPp * comboScale * missScale).coerceAtLeast(0.0)

        return SimulatedBeatmapStats(
            starRating = effectiveSr,
            cs = scaledCs,
            ar = effectiveAr,
            od = effectiveOd,
            hp = scaledHp,
            bpm = effectiveBpm,
            totalLengthSeconds = effectiveTotalSec,
            drainLengthSeconds = effectiveDrainSec,
            estimatedFcPp = adjustedPp,
            activeMods = normMods,
            accuracyPercent = acc,
            nMisses = safeMisses,
            combo = safeCombo,
            maxCombo = resolvedMaxCombo,
            clockRate = effectiveClockRate,
            isLazer = isLazer
        )
    }

    private fun convertArForClockRate(ar: Double, rate: Double): Double {
        val preempt = if (ar < 5.0) 1800.0 - 120.0 * ar else 1200.0 - 150.0 * (ar - 5.0)
        val newPreempt = preempt / rate
        return if (newPreempt > 1200.0) {
            (1800.0 - newPreempt) / 120.0
        } else {
            5.0 + (1200.0 - newPreempt) / 150.0
        }
    }

    private fun convertOdForClockRate(od: Double, rate: Double, mode: String): Double {
        return when (mode) {
            "taiko" -> {
                val window = 50.0 - 3.0 * od
                (50.0 - window / rate) / 3.0
            }
            "mania" -> {
                val window = 64.0 - 3.0 * od
                (64.0 - window / rate) / 3.0
            }
            else -> {
                val window = 80.0 - 6.0 * od
                (80.0 - window / rate) / 6.0
            }
        }
    }

    private fun estimateStarRating(
        baseSr: Double,
        mode: String,
        hasHr: Boolean,
        hasEz: Boolean,
        hasDt: Boolean,
        hasHt: Boolean,
        customRate: Double?,
        effectiveClockRate: Double
    ): Double {
        if (baseSr <= 0.0) return 0.0
        var mult = 1.0
        if (hasHr) {
            mult *= when (mode) {
                "taiko", "mania" -> 1.05
                else -> 1.08
            }
        } else if (hasEz) {
            mult *= 0.84
        }
        if (customRate == null && hasDt && !hasHt) {
            mult *= when (mode) {
                "mania" -> 1.32
                else -> (1.38 * (5.5 / baseSr.coerceAtLeast(1.0)).pow(0.05)).coerceIn(1.30, 1.44)
            }
        } else if (abs(effectiveClockRate - 1.0) > 1e-4) {
            val rateExp = when (mode) {
                "mania" -> 0.68
                else -> 0.78
            }
            mult *= effectiveClockRate.pow(rateExp).coerceIn(0.55, 1.75)
        }
        return (baseSr * mult).coerceAtLeast(0.0)
    }

    private fun estimateFcPp(
        starRating: Double,
        mode: String,
        accuracyPercent: Double,
        od: Double,
        ar: Double,
        lengthSeconds: Int,
        maxCombo: Int?,
        hasHd: Boolean,
        hasHr: Boolean,
        hasDt: Boolean,
        hasEz: Boolean,
        hasFl: Boolean
    ): Double {
        if (starRating <= 0.05) return 0.0
        val accFrac = (accuracyPercent / 100.0).coerceIn(0.5, 1.0)
        val estimatedObjects = max(
            120,
            maxCombo ?: (lengthSeconds * 4).coerceAtLeast(150)
        )
        val lengthBonus = (0.95 + 0.4 * min(1.0, estimatedObjects / 2000.0) +
            if (estimatedObjects > 2000) kotlin.math.log10(estimatedObjects / 2000.0) * 0.5 else 0.0)
            .coerceIn(0.85, 1.45)

        val rawPp = when (mode) {
            "taiko" -> {
                val strain = (5.0 * max(1.0, starRating / 0.115) - 4.0).pow(2.25) / 1150.0
                val strainWithLength = strain * lengthBonus * accFrac.pow(2.5)
                val accPp = 60.0 * ((accFrac - 0.85).coerceAtLeast(0.0) / 0.15).pow(2.0) *
                    (1.0 + od.coerceAtLeast(0.0) * 0.05)
                var modBonus = 1.0
                if (hasHd) modBonus *= 1.075
                if (hasFl) modBonus *= 1.05
                if (hasEz) modBonus *= 0.90
                (strainWithLength + accPp) * modBonus
            }
            "fruits" -> {
                val base = (5.0 * max(1.0, starRating / 0.145) - 4.0).pow(2.0) / 100.0
                val arBonus = when {
                    ar > 9.0 -> 1.0 + 0.1 * (ar - 9.0)
                    ar < 8.0 -> 1.0 + 0.025 * (8.0 - ar)
                    else -> 1.0
                }
                val accTerm = accFrac.pow(5.5)
                var modBonus = 1.0
                if (hasHd) modBonus *= 1.05 + 0.075 * (10.0 - min(10.0, ar))
                if (hasFl) modBonus *= 1.35 * (lengthBonus / 1.1)
                if (hasEz) modBonus *= 0.85
                base * lengthBonus * arBonus * accTerm * modBonus
            }
            "mania" -> {
                val strain = (8.0 * max(1.0, starRating - 0.15)).pow(2.2) * accFrac.pow(3.5)
                val accScale = ((accuracyPercent - 80.0).coerceAtLeast(0.0) / 20.0).pow(1.4)
                var modBonus = 1.0
                if (hasEz) modBonus *= 0.50
                (strain * (0.45 + 0.55 * accScale) * (0.9 + 0.1 * min(1.0, estimatedObjects / 1500.0))) * modBonus
            }
            else -> {
                // osu!standard benchmark-calibrated fallback FC curve
                val baseStrain = (5.0 * max(1.0, starRating / 0.0675) - 4.0).pow(3.0) / 100000.0
                val aimSpeedValue = baseStrain * 0.26 * lengthBonus * (0.5 + accFrac / 2.0) *
                    (0.95 + (od * od) / 2500.0)
                val accPp = (1.52163.pow(od.coerceAtMost(11.0)) * accFrac.pow(24.0) * 2.83) *
                    min(1.15, (estimatedObjects / 1000.0).pow(0.3))
                var modMult = 1.0
                if (hasHd) modMult *= 1.06
                if (hasHr && hasHd) modMult *= 1.02
                if (hasFl) modMult *= 1.12
                if (hasEz) modMult *= 0.88
                (aimSpeedValue + accPp) * modMult * if (hasDt) 1.02 else 1.0
            }
        }
        return rawPp.coerceAtLeast(0.0)
    }
}
